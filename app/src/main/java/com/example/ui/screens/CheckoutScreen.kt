package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AddressEntity
import com.example.data.model.CartItemEntity
import com.example.data.model.DeliveryConfigSummary
import com.example.data.model.MerchantPaymentSettings
import com.example.data.model.OrderEntity
import com.example.data.model.PaymentMethod
import com.example.data.model.ProductEntity
import com.example.data.model.StoreConstants
import com.example.data.model.SupplierEntity
import com.example.data.model.UserAccountEntity
import com.example.ui.components.formatBdt
import com.example.ui.theme.CodTeal
import com.example.ui.theme.DiscountRed
import com.example.ui.theme.StarAmber
import com.example.ui.theme.VerifiedGreen
import com.example.ui.viewmodel.CartSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    currentUser: UserAccountEntity?,
    addresses: List<AddressEntity>,
    cartItems: List<CartItemEntity>,
    summary: CartSummary,
    paymentSettings: MerchantPaymentSettings,
    deliveryConfig: DeliveryConfigSummary = DeliveryConfigSummary(),
    suppliers: Map<String, SupplierEntity> = emptyMap(),
    products: List<ProductEntity> = emptyList(),
    onBack: () -> Unit,
    onSaveAddress: (
        label: String,
        recipientName: String,
        phone: String,
        division: String,
        districtThana: String,
        streetAddress: String,
        makeDefault: Boolean,
        onResult: (Result<Long>) -> Unit
    ) -> Unit,
    onPlaceOrder: (
        address: AddressEntity,
        paymentMethod: PaymentMethod,
        senderNumberInput: String,
        trxIdInput: String,
        onResult: (Result<OrderEntity>) -> Unit
    ) -> Unit,
    onViewOrders: () -> Unit,
    onContinueShopping: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedAddressId by remember { mutableStateOf<Long?>(null) }
    var showAddressForm by remember { mutableStateOf(false) }
    val selectedPayment = PaymentMethod.COD
    var placedOrder by remember { mutableStateOf<OrderEntity?>(null) }
    var checkoutError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(addresses) {
        if (addresses.isEmpty()) {
            showAddressForm = true
        } else if (selectedAddressId == null || addresses.none { it.id == selectedAddressId }) {
            selectedAddressId = addresses.firstOrNull { it.isDefault }?.id ?: addresses.first().id
        }
    }

    val selectedAddress = addresses.find { it.id == selectedAddressId }
    val productsById = remember(products) { products.associateBy { it.id } }
    val deliveryBreakdown = remember(
        cartItems,
        summary.subtotalBdt,
        selectedAddress?.division,
        deliveryConfig,
        suppliers,
        productsById,
        summary.appliedPromo
    ) {
        StoreConstants.calculateSupplierWiseDelivery(
            cartItems = cartItems,
            subtotalBdt = summary.subtotalBdt,
            division = selectedAddress?.division ?: "Dhaka",
            deliveryConfig = deliveryConfig,
            suppliersById = suppliers,
            productsById = productsById,
            couponFreeShipping = summary.appliedPromo?.freeShipping == true
        )
    }
    val effectiveDeliveryFee = if (cartItems.isEmpty() || selectedAddress == null) 0L else deliveryBreakdown.totalFeeBdt
    val effectiveTotal = (summary.subtotalBdt - summary.discountBdt + effectiveDeliveryFee).coerceAtLeast(0L)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (placedOrder != null) "Order Receipt" else "Bangladesh Checkout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (placedOrder != null) {
                OrderSuccessReceipt(
                    order = placedOrder!!,
                    onTrackOrder = onViewOrders,
                    onContinueShopping = onContinueShopping
                )
                return@Scaffold
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 820.dp)
            ) {
                // Step 1: Shipping Address
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "1. Delivery Address",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showAddressForm = !showAddressForm }) {
                            Icon(Icons.Filled.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (showAddressForm) "Close Form" else "Add New")
                        }
                    }
                }

                if (showAddressForm) {
                    item {
                        NewAddressCard(
                            defaultName = currentUser?.fullName.orEmpty(),
                            defaultPhone = currentUser?.phone.orEmpty(),
                            onSave = { label, name, phone, division, thana, street ->
                                onSaveAddress(label, name, phone, division, thana, street, true) { res ->
                                    if (res.isSuccess) {
                                        selectedAddressId = res.getOrNull()
                                        showAddressForm = false
                                        checkoutError = null
                                    } else {
                                        checkoutError = res.exceptionOrNull()?.message
                                    }
                                }
                            }
                        )
                    }
                }

                items(addresses, key = { it.id }) { addr ->
                    val isSelected = addr.id == selectedAddressId
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAddressId = addr.id }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedAddressId = addr.id }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(addr.recipientName, style = MaterialTheme.typography.titleSmall)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = addr.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(addr.phone, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = addr.formattedAddress,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Step 2: Cash on Delivery Payment Method
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("2. Payment Method", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.payment_trx_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(PaymentMethod.entries) { method ->
                    val brandColor = CodTeal
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(2.dp, brandColor),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payment_method_${method.name}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Surface(
                                color = brandColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.LocalShipping,
                                        contentDescription = null,
                                        tint = brandColor
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = method.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = method.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = true,
                                onClick = {}
                            )
                        }
                    }
                }

                // Step 3: Final Totals & Pay CTA
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text("3. Review & Pay", style = MaterialTheme.typography.titleMedium)
                            SummaryRow("Items (${cartItems.sumOf { it.quantity }})", formatBdt(summary.subtotalBdt))
                            if (summary.discountBdt > 0) {
                                SummaryRow("Promo (${summary.appliedPromo?.code})", "-${formatBdt(summary.discountBdt)}", valueColor = VerifiedGreen)
                            }
                            if (selectedAddress != null && deliveryBreakdown.breakdown.isNotEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        Text(
                                            text = if (deliveryBreakdown.breakdown.size > 1) {
                                                "Supplier-Wise Courier (${deliveryBreakdown.breakdown.size} Suppliers · ${selectedAddress.division})"
                                            } else {
                                                "Supplier Courier (${selectedAddress.division})"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        deliveryBreakdown.breakdown.forEach { line ->
                                            val pickupPart = if (line.pickupLocation.isNotBlank()) " (${line.pickupLocation})" else ""
                                            SummaryRow(
                                                label = "• ${line.supplierName}$pickupPart ×${line.itemCount}",
                                                value = if (line.feeBdt == 0L) "FREE (৳0)" else formatBdt(line.feeBdt),
                                                valueColor = if (line.feeBdt == 0L) VerifiedGreen else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                            SummaryRow(
                                label = if (selectedAddress != null) "Total Delivery Fee (${selectedAddress.division})" else "Delivery Fee",
                                value = if (effectiveDeliveryFee == 0L) "FREE (৳0)" else formatBdt(effectiveDeliveryFee),
                                valueColor = if (effectiveDeliveryFee == 0L) VerifiedGreen else MaterialTheme.colorScheme.onSurface
                            )
                            HorizontalDivider()
                            SummaryRow("Total Amount", formatBdt(effectiveTotal), bold = true, valueColor = MaterialTheme.colorScheme.primary)

                            if (checkoutError != null) {
                                Text(
                                    text = checkoutError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DiscountRed
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Button(
                                enabled = !isSubmitting,
                                onClick = {
                                    if (selectedAddress == null) {
                                        checkoutError = "Please save or select a shipping address first."
                                        showAddressForm = true
                                    } else {
                                        isSubmitting = true
                                        onPlaceOrder(selectedAddress, selectedPayment, "", "") { res ->
                                            isSubmitting = false
                                            if (res.isSuccess) placedOrder = res.getOrNull()
                                            else checkoutError = res.exceptionOrNull()?.message
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CodTeal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("checkout_confirm_pay_button")
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Place COD Order (${formatBdt(effectiveTotal)})")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewAddressCard(
    defaultName: String,
    defaultPhone: String,
    onSave: (String, String, String, String, String, String) -> Unit
) {
    var label by remember { mutableStateOf("Home") }
    var name by remember { mutableStateOf(defaultName) }
    var phone by remember { mutableStateOf(defaultPhone) }
    LaunchedEffect(defaultName, defaultPhone) {
        if (name.isBlank() && defaultName.isNotBlank()) name = defaultName
        if (phone.isBlank() && defaultPhone.isNotBlank()) phone = defaultPhone
    }
    var division by remember { mutableStateOf("Dhaka") }
    var showDivisionMenu by remember { mutableStateOf(false) }
    val divisionDistricts = remember(division) { StoreConstants.districtsForDivision(division) }
    var district by remember(division) { mutableStateOf(divisionDistricts.firstOrNull().orEmpty()) }
    var showDistrictMenu by remember { mutableStateOf(false) }
    var thana by remember { mutableStateOf("") }
    var street by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Text("New Bangladesh Delivery Address", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Recipient Full Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("BD Mobile Number (e.g. 017XXXXXXXX)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { showDivisionMenu = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checkout_division_dropdown")
                    ) {
                        Text("Division: $division")
                    }
                    DropdownMenu(
                        expanded = showDivisionMenu,
                        onDismissRequest = { showDivisionMenu = false }
                    ) {
                        StoreConstants.BD_DIVISIONS.forEach { div ->
                            DropdownMenuItem(
                                text = { Text(div) },
                                onClick = {
                                    division = div
                                    district = StoreConstants.districtsForDivision(div).firstOrNull().orEmpty()
                                    showDivisionMenu = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (Home/Office)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { showDistrictMenu = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("checkout_district_dropdown")
                ) {
                    Text("District ($division): ${district.ifBlank { "Select District" }}")
                }
                DropdownMenu(
                    expanded = showDistrictMenu,
                    onDismissRequest = { showDistrictMenu = false }
                ) {
                    divisionDistricts.forEach { dist ->
                        DropdownMenuItem(
                            text = { Text(dist) },
                            onClick = {
                                district = dist
                                showDistrictMenu = false
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = thana,
                onValueChange = { thana = it },
                label = { Text("Thana / Area (e.g. Banani, Mirpur 10)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = street,
                onValueChange = { street = it },
                label = { Text("House, Road, Block / Area") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val combinedDistrictThana = if (thana.isBlank()) district else "$district, ${thana.trim()}"
                    onSave(label, name, phone, division, combinedDistrictThana, street)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Shipping Address")
            }
        }
    }
}

@Composable
private fun OrderSuccessReceipt(
    order: OrderEntity,
    onTrackOrder: () -> Unit,
    onContinueShopping: () -> Unit
) {
    val isConfirmed = !StoreConstants.canCancelOrder(order.orderStatus)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 540.dp)
            .padding(24.dp)
    ) {
        Surface(
            color = VerifiedGreen.copy(alpha = 0.14f),
            shape = CircleShape,
            modifier = Modifier.size(84.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = VerifiedGreen,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isConfirmed) "Order Confirmed!" else "Order Placed!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isConfirmed) {
                "Your Cash on Delivery order is confirmed and being prepared."
            } else {
                "Your Cash on Delivery order has been placed. Pay cash upon doorstep delivery."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(18.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                SummaryRow("Order ID", order.orderId, bold = true)
                SummaryRow("Order Status", order.orderStatus, valueColor = if (isConfirmed) VerifiedGreen else StarAmber)
                SummaryRow("Payment Method", "Cash on Delivery")
                SummaryRow(
                    "Payment Status",
                    order.paymentStatus,
                    valueColor = if (order.paymentStatus.contains("Confirmed", ignoreCase = true)) VerifiedGreen else StarAmber
                )
                SummaryRow("Deliver To", "${order.recipientName} (${order.division})")
                SummaryRow(
                    label = "Delivery Fee",
                    value = if (order.deliveryFeeBdt == 0L) "FREE (৳0)" else formatBdt(order.deliveryFeeBdt),
                    valueColor = if (order.deliveryFeeBdt == 0L) VerifiedGreen else MaterialTheme.colorScheme.onSurface
                )
                if (order.deliveryBreakdownSummary.isNotBlank()) {
                    Text(
                        text = "Courier: ${order.deliveryBreakdownSummary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
                SummaryRow("Total Amount (COD)", formatBdt(order.totalBdt), bold = true, valueColor = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onTrackOrder,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("receipt_track_order_button")
        ) {
            Text("Track My Order")
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onContinueShopping,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Continue Shopping")
        }
    }
}

private fun paymentBrandColor(method: PaymentMethod): Color = when (method) {
    PaymentMethod.COD -> CodTeal
}
