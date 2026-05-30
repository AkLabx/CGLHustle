package com.example.di

import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

object SupabaseModule {

    val appJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        isLenient = true
    }

    @PrimaryBackend
    val primaryClient: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.CGL_HUSTLE_SUPABASE_URL,
            supabaseKey = BuildConfig.CGL_HUSTLE_SUPABASE_ANON_KEY
        ) {
            defaultSerializer = KotlinXSerializer(appJson)
            install(Postgrest)
            install(Auth) // Auth is safely enabled only for Primary Client
        }
    }

    @QuestionBackend
    val questionClient: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.GK_LLM_SUPABASE_URL,
            supabaseKey = BuildConfig.GK_LLM_SUPABASE_ANON_KEY
        ) {
            defaultSerializer = KotlinXSerializer(appJson)
            install(Postgrest)
            // No Auth installed for GK LLM backend per security rules
        }
    }
}
