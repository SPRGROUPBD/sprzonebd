package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.example.data.model.AppNotificationItem
import com.example.notifications.SprNotificationHelper
import com.example.ui.components.SprBrandLogo
import com.example.ui.screens.AccountScreen
import com.example.ui.screens.CartScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.CheckoutScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OrdersScreen
import com.example.ui.screens.ProductDetailScreen
import com.example.ui.theme.CrimsonSecondary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SprZoneTheme
import com.example.ui.viewmodel.SprZoneViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
fun SprZoneApp(
    viewModel: SprZoneViewModel,
    openNotificationsNonce: Int = 0,
    deepLinkProductId: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    enableSplash: Boolean = true
) {
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    var splashVisible by remember { mutableStateOf(enableSplash) }

    LaunchedEffect(enableSplash) {
        if (enableSplash) {
            delay(1350L)
            splashVisible = false
        }
    }

    SprZoneTheme(darkTheme = isDarkMode) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()
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
        val deliveryConfig by viewModel.deliveryConfig.collectAsStateWithLifecycle()
        val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
        val notifications by viewModel.notifications.collectAsStateWithLifecycle()
        val readNotificationIds by viewModel.readNotificationIds.collectAsStateWithLifecycle()
        val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
        val detectedLocationLabel by viewModel.detectedLocationLabel.collectAsStateWithLifecycle()
        val linkedProviders by viewModel.linkedProviders.collectAsStateWithLifecycle()

        var showNotificationsDialog by remember { mutableStateOf(false) }
        var permissionStatus by remember {
            mutableStateOf(SprNotificationHelper.readPermissionStatus(context))
        }

        fun refreshLocationEstimate(notifyIfMissing: Boolean = false) {
            if (!SprNotificationHelper.hasLocationPermission(context)) {
                if (notifyIfMissing) {
                    viewModel.showMessage("Location permission is needed to detect your division")
                }
                return
            }
            scope.launch {
                val res = withContext(Dispatchers.IO) {
                    SprNotificationHelper.detectCurrentLocation(context)
                }
                viewModel.updateDetectedLocationLabel(res.displayText)
            }
        }

        val multiPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
            permissionStatus = SprNotificationHelper.readPermissionStatus(context)
            if (permissionStatus.locationGranted) {
                refreshLocationEstimate()
            }
        }

        fun requestMissingPermissions() {
            val missing = buildList {
                if (!SprNotificationHelper.hasLocationPermission(context)) {
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !SprNotificationHelper.hasNotificationPermission(context)
                ) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (missing.isNotEmpty()) {
                multiPermissionLauncher.launch(missing.toTypedArray())
            } else {
                permissionStatus = SprNotificationHelper.readPermissionStatus(context)
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    permissionStatus = SprNotificationHelper.readPermissionStatus(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(Unit) {
            permissionStatus = SprNotificationHelper.readPermissionStatus(context)
            val missing = buildList {
                if (!permissionStatus.locationGranted) add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionStatus.notificationGranted) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (missing.isNotEmpty()) {
                multiPermissionLauncher.launch(missing.toTypedArray())
            } else if (permissionStatus.locationGranted) {
                refreshLocationEstimate()
            }
        }

        LaunchedEffect(openNotificationsNonce) {
            if (openNotificationsNonce > 0) {
                viewModel.syncNotificationsNow()
                showNotificationsDialog = true
            }
        }

        LaunchedEffect(deepLinkProductId) {
            if (!deepLinkProductId.isNullOrBlank()) {
                navController.navigate(SprRoutes.productDetail(deepLinkProductId))
                onDeepLinkConsumed()
            }
        }

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
                                    unreadNotificationCount = unreadNotificationCount,
                                    locationPermissionGranted = permissionStatus.locationGranted,
                                    notificationPermissionGranted = permissionStatus.notificationGranted,
                                    onSearchChange = viewModel::updateSearchQuery,
                                    onSelectCategory = viewModel::selectCategory,
                                    onSortChange = viewModel::updateSortOption,
                                    onToggleDarkMode = viewModel::setDarkMode,
                                    onRefreshStore = viewModel::refreshStore,
                                    onOpenNotifications = {
                                        viewModel.syncNotificationsNow()
                                        showNotificationsDialog = true
                                    },
                                    onRequestPermissions = { requestMissingPermissions() },
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
                                    linkedProviders = linkedProviders,
                                    wishlistProducts = wishlistProducts,
                                    addresses = addresses,
                                    isDarkMode = isDarkMode,
                                    permissionStatus = permissionStatus,
                                    detectedLocationLabel = detectedLocationLabel,
                                    onRequestLocationPermission = {
                                        multiPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
                                    },
                                    onRequestNotificationPermission = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            multiPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                        }
                                    },
                                    onDetectLocation = { refreshLocationEstimate(notifyIfMissing = true) },
                                    onOpenNotifications = {
                                        viewModel.syncNotificationsNow(showFeedback = true)
                                        showNotificationsDialog = true
                                    },
                                    onOpenAppSettings = { SprNotificationHelper.openAppPermissionSettings(context) },
                                    onLogin = viewModel::login,
                                    onRegister = viewModel::register,
                                    onForgotPassword = viewModel::sendPasswordResetEmail,
                                    onGoogleSignIn = viewModel::signInWithGoogleIdToken,
                                    onLoginAndLinkGoogle = viewModel::loginAndLinkGoogle,
                                    onLinkGoogleAccount = viewModel::linkGoogleToCurrentAccount,
                                    onLinkPassword = viewModel::linkPasswordToCurrentAccount,
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
                                    suppliers = suppliers,
                                    isWishlisted = productId in wishlistIds,
                                    isSignedIn = currentUser != null,
                                    onBack = { navController.popBackStack() },
                                    onToggleWishlist = { viewModel.toggleWishlist(productId) },
                                    onAddToCart = { variant, qty, unitPrice ->
                                        product?.let { viewModel.addToCart(it, variant, qty, unitPrice) }
                                    },
                                    onBuyNow = { variant, qty, unitPrice ->
                                        product?.let { prod ->
                                            viewModel.addToCart(prod, variant, qty, unitPrice) {
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
                                    deliveryConfig = deliveryConfig,
                                    suppliers = suppliers,
                                    products = allProducts,
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

        if (showNotificationsDialog) {
            NotificationsInboxDialog(
                notifications = notifications,
                readIds = readNotificationIds,
                onDismiss = { showNotificationsDialog = false },
                onRefresh = { viewModel.syncNotificationsNow(showFeedback = true) },
                onMarkAllRead = viewModel::markAllNotificationsRead,
                onOpenNotification = { item ->
                    viewModel.markNotificationRead(item.id)
                    if (item.productId.isNotBlank()) {
                        showNotificationsDialog = false
                        navController.navigate(SprRoutes.productDetail(item.productId))
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = splashVisible,
            exit = fadeOut(animationSpec = tween(durationMillis = 380))
        ) {
            SprSplashScreen()
        }
    }
}

@Composable
private fun SprSplashScreen() {
    val logoScale = remember { Animatable(0.76f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        logoScale.animateTo(1f, animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        contentAlpha.animateTo(1f, animationSpec = tween(durationMillis = 480))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF003D1E),
                        EmeraldPrimary,
                        Color(0xFF009A4E)
                    )
                )
            )
            .testTag("spr_splash_screen")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(32.dp)
                .alpha(contentAlpha.value)
        ) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(26.dp),
                shadowElevation = 14.dp,
                modifier = Modifier
                    .size(108.dp)
                    .scale(logoScale.value)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SprBrandLogo(size = 86.dp)
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "SPR ZONE BD",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Shop Smart • Live Better",
                color = Color(0xFFDCFCE7),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(34.dp))
            CircularProgressIndicator(
                color = Color(0xFFFEF08A),
                trackColor = Color.White.copy(alpha = 0.24f),
                strokeWidth = 3.dp,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun NotificationsInboxDialog(
    notifications: List<AppNotificationItem>,
    readIds: Set<String>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenNotification: (AppNotificationItem) -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.US) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = EmeraldPrimary)
                    Text("App Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Sync notifications", tint = EmeraldPrimary)
                }
            }
        },
        text = {
            if (notifications.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No notifications yet", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Offers, flash sales, and delivery updates sent by SPR Zone BD Admin appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    items(notifications, key = { it.id }) { item ->
                        val isUnread = item.id !in readIds
                        val badgeLabel = when (item.type.lowercase()) {
                            "order" -> "Order / Delivery"
                            "info" -> "Announcement"
                            else -> "Promo / Sale"
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUnread) Color(0xFFE8F6EE) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenNotification(item) }
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        color = if (item.type == "order") EmeraldPrimary else CrimsonSecondary,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = badgeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = dateFormatter.format(Date(item.createdAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = item.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (item.productId.isNotBlank()) {
                                    Text(
                                        text = "Tap to view product →",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (notifications.any { it.id !in readIds }) {
                Button(onClick = onMarkAllRead) {
                    Text("Mark All Read")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
