package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.ui.components.SprBrandLogo
import com.example.ui.screens.AccountScreen
import com.example.ui.screens.CartScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.CheckoutScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OrdersScreen
import com.example.ui.screens.ProductDetailScreen
import com.example.ui.theme.SprZoneTheme
import com.example.ui.viewmodel.SprZoneViewModel
import kotlinx.coroutines.flow.collectLatest

object SprRoutes {
    const val HOME = "home"
    const val CATEGORIES = "categories"
    const val CART = "cart"
    const val ORDERS = "orders"
    const val ACCOUNT = "account"
    const val PRODUCT_DETAIL = "product/{productId}"
    const val CHECKOUT = "checkout"

    fun productDetail(productId: String) = "product/$productId"
}

private data class TopTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TOP_TABS = listOf(
    TopTab(SprRoutes.HOME, R.string.nav_home, Icons.Filled.Storefront, Icons.Outlined.Storefront),
    TopTab(SprRoutes.CATEGORIES, R.string.nav_categories, Icons.Filled.Category, Icons.Outlined.Category),
    TopTab(SprRoutes.CART, R.string.nav_cart, Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
    TopTab(SprRoutes.ORDERS, R.string.nav_orders, Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    TopTab(SprRoutes.ACCOUNT, R.string.nav_account, Icons.Filled.Person, Icons.Outlined.Person)
)

@Composable
fun SprZoneApp(viewModel: SprZoneViewModel) {
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

    SprZoneTheme(darkTheme = isDarkMode) {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }

        val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
        val filteredProducts by viewModel.filteredProducts.collectAsStateWithLifecycle()
        val filterState by viewModel.filterState.collectAsStateWithLifecycle()
        val wishlistIds by viewModel.wishlistIds.collectAsStateWithLifecycle()
        val wishlistProducts by viewModel.wishlistProducts.collectAsStateWithLifecycle()
        val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
        val cartSummary by viewModel.cartSummary.collectAsStateWithLifecycle()
        val availableCoupons by viewModel.availableCoupons.collectAsStateWithLifecycle()
        val countdown by viewModel.flashCountdownSeconds.collectAsStateWithLifecycle()
        val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
        val addresses by viewModel.addresses.collectAsStateWithLifecycle()
        val userOrders by viewModel.userOrders.collectAsStateWithLifecycle()
        val paymentSettings by viewModel.paymentSettings.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            viewModel.snackbarEvents.collectLatest { message ->
                snackbarHostState.showSnackbar(message)
            }
        }

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val showChromeNav = currentDestination?.route in TOP_TABS.map { it.route }

        fun navigateTopTab(route: String) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (showChromeNav && !useRail) {
                        NavigationBar {
                            TOP_TABS.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopTab(tab.route) },
                                    icon = {
                                        val iconVector = if (selected) tab.selectedIcon else tab.unselectedIcon
                                        if (tab.route == SprRoutes.CART && cartSummary.itemCount > 0) {
                                            BadgedBox(
                                                badge = { Badge { Text("${cartSummary.itemCount}") } }
                                            ) {
                                                Icon(iconVector, contentDescription = stringResource(tab.labelRes))
                                            }
                                        } else {
                                            Icon(iconVector, contentDescription = stringResource(tab.labelRes))
                                        }
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag("nav_${tab.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (showChromeNav && useRail) {
                        NavigationRail(
                            header = {
                                SprBrandLogo(
                                    size = 38.dp,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                                )
                            },
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Spacer(modifier = Modifier.weight(0.2f))
                            TOP_TABS.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopTab(tab.route) },
                                    icon = {
                                        val iconVector = if (selected) tab.selectedIcon else tab.unselectedIcon
                                        if (tab.route == SprRoutes.CART && cartSummary.itemCount > 0) {
                                            BadgedBox(
                                                badge = { Badge { Text("${cartSummary.itemCount}") } }
                                            ) {
                                                Icon(iconVector, contentDescription = stringResource(tab.labelRes))
                                            }
                                        } else {
                                            Icon(iconVector, contentDescription = stringResource(tab.labelRes))
                                        }
                                    },
                                    label = { Text(stringResource(tab.labelRes)) }
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = SprRoutes.HOME
                        ) {
                            composable(SprRoutes.HOME) {
                                HomeScreen(
                                    products = filteredProducts,
                                    allProducts = allProducts,
                                    wishlistIds = wishlistIds,
                                    filterState = filterState,
                                    countdownSeconds = countdown,
                                    isDarkMode = isDarkMode,
                                    onSearchChange = viewModel::updateSearchQuery,
                                    onSelectCategory = viewModel::selectCategory,
                                    onSortChange = viewModel::updateSortOption,
                                    onToggleDarkMode = viewModel::setDarkMode,
                                    onRefreshStore = viewModel::refreshStore,
                                    onProductClick = { id -> navController.navigate(SprRoutes.productDetail(id)) },
                                    onToggleWishlist = viewModel::toggleWishlist,
                                    onQuickAdd = { prod -> viewModel.addToCart(prod) },
                                    onOpenTrackOrder = { navigateTopTab(SprRoutes.ORDERS) },
                                    onOpenCategories = { navigateTopTab(SprRoutes.CATEGORIES) }
                                )
                            }

                            composable(SprRoutes.CATEGORIES) {
                                CategoriesScreen(
                                    allProducts = allProducts,
                                    filteredProducts = filteredProducts,
                                    wishlistIds = wishlistIds,
                                    filterState = filterState,
                                    onSearchChange = viewModel::updateSearchQuery,
                                    onSelectCategory = viewModel::selectCategory,
                                    onSortChange = viewModel::updateSortOption,
                                    onInStockToggle = viewModel::updateInStockOnly,
                                    onMinRatingChange = viewModel::updateMinRating,
                                    onClearFilters = viewModel::clearFilters,
                                    onProductClick = { id -> navController.navigate(SprRoutes.productDetail(id)) },
                                    onToggleWishlist = viewModel::toggleWishlist,
                                    onQuickAdd = { prod -> viewModel.addToCart(prod) }
                                )
                            }

                            composable(SprRoutes.CART) {
                                CartScreen(
                                    cartItems = cartItems,
                                    summary = cartSummary,
                                    availableCoupons = availableCoupons,
                                    isSignedIn = currentUser != null,
                                    onUpdateQuantity = viewModel::updateCartQuantity,
                                    onRemoveItem = viewModel::removeCartItem,
                                    onApplyPromo = viewModel::applyPromoCode,
                                    onProductClick = { id -> navController.navigate(SprRoutes.productDetail(id)) },
                                    onCheckoutClick = {
                                        if (currentUser == null) {
                                            navigateTopTab(SprRoutes.ACCOUNT)
                                        } else {
                                            navController.navigate(SprRoutes.CHECKOUT)
                                        }
                                    },
                                    onBrowseShop = { navigateTopTab(SprRoutes.HOME) }
                                )
                            }

                            composable(SprRoutes.ORDERS) {
                                OrdersScreen(
                                    isSignedIn = currentUser != null,
                                    orders = userOrders,
                                    onSignInClick = { navigateTopTab(SprRoutes.ACCOUNT) },
                                    onBrowseShop = { navigateTopTab(SprRoutes.HOME) },
                                    onCancelOrder = viewModel::cancelOrder,
                                    onTrackOrderById = viewModel::trackOrderById,
                                    onRefreshOrders = viewModel::refreshStore
                                )
                            }

                            composable(SprRoutes.ACCOUNT) {
                                AccountScreen(
                                    currentUser = currentUser,
                                    wishlistProducts = wishlistProducts,
                                    addresses = addresses,
                                    isDarkMode = isDarkMode,
                                    onLogin = viewModel::login,
                                    onRegister = viewModel::register,
                                    onUpdateProfile = viewModel::updateProfile,
                                    onDeleteProfile = viewModel::deleteProfile,
                                    onSignOut = viewModel::signOut,
                                    onProductClick = { id -> navController.navigate(SprRoutes.productDetail(id)) },
                                    onToggleWishlist = viewModel::toggleWishlist,
                                    onQuickAdd = { prod -> viewModel.addToCart(prod) },
                                    onSaveAddress = viewModel::saveAddress,
                                    onUpdateAddress = viewModel::updateAddress,
                                    onSetDefaultAddress = viewModel::setDefaultAddress,
                                    onDeleteAddress = viewModel::deleteAddress,
                                    onToggleDarkMode = viewModel::setDarkMode
                                )
                            }

                            composable(
                                route = SprRoutes.PRODUCT_DETAIL,
                                arguments = listOf(navArgument("productId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val productId = backStackEntry.arguments?.getString("productId").orEmpty()
                                val product by viewModel.observeProduct(productId).collectAsStateWithLifecycle(initialValue = null)
                                val reviews by viewModel.observeReviews(productId).collectAsStateWithLifecycle(initialValue = emptyList())
                                ProductDetailScreen(
                                    product = product,
                                    reviews = reviews,
                                    isWishlisted = productId in wishlistIds,
                                    isSignedIn = currentUser != null,
                                    onBack = { navController.popBackStack() },
                                    onToggleWishlist = { viewModel.toggleWishlist(productId) },
                                    onAddToCart = { variant, qty ->
                                        product?.let { viewModel.addToCart(it, variant, qty) }
                                    },
                                    onBuyNow = { variant, qty ->
                                        product?.let { prod ->
                                            viewModel.addToCart(prod, variant, qty) {
                                                if (currentUser == null) {
                                                    navigateTopTab(SprRoutes.ACCOUNT)
                                                } else {
                                                    navController.navigate(SprRoutes.CHECKOUT)
                                                }
                                            }
                                        }
                                    },
                                    onSubmitReview = { rating, comment, onDone ->
                                        viewModel.submitReview(productId, rating, comment, onDone)
                                    },
                                    onRequireAuth = { navigateTopTab(SprRoutes.ACCOUNT) }
                                )
                            }

                            composable(SprRoutes.CHECKOUT) {
                                CheckoutScreen(
                                    currentUser = currentUser,
                                    addresses = addresses,
                                    cartItems = cartItems,
                                    summary = cartSummary,
                                    paymentSettings = paymentSettings,
                                    onBack = { navController.popBackStack() },
                                    onSaveAddress = viewModel::saveAddress,
                                    onPlaceOrder = viewModel::placeOrder,
                                    onViewOrders = {
                                        navController.popBackStack()
                                        navigateTopTab(SprRoutes.ORDERS)
                                    },
                                    onContinueShopping = {
                                        navController.popBackStack()
                                        navigateTopTab(SprRoutes.HOME)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
