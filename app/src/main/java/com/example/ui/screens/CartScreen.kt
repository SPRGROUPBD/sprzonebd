package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CartItemEntity
import com.example.data.model.StoreConstants
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.formatBdt
import com.example.ui.theme.DiscountRed
import com.example.ui.theme.VerifiedGreen
import com.example.ui.viewmodel.CartSummary

@Composable
fun CartScreen(
    cartItems: List<CartItemEntity>,
    summary: CartSummary,
    availableCoupons: List<String>,
    isSignedIn: Boolean,
    onUpdateQuantity: (CartItemEntity, Int) -> Unit,
    onRemoveItem: (Long) -> Unit,
    onApplyPromo: (String) -> Unit,
    onProductClick: (String) -> Unit,
    onCheckoutClick: () -> Unit,
    onBrowseShop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var promoInput by remember { mutableStateOf(summary.appliedPromo?.code ?: "") }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        if (cartItems.isEmpty()) {
            EmptyStatePanel(
                title = "Your SPR Zone cart is empty",
                subtitle = "Browse live products from SPR Zone BD and add items to your cart.",
                icon = Icons.Filled.ShoppingCart,
                actionLabel = "Start Shopping",
                onAction = onBrowseShop,
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
        ) {
            item {
                Text(
                    text = "Shopping Cart (${summary.itemCount})",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            // Free Shipping Progress Banner
            item {
                val threshold = summary.freeDeliveryThresholdBdt.coerceAtLeast(1L)
                val unlocked = summary.hasQtyFreeDelivery ||
                    summary.freeDeliveryRemainingBdt == 0L ||
                    summary.appliedPromo?.freeShipping == true
                val progress = if (unlocked) 1f
                else (summary.subtotalBdt.toFloat() / threshold.toFloat()).coerceIn(0f, 1f)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.LocalShipping,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    summary.hasQtyFreeDelivery ->
                                        "Qty Free Delivery unlocked — Delivery Fee ৳0 at Checkout!"
                                    unlocked ->
                                        "You unlocked FREE nationwide Bangladesh delivery!"
                                    else ->
                                        "Add ${formatBdt(summary.freeDeliveryRemainingBdt)} more for FREE BD delivery"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }

            // Cart Line Items
            items(cartItems, key = { it.id }) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProductClick(item.productId) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        ProductThumbnail(
                            imageUrl = item.imageUrl,
                            imageKey = item.imageKey,
                            contentDescription = item.productName,
                            modifier = Modifier
                                .size(78.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.productName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.selectedVariant,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (item.supplierName.isNotBlank()) {
                                Text(
                                    text = "Supplier: ${item.supplierName}${if (item.supplierPickupLocation.isNotBlank()) " (${item.supplierPickupLocation})" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (item.freeDeliveryMinQty > 0 && item.quantity >= item.freeDeliveryMinQty) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Free Delivery (৳0)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VerifiedGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = formatBdt(item.unitPriceBdt * item.quantity),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            IconButton(
                                onClick = { onRemoveItem(item.id) },
                                modifier = Modifier.testTag("cart_remove_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = stringResource(R.string.cd_cart_remove),
                                    tint = DiscountRed
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onUpdateQuantity(item, item.quantity - 1) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Filled.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                    }
                                    Text(
                                        text = "${item.quantity}",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                    IconButton(
                                        onClick = { onUpdateQuantity(item, item.quantity + 1) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Promo Code Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.LocalOffer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Promo & Voucher Code", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = promoInput,
                                onValueChange = { promoInput = it.uppercase() },
                                placeholder = { Text("Enter promo code") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("promo_code_input")
                            )
                            OutlinedButton(
                                onClick = { onApplyPromo(promoInput) },
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("apply_promo_button")
                            ) {
                                Text("Apply")
                            }
                        }
                        if (availableCoupons.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(availableCoupons) { code ->
                                    AssistChip(
                                        onClick = {
                                            promoInput = code
                                            onApplyPromo(code)
                                        },
                                        label = { Text(code) }
                                    )
                                }
                            }
                        }
                        if (summary.appliedPromo != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Applied: ${summary.appliedPromo.message}",
                                style = MaterialTheme.typography.labelMedium,
                                color = VerifiedGreen
                            )
                        }
                    }
                }
            }

            // Order Summary Card
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
                        Text("Order Summary", style = MaterialTheme.typography.titleMedium)
                        SummaryRow("Subtotal", formatBdt(summary.subtotalBdt))
                        if (summary.discountBdt > 0) {
                            SummaryRow("Promo Discount", "-${formatBdt(summary.discountBdt)}", valueColor = VerifiedGreen)
                        }
                        val isFreeUnlocked = summary.hasQtyFreeDelivery ||
                            summary.freeDeliveryRemainingBdt == 0L ||
                            summary.appliedPromo?.freeShipping == true ||
                            (summary.estDhakaDeliveryBdt == 0L && summary.estOutsideDhakaDeliveryBdt == 0L)
                        val courierValue = when {
                            isFreeUnlocked -> "FREE (৳0)"
                            summary.estDhakaDeliveryBdt == summary.estOutsideDhakaDeliveryBdt ->
                                formatBdt(summary.estDhakaDeliveryBdt)
                            else ->
                                "Dhaka ${formatBdt(summary.estDhakaDeliveryBdt)} / Out ${formatBdt(summary.estOutsideDhakaDeliveryBdt)}"
                        }
                        SummaryRow(
                            label = if (summary.supplierCount > 1) "Courier Fee (${summary.supplierCount} Suppliers)" else "Delivery Fee",
                            value = courierValue,
                            valueColor = if (isFreeUnlocked) VerifiedGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        SummaryRow(
                            label = "Estimated Total",
                            value = formatBdt(summary.totalBdt),
                            bold = true,
                            valueColor = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = onCheckoutClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("proceed_to_checkout_button")
                        ) {
                            Text(
                                text = if (isSignedIn) {
                                    "Proceed to Checkout (${formatBdt(summary.totalBdt)})"
                                } else {
                                    "Sign In & Checkout (${formatBdt(summary.totalBdt)})"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryRow(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (bold) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor
        )
    }
}
