package com.example.telegrambackup.network

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClientProvider {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                val token = ServerApi.activeAuthToken
                val isExternal = url.contains("telegram.org") ||
                                 url.contains("googleapis.com") ||
                                 url.contains("github.com") ||
                                 url.contains("raw.githubusercontent.com")
                if (!isExternal && token.isNotBlank() && request.header("Authorization") == null) {
                    val authRequest = request.newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                    chain.proceed(authRequest)
                } else {
                    chain.proceed(request)
                }
            }
            .build()
    }

    // Extended timeout client specifically for Telegram document/video file uploads on low networks
    val uploadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 10, TimeUnit.MINUTES))
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.MINUTES)
            .writeTimeout(15, TimeUnit.MINUTES)
            .pingInterval(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // High-throughput parallel download client for fast Telegram chunk streaming
    val downloadClient: OkHttpClient by lazy {
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 32
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(60, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
