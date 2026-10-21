package pl.sinusoide.sinuschat.util

import android.content.Context
import pl.sinusoide.sinuschat.R
import java.text.Normalizer

object SettingsSearch {

    enum class Target {
        NOTIFICATIONS, SOUND, PRIVACY, SCREEN_LOCK, ACCOUNT, THEMES, LANGUAGE, TRANSLATION,
        STORAGE, INTERNET, EXPERIMENTAL, DIAGNOSTICS, REPORT, SUPPORT
    }

    data class Entry(
        val title: Int,
        val screen: Int,
        val icon: Int,
        val color: Int,
        val target: Target,
        val keywords: String = ""
    ) {
        val isScreen get() = screen == 0
    }

    private const val C_NOTIF = 0xFFE5A50A.toInt()
    private const val C_PRIV = 0xFF9B6BE0.toInt()
    private const val C_ACCOUNT = 0xFFE67E22.toInt()
    private const val C_THEME = 0xFFE0578B.toInt()
    private const val C_LANG = 0xFF5288C1.toInt()
    private const val C_STORAGE = 0xFF3FB97A.toInt()
    private const val C_NET = 0xFF26A5B8.toInt()
    private const val C_EXP = 0xFFE5484D.toInt()
    private const val C_TRANS = 0xFF2FA88F.toInt()

    val entries: List<Entry> = listOf(
        Entry(R.string.notif_settings_title, 0, R.drawable.ic_notifications, C_NOTIF, Target.NOTIFICATIONS, "powiadomienia notifications push"),
        Entry(R.string.sound_title, 0, R.drawable.ic_call_speaker_on, C_ACCOUNT, Target.SOUND, "dzwiek dźwięk wibracje dzwonek melodia sound vibration ringtone"),
        Entry(R.string.sound_section_ring, R.string.sound_title, R.drawable.ic_call_speaker_on, C_ACCOUNT, Target.SOUND, "dzwonek polaczenia połączenia melodia ringtone calls bit puls"),
        Entry(R.string.sound_section_notif, R.string.sound_title, R.drawable.ic_notifications, C_ACCOUNT, Target.SOUND, "dzwiek dźwięk powiadomien powiadomień fala kropla notification sound"),
        Entry(R.string.sound_volume_ring, R.string.sound_title, R.drawable.ic_call_speaker_on, C_ACCOUNT, Target.SOUND, "glosnosc głośność dzwonka dzwonek suwak cicho glosno głośno volume ringtone slider"),
        Entry(R.string.sound_volume_notif, R.string.sound_title, R.drawable.ic_notifications, C_ACCOUNT, Target.SOUND, "glosnosc głośność powiadomien powiadomień suwak cicho glosno głośno volume notification slider"),
        Entry(R.string.sound_vibrate_notif, R.string.sound_title, R.drawable.ic_vibrations, C_ACCOUNT, Target.SOUND, "wibracje wibracja powiadomienia powiadomień wiadomosci wiadomości vibration vibrate notifications"),
        Entry(R.string.sound_vibrate_calls, R.string.sound_title, R.drawable.ic_vibrations, C_ACCOUNT, Target.SOUND, "wibracje wibracja dzwonienie dzwonek polaczenia połączenia vibration vibrate calls ringing"),
        Entry(R.string.sound_section_outgoing, R.string.sound_title, R.drawable.ic_call_answer, C_ACCOUNT, Target.SOUND, "dzwonienie sygnal sygnał wibracja wychodzace wychodzące outgoing ringback vibration"),
        Entry(R.string.privacy, 0, R.drawable.ic_lock, C_PRIV, Target.PRIVACY, "prywatnosc privacy"),
        Entry(R.string.lock_settings_title, 0, R.drawable.ic_lock, C_PRIV, Target.SCREEN_LOCK, "pin blokada lock haslo password"),
        Entry(R.string.account, 0, R.drawable.ic_key, C_ACCOUNT, Target.ACCOUNT, "konto account profil"),
        Entry(R.string.themes, 0, R.drawable.ic_palette, C_THEME, Target.THEMES, "motyw ciemny jasny kolor wyglad theme dark light"),
        Entry(R.string.app_language, 0, R.drawable.ic_language, C_LANG, Target.LANGUAGE, "jezyk language polski english angielski"),
        Entry(R.string.translate_title, 0, R.drawable.ic_translate, C_TRANS, Target.TRANSLATION, "tlumaczenie tłumaczenie tlumacz przetlumacz translate translation preklad překlad переклад übersetzung"),
        Entry(R.string.translate_section_langs, R.string.translate_title, R.drawable.ic_download, C_TRANS, Target.TRANSLATION, "pobierz jezyki języki slownik słownik download languages"),
        Entry(R.string.translate_section_blocked, R.string.translate_title, R.drawable.ic_translate, C_TRANS, Target.TRANSLATION, "blokuj zablokowane nie tlumacz tłumacz czaty block blocked dont translate chats"),
        Entry(R.string.device_storage, 0, R.drawable.ic_storage, C_STORAGE, Target.STORAGE, "pamiec miejsce storage"),
        Entry(R.string.internet, 0, R.drawable.ic_internet, C_NET, Target.INTERNET, "siec network dane"),
        Entry(R.string.experimental_title, 0, R.drawable.ic_experimental, C_EXP, Target.EXPERIMENTAL, "dodatkowe eksperymentalne beta additional experimental obraz w obrazie pip"),
        Entry(R.string.support_title, 0, R.drawable.ic_heart, C_EXP, Target.SUPPORT, "wesprzyj wsparcie dotacja darowizna wplata wpłata patreon ko-fi kofi przelew konto iban support donate donation bank transfer"),
        Entry(R.string.diagnostics_title, 0, R.drawable.ic_question_exchange, C_STORAGE, Target.DIAGNOSTICS, "diagnostyka problem troubleshooting nie dziala"),
        Entry(R.string.diag_more_title, R.string.diagnostics_title, R.drawable.ic_help, C_STORAGE, Target.DIAGNOSTICS, "pomoc faq help pytania czeste problemy centrum pomocy"),
        Entry(R.string.report_title, 0, R.drawable.ic_report, C_ACCOUNT, Target.REPORT, "blad bug zgloszenie report"),

        Entry(R.string.notif_mode_no_google, R.string.notif_settings_title, R.drawable.ic_shield, C_NOTIF, Target.NOTIFICATIONS, "dostarczanie ntfy push bez google"),
        Entry(R.string.notif_mode_fcm, R.string.notif_settings_title, R.drawable.ic_internet, C_NOTIF, Target.NOTIFICATIONS, "google firebase fcm push"),
        Entry(R.string.notif_status_background, R.string.notif_settings_title, R.drawable.ic_timer, C_NOTIF, Target.NOTIFICATIONS, "bateria battery tlo optymalizacja"),
        Entry(R.string.notif_status_fullscreen, R.string.notif_settings_title, R.drawable.ic_call_answer, C_NOTIF, Target.NOTIFICATIONS, "ekran blokady polaczenia pelny ekran"),
        Entry(R.string.notif_msg_private, R.string.notif_settings_title, R.drawable.ic_chat, C_NOTIF, Target.NOTIFICATIONS, "czaty wiadomosci messages"),
        Entry(R.string.notif_msg_groups, R.string.notif_settings_title, R.drawable.ic_group, C_NOTIF, Target.NOTIFICATIONS, "grupy groups"),
        Entry(R.string.notif_msg_ignore_requests, R.string.notif_settings_title, R.drawable.ic_notifications_off, C_NOTIF, Target.NOTIFICATIONS, "prosby nieznajomi obcy spam nowe rozmowy ignoruj requests strangers"),
        Entry(R.string.notif_msg_preview, R.string.notif_settings_title, R.drawable.ic_visibility, C_NOTIF, Target.NOTIFICATIONS, "tresc podglad dyskretne nadawca preview discreet"),
        Entry(R.string.notif_msg_sound, R.string.notif_settings_title, R.drawable.ic_notifications, C_NOTIF, Target.NOTIFICATIONS, "dzwiek wibracje cicho sound vibration silent"),
        Entry(R.string.notif_msg_in_app, R.string.notif_settings_title, R.drawable.ic_settings, C_NOTIF, Target.NOTIFICATIONS, "otwarta aplikacja in app"),
        Entry(R.string.notif_undelivered, R.string.notif_settings_title, R.drawable.ic_close, C_NOTIF, Target.NOTIFICATIONS, "niedostarczona nie dostarczono 24 godziny krzyzyk undelivered"),
        Entry(R.string.notif_quiet_vibrate, R.string.notif_settings_title, R.drawable.ic_vibrations, C_NOTIF, Target.NOTIFICATIONS, "ciche godziny wibracje wibracja noc quiet vibration"),
        Entry(R.string.notif_call_missed, R.string.notif_settings_title, R.drawable.ic_call_missed, C_NOTIF, Target.NOTIFICATIONS, "nieodebrane missed calls"),
        Entry(R.string.notif_safety, R.string.notif_settings_title, R.drawable.ic_privacy_warning, C_NOTIF, Target.NOTIFICATIONS, "zagrozenie zagrożenie bezpieczenstwo bezpieczeństwo ostrzezenie ostrzeżenie safe engine safety warning"),
        Entry(R.string.notif_update, R.string.notif_settings_title, R.drawable.ic_update, C_NOTIF, Target.NOTIFICATIONS, "aktualizacja aktualizuj wersja nowa stara przestarzala przestarzała sklep play update version outdated store"),
        Entry(R.string.notif_quiet, R.string.notif_settings_title, R.drawable.ic_moon, C_NOTIF, Target.NOTIFICATIONS, "nie przeszkadzac cisza noc godziny dnd quiet"),
        Entry(R.string.notif_other_group_added, R.string.notif_settings_title, R.drawable.ic_group, C_NOTIF, Target.NOTIFICATIONS, "dodanie grupa"),
        Entry(R.string.notif_other_reports, R.string.notif_settings_title, R.drawable.ic_report, C_NOTIF, Target.NOTIFICATIONS, "zgloszenia odpowiedzi"),
        Entry(R.string.notif_other_muted, R.string.notif_settings_title, R.drawable.ic_notifications_off, C_NOTIF, Target.NOTIFICATIONS, "wyciszone wycisz mute"),
        Entry(R.string.notif_sys_channel, R.string.notif_settings_title, R.drawable.ic_settings, C_NOTIF, Target.NOTIFICATIONS, "dzwiek systemowy kanal sound"),
        Entry(R.string.notif_sys_test, R.string.notif_settings_title, R.drawable.ic_send, C_NOTIF, Target.NOTIFICATIONS, "test sprawdz"),

        Entry(R.string.priv_blocknew_title, R.string.privacy, R.drawable.ic_block, C_PRIV, Target.PRIVACY, "blokuj nowe rozmowy obcy nieznajomi nikt nowy zaproszenia block new chats strangers"),
        Entry(R.string.priv_read_status_title, R.string.privacy, R.drawable.ic_check_double, C_PRIV, Target.PRIVACY, "odczytane przeczytane read receipts"),
        Entry(R.string.priv_typing_status_title, R.string.privacy, R.drawable.ic_edit, C_PRIV, Target.PRIVACY, "pisze typing"),
        Entry(R.string.priv_activity_status_title, R.string.privacy, R.drawable.ic_person, C_PRIV, Target.PRIVACY, "online aktywnosc ostatnio widziany last seen"),
        Entry(R.string.priv_discreet_title, R.string.privacy, R.drawable.ic_notifications, C_PRIV, Target.PRIVACY, "powiadomienia tresc discreet"),
        Entry(R.string.priv_notif_reply_title, R.string.privacy, R.drawable.ic_reply, C_PRIV, Target.PRIVACY, "odpowiedz z powiadomienia szybka odpowiedz reply notification"),
        Entry(R.string.priv_hidelast_title, R.string.privacy, R.drawable.ic_chat_dashed, C_PRIV, Target.PRIVACY, "ostatnia wiadomosc lista czatow"),
        Entry(R.string.priv_capture_title, R.string.privacy, R.drawable.ic_sys_screenshot, C_PRIV, Target.PRIVACY, "zrzut ekranu screenshot nagrywanie"),
        Entry(R.string.priv_incognito_kb_title, R.string.privacy, R.drawable.ic_keyboard, C_PRIV, Target.PRIVACY, "klawiatura incognito gboard swiftkey nauka slow keyboard incognito learning"),
        Entry(R.string.priv_qr_title, R.string.privacy, R.drawable.ic_qr_code, C_PRIV, Target.PRIVACY, "kod qr"),
        Entry(R.string.priv_emailblock_title, R.string.privacy, R.drawable.ic_mail, C_PRIV, Target.PRIVACY, "email adres"),
        Entry(R.string.tor_routing, R.string.privacy, R.drawable.ic_internet, C_PRIV, Target.PRIVACY, "tor anonimowosc proxy onion"),

        Entry(R.string.lock_change_pin, R.string.lock_settings_title, R.drawable.ic_changecode, C_PRIV, Target.SCREEN_LOCK, "pin kod haslo zmien"),
        Entry(R.string.lock_show_dots, R.string.lock_settings_title, R.drawable.ic_visibility, C_PRIV, Target.SCREEN_LOCK, "kropki pin"),
        Entry(R.string.lock_shuffle_title, R.string.lock_settings_title, R.drawable.ic_dialpad, C_PRIV, Target.SCREEN_LOCK, "losowa klawiatura cyfry kolejnosc mieszaj pin shuffle keypad"),
        Entry(R.string.lock_auto_title, R.string.lock_settings_title, R.drawable.ic_lock_clock, C_PRIV, Target.SCREEN_LOCK, "czas automatyczna blokada sekundy minuty tlo wygaszenie ekranu timeout auto lock"),
        Entry(R.string.lock_biometrics_desc, R.string.lock_settings_title, R.drawable.ic_fingerprint, C_PRIV, Target.SCREEN_LOCK, "odcisk palca biometria fingerprint"),
        Entry(R.string.lock_set_duress, R.string.lock_settings_title, R.drawable.ic_duresspin, C_PRIV, Target.SCREEN_LOCK, "przymus duress falszywy pin"),
        Entry(R.string.reset_after_10_attempts, R.string.lock_settings_title, R.drawable.ic_danger, C_PRIV, Target.SCREEN_LOCK, "wymaz kasuj proby wipe"),
        Entry(R.string.lock_disable_pin, R.string.lock_settings_title, R.drawable.ic_codeoff, C_PRIV, Target.SCREEN_LOCK, "wylacz pin usun blokade"),

        Entry(R.string.settings_change_email, R.string.account, R.drawable.ic_mail, C_ACCOUNT, Target.ACCOUNT, "email adres zmien"),
        Entry(R.string.export_data_title, R.string.account, R.drawable.ic_download, C_ACCOUNT, Target.ACCOUNT, "eksport kopia dane pobierz export"),
        Entry(R.string.settings_delete_account, R.string.account, R.drawable.ic_delete, C_ACCOUNT, Target.ACCOUNT, "usun konto delete"),

        Entry(R.string.storage_cache_title, R.string.device_storage, R.drawable.ic_delete, C_STORAGE, Target.STORAGE, "cache wyczysc pamiec podreczna"),
        Entry(R.string.larger_than_5mb, R.string.device_storage, R.drawable.ic_image, C_STORAGE, Target.STORAGE, "multimedia zdjecia filmy media"),
        Entry(R.string.wifi_only, R.string.internet, R.drawable.ic_wifi, C_NET, Target.INTERNET, "automatyczne pobieranie wifi"),
        Entry(R.string.any_network, R.string.internet, R.drawable.ic_internet, C_NET, Target.INTERNET, "dane mobilne pobieranie"),
        Entry(R.string.reset_counter, R.string.internet, R.drawable.ic_internet, C_NET, Target.INTERNET, "licznik dane mobilne zuzycie"),

        Entry(R.string.exp_ai_title, R.string.experimental_title, R.drawable.ic_sinia, C_EXP, Target.EXPERIMENTAL, "sinia ai sztuczna inteligencja"),
        Entry(R.string.exp_models_title, R.string.experimental_title, R.drawable.ic_download, C_EXP, Target.EXPERIMENTAL, "modele ai pobrane")
    )

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('ł', 'l')

    fun search(context: Context, query: String): List<Entry> {
        val tokens = normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val anon = pl.sinusoide.sinuschat.setup.AnonAccount.isCurrent(context)
        val emailOnly = setOf(R.string.settings_change_email, R.string.priv_emailblock_title)
        return entries.filter { !anon || it.title !in emailOnly }.mapNotNull { e ->
            val title = normalize(context.getString(e.title))
            val screen = if (e.isScreen) "" else normalize(context.getString(e.screen))
            val haystack = "$title $screen ${e.keywords}"
            if (!tokens.all { haystack.contains(it) }) return@mapNotNull null
            val score = when {
                title.startsWith(tokens.first()) -> 0
                tokens.all { title.contains(it) } -> 1
                else -> 2
            } * 10 + (if (e.isScreen) 0 else 1)
            score to e
        }.sortedBy { it.first }.map { it.second }
    }
}
