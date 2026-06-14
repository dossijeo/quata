package com.quata.core.translation

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.quata.core.language.QuataTranslationLanguage
import com.quata.core.language.QuataTranslationResult
import com.quata.core.language.QuataTranslator
import com.quata.core.language.QuataTranslatorClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class CachedQuataTranslator(
    context: Context,
    private val client: QuataTranslatorClient = QuataTranslator.shared
) {
    private val store = QuataTranslationCacheStore(context.applicationContext)

    suspend fun translate(
        text: String,
        sourceLanguage: QuataTranslationLanguage,
        targetLanguage: QuataTranslationLanguage,
        maxNewTokens: Int = QuataTranslatorClient.DefaultMaxNewTokens
    ): QuataTranslationResult {
        val normalizedText = text.trim()
        val key = translationCacheKey(normalizedText, sourceLanguage, targetLanguage, maxNewTokens)
        store.read(key)?.let { return it }

        val result = client.translate(
            text = normalizedText,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            maxNewTokens = maxNewTokens
        )
        store.write(key, normalizedText, sourceLanguage, targetLanguage, maxNewTokens, result)
        return result
    }
}

object QuataCachedTranslator {
    @Volatile
    private var instance: CachedQuataTranslator? = null

    fun get(context: Context): CachedQuataTranslator =
        instance ?: synchronized(this) {
            instance ?: CachedQuataTranslator(context.applicationContext).also { instance = it }
        }
}

private class QuataTranslationCacheStore(context: Context) {
    private val helper = CacheOpenHelper(context.applicationContext)

    suspend fun read(key: String): QuataTranslationResult? = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            TABLE_CACHE,
            COLUMNS,
            "$COL_KEY = ?",
            arrayOf(key),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            QuataTranslationResult(
                translation = cursor.getString(cursor.getColumnIndexOrThrow(COL_TRANSLATION)),
                pivotUsed = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PIVOT_USED)) == 1,
                route = cursor.getString(cursor.getColumnIndexOrThrow(COL_ROUTE))
                    .split(',')
                    .mapNotNull { QuataTranslationLanguage.fromApiCode(it) },
                pivotLanguage = cursor.getString(cursor.getColumnIndexOrThrow(COL_PIVOT_LANGUAGE))
                    ?.let(QuataTranslationLanguage::fromApiCode),
                pivotText = cursor.getString(cursor.getColumnIndexOrThrow(COL_PIVOT_TEXT)),
                pivotEngine = cursor.getString(cursor.getColumnIndexOrThrow(COL_PIVOT_ENGINE))
            )
        }
    }

    suspend fun write(
        key: String,
        text: String,
        sourceLanguage: QuataTranslationLanguage,
        targetLanguage: QuataTranslationLanguage,
        maxNewTokens: Int,
        result: QuataTranslationResult
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COL_KEY, key)
            put(COL_TEXT, text)
            put(COL_SOURCE_LANGUAGE, sourceLanguage.apiCode)
            put(COL_TARGET_LANGUAGE, targetLanguage.apiCode)
            put(COL_MAX_NEW_TOKENS, maxNewTokens)
            put(COL_TRANSLATION, result.translation)
            put(COL_PIVOT_USED, if (result.pivotUsed) 1 else 0)
            put(COL_ROUTE, result.route.joinToString(",") { it.apiCode })
            put(COL_PIVOT_LANGUAGE, result.pivotLanguage?.apiCode)
            put(COL_PIVOT_TEXT, result.pivotText)
            put(COL_PIVOT_ENGINE, result.pivotEngine)
            put(COL_CREATED_AT, System.currentTimeMillis())
        }
        helper.writableDatabase.insertWithOnConflict(
            TABLE_CACHE,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private class CacheOpenHelper(context: Context) : SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        null,
        DATABASE_VERSION
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_CACHE (
                    $COL_KEY TEXT PRIMARY KEY,
                    $COL_TEXT TEXT NOT NULL,
                    $COL_SOURCE_LANGUAGE TEXT NOT NULL,
                    $COL_TARGET_LANGUAGE TEXT NOT NULL,
                    $COL_MAX_NEW_TOKENS INTEGER NOT NULL,
                    $COL_TRANSLATION TEXT NOT NULL,
                    $COL_PIVOT_USED INTEGER NOT NULL,
                    $COL_ROUTE TEXT NOT NULL,
                    $COL_PIVOT_LANGUAGE TEXT,
                    $COL_PIVOT_TEXT TEXT,
                    $COL_PIVOT_ENGINE TEXT,
                    $COL_CREATED_AT INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_${TABLE_CACHE}_languages ON $TABLE_CACHE($COL_SOURCE_LANGUAGE, $COL_TARGET_LANGUAGE)")
            db.execSQL("CREATE INDEX idx_${TABLE_CACHE}_created_at ON $TABLE_CACHE($COL_CREATED_AT)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_CACHE")
            onCreate(db)
        }
    }

    private companion object {
        const val DATABASE_NAME = "quata_translation_cache.db"
        const val DATABASE_VERSION = 1
        const val TABLE_CACHE = "translation_cache"
        const val COL_KEY = "cache_key"
        const val COL_TEXT = "source_text"
        const val COL_SOURCE_LANGUAGE = "source_language"
        const val COL_TARGET_LANGUAGE = "target_language"
        const val COL_MAX_NEW_TOKENS = "max_new_tokens"
        const val COL_TRANSLATION = "translation"
        const val COL_PIVOT_USED = "pivot_used"
        const val COL_ROUTE = "route"
        const val COL_PIVOT_LANGUAGE = "pivot_language"
        const val COL_PIVOT_TEXT = "pivot_text"
        const val COL_PIVOT_ENGINE = "pivot_engine"
        const val COL_CREATED_AT = "created_at_millis"
        val COLUMNS = arrayOf(
            COL_KEY,
            COL_TEXT,
            COL_SOURCE_LANGUAGE,
            COL_TARGET_LANGUAGE,
            COL_MAX_NEW_TOKENS,
            COL_TRANSLATION,
            COL_PIVOT_USED,
            COL_ROUTE,
            COL_PIVOT_LANGUAGE,
            COL_PIVOT_TEXT,
            COL_PIVOT_ENGINE,
            COL_CREATED_AT
        )
    }
}

private fun translationCacheKey(
    text: String,
    sourceLanguage: QuataTranslationLanguage,
    targetLanguage: QuataTranslationLanguage,
    maxNewTokens: Int
): String {
    val raw = buildString {
        append("v1")
        append('\n')
        append(sourceLanguage.apiCode)
        append('\n')
        append(targetLanguage.apiCode)
        append('\n')
        append(maxNewTokens)
        append('\n')
        append(text)
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
