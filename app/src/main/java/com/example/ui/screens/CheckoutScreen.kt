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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AddressEntity
import com.example.data.model.CartItemEntity
import com.example.data.model.MerchantPaymentSettings
import com.example.data.model.OrderEntity
import com.example.data.model.PaymentMethod
import com.example.data.model.StoreConstants
import com.example.data.model.UserAccountEntity
import com.example.ui.components.formatBdt
import com.example.ui.theme.BkashPink
import com.example.ui.theme.CodTeal
import com.example.ui.theme.DiscountRed
import com.example.ui.theme.NagadOrange
import com.example.ui.theme.RocketPurple
import com.example.ui.theme.SslCommerzBlue
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
    var selectedPayment by remember { mutableStateOf(PaymentMethod.BKASH) }
    var showGatewayDialog by remember { mutableStateOf(false) }
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
    val effectiveDeliveryFee = when {
        cartItems.isEmpty() -> 0L
        summary.subtotalBdt >= StoreConstants.FREE_DELIVERY_THRESHOLD_BDT || summary.appliedPromo?.freeShipping == true -> 0L
        selectedAddress != null -> StoreConstants.deliveryFeeForDivision(selectedAddress.division)
        else -> summary.deliveryFeeBdt
    }
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

                // Step 2: Bangladesh Payment Gateway Selector
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("2. Bangladesh Payment Method", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.payment_trx_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(PaymentMethod.entries) { method ->
                    val isSelected = selectedPayment == method
                    val brandColor = paymentBrandColor(method)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = if (isSelected) BorderStroke(2.dp, brandColor) else null,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payment_method_${method.name}")
                            .clickable { selectedPayment = method }
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
                                        imageVector = when {
                                            method.isCardGateway -> Icons.Filled.CreditCard
                                            method == PaymentMethod.COD -> Icons.Filled.LocalShipping
                                            else -> Icons.Filled.AccountBalanceWallet
                                        },
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
                                selected = isSelected,
                                onClick = { selectedPayment = method }
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
                            SummaryRow("Delivery Fee", if (effectiveDeliveryFee == 0L) "FREE" else formatBdt(effectiveDeliveryFee))
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
                                    } else if (selectedPayment == PaymentMethod.COD) {
                                        isSubmitting = true
                                        onPlaceOrder(selectedAddress, selectedPayment, "", "") { res ->
                                            isSubmitting = false
                                            if (res.isSuccess) placedOrder = res.getOrNull()
                                            else checkoutError = res.exceptionOrNull()?.message
                                        }
                                    } else {
                                        checkoutError = null
                                        showGatewayDialog = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = paymentBrandColor(selectedPayment)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("checkout_confirm_pay_button")
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (selectedPayment == PaymentMethod.COD) {
                                        "Place COD Order (${formatBdt(effectiveTotal)})"
                                    } else {
                                        "Pay ${formatBdt(effectiveTotal)} via ${selectedPayment.displayName}"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGatewayDialog && selectedAddress != null) {
        PaymentGatewayVerificationDialog(
            method = selectedPayment,
            amountBdt = effectiveTotal,
            defaultPhone = currentUser?.phone.orEmpty(),
            paymentSettings = paymentSettings,
            onDismiss = { showGatewayDialog = false },
            onConfirmPayment = { senderInput, trxInput ->
                showGatewayDialog = false
                isSubmitting = true
                onPlaceOrder(selectedAddress, selectedPayment, senderInput, trxInput) { res ->
                    isSubmitting = false
                    if (res.isSuccess) placedOrder = res.getOrNull()
                    else checkoutError = res.exceptionOrNull()?.message
                }
            }
        )
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
    var division by remember { mutableStateOf("Dhaka") }
    var showDivisionMenu by remember { mutableStateOf(false) }
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
                        modifier = Modifier.fillMaxWidth()
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
            OutlinedTextField(
                value = thana,
                onValueChange = { thana = it },
                label = { Text("District / Thana (e.g. Banani, Dhaka)") },
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
                onClick = { onSave(label, name, phone, division, thana, street) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Shipping Address")
            }
        }
    }
}

@Composable
private fun PaymentGatewayVerificationDialog(
    method: PaymentMethod,
    amountBdt: Long,
    defaultPhone: String,
    paymentSettings: MerchantPaymentSettings,
    onDismiss: () -> Unit,
    onConfirmPayment: (senderNumber: String, trxId: String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copiedNotice by remember { mutableStateOf(false) }

    val merchantNumber = when (method) {
        PaymentMethod.BKASH -> paymentSettings.bkashNumber
        PaymentMethod.NAGAD -> paymentSettings.nagadNumber
        PaymentMethod.ROCKET -> paymentSettings.rocketNumber
        else -> ""
    }

    var senderOrCard by remember { mutableStateOf(if (method.isCardGateway) "" else defaultPhone) }
    var trxOrCvv by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val brandColor = paymentBrandColor(method)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = brandColor,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Payments, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("${method.displayName} Payment", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Payable: ${formatBdt(amountBdt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = brandColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (method.requiresMobileTrx) {
                    Surface(
                        color = brandColor.copy(alpha = 0.09f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Step 1: Send Money / Payment to SPR ZONE BD (${method.displayName})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = merchantNumber.ifBlank { "Set merchant number in Admin" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = brandColor
                                )
                                if (merchantNumber.isNotBlank()) {
                                    TextButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(merchantNumber))
                                            copiedNotice = true
                                        }
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy number", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (copiedNotice) "Copied" else "Copy")
                                    }
                                }
                            }
                            Text(
                                text = "Step 2: Enter the Sender Number you paid from & your TrxID below to auto-verify.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = senderOrCard,
                        onValueChange = {
                            if (it.length <= 14) senderOrCard = it
                            errorMsg = null
                        },
                        label = { Text("Sender ${method.displayName} Number") },
                        placeholder = { Text("017XXXXXXXX") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trx_sender_input")
                    )

                    OutlinedTextField(
                        value = trxOrCvv,
                        onValueChange = {
                            if (it.length <= 16) trxOrCvv = it.uppercase()
                            errorMsg = null
                        },
                        label = { Text("Transaction ID (TrxID)") },
                        placeholder = {
                            Text(
                                when (method) {
                                    PaymentMethod.BKASH -> "e.g. BL74K9M2PQ (10 chars)"
                                    PaymentMethod.NAGAD -> "e.g. 73AB92CD (8-12 chars)"
                                    else -> "e.g. 492817364 (8-14 chars)"
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trx_id_input")
                    )
                } else {
                    Text(
                        text = "Enter your card details to complete payment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = senderOrCard,
                        onValueChange = { if (it.length <= 19) senderOrCard = it },
                        label = { Text("Card Number (16 digits)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = expiry,
                            onValueChange = { if (it.length <= 5) expiry = it },
                            label = { Text("MM/YY") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = trxOrCvv,
                            onValueChange = { if (it.length <= 4) trxOrCvv = it },
                            label = { Text("CVV") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, style = MaterialTheme.typography.bodySmall, color = DiscountRed)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (method.requiresMobileTrx) {
                        if (!StoreConstants.isValidBdPhone(senderOrCard)) {
                            errorMsg = "Enter a valid 11-digit BD sender mobile number (01XXXXXXXXX)."
                            return@Button
                        }
                        if (!StoreConstants.isValidTrxId(method, trxOrCvv)) {
                            errorMsg = "Enter a valid ${method.displayName} TrxID (e.g. 8–14 alphanumeric characters)."
                            return@Button
                        }
                        onConfirmPayment(StoreConstants.normalizeBdPhone(senderOrCard), trxOrCvv.trim().uppercase())
                    } else {
                        val digits = senderOrCard.filter { it.isDigit() }
                        if (digits.length < 12 || expiry.length < 4 || trxOrCvv.length < 3) {
                            errorMsg = "Enter a valid card number, expiry, and CVV."
                            return@Button
                        }
                        onConfirmPayment(digits, "")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = brandColor),
                modifier = Modifier.testTag("verify_trx_submit_button")
            ) {
                Text("Verify & Confirm Order")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
                "Payment verified automatically. Confirmed orders cannot be cancelled."
            } else {
                "Your order is received and queued for merchant TrxID verification."
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
                SummaryRow("Payment Method", order.paymentMethod)
                if (order.senderNumber.isNotBlank()) {
                    SummaryRow("Sender Number", order.senderNumber)
                }
                SummaryRow("Transaction ID", order.transactionId)
                SummaryRow(
                    "Payment Status",
                    order.paymentStatus,
                    valueColor = if (order.paymentStatus.contains("Verified", ignoreCase = true)) VerifiedGreen else StarAmber
                )
                SummaryRow("Deliver To", "${order.recipientName} (${order.division})")
                HorizontalDivider()
                SummaryRow("Total Amount", formatBdt(order.totalBdt), bold = true, valueColor = MaterialTheme.colorScheme.primary)
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
    PaymentMethod.BKASH -> BkashPink
    PaymentMethod.NAGAD -> NagadOrange
    PaymentMethod.ROCKET -> RocketPurple
    PaymentMethod.SSLCOMMERZ -> SslCommerzBlue
    PaymentMethod.COD -> CodTeal
}
