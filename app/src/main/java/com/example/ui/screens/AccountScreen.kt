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
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.example.data.model.ProductEntity
import com.example.data.model.StoreConstants
import com.example.data.model.UserAccountEntity
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.ProductCard
import com.example.ui.components.SprBrandLogo
import com.example.ui.theme.DiscountRed
import com.example.ui.theme.EmeraldPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    currentUser: UserAccountEntity?,
    wishlistProducts: List<ProductEntity>,
    addresses: List<AddressEntity>,
    isDarkMode: Boolean,
    onLogin: (email: String, password: String, onResult: (Result<UserAccountEntity>) -> Unit) -> Unit,
    onRegister: (fullName: String, email: String, phone: String, password: String, onResult: (Result<UserAccountEntity>) -> Unit) -> Unit,
    onUpdateProfile: (fullName: String, phone: String, bio: String, onResult: (Result<UserAccountEntity>) -> Unit) -> Unit,
    onDeleteProfile: (onResult: (Result<Unit>) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    onProductClick: (String) -> Unit,
    onToggleWishlist: (String) -> Unit,
    onQuickAdd: (ProductEntity) -> Unit,
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
    onUpdateAddress: (
        addressId: Long,
        label: String,
        recipientName: String,
        phone: String,
        division: String,
        districtThana: String,
        streetAddress: String,
        makeDefault: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) -> Unit,
    onSetDefaultAddress: (Long) -> Unit,
    onDeleteAddress: (Long) -> Unit,
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
        var showEditProfileDialog by remember { mutableStateOf(false) }
        var showDeleteProfileDialog by remember { mutableStateOf(false) }
        var showAddressDialog by remember { mutableStateOf(false) }
        var editingAddress by remember { mutableStateOf<AddressEntity?>(null) }

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
                            if (currentUser.bio.isNotBlank()) {
                                Text(
                                    text = currentUser.bio,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
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
                                Text("Profile Details", style = MaterialTheme.typography.titleMedium)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Name: ${currentUser.fullName}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Phone: ${currentUser.phone.ifBlank { "Not added" }}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (currentUser.bio.isNotBlank()) {
                                        Text(
                                            text = "Bio / Note: ${currentUser.bio}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { showEditProfileDialog = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("edit_profile_button")
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Edit Profile")
                                    }
                                    OutlinedButton(
                                        onClick = { showDeleteProfileDialog = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DiscountRed),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("delete_profile_button")
                                    ) {
                                        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Delete Profile")
                                    }
                                }

                                HorizontalDivider()

                                Text("Appearance", style = MaterialTheme.typography.titleMedium)
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
                    item {
                        Button(
                            onClick = {
                                editingAddress = null
                                showAddressDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("add_address_button")
                        ) {
                            Icon(Icons.Filled.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add New Address")
                        }
                    }

                    if (addresses.isEmpty()) {
                        item {
                            EmptyStatePanel(
                                title = "No saved BD addresses",
                                subtitle = "Tap Add New Address above to save your delivery details.",
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
                                            TextButton(
                                                onClick = { onSetDefaultAddress(addr.id) },
                                                modifier = Modifier.testTag("default_address_${addr.id}")
                                            ) {
                                                Text("Set as Default")
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            editingAddress = addr
                                            showAddressDialog = true
                                        },
                                        modifier = Modifier.testTag("edit_address_${addr.id}")
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit Address", tint = EmeraldPrimary)
                                    }
                                    IconButton(
                                        onClick = { onDeleteAddress(addr.id) },
                                        modifier = Modifier.testTag("delete_address_${addr.id}")
                                    ) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Address", tint = DiscountRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showEditProfileDialog) {
            EditProfileDialog(
                user = currentUser,
                onDismiss = { showEditProfileDialog = false },
                onSave = { name, phone, bio, onDone ->
                    onUpdateProfile(name, phone, bio) { res ->
                        if (res.isSuccess) showEditProfileDialog = false
                        onDone(res)
                    }
                }
            )
        }

        if (showDeleteProfileDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteProfileDialog = false },
                title = { Text("Delete Your Profile?") },
                text = {
                    Text("This permanently deletes your profile, saved addresses, wishlist, and cart from this device.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteProfileDialog = false
                            onDeleteProfile {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DiscountRed),
                        modifier = Modifier.testTag("confirm_delete_profile_button")
                    ) {
                        Text("Delete Permanently")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteProfileDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showAddressDialog) {
            AddressFormDialog(
                existing = editingAddress,
                defaultName = currentUser.fullName,
                defaultPhone = currentUser.phone,
                onDismiss = { showAddressDialog = false },
                onSave = { label, name, phone, division, thana, street, makeDefault, onError ->
                    val target = editingAddress
                    if (target == null) {
                        onSaveAddress(label, name, phone, division, thana, street, makeDefault) { res ->
                            if (res.isSuccess) showAddressDialog = false
                            else onError(res.exceptionOrNull()?.message ?: "Failed to save address")
                        }
                    } else {
                        onUpdateAddress(target.id, label, name, phone, division, thana, street, makeDefault) { res ->
                            if (res.isSuccess) showAddressDialog = false
                            else onError(res.exceptionOrNull()?.message ?: "Failed to update address")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun EditProfileDialog(
    user: UserAccountEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String, (Result<UserAccountEntity>) -> Unit) -> Unit
) {
    var fullName by remember(user.email) { mutableStateOf(user.fullName) }
    var phone by remember(user.email) { mutableStateOf(user.phone) }
    var bio by remember(user.email) { mutableStateOf(user.bio) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; errorMsg = null },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMsg = null },
                    label = { Text("BD Mobile Number (01XXXXXXXXX)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_phone_input")
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it; errorMsg = null },
                    label = { Text("Bio / Delivery Note (Optional)") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_bio_input")
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, style = MaterialTheme.typography.bodySmall, color = DiscountRed)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(fullName, phone, bio) { res ->
                        if (res.isFailure) errorMsg = res.exceptionOrNull()?.message
                    }
                },
                modifier = Modifier.testTag("save_profile_button")
            ) {
                Text("Save Profile")
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
private fun AddressFormDialog(
    existing: AddressEntity?,
    defaultName: String,
    defaultPhone: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, Boolean, (String) -> Unit) -> Unit
) {
    var label by remember(existing) { mutableStateOf(existing?.label ?: "Home") }
    var recipientName by remember(existing) { mutableStateOf(existing?.recipientName ?: defaultName) }
    var phone by remember(existing) { mutableStateOf(existing?.phone ?: defaultPhone) }
    var division by remember(existing) { mutableStateOf(existing?.division ?: "Dhaka") }
    var showDivisionMenu by remember { mutableStateOf(false) }
    var thana by remember(existing) { mutableStateOf(existing?.districtThana.orEmpty()) }
    var street by remember(existing) { mutableStateOf(existing?.streetAddress.orEmpty()) }
    var makeDefault by remember(existing) { mutableStateOf(existing?.isDefault ?: false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add New Address" else "Edit Address") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it; errorMsg = null },
                    label = { Text("Label (Home / Office)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("address_label_input")
                )
                OutlinedTextField(
                    value = recipientName,
                    onValueChange = { recipientName = it; errorMsg = null },
                    label = { Text("Recipient Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("address_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMsg = null },
                    label = { Text("BD Mobile Phone *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("address_phone_input")
                )
                Box {
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
                    value = thana,
                    onValueChange = { thana = it; errorMsg = null },
                    label = { Text("District / Thana *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("address_thana_input")
                )
                OutlinedTextField(
                    value = street,
                    onValueChange = { street = it; errorMsg = null },
                    label = { Text("House, Road, Block / Area *") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("address_street_input")
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = makeDefault, onCheckedChange = { makeDefault = it })
                    Text("Set as default address", style = MaterialTheme.typography.bodyMedium)
                }
                if (errorMsg != null) {
                    Text(errorMsg!!, style = MaterialTheme.typography.bodySmall, color = DiscountRed)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(label, recipientName, phone, division, thana, street, makeDefault) { msg ->
                        errorMsg = msg
                    }
                },
                modifier = Modifier.testTag("save_address_dialog_button")
            ) {
                Text(if (existing == null) "Save Address" else "Update Address")
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
                text = "Customer account for Cash on Delivery orders, parcel tracking & wishlist",
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
