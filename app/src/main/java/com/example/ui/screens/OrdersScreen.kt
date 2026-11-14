package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.OrderEntity
import com.example.data.model.StoreConstants
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.formatBdt
import com.example.ui.theme.DiscountRed
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.StarAmber
import com.example.ui.theme.VerifiedGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OrdersScreen(
    isSignedIn: Boolean,
    orders: List<OrderEntity>,
    onSignInClick: () -> Unit,
    onBrowseShop: () -> Unit,
    onCancelOrder: (String) -> Unit,
    onTrackOrderById: (String, (Result<OrderEntity>) -> Unit) -> Unit,
    onRefreshOrders: () -> Unit,
    modifier: Modifier = Modifier
) {
    var trackQuery by remember { mutableStateOf("") }
    var trackedOrder by remember { mutableStateOf<OrderEntity?>(null) }
    var trackError by remember { mutableStateOf<String?>(null) }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Track & Manage Orders",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRefreshOrders) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh orders")
                    }
                }
            }

            // Track Order By ID Lookup Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            text = "Track Your Order by Order ID",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = trackQuery,
                                onValueChange = {
                                    trackQuery = it
                                    trackError = null
                                },
                                placeholder = { Text("e.g. SPR-BD-20261113-1234") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("track_order_id_input")
                            )
                            Button(
                                onClick = {
                                    onTrackOrderById(trackQuery) { res ->
                                        if (res.isSuccess) {
                                            trackedOrder = res.getOrNull()
                                            trackError = null
                                        } else {
                                            trackedOrder = null
                                            trackError = res.exceptionOrNull()?.message
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("track_order_submit_button")
                            ) {
                                Text("Track")
                            }
                        }
                        if (trackError != null) {
                            Text(
                                text = trackError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = DiscountRed
                            )
                        }
                    }
                }
            }

            if (trackedOrder != null) {
                item {
                    OrderTrackingCard(
                        order = trackedOrder!!,
                        onCancelOrder = { onCancelOrder(trackedOrder!!.orderId) }
                    )
                }
            }

            if (!isSignedIn) {
                item {
                    EmptyStatePanel(
                        title = "Sign in to see all your orders",
                        subtitle = "View live courier milestones, payment TrxID receipts, and order history.",
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        actionLabel = "Sign In / Register",
                        onAction = onSignInClick
                    )
                }
            } else if (orders.isEmpty()) {
                item {
                    EmptyStatePanel(
                        title = "No orders placed yet",
                        subtitle = "Your SPR Zone BD orders and live parcel tracking will appear here.",
                        icon = Icons.Filled.LocalShipping,
                        actionLabel = "Explore Marketplace",
                        onAction = onBrowseShop
                    )
                }
            } else {
                item {
                    Text(
                        text = "My Order History (${orders.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                items(orders, key = { it.orderId }) { order ->
                    OrderTrackingCard(
                        order = order,
                        onCancelOrder = { onCancelOrder(order.orderId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderTrackingCard(
    order: OrderEntity,
    onCancelOrder: () -> Unit
) {
    val dateLabel = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(order.createdAt))
    val statusColor = when (order.orderStatus) {
        "Delivered", "Confirmed" -> VerifiedGreen
        "Cancelled" -> DiscountRed
        "Out for Delivery", "Shipped" -> StarAmber
        else -> EmeraldPrimary
    }
    val canCancel = StoreConstants.canCancelOrder(order.orderStatus)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(order.orderId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    color = statusColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = order.orderStatus,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            if (order.orderStatus != "Cancelled") {
                CourierTimeline(currentStatus = order.orderStatus)
            }

            HorizontalDivider()

            val lineItems = order.itemsSummary.split("||").filter { it.isNotBlank() }
            lineItems.forEach { line ->
                Text(
                    text = "• $line",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider()

            SummaryRow("Payment", "${order.paymentMethod} (${order.transactionId})")
            if (order.senderNumber.isNotBlank()) {
                SummaryRow("Sender Number", order.senderNumber)
            }
            SummaryRow("Payment Status", order.paymentStatus)
            SummaryRow("Deliver To", "${order.recipientName} • ${order.fullAddress}")
            SummaryRow("Total", formatBdt(order.totalBdt), bold = true, valueColor = MaterialTheme.colorScheme.primary)

            if (canCancel) {
                OutlinedButton(
                    onClick = onCancelOrder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cancel_order_${order.orderId}")
                ) {
                    Text("Cancel Order", color = DiscountRed)
                }
            } else if (order.orderStatus != "Cancelled") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Order ${order.orderStatus} — cancellation is locked once confirmed.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourierTimeline(currentStatus: String) {
    val steps = listOf("Pending", "Confirmed", "Packed", "Out for Delivery", "Delivered")
    val normalized = when (currentStatus) {
        "Placed" -> "Pending"
        "Processing" -> "Confirmed"
        "Shipped" -> "Out for Delivery"
        else -> currentStatus
    }
    val activeIdx = steps.indexOf(normalized).coerceAtLeast(0)

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        steps.forEachIndexed { idx, step ->
            val reached = idx <= activeIdx
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = if (reached) VerifiedGreen else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (reached) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = step.replace("Out for Delivery", "Transit"),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
