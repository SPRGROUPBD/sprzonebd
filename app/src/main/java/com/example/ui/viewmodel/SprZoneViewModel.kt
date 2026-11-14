package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.SessionPreferences
import com.example.data.local.SprZoneDatabase
import com.example.data.model.AddressEntity
import com.example.data.model.CartItemEntity
import com.example.data.model.MerchantPaymentSettings
import com.example.data.model.OrderEntity
import com.example.data.model.PaymentMethod
import com.example.data.model.ProductEntity
import com.example.data.model.ReviewEntity
import com.example.data.model.StoreConstants
import com.example.data.model.UserAccountEntity
import com.example.data.repository.PromoResult
import com.example.data.repository.SprZoneRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ProductSortOption(val label: String) {
    FEATURED("Featured"),
    PRICE_LOW_HIGH("Price: Low to High"),
    PRICE_HIGH_LOW("Price: High to Low"),
    TOP_RATED("Top Rated")
}

data class CartSummary(
    val itemCount: Int = 0,
    val subtotalBdt: Long = 0L,
    val discountBdt: Long = 0L,
    val deliveryFeeBdt: Long = 0L,
    val totalBdt: Long = 0L,
    val appliedPromo: PromoResult? = null,
    val freeDeliveryRemainingBdt: Long = StoreConstants.FREE_DELIVERY_THRESHOLD_BDT
)

data class CatalogFilterState(
    val searchQuery: String = "",
    val selectedCategory: String = "All",
    val sortOption: ProductSortOption = ProductSortOption.FEATURED,
    val inStockOnly: Boolean = false,
    val minRating: Double = 0.0
)

@OptIn(ExperimentalCoroutinesApi::class)
class SprZoneViewModel(
    private val repository: SprZoneRepository
) : ViewModel() {

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val _filterState = MutableStateFlow(CatalogFilterState())
    val filterState: StateFlow<CatalogFilterState> = _filterState.asStateFlow()

    private val _promoCodeInput = MutableStateFlow("")

    private val _flashCountdownSeconds = MutableStateFlow(5 * 3600L + 42 * 60L + 19L)
    val flashCountdownSeconds: StateFlow<Long> = _flashCountdownSeconds.asStateFlow()

    val paymentSettings: StateFlow<MerchantPaymentSettings> = repository.paymentSettings

    val availableCoupons: StateFlow<List<String>> = repository.coupons
        .map { map -> map.keys.toList().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val loggedInEmail: StateFlow<String?> = repository.loggedInEmailFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val isDarkMode: StateFlow<Boolean> = repository.isDarkModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val currentUser: StateFlow<UserAccountEntity?> = loggedInEmail
        .flatMapLatest { email ->
            if (email.isNullOrBlank()) flowOf(null) else repository.observeUser(email)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val allProducts: StateFlow<List<ProductEntity>> = repository.observeAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val filteredProducts: StateFlow<List<ProductEntity>> = combine(allProducts, _filterState) { list, filter ->
        list.filter { product ->
            val matchesCategory = filter.selectedCategory == "All" || product.category == filter.selectedCategory
            val q = filter.searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                product.name.lowercase().contains(q) ||
                product.brand.lowercase().contains(q) ||
                product.category.lowercase().contains(q) ||
                product.description.lowercase().contains(q)
            val matchesStock = !filter.inStockOnly || product.stock > 0
            val matchesRating = product.rating >= filter.minRating
            matchesCategory && matchesQuery && matchesStock && matchesRating
        }.let { filtered ->
            when (filter.sortOption) {
                ProductSortOption.FEATURED -> filtered.sortedWith(
                    compareByDescending<ProductEntity> { it.isFeatured }.thenByDescending { it.rating }
                )
                ProductSortOption.PRICE_LOW_HIGH -> filtered.sortedBy { it.priceBdt }
                ProductSortOption.PRICE_HIGH_LOW -> filtered.sortedByDescending { it.priceBdt }
                ProductSortOption.TOP_RATED -> filtered.sortedByDescending { it.rating }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val activeCartOwnerEmail: Flow<String> = loggedInEmail.flatMapLatest { email ->
        flowOf(email ?: StoreConstants.GUEST_EMAIL)
    }

    val cartItems: StateFlow<List<CartItemEntity>> = activeCartOwnerEmail
        .flatMapLatest { owner -> repository.observeCart(owner) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val wishlistIds: StateFlow<List<String>> = activeCartOwnerEmail
        .flatMapLatest { owner -> repository.observeWishlistIds(owner) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val wishlistProducts: StateFlow<List<ProductEntity>> = combine(allProducts, wishlistIds) { products, ids ->
        val idSet = ids.toSet()
        products.filter { it.id in idSet }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val addresses: StateFlow<List<AddressEntity>> = loggedInEmail
        .flatMapLatest { email ->
            if (email.isNullOrBlank()) flowOf(emptyList()) else repository.observeAddresses(email)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val cartSummary: StateFlow<CartSummary> = combine(
        cartItems,
        addresses,
        _promoCodeInput
    ) { items, addrList, promoInput ->
        val itemCount = items.sumOf { it.quantity }
        val subtotal = items.sumOf { it.unitPriceBdt * it.quantity }
        val promo = if (promoInput.isNotBlank()) repository.evaluatePromo(promoInput, subtotal) else null
        val discount = promo?.discountBdt ?: 0L
        val defaultDivision = addrList.firstOrNull { it.isDefault }?.division ?: addrList.firstOrNull()?.division ?: "Dhaka"
        val delivery = when {
            items.isEmpty() -> 0L
            subtotal >= StoreConstants.FREE_DELIVERY_THRESHOLD_BDT || promo?.freeShipping == true -> 0L
            else -> StoreConstants.deliveryFeeForDivision(defaultDivision)
        }
        val total = (subtotal - discount + delivery).coerceAtLeast(0L)
        val remainingForFree = (StoreConstants.FREE_DELIVERY_THRESHOLD_BDT - subtotal).coerceAtLeast(0L)
        CartSummary(
            itemCount = itemCount,
            subtotalBdt = subtotal,
            discountBdt = discount,
            deliveryFeeBdt = delivery,
            totalBdt = total,
            appliedPromo = promo,
            freeDeliveryRemainingBdt = remainingForFree
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CartSummary())

    val userOrders: StateFlow<List<OrderEntity>> = loggedInEmail
        .flatMapLatest { email ->
            if (email.isNullOrBlank()) flowOf(emptyList()) else repository.observeUserOrders(email)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureSeededAndSync()
        }
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                val next = _flashCountdownSeconds.value - 1L
                _flashCountdownSeconds.value = if (next <= 0L) 6 * 3600L else next
            }
        }
    }

    fun refreshStore() {
        viewModelScope.launch {
            repository.syncRemoteCatalogAndSettings()
            loggedInEmail.value?.let { repository.syncUserOrdersFromRemote(it) }
            _snackbarEvents.tryEmit("Store synced with SPR Zone BD")
        }
    }

    fun observeProduct(id: String): Flow<ProductEntity?> = repository.observeProductById(id)
    fun observeReviews(productId: String): Flow<List<ReviewEntity>> = repository.observeReviews(productId)

    fun updateSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    fun selectCategory(category: String) {
        _filterState.value = _filterState.value.copy(selectedCategory = category)
    }

    fun updateSortOption(option: ProductSortOption) {
        _filterState.value = _filterState.value.copy(sortOption = option)
    }

    fun updateInStockOnly(only: Boolean) {
        _filterState.value = _filterState.value.copy(inStockOnly = only)
    }

    fun updateMinRating(rating: Double) {
        _filterState.value = _filterState.value.copy(minRating = rating)
    }

    fun clearFilters() {
        _filterState.value = CatalogFilterState()
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDarkMode(enabled)
        }
    }

    fun applyPromoCode(code: String) {
        val subtotal = cartSummary.value.subtotalBdt
        if (code.isBlank()) {
            _promoCodeInput.value = ""
            return
        }
        val promo = repository.evaluatePromo(code, subtotal)
        if (promo != null) {
            _promoCodeInput.value = promo.code
            _snackbarEvents.tryEmit(promo.message)
        } else {
            _snackbarEvents.tryEmit("Invalid promo code or minimum order requirement not met.")
        }
    }

    fun addToCart(product: ProductEntity, variant: String = "", quantity: Int = 1, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val owner = loggedInEmail.value ?: StoreConstants.GUEST_EMAIL
            val result = repository.addToCart(owner, product, variant, quantity)
            if (result.isSuccess) {
                _snackbarEvents.tryEmit("Added ${product.name} to cart")
                onSuccess()
            } else {
                _snackbarEvents.tryEmit(result.exceptionOrNull()?.message ?: "Unable to add item")
            }
        }
    }

    fun updateCartQuantity(item: CartItemEntity, newQty: Int) {
        viewModelScope.launch {
            repository.updateCartQuantity(item, newQty)
        }
    }

    fun removeCartItem(itemId: Long) {
        viewModelScope.launch {
            repository.removeCartItem(itemId)
        }
    }

    fun toggleWishlist(productId: String) {
        viewModelScope.launch {
            val owner = loggedInEmail.value ?: StoreConstants.GUEST_EMAIL
            val added = repository.toggleWishlist(owner, productId)
            _snackbarEvents.tryEmit(if (added) "Saved to wishlist" else "Removed from wishlist")
        }
    }

    fun register(
        fullName: String,
        email: String,
        phone: String,
        password: String,
        onResult: (Result<UserAccountEntity>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.registerUser(fullName, email, phone, password)
            if (res.isSuccess) {
                _snackbarEvents.tryEmit("Welcome to SPR Zone BD, ${res.getOrThrow().fullName}!")
            }
            onResult(res)
        }
    }

    fun login(
        email: String,
        password: String,
        onResult: (Result<UserAccountEntity>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.loginUser(email, password)
            if (res.isSuccess) {
                _snackbarEvents.tryEmit("Signed in as ${res.getOrThrow().fullName}")
            }
            onResult(res)
        }
    }

    fun updateProfile(
        fullName: String,
        phone: String,
        bio: String,
        onResult: (Result<UserAccountEntity>) -> Unit
    ) {
        viewModelScope.launch {
            val email = loggedInEmail.value
            if (email.isNullOrBlank()) {
                onResult(Result.failure(IllegalStateException("Please sign in first.")))
                return@launch
            }
            val res = repository.updateProfile(email, fullName, phone, bio)
            if (res.isSuccess) {
                _snackbarEvents.tryEmit("Profile updated")
            }
            onResult(res)
        }
    }

    fun deleteProfile(onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val email = loggedInEmail.value
            if (email.isNullOrBlank()) {
                onResult(Result.failure(IllegalStateException("No active profile.")))
                return@launch
            }
            val res = repository.deleteProfile(email)
            if (res.isSuccess) {
                _promoCodeInput.value = ""
                _snackbarEvents.tryEmit("Profile deleted")
            }
            onResult(res)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            _promoCodeInput.value = ""
            _snackbarEvents.tryEmit("Signed out")
        }
    }

    fun saveAddress(
        label: String,
        recipientName: String,
        phone: String,
        division: String,
        districtThana: String,
        streetAddress: String,
        makeDefault: Boolean,
        onResult: (Result<Long>) -> Unit
    ) {
        viewModelScope.launch {
            val email = loggedInEmail.value
            if (email.isNullOrBlank()) {
                onResult(Result.failure(IllegalStateException("Please sign in to save addresses.")))
                return@launch
            }
            val res = repository.saveAddress(
                userEmail = email,
                label = label,
                recipientName = recipientName,
                phone = phone,
                division = division,
                districtThana = districtThana,
                streetAddress = streetAddress,
                makeDefault = makeDefault
            )
            if (res.isSuccess) {
                _snackbarEvents.tryEmit("Shipping address saved")
            }
            onResult(res)
        }
    }

    fun updateAddress(
        addressId: Long,
        label: String,
        recipientName: String,
        phone: String,
        division: String,
        districtThana: String,
        streetAddress: String,
        makeDefault: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val email = loggedInEmail.value
            if (email.isNullOrBlank()) {
                onResult(Result.failure(IllegalStateException("Please sign in first.")))
                return@launch
            }
            val res = repository.updateAddress(
                userEmail = email,
                addressId = addressId,
                label = label,
                recipientName = recipientName,
                phone = phone,
                division = division,
                districtThana = districtThana,
                streetAddress = streetAddress,
                makeDefault = makeDefault
            )
            if (res.isSuccess) {
                _snackbarEvents.tryEmit("Shipping address updated")
            }
            onResult(res)
        }
    }

    fun setDefaultAddress(addressId: Long) {
        viewModelScope.launch {
            val email = loggedInEmail.value ?: return@launch
            repository.setDefaultAddress(email, addressId)
        }
    }

    fun deleteAddress(addressId: Long) {
        viewModelScope.launch {
            repository.deleteAddress(addressId)
            _snackbarEvents.tryEmit("Address deleted")
        }
    }

    fun placeOrder(
        address: AddressEntity,
        paymentMethod: PaymentMethod,
        senderNumberInput: String,
        trxIdInput: String,
        onResult: (Result<OrderEntity>) -> Unit
    ) {
        viewModelScope.launch {
            val email = loggedInEmail.value
            if (email.isNullOrBlank()) {
                onResult(Result.failure(IllegalStateException("Please sign in to complete your order.")))
                return@launch
            }
            val res = repository.placeOrder(
                userEmail = email,
                address = address,
                cartItems = cartItems.value,
                promoCode = _promoCodeInput.value,
                paymentMethod = paymentMethod,
                senderNumberInput = senderNumberInput,
                trxIdInput = trxIdInput
            )
            if (res.isSuccess) {
                _promoCodeInput.value = ""
                val order = res.getOrThrow()
                _snackbarEvents.tryEmit("Order ${order.orderId} (${order.orderStatus})!")
            }
            onResult(res)
        }
    }

    fun cancelOrder(orderId: String) {
        viewModelScope.launch {
            val res = repository.cancelOrder(orderId)
            if (res.isSuccess) {
                _snackbarEvents.tryEmit("Order $orderId cancelled")
            } else {
                _snackbarEvents.tryEmit(res.exceptionOrNull()?.message ?: "Cannot cancel this order")
            }
        }
    }

    fun trackOrderById(orderId: String, onResult: (Result<OrderEntity>) -> Unit) {
        viewModelScope.launch {
            onResult(repository.trackOrderById(orderId))
        }
    }

    fun submitReview(
        productId: String,
        rating: Int,
        comment: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val user = currentUser.value
            if (user == null) {
                onResult(Result.failure(IllegalStateException("Please sign in to post a verified review.")))
                return@launch
            }
            val res = repository.submitReview(productId, user.fullName, user.email, rating, comment)
            if (res.isSuccess) {
                _snackbarEvents.tryEmit("Thank you for your review!")
            }
            onResult(res)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = SprZoneDatabase.getInstance(app)
                val prefs = SessionPreferences(app)
                SprZoneViewModel(SprZoneRepository(db, prefs, filesDir = app.filesDir))
            }
        }
    }
}
