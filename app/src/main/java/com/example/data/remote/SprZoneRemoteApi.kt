package com.example.data.remote

import com.example.data.model.MerchantPaymentSettings
import com.example.data.model.StoreConstants
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class RemoteProductDto(
    val name: String? = null,
    val category: String? = null,
    val brand: String? = null,
    val sellerName: String? = null,
    val price: Double? = null,
    val oldPrice: Double? = null,
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val stock: Int? = null,
    val isFlashDeal: Boolean? = null,
    val isFeatured: Boolean? = null,
    val image: String? = null,
    val description: String? = null,
    val specs: String? = null,
    val variants: String? = null,
    val variantColors: String? = null,
    val variantSizes: String? = null,
    val variantPrices: Map<String, Double>? = null,
    val freeDeliveryMinQty: Int? = null,
    val updatedAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class RemoteDeliveryZoneDto(
    val name: String? = null,
    val rate: Double? = null,
    val eta: String? = null
)

@JsonClass(generateAdapter = true)
data class RemoteDeliveryConfigDto(
    val freeDeliveryMinAmount: Double? = null,
    val zones: Map<String, RemoteDeliveryZoneDto>? = null
)

@JsonClass(generateAdapter = true)
data class RemotePaymentSettingsDto(
    val bkashNumber: String? = null,
    val nagadNumber: String? = null,
    val rocketNumber: String? = null,
    val smsApiUrl: String? = null,
    val smsApiKey: String? = null,
    val autoVerifyEnabled: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class RemoteCouponDto(
    val code: String? = null,
    val type: String? = null,
    val value: Double? = null,
    val minOrder: Double? = null
)

@JsonClass(generateAdapter = true)
data class RemoteAppNotificationDto(
    val title: String? = null,
    val message: String? = null,
    val type: String? = null,
    val productId: String? = null,
    val targetEmail: String? = null,
    val active: Boolean? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class RemoteSmsRecordDto(
    val senderNumber: String? = null,
    val provider: String? = null,
    val amount: Double? = null,
    val trxId: String? = null,
    val matchedOrderId: String? = null,
    val receivedAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class SmsVerifyApiResponse(
    val verified: Boolean? = null,
    val status: String? = null,
    val message: String? = null,
    val amount: Double? = null
)

@JsonClass(generateAdapter = true)
data class RemoteOrderItemDto(
    val id: String? = null,
    val name: String? = null,
    val price: Long? = null,
    val qty: Int? = null,
    val variant: String? = null
)

@JsonClass(generateAdapter = true)
data class RemoteCustomerDto(
    val name: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val district: String? = null,
    val division: String? = null
)

@JsonClass(generateAdapter = true)
data class RemoteOrderDto(
    val orderId: String? = null,
    val uid: String? = null,
    val customerEmail: String? = null,
    val customer: RemoteCustomerDto? = null,
    val items: List<RemoteOrderItemDto>? = null,
    val itemsSummary: String? = null,
    val subtotal: Long? = null,
    val discount: Long? = null,
    val deliveryFee: Long? = null,
    val total: Long? = null,
    val promoCode: String? = null,
    val currency: String? = null,
    val paymentMethod: String? = null,
    val senderNumber: String? = null,
    val trxId: String? = null,
    val paymentStatus: String? = null,
    val status: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class RemoteCustomerAddressDto(
    val label: String? = null,
    val recipientName: String? = null,
    val phone: String? = null,
    val division: String? = null,
    val district: String? = null,
    val thana: String? = null,
    val street: String? = null,
    val isDefault: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class RemoteCustomerProfileDto(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val bio: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val addresses: Map<String, RemoteCustomerAddressDto>? = null
)

@JsonClass(generateAdapter = true)
data class FirebaseAuthRequest(
    val email: String,
    val password: String,
    val returnSecureToken: Boolean = true
)

@JsonClass(generateAdapter = true)
data class FirebaseAuthResponse(
    val idToken: String? = null,
    val email: String? = null,
    val refreshToken: String? = null,
    val localId: String? = null,
    val displayName: String? = null
)

@JsonClass(generateAdapter = true)
data class FirebaseDeleteAccountRequest(
    val idToken: String
)

@JsonClass(generateAdapter = true)
data class FirebaseTokenRefreshResponse(
    val id_token: String? = null,
    val refresh_token: String? = null,
    val user_id: String? = null
)

interface FirebaseAuthRestService {
    @POST("v1/accounts:signUp")
    suspend fun signUp(
        @Query("key") apiKey: String = StoreConstants.FIREBASE_WEB_API_KEY,
        @Body body: FirebaseAuthRequest
    ): Response<FirebaseAuthResponse?>

    @POST("v1/accounts:signInWithPassword")
    suspend fun signInWithPassword(
        @Query("key") apiKey: String = StoreConstants.FIREBASE_WEB_API_KEY,
        @Body body: FirebaseAuthRequest
    ): Response<FirebaseAuthResponse?>

    @POST("v1/accounts:delete")
    suspend fun deleteAccount(
        @Query("key") apiKey: String = StoreConstants.FIREBASE_WEB_API_KEY,
        @Body body: FirebaseDeleteAccountRequest
    ): Response<Unit>
}

interface FirebaseTokenRefreshService {
    @FormUrlEncoded
    @POST("v1/token")
    suspend fun refreshToken(
        @Query("key") apiKey: String = StoreConstants.FIREBASE_WEB_API_KEY,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String
    ): Response<FirebaseTokenRefreshResponse?>
}

interface SprZoneRtdbService {
    @GET("products.json")
    suspend fun getProducts(): Response<Map<String, RemoteProductDto>?>

    @GET("deals/deliveryConfig.json")
    suspend fun getDeliveryConfig(): Response<RemoteDeliveryConfigDto?>

    @GET("settings/payment.json")
    suspend fun getPaymentSettings(): Response<RemotePaymentSettingsDto?>

    @GET("coupons.json")
    suspend fun getCoupons(): Response<Map<String, RemoteCouponDto>?>

    @GET("deals/appNotifications.json")
    suspend fun getAppNotifications(): Response<Map<String, RemoteAppNotificationDto>?>

    @GET("customers/{uid}.json")
    suspend fun getCustomerProfile(
        @Path("uid") uid: String,
        @Query("auth") authToken: String
    ): Response<RemoteCustomerProfileDto?>

    @PUT("customers/{uid}.json")
    suspend fun putCustomerProfile(
        @Path("uid") uid: String,
        @Query("auth") authToken: String,
        @Body profile: RemoteCustomerProfileDto
    ): Response<RemoteCustomerProfileDto?>

    @PATCH("customers/{uid}.json")
    suspend fun patchCustomerProfile(
        @Path("uid") uid: String,
        @Query("auth") authToken: String,
        @Body patch: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>?>

    @DELETE("customers/{uid}.json")
    suspend fun deleteCustomerProfile(
        @Path("uid") uid: String,
        @Query("auth") authToken: String
    ): Response<Unit>

    @PUT("customers/{uid}/addresses/{addrId}.json")
    suspend fun putCustomerAddress(
        @Path("uid") uid: String,
        @Path("addrId") addrId: String,
        @Query("auth") authToken: String,
        @Body address: RemoteCustomerAddressDto
    ): Response<RemoteCustomerAddressDto?>

    @PATCH("customers/{uid}/addresses.json")
    suspend fun patchCustomerAddresses(
        @Path("uid") uid: String,
        @Query("auth") authToken: String,
        @Body addressesPatch: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>?>

    @DELETE("customers/{uid}/addresses/{addrId}.json")
    suspend fun deleteCustomerAddress(
        @Path("uid") uid: String,
        @Path("addrId") addrId: String,
        @Query("auth") authToken: String
    ): Response<Unit>

    @GET("sms_inbox/{trxId}.json")
    suspend fun getSmsInboxRecord(@Path("trxId") trxId: String): Response<RemoteSmsRecordDto?>

    @PATCH("sms_inbox/{trxId}.json")
    suspend fun markSmsRecordMatched(
        @Path("trxId") trxId: String,
        @Body patch: Map<String, String>
    ): Response<Map<String, String>?>

    @PUT("orders/{orderId}.json")
    suspend fun putOrder(
        @Path("orderId") orderId: String,
        @Body order: RemoteOrderDto
    ): Response<RemoteOrderDto?>

    @GET("orders/{orderId}.json")
    suspend fun getOrder(@Path("orderId") orderId: String): Response<RemoteOrderDto?>

    @GET("orders.json")
    suspend fun getAllOrders(): Response<Map<String, RemoteOrderDto>?>

    @PATCH("orders/{orderId}.json")
    suspend fun patchOrderStatus(
        @Path("orderId") orderId: String,
        @Body patch: Map<String, Any>
    ): Response<Map<String, Any>?>

    @GET
    suspend fun verifyViaExternalSmsApi(
        @Url url: String,
        @Query("sender") sender: String,
        @Query("trxId") trxId: String,
        @Query("amount") amount: Long,
        @Query("provider") provider: String,
        @Header("X-API-Key") apiKey: String
    ): Response<SmsVerifyApiResponse?>
}

object SprZoneNetworkClient {
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttp: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    val rtdbService: SprZoneRtdbService by lazy {
        Retrofit.Builder()
            .baseUrl(StoreConstants.RTDB_BASE_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SprZoneRtdbService::class.java)
    }

    val authService: FirebaseAuthRestService by lazy {
        Retrofit.Builder()
            .baseUrl(StoreConstants.FIREBASE_AUTH_BASE_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FirebaseAuthRestService::class.java)
    }

    val tokenService: FirebaseTokenRefreshService by lazy {
        Retrofit.Builder()
            .baseUrl(StoreConstants.FIREBASE_TOKEN_BASE_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FirebaseTokenRefreshService::class.java)
    }

    fun RemotePaymentSettingsDto.toDomain(): MerchantPaymentSettings {
        return MerchantPaymentSettings(
            bkashNumber = bkashNumber.orEmpty().trim(),
            nagadNumber = nagadNumber.orEmpty().trim(),
            rocketNumber = rocketNumber.orEmpty().trim(),
            smsApiUrl = smsApiUrl.orEmpty().trim(),
            smsApiKey = smsApiKey.orEmpty().trim(),
            autoVerifyEnabled = autoVerifyEnabled ?: true
        )
    }
}
