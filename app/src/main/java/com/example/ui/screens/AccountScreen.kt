package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.data.model.AddressEntity
import com.example.data.model.ProductEntity
import com.example.data.model.StoreConstants
import com.example.data.model.UserAccountEntity
import com.example.data.repository.GooglePendingLinkException
import com.example.notifications.AppPermissionStatus
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.ProductCard
import com.example.ui.components.SprBrandLogo
import com.example.ui.theme.DiscountRed
import com.example.ui.theme.EmeraldPrimary
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    currentUser: UserAccountEntity?,
    linkedProviders: List<String>,
    wishlistProducts: List<ProductEntity>,
    addresses: List<AddressEntity>,
    isDarkMode: Boolean,
    permissionStatus: AppPermissionStatus,
    detectedLocationLabel: String?,
    onRequestLocationPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onDetectLocation: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onLogin: (email: String, password: String, onResult: (Result<UserAccountEntity>) -> Unit) -> Unit,
    onRegister: (fullName: String, email: String, phone: String, password: String, onResult: (Result<UserAccountEntity>) -> Unit) -> Unit,
    onForgotPassword: (email: String, onResult: (Result<String>) -> Unit) -> Unit,
    onGoogleSignIn: (googleIdToken: String, onResult: (Result<UserAccountEntity>) -> Unit) -> Unit,
    onLoginAndLinkGoogle: (email: String, password: String, googleIdToken: String, onResult: (Result<UserAccountEntity>) -> Unit) -> Unit,
    onLinkGoogleAccount: (googleIdToken: String, onResult: (Result<List<String>>) -> Unit) -> Unit,
    onLinkPassword: (newPassword: String, onResult: (Result<List<String>>) -> Unit) -> Unit,
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
                permissionStatus = permissionStatus,
                detectedLocationLabel = detectedLocationLabel,
                onRequestLocationPermission = onRequestLocationPermission,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onDetectLocation = onDetectLocation,
                onOpenNotifications = onOpenNotifications,
                onOpenAppSettings = onOpenAppSettings,
                onLogin = onLogin,
                onRegister = onRegister,
                onForgotPassword = onForgotPassword,
                onGoogleSignIn = onGoogleSignIn,
                onLoginAndLinkGoogle = onLoginAndLinkGoogle
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

                    item {
                        AccountLinkingCard(
                            linkedProviders = linkedProviders,
                            onLinkGoogleAccount = onLinkGoogleAccount,
                            onLinkPassword = onLinkPassword
                        )
                    }

                    item {
                        AppPermissionsCard(
                            permissionStatus = permissionStatus,
                            detectedLocationLabel = detectedLocationLabel,
                            onRequestLocationPermission = onRequestLocationPermission,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onDetectLocation = onDetectLocation,
                            onOpenNotifications = onOpenNotifications,
                            onOpenAppSettings = onOpenAppSettings
                        )
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
    val initialDivision = existing?.division?.takeIf { it.isNotBlank() } ?: "Dhaka"
    val initialDivisionDistricts = StoreConstants.districtsForDivision(initialDivision)
    val parsedDistrictAndThana = remember(existing) {
        val raw = existing?.districtThana.orEmpty().trim()
        if (raw.isBlank()) {
            initialDivisionDistricts.firstOrNull().orEmpty() to ""
        } else {
            val parts = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val firstPart = parts.firstOrNull().orEmpty()
            val matchedDistrict = initialDivisionDistricts.firstOrNull {
                it.equals(firstPart, ignoreCase = true)
            }
            if (matchedDistrict != null) {
                matchedDistrict to parts.drop(1).joinToString(", ")
            } else {
                initialDivisionDistricts.firstOrNull().orEmpty() to raw
            }
        }
    }

    var label by remember(existing) { mutableStateOf(existing?.label ?: "Home") }
    var recipientName by remember(existing) { mutableStateOf(existing?.recipientName ?: defaultName) }
    var phone by remember(existing) { mutableStateOf(existing?.phone ?: defaultPhone) }
    var division by remember(existing) { mutableStateOf(initialDivision) }
    var showDivisionMenu by remember { mutableStateOf(false) }
    val divisionDistricts = remember(division) { StoreConstants.districtsForDivision(division) }
    var district by remember(existing, division) {
        mutableStateOf(
            if (division == initialDivision && parsedDistrictAndThana.first in divisionDistricts) {
                parsedDistrictAndThana.first
            } else {
                divisionDistricts.firstOrNull().orEmpty()
            }
        )
    }
    var showDistrictMenu by remember { mutableStateOf(false) }
    var thana by remember(existing) { mutableStateOf(parsedDistrictAndThana.second) }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("address_division_dropdown")
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
                Box {
                    OutlinedButton(
                        onClick = { showDistrictMenu = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("address_district_dropdown")
                    ) {
                        Text("District: ${district.ifBlank { "Select District" }}")
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
                    onValueChange = { thana = it; errorMsg = null },
                    label = { Text("Thana / Area (e.g. Banani, Mirpur 10)") },
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
                    val combinedDistThana = if (thana.isBlank()) district else "$district, ${thana.trim()}"
                    onSave(label, recipientName, phone, division, combinedDistThana, street, makeDefault) { msg ->
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
private fun AppPermissionsCard(
    permissionStatus: AppPermissionStatus,
    detectedLocationLabel: String?,
    onRequestLocationPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onDetectLocation: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_permissions_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text("App Permissions (Internet, Location, Notification)", style = MaterialTheme.typography.titleMedium)

            PermissionStatusRow(
                icon = Icons.Filled.Wifi,
                title = "Internet Access",
                subtitle = if (permissionStatus.isOnline) "Connected to SPR Zone BD Cloud" else "Currently offline (cached mode)",
                granted = permissionStatus.internetGranted,
                actionLabel = null,
                onAction = {}
            )

            HorizontalDivider()

            PermissionStatusRow(
                icon = Icons.Filled.MyLocation,
                title = "Location Access",
                subtitle = when {
                    !permissionStatus.locationGranted -> "Allow location to auto-detect delivery zone"
                    !detectedLocationLabel.isNullOrBlank() -> "Detected: $detectedLocationLabel"
                    else -> "Granted — tap Detect to find your BD division"
                },
                granted = permissionStatus.locationGranted,
                actionLabel = if (permissionStatus.locationGranted) "Detect" else "Allow",
                onAction = {
                    if (permissionStatus.locationGranted) onDetectLocation() else onRequestLocationPermission()
                }
            )

            HorizontalDivider()

            PermissionStatusRow(
                icon = Icons.Filled.Notifications,
                title = "App Notifications",
                subtitle = if (permissionStatus.notificationGranted) {
                    "Enabled — receives Admin offer & order alerts"
                } else {
                    "Allow notifications for Admin sales & delivery alerts"
                },
                granted = permissionStatus.notificationGranted,
                actionLabel = if (permissionStatus.notificationGranted) "Inbox" else "Allow",
                onAction = {
                    if (permissionStatus.notificationGranted) onOpenNotifications() else onRequestNotificationPermission()
                }
            )

            if (!permissionStatus.locationGranted || !permissionStatus.notificationGranted) {
                OutlinedButton(
                    onClick = onOpenAppSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Android App Permission Settings")
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (granted) EmeraldPrimary else DiscountRed,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Surface(
                    color = if (granted) Color(0xFFE8F6EE) else Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (granted) "Granted" else "Needed",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (granted) EmeraldPrimary else DiscountRed,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (actionLabel != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private suspend fun launchGoogleCredentialFlow(context: Context): Result<String> {
    val activity = context.findActivity()
        ?: return Result.failure(IllegalStateException("Unable to open Google Sign-In picker from current screen."))
    return try {
        val manager = CredentialManager.create(activity)
        val googleOption = GetSignInWithGoogleOption.Builder(StoreConstants.GOOGLE_WEB_CLIENT_ID)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()
        val result = manager.getCredential(context = activity, request = request)
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            Result.success(googleCred.idToken)
        } else {
            Result.failure(IllegalStateException("Unexpected credential type returned by Google."))
        }
    } catch (_: GetCredentialCancellationException) {
        Result.failure(IllegalStateException("Google Sign-In was cancelled."))
    } catch (_: NoCredentialException) {
        Result.failure(IllegalStateException("No Google account available on this device. Please sign in to Google on your device or use Email/Password."))
    } catch (e: GoogleIdTokenParsingException) {
        Result.failure(IllegalStateException("Failed to read Google ID token: ${e.message ?: "Invalid token"}"))
    } catch (e: GetCredentialException) {
        Result.failure(IllegalStateException("Google Sign-In unavailable: ${e.errorMessage ?: "Check Google Play Services & Firebase SHA-1"}"))
    } catch (t: Throwable) {
        Result.failure(IllegalStateException(t.message ?: "Google Sign-In failed"))
    }
}

@Composable
private fun AccountLinkingCard(
    linkedProviders: List<String>,
    onLinkGoogleAccount: (googleIdToken: String, onResult: (Result<List<String>>) -> Unit) -> Unit,
    onLinkPassword: (newPassword: String, onResult: (Result<List<String>>) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasGoogle = linkedProviders.any { it.equals("google.com", ignoreCase = true) }
    val hasPassword = linkedProviders.any { it.equals("password", ignoreCase = true) }

    var showSetPasswordDialog by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var showNewPassword by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("account_linking_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Link, contentDescription = null, tint = EmeraldPrimary)
                Text("Sign-In Methods & Account Linking", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "Link both Email/Password and Google so you never run into sign-in errors across the website and mobile app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = if (hasPassword) Color(0xFFE8F6EE) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (hasPassword) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (hasPassword) "Password Linked" else "No Password Set",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Surface(
                    color = if (hasGoogle) Color(0xFFE8F6EE) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (hasGoogle) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (hasGoogle) "Google Linked" else "Google Not Linked",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (!hasGoogle) {
                    OutlinedButton(
                        enabled = !isBusy,
                        onClick = {
                            isBusy = true
                            statusMsg = null
                            scope.launch {
                                val credRes = launchGoogleCredentialFlow(context)
                                if (credRes.isFailure) {
                                    isBusy = false
                                    isError = true
                                    statusMsg = credRes.exceptionOrNull()?.message
                                } else {
                                    onLinkGoogleAccount(credRes.getOrThrow()) { res ->
                                        isBusy = false
                                        if (res.isSuccess) {
                                            isError = false
                                            statusMsg = "Google Sign-In linked to this account!"
                                        } else {
                                            isError = true
                                            statusMsg = res.exceptionOrNull()?.message
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("link_google_account_button")
                    ) {
                        Text(if (isBusy) "Linking..." else "Link Google Account")
                    }
                }
                if (!hasPassword) {
                    Button(
                        enabled = !isBusy,
                        onClick = { showSetPasswordDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("link_password_button")
                    ) {
                        Text("Set Email Password")
                    }
                }
            }

            if (statusMsg != null) {
                Text(
                    text = statusMsg!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) DiscountRed else EmeraldPrimary
                )
            }
        }
    }

    if (showSetPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showSetPasswordDialog = false },
            title = { Text("Set Email Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Add a password to your Google-signed account so you can log in with either Google or Email + Password.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; statusMsg = null },
                        label = { Text("New Password (min 6 chars)") },
                        visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { showNewPassword = !showNewPassword },
                                modifier = Modifier.testTag("link_password_visibility_toggle")
                            ) {
                                Icon(
                                    imageVector = if (showNewPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showNewPassword) "Hide password" else "Show password"
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("link_new_password_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isBusy,
                    onClick = {
                        isBusy = true
                        onLinkPassword(newPassword) { res ->
                            isBusy = false
                            if (res.isSuccess) {
                                newPassword = ""
                                showSetPasswordDialog = false
                                isError = false
                                statusMsg = "Email password linked successfully!"
                            } else {
                                isError = true
                                statusMsg = res.exceptionOrNull()?.message
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_link_password_button")
                ) {
                    Text(if (isBusy) "Saving..." else "Save Password")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AuthCardView(
    permissionStatus: AppPermissionStatus,
    detectedLocationLabel: String?,
    onRequestLocationPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onDetectLocation: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onLogin: (email: String, password: String, onResult: (Result<UserAccountEntity>) -> Unit) -> Unit,
    onRegister: (fullName: String, email: String, phone: String, password: String, onResult: (Result<UserAccountEntity>) -> Unit) -> Unit,
    onForgotPassword: (email: String, onResult: (Result<String>) -> Unit) -> Unit,
    onGoogleSignIn: (googleIdToken: String, onResult: (Result<UserAccountEntity>) -> Unit) -> Unit,
    onLoginAndLinkGoogle: (email: String, password: String, googleIdToken: String, onResult: (Result<UserAccountEntity>) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isRegisterMode by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var pendingGoogleLink by remember { mutableStateOf<GooglePendingLinkException?>(null) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }
    var forgotFeedback by remember { mutableStateOf<String?>(null) }
    var forgotFeedbackIsError by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var infoMsg by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

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
                    if (pendingGoogleLink != null) {
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pending_google_link_banner")
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = "Link Google Sign-In to ${pendingGoogleLink!!.email}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFF92400E),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Enter your account password below and tap 'Sign In & Link Google' once so both Google and Email/Password work seamlessly.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF78350F)
                                )
                            }
                        }
                    }

                    if (isRegisterMode && pendingGoogleLink == null) {
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
                        onValueChange = { email = it; errorMsg = null; infoMsg = null },
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
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.testTag("auth_password_visibility_toggle")
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_password_input")
                    )

                    if (!isRegisterMode) {
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = {
                                    forgotEmail = email.trim()
                                    forgotFeedback = null
                                    showForgotDialog = true
                                },
                                modifier = Modifier.testTag("forgot_password_button")
                            ) {
                                Text("Forgot Password?")
                            }
                        }
                    }

                    if (errorMsg != null) {
                        Text(
                            text = errorMsg!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = DiscountRed
                        )
                    }

                    if (infoMsg != null) {
                        Text(
                            text = infoMsg!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldPrimary
                        )
                    }

                    Button(
                        enabled = !isSubmitting,
                        onClick = {
                            isSubmitting = true
                            errorMsg = null
                            infoMsg = null
                            val pending = pendingGoogleLink
                            if (pending != null) {
                                onLoginAndLinkGoogle(email, password, pending.googleIdToken) { res ->
                                    isSubmitting = false
                                    if (res.isSuccess) {
                                        pendingGoogleLink = null
                                    } else {
                                        errorMsg = res.exceptionOrNull()?.message
                                    }
                                }
                            } else if (isRegisterMode) {
                                onRegister(fullName, email, phone, password) { res ->
                                    isSubmitting = false
                                    if (res.isFailure) errorMsg = res.exceptionOrNull()?.message
                                }
                            } else {
                                onLogin(email, password) { res ->
                                    isSubmitting = false
                                    if (res.isFailure) errorMsg = res.exceptionOrNull()?.message
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("auth_submit_button")
                    ) {
                        Text(
                            when {
                                isSubmitting && pendingGoogleLink != null -> "Verifying & Linking Google..."
                                pendingGoogleLink != null -> "Sign In & Link Google"
                                isSubmitting && isRegisterMode -> "Creating Account..."
                                isSubmitting -> "Signing In..."
                                isRegisterMode -> "Create Account"
                                else -> "Sign In"
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text = "OR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    OutlinedButton(
                        enabled = !isSubmitting,
                        onClick = {
                            isSubmitting = true
                            errorMsg = null
                            infoMsg = null
                            scope.launch {
                                val credRes = launchGoogleCredentialFlow(context)
                                if (credRes.isFailure) {
                                    isSubmitting = false
                                    errorMsg = credRes.exceptionOrNull()?.message
                                } else {
                                    val gToken = credRes.getOrThrow()
                                    onGoogleSignIn(gToken) { res ->
                                        isSubmitting = false
                                        if (res.isFailure) {
                                            val ex = res.exceptionOrNull()
                                            if (ex is GooglePendingLinkException) {
                                                pendingGoogleLink = ex
                                                isRegisterMode = false
                                                if (ex.email.isNotBlank()) email = ex.email
                                                errorMsg = ex.message
                                            } else {
                                                errorMsg = ex?.message
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("google_signin_button")
                    ) {
                        Text("Continue with Google (Login / Sign Up)", fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            isRegisterMode = !isRegisterMode
                            pendingGoogleLink = null
                            errorMsg = null
                            infoMsg = null
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

            Spacer(modifier = Modifier.height(14.dp))
            AppPermissionsCard(
                permissionStatus = permissionStatus,
                detectedLocationLabel = detectedLocationLabel,
                onRequestLocationPermission = onRequestLocationPermission,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onDetectLocation = onDetectLocation,
                onOpenNotifications = onOpenNotifications,
                onOpenAppSettings = onOpenAppSettings
            )
        }
    }

    if (showForgotDialog) {
        var sendingReset by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text("Reset Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter your registered email address and Firebase Authentication will email you a secure password reset link.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = forgotEmail,
                        onValueChange = { forgotEmail = it; forgotFeedback = null },
                        label = { Text("Email Address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_password_email_input")
                    )
                    if (forgotFeedback != null) {
                        Text(
                            text = forgotFeedback!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (forgotFeedbackIsError) DiscountRed else EmeraldPrimary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !sendingReset,
                    onClick = {
                        sendingReset = true
                        onForgotPassword(forgotEmail) { res ->
                            sendingReset = false
                            if (res.isSuccess) {
                                forgotFeedbackIsError = false
                                forgotFeedback = res.getOrThrow()
                                infoMsg = res.getOrThrow()
                            } else {
                                forgotFeedbackIsError = true
                                forgotFeedback = res.exceptionOrNull()?.message ?: "Could not send reset link"
                            }
                        }
                    },
                    modifier = Modifier.testTag("send_reset_email_button")
                ) {
                    Text(if (sendingReset) "Sending..." else "Send Reset Link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
