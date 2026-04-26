package com.quata.core.network.wordpress

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface WordpressApi {
    @FormUrlEncoded
    @POST("wp-json/jwt-auth/v1/token")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): WordpressTokenResponse

    /**
     * Endpoint recomendado: crear un pequeño plugin WP propio que exponga /wp-json/quata/v1/register.
     */
    @POST("wp-json/quata/v1/register")
    suspend fun register(@Body request: WordpressRegisterRequest): WordpressRegisterResponse

    @GET("wp-json/wp/v2/posts")
    suspend fun getPosts(
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
        @Query("_embed") embed: Int = 1
    ): List<WordpressPostDto>

    @POST("wp-json/wp/v2/posts")
    suspend fun createPost(
        @Header("Authorization") bearerToken: String,
        @Body request: WordpressCreatePostRequest
    ): WordpressPostDto

    @Multipart
    @POST("wp-json/wp/v2/media")
    suspend fun uploadMedia(
        @Header("Authorization") bearerToken: String,
        @Part file: MultipartBody.Part
    ): Map<String, Any>

    @GET("wp-json/wp/v2/users/{id}")
    suspend fun getUser(@Path("id") id: Int): Map<String, Any>
}
