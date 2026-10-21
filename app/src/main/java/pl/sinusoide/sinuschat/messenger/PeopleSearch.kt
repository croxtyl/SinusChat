package pl.sinusoide.sinuschat.messenger

import android.content.Context
import org.json.JSONArray
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.crypto.E2EManager
import pl.sinusoide.sinuschat.network.ApiClient
import pl.sinusoide.sinuschat.setup.SetupActivity
import pl.sinusoide.sinuschat.util.ContactCache
import pl.sinusoide.sinuschat.util.optStringOrNull

object PeopleSearch {

    data class Person(val hash: String, val name: String, val avatarUrl: String?)

    private val EMAIL_RE = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

    fun isEmail(q: String) = EMAIL_RE.matches(q.trim())

    fun shouldQueryServer(q: String): Boolean {
        val t = q.trim()
        return when {
            isEmail(t) -> true
            t.contains('@') -> false
            else -> t.length >= 2
        }
    }

    fun loadKnownContacts(context: Context, exclude: Set<String> = emptySet()): List<Person> {
        val myHash = SetupActivity.getUserPhoneHash(context)
        val out = LinkedHashMap<String, Person>()

        fun addFrom(arr: JSONArray?) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                if (j.optBoolean("is_group", false) || GroupRepository.isGroup(j.optString("id"))) continue
                val hash = j.optString("other_hash")
                if (hash.isEmpty() || hash == myHash || hash in exclude || out.containsKey(hash)) continue
                val name = ContactCache.bestName(context, hash, j.optStringOrNull("other_name") ?: "")
                    .ifEmpty { context.getString(R.string.contact) }
                val avatar = ContactCache.getAvatarUrl(context, hash)
                    ?: j.optStringOrNull("avatar_url")?.takeIf { !pl.sinusoide.sinuschat.crypto.ProfileCrypto.isEncryptedAvatar(it) }
                out[hash] = Person(hash, name, avatar)
            }
        }

        addFrom(ApiClient.getArray("/api/conversations", context))
        addFrom(try { pl.sinusoide.sinuschat.util.ConversationCache.load(context) } catch (_: Exception) { null })

        try {
            val senders = pl.sinusoide.sinuschat.data.SinusChatDatabase.getInstance(context)
                .messageDao().getPrivateSenderHashes()
            for (h in senders) {
                if (h == myHash || h in exclude || out.containsKey(h)) continue
                val name = ContactCache.bestName(context, h, "")
                if (name.isEmpty()) continue
                out[h] = Person(h, name, ContactCache.getAvatarUrl(context, h))
            }
        } catch (_: Exception) { }

        return out.values.sortedBy { it.name.lowercase() }
    }

    fun search(context: Context, query: String, exclude: Set<String> = emptySet()): List<Person> {
        val q = query.trim()
        if (!shouldQueryServer(q)) return emptyList()
        val myHash = SetupActivity.getUserPhoneHash(context)
        val param = if (isEmail(q)) {
            val hash = E2EManager.sha256(q.lowercase())
            if (hash == myHash) return emptyList()
            hash
        } else {
            java.net.URLEncoder.encode(q, "UTF-8")
        }
        val arr = ApiClient.getArray("/api/users/search?q=$param", context) ?: return emptyList()
        val out = ArrayList<Person>()
        for (i in 0 until arr.length()) {
            val u = arr.optJSONObject(i) ?: continue
            val hash = u.optString("email_hash")
            if (hash.isEmpty() || hash == myHash || hash in exclude) continue
            val name = pl.sinusoide.sinuschat.crypto.ProfileCrypto.openName(context, hash, u.optStringOrNull("display_name"))
                ?: ContactCache.realName(context, hash) ?: context.getString(R.string.contact)
            val rawAvatar = u.optStringOrNull("avatar_url")
            if (rawAvatar != null) ContactCache.saveAvatarUrl(context, hash, rawAvatar)
            val avatar = pl.sinusoide.sinuschat.crypto.ProfileCrypto.displayAvatar(context, hash, rawAvatar)
            ContactCache.saveName(context, hash, name)
            out += Person(hash, ContactCache.display(context, hash, name) ?: name, avatar)
        }
        return out
    }
}
