package com.quata.feature.auth.data

import com.quata.core.network.wordpress.WordpressApi
import com.quata.core.network.wordpress.WordpressRegisterRequest

class AuthRemoteDataSource(private val wordpressApi: WordpressApi) {
    suspend fun login(email: String, password: String) = wordpressApi.login(email, password)

    suspend fun register(email: String, password: String, displayName: String) =
        wordpressApi.register(WordpressRegisterRequest(email, password, displayName))
}
