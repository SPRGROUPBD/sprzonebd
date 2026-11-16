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
    version = 6,
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
    fun initialProducts(): List<ProductEntity> = emptyList()

    fun initialReviews(): List<ReviewEntity> = emptyList()
}
