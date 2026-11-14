package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AddressEntity
import com.example.data.model.CartItemEntity
import com.example.data.model.OrderEntity
import com.example.data.model.ProductEntity
import com.example.data.model.ReviewEntity
import com.example.data.model.UserAccountEntity
import com.example.data.model.WishlistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY isFeatured DESC, createdAt DESC")
    fun observeAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    fun observeProductById(id: String): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: String): ProductEntity?

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(product: ProductEntity)

    @Query("UPDATE products SET stock = MAX(0, stock - :qty) WHERE id = :productId")
    suspend fun decrementStock(productId: String, qty: Int)

    @Query("UPDATE products SET rating = :rating, reviewCount = :reviewCount WHERE id = :productId")
    suspend fun updateRatingSummary(productId: String, rating: Double, reviewCount: Int)
}

@Dao
interface CartDao {
    @Query("SELECT * FROM cart_items WHERE userEmail = :userEmail ORDER BY addedAt DESC")
    fun observeCart(userEmail: String): Flow<List<CartItemEntity>>

    @Query("SELECT * FROM cart_items WHERE userEmail = :userEmail")
    suspend fun getCartItems(userEmail: String): List<CartItemEntity>

    @Query(
        "SELECT * FROM cart_items WHERE userEmail = :userEmail AND productId = :productId AND selectedVariant = :variant LIMIT 1"
    )
    suspend fun findCartItem(userEmail: String, productId: String, variant: String): CartItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartItem(item: CartItemEntity)

    @Update
    suspend fun updateCartItem(item: CartItemEntity)

    @Query("DELETE FROM cart_items WHERE id = :id")
    suspend fun deleteCartItemById(id: Long)

    @Query("DELETE FROM cart_items WHERE userEmail = :userEmail")
    suspend fun clearCart(userEmail: String)
}

@Dao
interface WishlistDao {
    @Query("SELECT productId FROM wishlist_items WHERE userEmail = :userEmail")
    fun observeWishlistIds(userEmail: String): Flow<List<String>>

    @Query("SELECT * FROM wishlist_items WHERE userEmail = :userEmail AND productId = :productId LIMIT 1")
    suspend fun findWishlistItem(userEmail: String, productId: String): WishlistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WishlistEntity)

    @Query("DELETE FROM wishlist_items WHERE userEmail = :userEmail AND productId = :productId")
    suspend fun delete(userEmail: String, productId: String)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM user_accounts WHERE email = :email LIMIT 1")
    fun observeUser(email: String): Flow<UserAccountEntity?>

    @Query("SELECT * FROM user_accounts WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: UserAccountEntity)
}

@Dao
interface AddressDao {
    @Query("SELECT * FROM addresses WHERE userEmail = :userEmail ORDER BY isDefault DESC, id DESC")
    fun observeAddresses(userEmail: String): Flow<List<AddressEntity>>

    @Query("SELECT COUNT(*) FROM addresses WHERE userEmail = :userEmail")
    suspend fun countAddresses(userEmail: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddress(address: AddressEntity): Long

    @Query("UPDATE addresses SET isDefault = 0 WHERE userEmail = :userEmail")
    suspend fun clearDefaultAddresses(userEmail: String)

    @Query("UPDATE addresses SET isDefault = 1 WHERE id = :addressId AND userEmail = :userEmail")
    suspend fun setDefaultAddress(addressId: Long, userEmail: String)

    @Query("DELETE FROM addresses WHERE id = :id")
    suspend fun deleteAddress(id: Long)
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE userEmail = :userEmail ORDER BY createdAt DESC")
    fun observeUserOrders(userEmail: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE orderId = :orderId LIMIT 1")
    fun observeOrderById(orderId: String): Flow<OrderEntity?>

    @Query("SELECT * FROM orders WHERE orderId = :orderId LIMIT 1")
    suspend fun getOrderById(orderId: String): OrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    @Query("UPDATE orders SET orderStatus = :status, updatedAt = :updatedAt WHERE orderId = :orderId")
    suspend fun updateOrderStatus(orderId: String, status: String, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM reviews WHERE productId = :productId ORDER BY createdAt DESC")
    fun observeReviewsForProduct(productId: String): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews WHERE productId = :productId")
    suspend fun getReviewsForProduct(productId: String): List<ReviewEntity>

    @Query("SELECT COUNT(*) FROM reviews")
    suspend fun getReviewCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: ReviewEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reviews: List<ReviewEntity>)
}
