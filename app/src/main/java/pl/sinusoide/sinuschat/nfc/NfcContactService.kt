package pl.sinusoide.sinuschat.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import pl.sinusoide.sinuschat.AppConfig
import pl.sinusoide.sinuschat.messenger.ContactQr
import pl.sinusoide.sinuschat.setup.SetupActivity

class NfcContactService : HostApduService() {

    companion object {
        private const val TAG = "NfcContactService"

        private val MY_AID = byteArrayOf(0xF0.toByte(), 0x53, 0x49, 0x4E, 0x55, 0x53, 0x43)
        private val SELECT_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val UNKNOWN_CMD = byteArrayOf(0x6A.toByte(), 0x82.toByte())

        private val SELECT_HEADER = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00)

        @Volatile var active: Boolean = false
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return UNKNOWN_CMD
        val hex = commandApdu.joinToString(" ") { "%02X".format(it) }
        Log.d(TAG, "APDU received: $hex, active=$active")

        if (!isSelectAid(commandApdu)) {
            Log.d(TAG, "not a SELECT AID command")
            return UNKNOWN_CMD
        }
        
        if (!active) {
            Log.d(TAG, "NFC service not active (user not in ADD screen) -> 6A82")
            return UNKNOWN_CMD
        }

        return try {
            val myHash = SetupActivity.getUserPhoneHash(applicationContext)
            val myName = SetupActivity.getUserDisplayName(applicationContext)
            if (myHash.isEmpty()) {
                Log.e(TAG, "myHash is empty, cannot share contact")
                return UNKNOWN_CMD
            }

            val token = ContactQr.encode(myHash, myName, pl.sinusoide.sinuschat.crypto.ProfileCrypto.myKeyB64(applicationContext))
            val payload = "${AppConfig.ADD_LINK_BASE}$token"
            val data = payload.toByteArray(Charsets.UTF_8)
            Log.d(TAG, "Wysyłam kontakt przez NFC (${data.size} B)")

            data + SELECT_OK
        } catch (e: Exception) {
            Log.e(TAG, "Error processing NFC command", e)
            UNKNOWN_CMD
        }
    }

    private fun isSelectAid(apdu: ByteArray): Boolean {
        if (apdu.size < SELECT_HEADER.size + 1) return false
        
        for (i in SELECT_HEADER.indices) {
            if (apdu[i] != SELECT_HEADER[i]) return false
        }
        
        val lc = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + lc) return false
        
        val aid = apdu.copyOfRange(5, 5 + lc)
        if (!aid.contentEquals(MY_AID)) {
            Log.d(TAG, "AID mismatch: ${aid.joinToString(" ") { "%02X".format(it) }} != ${MY_AID.joinToString(" ") { "%02X".format(it) }}")
            return false
        }
        
        return true
    }

    override fun onDeactivated(reason: Int) {}
}
