package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ProductEntity
import com.example.data.model.StoreConstants
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.ProductCard
import com.example.ui.viewmodel.CatalogFilterState
import com.example.ui.viewmodel.ProductSortOption

private data class CategoryShowcase(
    val title: String,
    val subtitle: String,
    val imageRes: Int
)

@Composable
fun CategoriesScreen(
    allProducts: List<ProductEntity>,
    filteredProducts: List<ProductEntity>,
    wishlistIds: List<String>,
    filterState: CatalogFilterState,
    onSearchChange: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onSortChange: (ProductSortOption) -> Unit,
    onInStockToggle: (Boolean) -> Unit,
    onMinRatingChange: (Double) -> Unit,
    onClearFilters: () -> Unit,
    onProductClick: (String) -> Unit,
    onToggleWishlist: (String) -> Unit,
    onQuickAdd: (ProductEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val wishlistSet = remember(wishlistIds) { wishlistIds.toSet() }
    val showcases = remember {
        listOf(
            CategoryShowcase("Electronics & Gadgets", "Earbuds, Watches, Air Fryers", R.drawable.img_cat_electronics),
            CategoryShowcase("Fashion & Apparel", "Jamdani Sarees, Handbags, Panjabi", R.drawable.img_cat_fashion),
            CategoryShowcase("Groceries & Daily Needs", "Sundarbans Honey, Sylhet Tea", R.drawable.img_cat_groceries_home),
            CategoryShowcase("Home & Kitchen", "Air Fryers, Terracotta, Appliances", R.drawable.img_cat_groceries_home)
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 900.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1200.dp)
                .align(Alignment.TopCenter)
        ) {
            item {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                    Text(
                        text = "Explore Bangladesh A to Z",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Browse 10 curated marketplace departments",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = filterState.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search across all departments...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (filterState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("explore_search_input")
                )
            }

            // Visual Department Showcase Cards
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(showcases) { showcase ->
                        val count = allProducts.count { it.category == showcase.title }
                        val isSelected = filterState.selectedCategory == showcase.title
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 2.dp),
                            modifier = Modifier
                                .width(200.dp)
                                .height(112.dp)
                                .clickable {
                                    onSelectCategory(if (isSelected) "All" else showcase.title)
                                }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    painter = painterResource(id = showcase.imageRes),
                                    contentDescription = showcase.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.15f),
                                                    Color.Black.copy(alpha = 0.78f)
                                                )
                                            )
                                        )
                                )
                                if (isSelected) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "Active",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = showcase.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$count items • ${showcase.subtitle}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // All Category Pills
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(StoreConstants.CATEGORIES) { cat ->
                        val count = if (cat == "All") allProducts.size else allProducts.count { it.category == cat }
                        FilterChip(
                            selected = filterState.selectedCategory == cat,
                            onClick = { onSelectCategory(cat) },
                            label = { Text("$cat ($count)") }
                        )
                    }
                }
            }

            // Attribute Filter Chips
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = filterState.inStockOnly,
                            onClick = { onInStockToggle(!filterState.inStockOnly) },
                            label = { Text("In Stock") },
                            leadingIcon = if (filterState.inStockOnly) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else null
                        )
                    }
                    item {
                        val fourEight = filterState.minRating >= 4.8
                        FilterChip(
                            selected = fourEight,
                            onClick = { onMinRatingChange(if (fourEight) 0.0 else 4.8) },
                            label = { Text("4.8★ & Up") }
                        )
                    }
                    items(ProductSortOption.entries) { sortOpt ->
                        FilterChip(
                            selected = filterState.sortOption == sortOpt,
                            onClick = { onSortChange(sortOpt) },
                            label = { Text(sortOpt.label) }
                        )
                    }
                    item {
                        TextButton(onClick = onClearFilters) {
                            Text("Reset")
                        }
                    }
                }
            }

            if (filteredProducts.isEmpty()) {
                item {
                    EmptyStatePanel(
                        title = "No products match these filters",
                        subtitle = "Adjust your category, rating, or search keyword.",
                        icon = Icons.Filled.FilterListOff,
                        actionLabel = "Clear All Filters",
                        onAction = onClearFilters
                    )
                }
            } else {
                val rows = filteredProducts.chunked(columns)
                items(rows) { rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        for (product in rowItems) {
                            ProductCard(
                                product = product,
                                isWishlisted = product.id in wishlistSet,
                                onProductClick = { onProductClick(product.id) },
                                onToggleWishlist = { onToggleWishlist(product.id) },
                                onQuickAdd = { onQuickAdd(product) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(columns - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
