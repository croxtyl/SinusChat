package pl.sinusoide.sinuschat.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import pl.sinusoide.sinuschat.crypto.DbKeyManager

@Database(entities = [MessageEntity::class, CallEntity::class, LinkPreviewEntity::class], version = 9, exportSchema = false)
@TypeConverters(Converters::class)
abstract class SinusChatDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun callDao(): CallDao
    abstract fun linkPreviewDao(): LinkPreviewDao

    companion object {
        @Volatile private var INSTANCE: SinusChatDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN expiresAtMs INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_expiresAtMs ON messages(expiresAtMs)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN isSystemMessage INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS calls (
                        id TEXT NOT NULL PRIMARY KEY,
                        otherUserHash TEXT NOT NULL,
                        otherUserName TEXT NOT NULL,
                        conversationId TEXT NOT NULL,
                        isVideo INTEGER NOT NULL,
                        direction TEXT NOT NULL,
                        durationSec INTEGER NOT NULL,
                        startedAtMs INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_calls_startedAtMs ON calls(startedAtMs)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN edited INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN viewOnce INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN viewed INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN albumId TEXT")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN trOrig TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN trLocal TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN trFrom TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN trTo TEXT")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `link_previews` (`id` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                    "`title` TEXT, `description` TEXT, `image` BLOB, `icon` BLOB, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        fun getInstance(context: Context): SinusChatDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }

        private fun build(context: Context): SinusChatDatabase {
            val dbName = "sinuschat_messages.db"
            val builder = Room.databaseBuilder(context.applicationContext, SinusChatDatabase::class.java, dbName)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)

            val pass = DbKeyManager.sessionPassphraseBytes()

            if (DbKeyManager.isEnabled(context)) {
                if (pass != null) {
                    System.loadLibrary("sqlcipher")
                    builder.openHelperFactory(SupportOpenHelperFactory(pass))
                } else {
                    Log.w("SinusChatDatabase", "Baza zablokowana. Tworzę tymczasowy kontener.")
                    return Room.inMemoryDatabaseBuilder(context.applicationContext, SinusChatDatabase::class.java).build()
                }
            }
            return builder.build()
        }

        fun warmUp(context: Context) {
            val app = context.applicationContext
            if (DbKeyManager.isEnabled(app) && DbKeyManager.cachedKey() == null) return
            pl.sinusoide.sinuschat.util.AppExecutor.io {
                try { getInstance(app).openHelper.writableDatabase }
                catch (e: Throwable) { Log.w("SinusChatDatabase", "Rozgrzewka bazy nieudana: ${e.message}") }
            }
            if (pl.sinusoide.sinuschat.setup.SetupActivity.isSetupDone(app)) pl.sinusoide.sinuschat.util.AppExecutor.io {
                try { pl.sinusoide.sinuschat.crypto.SignalStore.get(app) }
                catch (e: Throwable) { Log.w("SinusChatDatabase", "Rozgrzewka magazynu kluczy nieudana: ${e.message}") }
            }
        }

        fun reset() {
            synchronized(this) {
                try { INSTANCE?.close() } catch (_: Throwable) {}
                INSTANCE = null
            }
        }
    }
}
