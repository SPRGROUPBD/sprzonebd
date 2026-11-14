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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.AddressEntity
import com.example.data.model.DeliveryZone
import com.example.data.model.ProductEntity
import com.example.data.model.UserAccountEntity
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.ProductCard
import com.example.ui.components.SprBrandLogo
import com.example.ui.theme.DiscountRed
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.VerifiedGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    currentUser: UserAccountEntity?,
    wishlistProducts: List<ProductEntity>,
    addresses: List<AddressEntity>,
    deliveryZone: DeliveryZone,
    isDarkMode: Boolean,
    onLogin: (email: String, password: String, onResult: (Result<UserAccountEntity>) -> Unit) -> Unit,
    onRegister: (fullName: String, email: String, phone: String, password: String, onResult: (Result<UserAccountEntity>) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    onProductClick: (String) -> Unit,
    onToggleWishlist: (String) -> Unit,
    onQuickAdd: (ProductEntity) -> Unit,
    onSetDefaultAddress: (Long) -> Unit,
    onDeleteAddress: (Long) -> Unit,
    onSelectDeliveryZone: (DeliveryZone) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        if (currentUser == null) {
            AuthCardView(
                onLogin = onLogin,
                onRegister = onRegister
            )
            return@Box
        }

        var selectedTab by remember { mutableIntStateOf(0) }
        val tabs = listOf("Profile", "Wishlist (${wishlistProducts.size})", "Addresses (${addresses.size})")

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
        ) {
            // Customer Header Card (No Admin controls on mobile)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Surface(
                            color = EmeraldPrimary,
                            shape = CircleShape,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = currentUser.fullName.take(1).uppercase(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(currentUser.fullName, style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = currentUser.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (currentUser.phone.isNotBlank()) {
                                Text(
                                    text = currentUser.phone,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign Out",
                                tint = DiscountRed
                            )
                        }
                    }
                }
            }

            // Tabs
            item {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(title) }
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text("Store Preferences", style = MaterialTheme.typography.titleMedium)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Dark Theme", style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            "Switch between Emerald Light and Night Dark",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = isDarkMode,
                                        onCheckedChange = onToggleDarkMode
                                    )
                                }
                                HorizontalDivider()
                                Text("Default Delivery Zone", style = MaterialTheme.typography.titleSmall)
                                DeliveryZone.entries.forEach { zone ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(zone.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text(
                                                "৳${zone.feeBdt} • ${zone.etaText}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (deliveryZone == zone) {
                                            Text("Selected", style = MaterialTheme.typography.labelMedium, color = VerifiedGreen)
                                        } else {
                                            TextButton(onClick = { onSelectDeliveryZone(zone) }) {
                                                Text("Select")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    if (wishlistProducts.isEmpty()) {
                        item {
                            EmptyStatePanel(
                                title = "Your wishlist is empty",
                                subtitle = "Tap the heart icon on any product to save it for later.",
                                icon = Icons.Filled.Favorite
                            )
                        }
                    } else {
                        val rows = wishlistProducts.chunked(2)
                        items(rows) { pair ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (product in pair) {
                                    ProductCard(
                                        product = product,
                                        isWishlisted = true,
                                        onProductClick = { onProductClick(product.id) },
                                        onToggleWishlist = { onToggleWishlist(product.id) },
                                        onQuickAdd = { onQuickAdd(product) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                2 -> {
                    if (addresses.isEmpty()) {
                        item {
                            EmptyStatePanel(
                                title = "No saved BD addresses",
                                subtitle = "Add your Dhaka or nationwide Bangladesh shipping address during checkout.",
                                icon = Icons.Filled.LocationOn
                            )
                        }
                    } else {
                        items(addresses, key = { it.id }) { addr ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(addr.recipientName, style = MaterialTheme.typography.titleSmall)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (addr.isDefault) "${addr.label} • Default" else addr.label,
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
                                        if (!addr.isDefault) {
                                            TextButton(onClick = { onSetDefaultAddress(addr.id) }) {
                                                Text("Set as Default")
                                            }
                                        }
                                    }
                                    IconButton(onClick = { onDeleteAddress(addr.id) }) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = DiscountRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthCardView(
    onLogin: (email: String, password: String, onResult: (Result<UserAccountEntity>) -> Unit) -> Unit,
    onRegister: (fullName: String, email: String, phone: String, password: String, onResult: (Result<UserAccountEntity>) -> Unit) -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 520.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(18.dp))
            SprBrandLogo(size = 68.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isRegisterMode) "Create SPR Zone BD Account" else "Welcome to SPR Zone BD",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Customer account for checkout, TrxID orders, parcel tracking & wishlist",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(18.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(18.dp)
                ) {
                    if (isRegisterMode) {
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it; errorMsg = null },
                            label = { Text("Full Name") },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_name_input")
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it; errorMsg = null },
                            label = { Text("BD Mobile Number (e.g. 017XXXXXXXX)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_phone_input")
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMsg = null },
                        label = { Text("Email Address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_email_input")
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMsg = null },
                        label = { Text("Password (min 6 chars)") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_password_input")
                    )

                    if (errorMsg != null) {
                        Text(
                            text = errorMsg!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = DiscountRed
                        )
                    }

                    Button(
                        onClick = {
                            if (isRegisterMode) {
                                onRegister(fullName, email, phone, password) { res ->
                                    if (res.isFailure) errorMsg = res.exceptionOrNull()?.message
                                }
                            } else {
                                onLogin(email, password) { res ->
                                    if (res.isFailure) errorMsg = res.exceptionOrNull()?.message
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("auth_submit_button")
                    ) {
                        Text(if (isRegisterMode) "Create Account" else "Sign In")
                    }

                    OutlinedButton(
                        onClick = {
                            isRegisterMode = !isRegisterMode
                            errorMsg = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isRegisterMode) "Already have an account? Sign In"
                            else "New to SPR Zone BD? Create Account"
                        )
                    }
                }
            }
        }
    }
}
