package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.AddressEntity
import com.example.data.model.CartItemEntity
import com.example.data.model.OrderEntity
import com.example.data.model.ProductEntity
import com.example.data.model.ReviewEntity
import com.example.data.model.UserAccountEntity
import com.example.data.model.WishlistEntity

@Database(
    entities = [
        ProductEntity::class,
        CartItemEntity::class,
        WishlistEntity::class,
        UserAccountEntity::class,
        AddressEntity::class,
        OrderEntity::class,
        ReviewEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SprZoneDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun cartDao(): CartDao
    abstract fun wishlistDao(): WishlistDao
    abstract fun userDao(): UserDao
    abstract fun addressDao(): AddressDao
    abstract fun orderDao(): OrderDao
    abstract fun reviewDao(): ReviewDao

    companion object {
        @Volatile
        private var INSTANCE: SprZoneDatabase? = null

        fun getInstance(context: Context): SprZoneDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SprZoneDatabase::class.java,
                    "spr_zone_bd.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

object CatalogSeeder {
    fun initialProducts(): List<ProductEntity> = listOf(
        ProductEntity(
            id = "prod_elec_01",
            name = "Bluetooth Earbuds TWS M10",
            category = "Electronics & Gadgets",
            brand = "SPR Audio",
            sellerName = "SPR Official Store",
            priceBdt = 1250,
            originalPriceBdt = 1800,
            discountPercent = 31,
            rating = 4.6,
            reviewCount = 120,
            stock = 45,
            isFlashSale = true,
            isFeatured = true,
            imageKey = "electronics",
            description = "Digital LED battery display TWS wireless earbuds with powerbank charging case, deep bass stereo, and splash-resistant ergonomic buds.",
            specsSummary = "Bluetooth 5.3 • 2000mAh Power Case • Touch Control • IPX5",
            variantsCsv = "Matte Black, Pearl White"
        ),
        ProductEntity(
            id = "prod_mob_01",
            name = "Samsung Galaxy A16 (6GB + 128GB)",
            category = "Mobile & Accessories",
            brand = "Samsung BD",
            sellerName = "SPR Official Store",
            priceBdt = 18999,
            originalPriceBdt = 22999,
            discountPercent = 17,
            rating = 4.7,
            reviewCount = 80,
            stock = 22,
            isFlashSale = true,
            isFeatured = true,
            imageKey = "electronics",
            description = "Official Bangladesh PTA-approved smartphone with vibrant 6.7-inch Super AMOLED 90Hz display, 50MP triple camera, and 5000mAh 25W fast battery.",
            specsSummary = "6.7\" Super AMOLED • 6GB/128GB • 50MP Camera • 5000mAh",
            variantsCsv = "6GB/128GB Blue Black, 6GB/128GB Light Green"
        ),
        ProductEntity(
            id = "prod_comp_01",
            name = "HP Laptop 15s (i5, 12GB, 512GB SSD)",
            category = "Computer & Accessories",
            brand = "HP Bangladesh",
            sellerName = "SPR Official Store",
            priceBdt = 49999,
            originalPriceBdt = 62000,
            discountPercent = 19,
            rating = 4.5,
            reviewCount = 50,
            stock = 11,
            isFlashSale = true,
            isFeatured = true,
            imageKey = "electronics",
            description = "Powerful 12th Gen Intel Core i5 laptop with 12GB DDR4 RAM, 512GB NVMe SSD, 15.6\" FHD anti-glare display, and 2-year official warranty.",
            specsSummary = "Core i5 • 12GB RAM • 512GB NVMe SSD • 15.6\" FHD",
            variantsCsv = "Natural Silver, Chalkboard Gray"
        ),
        ProductEntity(
            id = "prod_elec_02",
            name = "Smart Watch T900 Ultra",
            category = "Electronics & Gadgets",
            brand = "PulseBD",
            sellerName = "Dhaka Gadget Hub",
            priceBdt = 1950,
            originalPriceBdt = 2500,
            discountPercent = 22,
            rating = 4.5,
            reviewCount = 75,
            stock = 38,
            isFlashSale = true,
            isFeatured = true,
            imageKey = "electronics",
            description = "Large 2.09-inch HD display smartwatch with Bluetooth calling, heart-rate & step tracking, wireless charging, and multiple Bangla/English watch faces.",
            specsSummary = "2.09\" HD Display • BT Calling • Wireless Charge • IP67",
            variantsCsv = "Orange Trail Loop, Midnight Black, Titanium Gray"
        ),
        ProductEntity(
            id = "prod_home_01",
            name = "Air Fryer 6L Digital",
            category = "Home & Kitchen",
            brand = "Nova Kitchen BD",
            sellerName = "SPR Home Studio",
            priceBdt = 6999,
            originalPriceBdt = 8500,
            discountPercent = 18,
            rating = 4.5,
            reviewCount = 42,
            stock = 16,
            isFlashSale = false,
            isFeatured = true,
            imageKey = "groceries",
            description = "6-Litre family capacity digital touchscreen air fryer with 85% less oil rapid 360° air circulation, 8 presets, and non-stick dishwasher-safe basket.",
            specsSummary = "6L Capacity • 1700W Rapid Air • Digital LED Touch • Non-Stick",
            variantsCsv = "Matte Black 6L, Brushed Steel 6L"
        ),
        ProductEntity(
            id = "prod_fash_01",
            name = "Ladies Hand Bag Fashion",
            category = "Fashion & Apparel",
            brand = "Dhaka Vogue",
            sellerName = "SPR Fashion",
            priceBdt = 1550,
            originalPriceBdt = 2200,
            discountPercent = 30,
            rating = 4.6,
            reviewCount = 62,
            stock = 30,
            isFlashSale = true,
            isFeatured = true,
            imageKey = "fashion",
            description = "Structured PU-leather tote handbag with gold hardware clasps, detachable crossbody strap, and spacious inner zip compartments for office or casual wear.",
            specsSummary = "Premium Vegan Leather • Detachable Strap • 3 Compartments",
            variantsCsv = "Dusty Rose, Classic Black, Warm Camel"
        ),
        ProductEntity(
            id = "prod_fash_02",
            name = "Authentic Dhakai Jamdani Half-Silk Saree",
            category = "Fashion & Apparel",
            brand = "Narayanganj Loom",
            sellerName = "Sonargaon Heritage",
            priceBdt = 12800,
            originalPriceBdt = 16500,
            discountPercent = 22,
            rating = 4.9,
            reviewCount = 88,
            stock = 14,
            isFlashSale = true,
            isFeatured = true,
            imageKey = "fashion",
            description = "Handwoven by master artisans in Rupganj, Narayanganj using fine 84-count cotton-silk blend with traditional emerald and gold zari motif work.",
            specsSummary = "Handwoven 84-Count • 12 Haat + Blouse Piece • GI Certified",
            variantsCsv = "Emerald & Gold Zari, Royal Crimson & Gold"
        ),
        ProductEntity(
            id = "prod_groc_01",
            name = "Sundarbans Raw Wildflower Honey (1kg)",
            category = "Groceries & Daily Needs",
            brand = "Khulna Apiaries",
            sellerName = "Sylhet & Sundarban Organics",
            priceBdt = 1150,
            originalPriceBdt = 1400,
            discountPercent = 18,
            rating = 4.9,
            reviewCount = 156,
            stock = 60,
            isFlashSale = true,
            isFeatured = true,
            imageKey = "groceries",
            description = "100% pure, unfiltered Khalisha and Goran flower honey harvested ethically from the Sundarbans mangrove forest.",
            specsSummary = "1kg Glass Jar • BSTI Certified • Harvested 2026",
            variantsCsv = "500g Jar, 1kg Family Jar"
        ),
        ProductEntity(
            id = "prod_beauty_01",
            name = "Botanical Niacinamide & Rice Glow Serum",
            category = "Health & Beauty",
            brand = "Noor Botanics BD",
            sellerName = "Dhaka Glow Mart",
            priceBdt = 890,
            originalPriceBdt = 1150,
            discountPercent = 23,
            rating = 4.7,
            reviewCount = 74,
            stock = 50,
            isFlashSale = false,
            isFeatured = false,
            imageKey = "fashion",
            description = "Lightweight non-comedogenic daily serum formulated for humid tropical skin with 5% Niacinamide and fermented rice water.",
            specsSummary = "30ml Dropper • Fragrance-Free • Dermatologist Tested",
            variantsCsv = "30ml Single, 60ml Duo Pack"
        ),
        ProductEntity(
            id = "prod_sport_01",
            name = "Bangladesh Tigers English Willow Cricket Bat",
            category = "Sports & Outdoors",
            brand = "Mirpur Pro",
            sellerName = "Stadium Sports BD",
            priceBdt = 7500,
            originalPriceBdt = 9200,
            discountPercent = 18,
            rating = 4.9,
            reviewCount = 51,
            stock = 11,
            isFlashSale = false,
            isFeatured = true,
            imageKey = "fashion",
            description = "Grade-2 English Willow short-handle cricket bat with thick sweet-spot edges, Singapore cane handle, and padded carry cover.",
            specsSummary = "Short Handle • 1180g Balanced • Free Carry Cover",
            variantsCsv = "1160g Light, 1200g Heavy"
        ),
        ProductEntity(
            id = "prod_baby_01",
            name = "Organic Muslin Baby Swaddle Bundle",
            category = "Toys & Baby Care",
            brand = "Shishu Care BD",
            sellerName = "SPR Family Store",
            priceBdt = 1490,
            originalPriceBdt = 1850,
            discountPercent = 19,
            rating = 4.8,
            reviewCount = 62,
            stock = 33,
            isFlashSale = false,
            isFeatured = false,
            imageKey = "groceries",
            description = "Ultra-soft 100% organic Dhaka muslin 3-pack swaddle wraps gentle on newborn skin.",
            specsSummary = "3-Pack 120x120cm • 100% Organic Muslin • Hypoallergenic",
            variantsCsv = "Pastel Meadow Set, Starlight Cloud Set"
        ),
        ProductEntity(
            id = "prod_book_01",
            name = "Bangla Literature Collector Hardcover Box",
            category = "Books & Stationery",
            brand = "BoiBitan Press",
            sellerName = "Nilkhet Book House",
            priceBdt = 1650,
            originalPriceBdt = 2100,
            discountPercent = 21,
            rating = 4.9,
            reviewCount = 47,
            stock = 27,
            isFlashSale = false,
            isFeatured = false,
            imageKey = "fashion",
            description = "Curated 4-volume hardcover collector edition celebrating classic Bengali short stories and poetry.",
            specsSummary = "4 Hardcover Volumes • Acid-Free Cream Paper",
            variantsCsv = "Complete 4-Book Box"
        )
    )

    fun initialReviews(): List<ReviewEntity> = listOf(
        ReviewEntity(
            productId = "prod_elec_01",
            userName = "Tariqul Islam (Dhaka)",
            userEmail = "tariq@demo.bd",
            rating = 5,
            comment = "Awesome sound and battery display for ৳1,250! Fast delivery inside Dhaka.",
            verifiedPurchase = true
        ),
        ReviewEntity(
            productId = "prod_mob_01",
            userName = "Nusrat Jahan (Chattogram)",
            userEmail = "nusrat@demo.bd",
            rating = 5,
            comment = "Official BTRC approved phone, paid with bKash TrxID and verified instantly.",
            verifiedPurchase = true
        ),
        ReviewEntity(
            productId = "prod_groc_01",
            userName = "Mahmudul Hasan (Sylhet)",
            userEmail = "mahmud@demo.bd",
            rating = 5,
            comment = "Pure Sundarbans aroma and thick texture. Well packed.",
            verifiedPurchase = true
        )
    )
}
