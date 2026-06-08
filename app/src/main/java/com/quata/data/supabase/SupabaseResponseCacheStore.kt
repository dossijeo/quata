package com.quata.data.supabase

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

data class CachedSupabaseResponse(
    val key: String,
    val method: String,
    val url: String,
    val tableName: String?,
    val responseJson: String,
    val updatedAtMillis: Long
)

class SupabaseResponseCacheStore(context: Context) {
    private val helper = CacheOpenHelper(context.applicationContext)
    private val changes = MutableSharedFlow<String>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    suspend fun read(key: String): CachedSupabaseResponse? = withContext(Dispatchers.IO) {
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
            cursor.toCachedResponse()
        }
    }

    suspend fun write(
        key: String,
        method: String,
        url: String,
        tableName: String?,
        responseJson: String
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COL_KEY, key)
            put(COL_METHOD, method)
            put(COL_URL, url)
            put(COL_TABLE_NAME, tableName)
            put(COL_RESPONSE_JSON, responseJson)
            put(COL_UPDATED_AT, System.currentTimeMillis())
        }
        helper.writableDatabase.insertWithOnConflict(
            TABLE_CACHE,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        changes.tryEmit(key)
    }

    suspend fun invalidateTable(tableName: String) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val keys = mutableListOf<String>()
        db.query(
            TABLE_CACHE,
            arrayOf(COL_KEY),
            "$COL_TABLE_NAME = ?",
            arrayOf(tableName),
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                keys += cursor.getString(0)
            }
        }
        if (keys.isNotEmpty()) {
            db.delete(TABLE_CACHE, "$COL_TABLE_NAME = ?", arrayOf(tableName))
            keys.forEach { key -> changes.tryEmit(key) }
        }
    }

    suspend fun invalidateTables(vararg tableNames: String) {
        tableNames.distinct().forEach { tableName -> invalidateTable(tableName) }
    }

    fun observe(key: String): Flow<CachedSupabaseResponse?> =
        changes
            .filter { changedKey -> changedKey == key }
            .onStart { emit(key) }
            .map { read(key) }
            .distinctUntilChanged { old, new -> old?.responseJson == new?.responseJson }

    private fun android.database.Cursor.toCachedResponse(): CachedSupabaseResponse =
        CachedSupabaseResponse(
            key = getString(getColumnIndexOrThrow(COL_KEY)),
            method = getString(getColumnIndexOrThrow(COL_METHOD)),
            url = getString(getColumnIndexOrThrow(COL_URL)),
            tableName = getString(getColumnIndexOrThrow(COL_TABLE_NAME)),
            responseJson = getString(getColumnIndexOrThrow(COL_RESPONSE_JSON)),
            updatedAtMillis = getLong(getColumnIndexOrThrow(COL_UPDATED_AT))
        )

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
                    $COL_METHOD TEXT NOT NULL,
                    $COL_URL TEXT NOT NULL,
                    $COL_TABLE_NAME TEXT,
                    $COL_RESPONSE_JSON TEXT NOT NULL,
                    $COL_UPDATED_AT INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_${TABLE_CACHE}_table_name ON $TABLE_CACHE($COL_TABLE_NAME)")
            db.execSQL("CREATE INDEX idx_${TABLE_CACHE}_updated_at ON $TABLE_CACHE($COL_UPDATED_AT)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_CACHE")
            onCreate(db)
        }
    }

    private companion object {
        const val DATABASE_NAME = "quata_supabase_cache.db"
        const val DATABASE_VERSION = 1
        const val TABLE_CACHE = "supabase_response_cache"
        const val COL_KEY = "cache_key"
        const val COL_METHOD = "method"
        const val COL_URL = "url"
        const val COL_TABLE_NAME = "table_name"
        const val COL_RESPONSE_JSON = "response_json"
        const val COL_UPDATED_AT = "updated_at_millis"
        val COLUMNS = arrayOf(
            COL_KEY,
            COL_METHOD,
            COL_URL,
            COL_TABLE_NAME,
            COL_RESPONSE_JSON,
            COL_UPDATED_AT
        )
    }
}
