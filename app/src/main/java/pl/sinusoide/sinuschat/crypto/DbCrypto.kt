package pl.sinusoide.sinuschat.crypto

import android.content.Context
import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import pl.sinusoide.sinuschat.data.SinusChatDatabase
import java.io.File

object DbCrypto {

    private const val TAG = "DbCrypto"
    private const val DB_NAME = "sinuschat_messages.db"

    private fun dbFile(ctx: Context) = ctx.getDatabasePath(DB_NAME)

    fun dbExists(context: Context): Boolean = dbFile(context).exists()

    fun isDbEncrypted(context: Context): Boolean {
        val f = dbFile(context)
        if (!f.exists() || f.length() < 16L) return false
        return try {
            val head = ByteArray(16)
            val n = f.inputStream().use { it.read(head) }
            if (n < 15) return false
            String(head, 0, 15, Charsets.US_ASCII) != "SQLite format 3"
        } catch (e: Throwable) { false }
    }
    private fun sidecars(f: File) = listOf(File(f.path + "-wal"), File(f.path + "-shm"))

    fun encryptExisting(context: Context, dbKey: ByteArray): Boolean {
        val db = context.getDatabasePath(DB_NAME)
        val pass = DbKeyManager.passphrase(dbKey)
        Log.i(TAG, "Starting final migration attempt for ${db.path}")
        
        SinusChatDatabase.reset()

        if (!db.exists()) {
            Log.i(TAG, "Database file does not exist, nothing to encrypt.")
            return true
        }

        val backup = File(db.path + ".plainbak")
        val tmp = File(db.parentFile, "migration_tmp.db")
        
        return try {
            System.loadLibrary("sqlcipher")

            Log.d(TAG, "Creating backup and FORCE deleting WAL/SHM...")
            db.copyTo(backup, overwrite = true)
            
            val wal = File(db.path + "-wal")
            val shm = File(db.path + "-shm")
            if (wal.exists()) wal.delete()
            if (shm.exists()) shm.delete()
            
            if (tmp.exists()) tmp.delete()
            tmp.createNewFile()

            Log.d(TAG, "Opening source database (plaintext)...")
            val src = SQLiteDatabase.openDatabase(db.path, null as ByteArray?, null, SQLiteDatabase.OPEN_READWRITE, null, null)
            
            Log.d(TAG, "Executing ATTACH...")
            src.execSQL("ATTACH DATABASE '${tmp.absolutePath}' AS encrypted KEY '$pass'")
            
            Log.d(TAG, "Executing sqlcipher_export...")
            src.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { it.moveToFirst() }
            
            Log.d(TAG, "Detaching and closing...")
            src.execSQL("DETACH DATABASE encrypted")
            src.close()

            Log.d(TAG, "Verifying encrypted file...")
            if (!verifyEncrypted(tmp, pass)) {
                throw IllegalStateException("Baza tymczasowa nie jest poprawnie zaszyfrowana.")
            }

            Log.d(TAG, "Replacing original...")
            if (db.exists() && !db.delete()) Log.w(TAG, "Could not delete original, will overwrite.")
            tmp.copyTo(db, overwrite = true)
            tmp.delete()

            backup.delete()
            DbKeyManager.setMigrationError(context, null)
            Log.i(TAG, "encryptExisting SUCCESS.")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "encryptExisting FAILED", e)
            DbKeyManager.setMigrationError(context, e.message ?: e.toString())
            restoreFrom(backup, db)
            if (tmp.exists()) tmp.delete()
            false
        }
    }

    fun decryptExisting(context: Context, dbKey: ByteArray): Boolean {
        val db = dbFile(context)
        val pass = DbKeyManager.passphrase(dbKey)
        Log.i(TAG, "Starting decryption migration...")
        SinusChatDatabase.reset()

        if (!db.exists()) return true

        val backup = File(db.path + ".encbak")
        val tmp = File(context.cacheDir, "migration_dec.tmp")
        return try {
            System.loadLibrary("sqlcipher")
            db.copyTo(backup, overwrite = true)
            if (tmp.exists()) tmp.delete()

            Log.d(TAG, "Opening encrypted database...")
            val src = SQLiteDatabase.openDatabase(db.absolutePath, pass, null, SQLiteDatabase.OPEN_READWRITE, null, null)
            
            Log.d(TAG, "Attaching plain temporary database...")
            src.execSQL("ATTACH DATABASE '${tmp.absolutePath}' AS plain KEY ''")
            Log.d(TAG, "Running sqlcipher_export...")
            src.rawQuery("SELECT sqlcipher_export('plain')", null).use { it.moveToFirst() }
            src.execSQL("DETACH DATABASE plain")
            src.close()

            Log.d(TAG, "Verifying plain database...")
            val check = SQLiteDatabase.openDatabase(tmp.absolutePath, "", null, SQLiteDatabase.OPEN_READONLY, null, null)
            val ok = check.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst(); it.getInt(0) >= 0 }
            check.close()
            if (!ok) throw IllegalStateException("Baza tymczasowa nie przeszła weryfikacji.")

            if (!db.delete()) throw IllegalStateException("Nie udało się usunąć oryginału.")
            sidecars(db).forEach { if (it.exists()) it.delete() }
            
            tmp.copyTo(db, overwrite = true)
            tmp.delete()

            Log.i(TAG, "decryptExisting SUCCESS.")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "decryptExisting FAILED", e)
            restoreFrom(backup, db)
            if (tmp.exists()) tmp.delete()
            false
        }
    }

    private fun verifyEncrypted(file: File, pass: String): Boolean = try {
        val d = SQLiteDatabase.openDatabase(file.absolutePath, pass, null, SQLiteDatabase.OPEN_READONLY, null, null)
        val ok = d.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst(); it.getInt(0) >= 0 }
        d.close(); ok
    } catch (e: Throwable) { Log.e(TAG, "verifyEncrypted failed", e); false }

    private fun restoreFrom(backup: File, db: File) {
        try {
            if (backup.exists()) {
                if (db.exists()) db.delete()
                backup.copyTo(db, overwrite = true)
            }
        } catch (e: Throwable) { Log.e(TAG, "restore failed", e) }
    }
}
