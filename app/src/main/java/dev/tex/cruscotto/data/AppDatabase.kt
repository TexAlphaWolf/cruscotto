package dev.tex.cruscotto.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [Category::class, Txn::class, Car::class, Deadline::class, ServiceLog::class, AppSettings::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        const val FILE = "cruscotto.db"

        /**
         * Opens the SQLCipher database. The key is passed as a raw key (x'…'), so SQLCipher skips its own
         * key derivation: the key is already 256 random bits.
         */
        /** The native library is loaded on first use, not at app start: a faster cold start. */
        private val nativeLoaded by lazy { System.loadLibrary("sqlcipher") }

        fun open(context: Context, dbKey: ByteArray): AppDatabase {
            nativeLoaded
            val hex = dbKey.joinToString("") { "%02x".format(it) }
            val passphrase = "x'$hex'".toByteArray(Charsets.US_ASCII)
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE)
                .openHelperFactory(factory)
                .build()
        }
    }
}
