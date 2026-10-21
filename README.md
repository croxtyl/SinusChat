# SinusChat

Komunikator na Androida z szyfrowaniem end-to-end protokołem Signal w każdej rozmowie, rozmowach i grupach. Konto zakłada się na adres e-mail albo bez żadnego adresu. Strona: [sinusoide.pl](https://sinusoide.pl).

- wiadomości, zdjęcia, pliki, głosówki i połączenia szyfrowane na telefonie,
- grupy, społeczności, rozmowy grupowe i krótkofalówka w sieci lokalnej,
- tryb Tor, tryb „Bez Google” (powiadomienia przez ntfy zamiast Firebase),
- tłumaczenie wiadomości w całości na telefonie (Bergamot / Firefox Translations),
- asystent Sinia działający offline,
- baza danych zawsze zaszyfrowana (SQLCipher), opcjonalny PIN i PIN awaryjny.

## Budowanie

Potrzebne: Android Studio (lub sam Android SDK), JDK 21 i NDK w wersji podanej w `app/build.gradle.kts`.

```bash
./gradlew assembleDebug
```

Moduł `link` (połączenie z komputerem) jest częścią tego repozytorium i buduje się razem z aplikacją.

Powiadomienia przez Firebase są opcjonalne. Bez nich aplikacja działa w trybie „Bez Google”. Żeby je włączyć we własnej wersji, dopisz do `local.properties` wartości własnego projektu Firebase:

```properties
fcm.apiKey=
fcm.appId=
fcm.projectId=
fcm.senderId=
```

`local.properties`, `google-services.json`, klucze do podpisywania i zbudowane pliki są w `.gitignore` i nigdy nie trafiają do repozytorium.

## Licencja

AGPL-3.0 z warunkami dodatkowymi z sekcji 7 (nazwy i znaki, oznaczenie zmienionej wersji, autorstwo, poparcie) — pełny tekst w [LICENSE](LICENSE), warunki w [NOTICE.md](NOTICE.md). Zmieniona wersja musi mieć własną nazwę, ikonę i identyfikator aplikacji.

## Bezpieczeństwo

Błędy bezpieczeństwa zgłaszaj prywatnie, według [SECURITY.md](SECURITY.md).

## English

SinusChat is an Android messenger with end-to-end encryption (Signal protocol) for every chat, call and group. Accounts use an e-mail address or no address at all. Build it with `./gradlew assembleDebug` (JDK 21, Android SDK and the NDK version from `app/build.gradle.kts`). Firebase push is optional; without the `fcm.*` values in `local.properties` the app runs in its "No Google" mode. Licensed under AGPL-3.0 with additional terms, see [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md). Report security issues privately as described in [SECURITY.md](SECURITY.md).
