package pl.sinusoide.sinuschat.pclink

import android.content.Context
import android.util.Log
import androidx.room.InvalidationTracker
import org.json.JSONArray
import pl.sinusoide.sinuschat.crypto.DbKeyManager
import pl.sinusoide.sinuschat.crypto.E2EManager
import pl.sinusoide.sinuschat.data.MessageEntity
import pl.sinusoide.sinuschat.data.SinusChatDatabase
import pl.sinusoide.sinuschat.link.ChatMessage
import pl.sinusoide.sinuschat.link.ChatSummary
import pl.sinusoide.sinuschat.link.LinkConnection
import pl.sinusoide.sinuschat.link.LinkFrame
import pl.sinusoide.sinuschat.messenger.GroupRepository
import pl.sinusoide.sinuschat.messenger.MessageListenerService
import pl.sinusoide.sinuschat.messenger.MessageRepository
import pl.sinusoide.sinuschat.messenger.ReplyLabel
import pl.sinusoide.sinuschat.network.ApiClient
import pl.sinusoide.sinuschat.network.WebSocketManager
import pl.sinusoide.sinuschat.setup.SetupActivity
import pl.sinusoide.sinuschat.util.AvatarHelper
import pl.sinusoide.sinuschat.util.BlockList
import pl.sinusoide.sinuschat.util.ContactCache
import pl.sinusoide.sinuschat.util.ConversationCache
import pl.sinusoide.sinuschat.util.DisappearingMessages
import pl.sinusoide.sinuschat.util.LocalChats
import pl.sinusoide.sinuschat.util.MediaManager
import pl.sinusoide.sinuschat.util.MessageNotifier
import pl.sinusoide.sinuschat.util.MuteSettings
import pl.sinusoide.sinuschat.util.NotificationPrefs
import pl.sinusoide.sinuschat.util.PrivacySettings
import pl.sinusoide.sinuschat.util.optStringOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object PcLinkHub {

    private const val TAG = "PcLinkHub"
    private const val LIST_DEBOUNCE_MS = 400L
    private const val CHAT_DEBOUNCE_MS = 150L
    private const val SERVER_REFRESH_MS = 60_000L
    private const val LOCK_POLL_MS = 3_000L
    private const val NOTIFY_LOOKBACK_MS = 5 * 60_000L
    private const val GROUP_INFO_MS = 5 * 60_000L
    private const val MAX_TEXT = 8_000
    private const val MAX_LIMIT = 200

    private class Session(val connection: LinkConnection) {
        @Volatile var openConv: String? = null
        @Volatile var openLimit: Int = 80
    }

    private val sessions = ConcurrentHashMap<LinkConnection, Session>()
    private val worker = Executors.newSingleThreadExecutor()
    private val net = Executors.newSingleThreadExecutor()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    @Volatile private var app: Context? = null
    @Volatile private var observedDb: SinusChatDatabase? = null
    @Volatile private var lastServerRefresh = 0L
    @Volatile private var lastLocked: Boolean? = null
    @Volatile private var notifyFrom = 0L
    private val listScheduled = AtomicBoolean(false)
    private val notifyScheduled = AtomicBoolean(false)
    private val refreshing = AtomicBoolean(false)
    private val chatScheduled = ConcurrentHashMap.newKeySet<String>()
    private val notified = object : LinkedHashMap<String, Boolean>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 500
    }
    private val groupNames = ConcurrentHashMap<String, Map<String, String>>()
    private val groupInfoAt = ConcurrentHashMap<String, Long>()
    private val groupReadOnly = ConcurrentHashMap<String, Boolean>()
    private val lockPoll = AtomicReference<ScheduledFuture<*>?>(null)

    private val observer = object : InvalidationTracker.Observer("messages") {
        override fun onInvalidated(tables: Set<String>) = onDbChanged()
    }

    fun onConnected(ctx: Context, connection: LinkConnection) {
        app = ctx.applicationContext
        sessions[connection] = Session(connection)
        if (notifyFrom == 0L) notifyFrom = System.currentTimeMillis()
        startLockPoll()
        worker.execute {
            val locked = isLocked()
            connection.send(LinkFrame.PhoneState(locked))
            if (!locked) ensureObserver()
        }
    }

    fun onDisconnected(connection: LinkConnection) {
        sessions.remove(connection)
        if (sessions.isEmpty()) {
            notifyFrom = 0L
            lockPoll.getAndSet(null)?.cancel(false)
        }
    }

    fun onFrame(connection: LinkConnection, frame: LinkFrame) {
        val session = sessions[connection] ?: return
        when (frame) {
            is LinkFrame.TypingOut -> worker.execute { sendTyping(frame.conv, frame.typing) }
            else -> worker.execute {
                try {
                    handle(session, frame)
                } catch (e: Exception) {
                    Log.w(TAG, "obsługa ${frame::class.simpleName}: ${e.message}")
                }
            }
        }
    }

    fun onTyping(conversationId: String, senderHash: String, typing: Boolean) {
        val ctx = app ?: return
        if (sessions.isEmpty() || conversationId.isEmpty()) return
        val name = ContactCache.getName(ctx, senderHash) ?: ""
        broadcast(LinkFrame.TypingIn(conversationId, name, typing))
    }

    private fun ctx(): Context? = app

    private fun isLocked(): Boolean {
        val ctx = ctx() ?: return true
        return DbKeyManager.isEnabled(ctx) && DbKeyManager.cachedKey() == null
    }

    private fun me(ctx: Context) = SetupActivity.getUserPhoneHash(ctx)

    private fun broadcast(frame: LinkFrame) {
        for (s in sessions.values) s.connection.send(frame)
    }

    private fun startLockPoll() {
        if (lockPoll.get() != null) return
        val task = scheduler.scheduleWithFixedDelay({
            worker.execute { checkLock() }
        }, LOCK_POLL_MS, LOCK_POLL_MS, TimeUnit.MILLISECONDS)
        if (!lockPoll.compareAndSet(null, task)) task.cancel(false)
    }

    private fun checkLock() {
        if (sessions.isEmpty()) return
        val locked = isLocked()
        if (lastLocked == locked) {
            if (!locked) ensureObserver()
            return
        }
        lastLocked = locked
        broadcast(LinkFrame.PhoneState(locked))
        if (!locked) {
            ensureObserver()
            scheduleList(refresh = true)
            sessions.values.mapNotNull { it.openConv }.toSet().forEach { scheduleChat(it) }
        }
    }

    private fun ensureObserver() {
        val ctx = ctx() ?: return
        if (isLocked()) return
        val db = try { SinusChatDatabase.getInstance(ctx) } catch (_: Exception) { return }
        if (db === observedDb) return
        try {
            db.invalidationTracker.addObserver(observer)
            observedDb = db
        } catch (e: Exception) {
            Log.w(TAG, "observer: ${e.message}")
        }
    }

    private fun onDbChanged() {
        if (sessions.isEmpty()) return
        scheduleList(refresh = false)
        sessions.values.mapNotNull { it.openConv }.toSet().forEach { scheduleChat(it) }
        scheduleNotify()
    }

    private fun scheduleList(refresh: Boolean) {
        if (refresh) {
            refreshServer()
            return
        }
        if (!listScheduled.compareAndSet(false, true)) return
        scheduler.schedule({
            listScheduled.set(false)
            worker.execute { pushChats() }
        }, LIST_DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    private fun scheduleChat(conv: String) {
        if (!chatScheduled.add(conv)) return
        scheduler.schedule({
            chatScheduled.remove(conv)
            worker.execute { pushLatest(conv, null) }
        }, CHAT_DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    private fun scheduleNotify() {
        if (!notifyScheduled.compareAndSet(false, true)) return
        scheduler.schedule({
            notifyScheduled.set(false)
            worker.execute { pushIncoming() }
        }, CHAT_DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    private fun handle(session: Session, frame: LinkFrame) {
        val conn = session.connection
        if (isLocked()) {
            conn.send(LinkFrame.PhoneState(true))
            if (frame is LinkFrame.SendText) conn.send(LinkFrame.SendResult(frame.id, false, LinkFrame.ERROR_LOCKED))
            return
        }
        ensureObserver()
        when (frame) {
            is LinkFrame.ChatsRequest -> {
                pushChats(conn)
                if (frame.refresh || System.currentTimeMillis() - lastServerRefresh > SERVER_REFRESH_MS) refreshServer()
            }
            is LinkFrame.OpenChat -> {
                session.openConv = frame.conv
                session.openLimit = frame.limit.coerceIn(1, MAX_LIMIT)
                pushLatest(frame.conv, conn)
                if (GroupRepository.isGroup(frame.conv)) loadGroupInfo(frame.conv)
            }
            is LinkFrame.CloseChat -> if (session.openConv == frame.conv) session.openConv = null
            is LinkFrame.OlderMessages -> pushOlder(frame, conn)
            is LinkFrame.SendText -> conn.send(sendText(frame))
            is LinkFrame.MarkRead -> markRead(frame.conv)
            else -> Unit
        }
    }

    private fun refreshServer() {
        if (!refreshing.compareAndSet(false, true)) return
        net.execute {
            try {
                val ctx = ctx() ?: return@execute
                if (isLocked()) return@execute
                val me = me(ctx)
                val server: JSONArray = ApiClient.getArray("/api/conversations", ctx) ?: return@execute
                val arr = LocalChats.merge(ctx, me, server)
                pl.sinusoide.sinuschat.crypto.GroupMeta.openConversations(ctx, arr)
                ConversationCache.save(ctx, arr)
                MessageListenerService.syncPeerKeys(ctx, arr)
                val repair = MessageRepository.needsAckRepair(ctx)
                for (i in 0 until arr.length()) {
                    val j = arr.optJSONObject(i) ?: continue
                    val id = j.optString("id")
                    if (id.isEmpty() || GroupRepository.isGroup(id) || j.optBoolean("is_group", false)) continue
                    if (BlockList.isBlockedByThem(ctx, j.optString("other_hash"))) continue
                    MessageRepository.applyServerAcks(ctx, id, me,
                        j.optLong("other_delivered_up_to", 0), j.optLong("other_read_up_to", 0), repair)
                }
                MessageRepository.markAckRepairDone(ctx)
                lastServerRefresh = System.currentTimeMillis()
            } catch (e: Exception) {
                Log.w(TAG, "odświeżanie listy: ${e.message}")
            } finally {
                refreshing.set(false)
                worker.execute { pushChats() }
            }
        }
    }

    private fun kindOf(e: MessageEntity): String {
        val name = e.attachmentName.orEmpty()
        return when {
            e.isSystemMessage || e.type == MessageEntity.TYPE_EXPIRED_NOTICE -> ChatMessage.KIND_SYSTEM
            e.type == MessageEntity.TYPE_VOICE -> ChatMessage.KIND_VOICE
            e.text.startsWith("geo:") || name.startsWith("location_") -> ChatMessage.KIND_LOCATION
            e.type == MessageEntity.TYPE_ATTACHMENT -> when {
                name.isNotEmpty() && MediaManager.isImage(name) -> ChatMessage.KIND_PHOTO
                name.isNotEmpty() && MediaManager.isVideo(name) -> ChatMessage.KIND_VIDEO
                else -> ChatMessage.KIND_FILE
            }
            else -> ChatMessage.KIND_TEXT
        }
    }

    private fun visibleText(e: MessageEntity, kind: String): String = when (kind) {
        ChatMessage.KIND_LOCATION, ChatMessage.KIND_VOICE -> ""
        else -> e.text
    }

    private fun buildChats(ctx: Context): List<ChatSummary> {
        val arr = ConversationCache.load(ctx) ?: JSONArray()
        val dao = SinusChatDatabase.getInstance(ctx).messageDao()
        val me = me(ctx)
        val out = ArrayList<ChatSummary>(arr.length())
        for (i in 0 until arr.length()) {
            val j = arr.optJSONObject(i) ?: continue
            val id = j.optString("id")
            if (id.isEmpty()) continue
            val isGroup = j.optBoolean("is_group", false) || GroupRepository.isGroup(id)
            val peer = if (isGroup) "" else j.optString("other_hash")
            val title = if (isGroup) j.optStringOrNull("group_name") ?: ContactCache.getName(ctx, id) ?: ""
            else ContactCache.bestName(ctx, peer, j.optStringOrNull("other_name"))
            val color = if (isGroup) j.optStringOrNull("group_avatar_color") ?: "#5288C1"
            else AvatarHelper.getContactColor(ctx, peer)
            val last = try { dao.getLastMessage(id) } catch (_: Exception) { null }
            val kind = last?.let { kindOf(it) } ?: ChatMessage.KIND_TEXT
            val unread = try { dao.countIncomingSince(id, me, LocalChats.readMark(ctx, id)) } catch (_: Exception) { 0 }
            out += ChatSummary(
                id = id,
                title = title,
                isGroup = isGroup,
                peer = peer,
                color = color,
                lastText = last?.let { visibleText(it, kind) }.orEmpty().take(200),
                lastKind = kind,
                lastAt = maxOf(j.optLong("last_message_at", 0L), last?.timestampMs ?: 0L),
                lastFromMe = last?.senderHash == me,
                lastStatus = if (last?.senderHash == me) last.status else "",
                unread = unread,
                muted = MuteSettings.isMuted(ctx, id),
                online = j.optBoolean("is_online", false)
            )
        }
        return out.sortedByDescending { it.lastAt }
    }

    private fun pushChats(target: LinkConnection? = null) {
        val ctx = ctx() ?: return
        if (sessions.isEmpty() || isLocked()) return
        val frame = LinkFrame.Chats(buildChats(ctx), PrivacySettings.isHideLastMessageEnabled(ctx))
        if (target != null) target.send(frame) else broadcast(frame)
    }

    private fun toMessage(ctx: Context, e: MessageEntity, me: String, names: Map<String, String>?): ChatMessage {
        val kind = kindOf(e)
        val mine = e.senderHash == me
        val senderName = if (mine || kind == ChatMessage.KIND_SYSTEM) ""
        else names?.get(e.senderHash) ?: ContactCache.getName(ctx, e.senderHash) ?: ""
        return ChatMessage(
            id = e.id,
            conv = e.conversationId,
            sender = e.senderHash,
            senderName = senderName,
            mine = mine,
            text = visibleText(e, kind),
            kind = kind,
            fileName = e.attachmentName,
            at = e.timestampMs,
            status = if (mine) e.status else "",
            replyTo = e.replyToId,
            replyText = e.replyToText,
            edited = e.edited,
            reactions = e.reactions,
            viewOnce = e.viewOnce,
            expiresAt = e.expiresAtMs,
            pinned = e.pinned
        )
    }

    private fun alive(list: List<MessageEntity>): List<MessageEntity> {
        val now = System.currentTimeMillis()
        return list.filter { it.expiresAtMs == null || it.expiresAtMs > now }
    }

    private fun pushLatest(conv: String, target: LinkConnection?) {
        val ctx = ctx() ?: return
        if (isLocked()) return
        val receivers = if (target != null) listOfNotNull(sessions[target])
        else sessions.values.filter { it.openConv == conv }
        if (receivers.isEmpty()) return
        val dao = SinusChatDatabase.getInstance(ctx).messageDao()
        val me = me(ctx)
        val limit = receivers.maxOf { it.openLimit }
        val rows = dao.getLatest(conv, limit + 1)
        val more = rows.size > limit
        val names = groupNames[conv]
        val items = alive(rows.take(limit)).asReversed().map { toMessage(ctx, it, me, names) }
        val frame = LinkFrame.Messages(conv, items, LinkFrame.MODE_LATEST, more)
        for (s in receivers) s.connection.send(frame)
    }

    private fun pushOlder(f: LinkFrame.OlderMessages, conn: LinkConnection) {
        val ctx = ctx() ?: return
        val dao = SinusChatDatabase.getInstance(ctx).messageDao()
        val me = me(ctx)
        val limit = f.limit.coerceIn(1, MAX_LIMIT)
        val rows = dao.getBefore(f.conv, f.before, limit + 1)
        val items = alive(rows.take(limit)).asReversed().map { toMessage(ctx, it, me, groupNames[f.conv]) }
        conn.send(LinkFrame.Messages(f.conv, items, LinkFrame.MODE_OLDER, rows.size > limit))
    }

    private fun pushIncoming() {
        val ctx = ctx() ?: return
        if (sessions.isEmpty() || isLocked() || notifyFrom == 0L) return
        val me = me(ctx)
        val since = maxOf(notifyFrom, System.currentTimeMillis() - NOTIFY_LOOKBACK_MS)
        val rows = try { SinusChatDatabase.getInstance(ctx).messageDao().getIncomingSince(me, since) } catch (_: Exception) { return }
        val preview = when (NotificationPrefs.preview(ctx)) {
            NotificationPrefs.Preview.FULL -> LinkFrame.PREVIEW_FULL
            NotificationPrefs.Preview.NAME -> LinkFrame.PREVIEW_NAME
            NotificationPrefs.Preview.HIDDEN -> LinkFrame.PREVIEW_HIDDEN
        }
        for (e in rows) {
            synchronized(notified) {
                if (notified.containsKey(e.id)) return@synchronized null
                notified[e.id] = true
                e
            } ?: continue
            val conv = e.conversationId
            val isGroup = GroupRepository.isGroup(conv)
            if (MuteSettings.isMuted(ctx, conv)) continue
            if (isGroup && !NotificationPrefs.groupsEnabled(ctx)) continue
            if (!isGroup && !NotificationPrefs.messagesEnabled(ctx)) continue
            if (BlockList.isBlocked(ctx, e.senderHash)) continue
            val kind = kindOf(e)
            val sender = groupNames[conv]?.get(e.senderHash) ?: ContactCache.getName(ctx, e.senderHash) ?: ""
            val title = if (isGroup) ContactCache.getName(ctx, conv) ?: groupTitle(ctx, conv) else sender
            broadcast(LinkFrame.Incoming(
                conv = conv,
                id = e.id,
                title = title,
                sender = sender,
                text = if (preview == LinkFrame.PREVIEW_FULL) visibleText(e, kind).take(300) else "",
                kind = kind,
                at = e.timestampMs,
                preview = preview
            ))
        }
    }

    private fun groupTitle(ctx: Context, conv: String): String {
        val arr = ConversationCache.load(ctx) ?: return ""
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id") == conv) return o.optStringOrNull("group_name").orEmpty()
        }
        return ""
    }

    private fun loadGroupInfo(conv: String) {
        val ctx = ctx() ?: return
        val now = System.currentTimeMillis()
        if (now - (groupInfoAt[conv] ?: 0L) < GROUP_INFO_MS) return
        groupInfoAt[conv] = now
        GroupRepository.getGroupInfo(ctx, conv) { info ->
            if (info == null) {
                groupInfoAt.remove(conv)
                return@getGroupInfo
            }
            val me = me(ctx)
            groupNames[conv] = info.members.associate { it.userHash to it.displayName }
            groupReadOnly[conv] = !(info.amIAdmin || info.perms.sendMessages)
            ContactCache.saveName(ctx, conv, info.name)
            net.execute {
                try {
                    pl.sinusoide.sinuschat.crypto.GroupKeyManager.distributeMyKey(
                        ctx, conv, me, info.members.map { it.userHash })
                } catch (e: Exception) {
                    Log.w(TAG, "klucz grupy: ${e.message}")
                }
            }
            scheduleChat(conv)
        }
    }

    private fun otherOf(conv: String, me: String): String? =
        conv.split('_').takeIf { it.size == 2 && me in it }?.firstOrNull { it != me }?.takeIf { it.isNotEmpty() }

    private fun secretFor(ctx: Context, other: String): ByteArray? {
        E2EManager.getSharedSecretCached(ctx, other)?.let { return it }
        val result = AtomicReference<ByteArray?>(null)
        val latch = CountDownLatch(1)
        E2EManager.getSharedSecretForUser(ctx, other) { secret ->
            result.set(secret)
            latch.countDown()
        }
        latch.await(20, TimeUnit.SECONDS)
        return result.get()
    }

    private fun sendText(f: LinkFrame.SendText): LinkFrame.SendResult {
        val ctx = ctx() ?: return LinkFrame.SendResult(f.id, false, LinkFrame.ERROR_FAILED)
        val text = f.text.trim().take(MAX_TEXT)
        if (text.isEmpty() || f.conv.isEmpty()) return LinkFrame.SendResult(f.id, false, LinkFrame.ERROR_FAILED)
        val me = me(ctx)
        val dao = SinusChatDatabase.getInstance(ctx).messageDao()
        val reply = f.replyTo?.let { dao.getById(it) }
        val replyText = reply?.let { ReplyLabel.of(ctx, it) }

        if (GroupRepository.isGroup(f.conv)) {
            if (groupReadOnly[f.conv] == true) return LinkFrame.SendResult(f.id, false, LinkFrame.ERROR_READ_ONLY)
            val replyId = reply?.id?.removeSuffix("_" + me.take(16))
            if (!GroupRepository.sendTextBlocking(ctx, f.conv, me, text, replyId, replyText))
                return LinkFrame.SendResult(f.id, false, LinkFrame.ERROR_FAILED)
            MessageRepository.publishRead(ctx, f.conv, me, System.currentTimeMillis())
            return LinkFrame.SendResult(f.id, true)
        }

        val other = otherOf(f.conv, me) ?: return LinkFrame.SendResult(f.id, false, LinkFrame.ERROR_FAILED)
        if (BlockList.isBlocked(ctx, other)) return LinkFrame.SendResult(f.id, false, LinkFrame.ERROR_BLOCKED)
        if (BlockList.isDeleted(ctx, other)) return LinkFrame.SendResult(f.id, false, LinkFrame.ERROR_DELETED)
        val secret = secretFor(ctx, other) ?: return LinkFrame.SendResult(f.id, false, LinkFrame.ERROR_NO_KEY)
        MessageRepository.sendText(ctx, f.conv, me, other, secret, text, reply?.id, replyText)
        return LinkFrame.SendResult(f.id, true)
    }

    private fun markRead(conv: String) {
        val ctx = ctx() ?: return
        val me = me(ctx)
        val dao = SinusChatDatabase.getInstance(ctx).messageDao()
        MessageNotifier.dismiss(ctx, conv)
        val last = dao.getLastIncoming(conv, me, 1).firstOrNull()?.timestampMs ?: return
        if (last > LocalChats.readMark(ctx, conv)) {
            MessageRepository.publishRead(ctx, conv, me, last)
            MessageRepository.publishDelivered(ctx, conv, me, last)
        }
        DisappearingMessages.applyOnRead(ctx, conv, me)
        scheduleList(refresh = false)
    }

    private fun sendTyping(conv: String, typing: Boolean) {
        val ctx = ctx() ?: return
        if (PrivacySettings.isTypingStatusDisabled(ctx) || conv.isEmpty()) return
        val me = me(ctx)
        val isGroup = GroupRepository.isGroup(conv)
        val other = if (isGroup) null else otherOf(conv, me) ?: return
        try {
            if (other != null && pl.sinusoide.sinuschat.crypto.SealedSender.sendTyping(ctx, conv, other, typing)) return
            val msg = org.json.JSONObject()
                .put("type", if (typing) "typing" else "typing_stop")
                .put("conversationId", conv)
            if (other != null) msg.put("recipientHash", other)
            WebSocketManager.send(msg)
        } catch (_: Exception) {
        }
    }
}
