package com.kryptex.vault.i18n

import com.kryptex.vault.stego.PayloadType
import com.kryptex.vault.ui.onboarding.PassphraseProblem
import com.kryptex.vault.ui.onboarding.Strength
import com.kryptex.vault.vision.FailureReason
import com.kryptex.vault.vision.Gesture
import java.util.Locale

object DeStrings : AppStrings {
    override val locale: Locale = Locale.GERMAN
    override val byteUnits = listOf("B", "KB", "MB", "GB")

    // -- shared
    override val back = "Zurück"
    override val cancel = "Abbrechen"
    override val dismiss = "Schließen"
    override val noticeDone = "Erledigt"
    override val noticeNotDone = "Nicht erledigt"
    override val vaultLocked = "Der Tresor ist gesperrt."
    override val vaultLockedTryAgain = "Der Tresor ist gesperrt. Entsperre ihn und versuche es erneut."
    override val staysOnPhoneTitle = "Bleibt auf diesem Handy"
    override val helpDescription = "Hilfe"
    override fun payloadType(type: PayloadType) = when (type) {
        PayloadType.TEXT -> "Notiz"
        PayloadType.AUDIO -> "Audio"
        PayloadType.IMAGE -> "Bild"
        PayloadType.FILE -> "Datei"
    }
    override fun strength(strength: Strength) = when (strength) {
        Strength.TOO_WEAK -> "Zu schwach"
        Strength.WEAK -> "Schwach"
        Strength.FAIR -> "Mittel"
        Strength.STRONG -> "Stark"
        Strength.EXCELLENT -> "Hervorragend"
    }
    override fun passphraseProblem(problem: PassphraseProblem, minLength: Int) = when (problem) {
        PassphraseProblem.TOO_SHORT -> "Verwende mindestens $minLength Zeichen."
        PassphraseProblem.TOO_FEW_CHARACTERS -> "Verwende mehr verschiedene Zeichen."
        PassphraseProblem.TOO_COMMON -> "Das ist zu leicht zu erraten."
        PassphraseProblem.SEQUENTIAL -> "Vermeide einfache Folgen wie „abcdefgh“ oder „12345678“."
    }

    // -- splash, navigation
    override val splashTagline = "Offline-Tresor"
    override val navDashboard = "Übersicht"
    override val navVault = "Tresor"
    override val navSettings = "Einstellungen"

    // -- lock
    override val lockLocked = "Gesperrt"
    override val lockPassphrase = "Passphrase"
    override val lockUnlock = "Entsperren"
    override val lockUnlocking = "Entsperre ..."
    override val lockUseGestures = "Mit Gesten entsperren"
    override val lockGestureSwitchedOff =
        "Die Gesten-Entsperrung wurde nach zu vielen Fehlversuchen abgeschaltet. Das Entsperren mit der Passphrase schaltet sie wieder ein."
    override val lockWrongPassphrase = "Diese Passphrase ist nicht richtig."
    override val lockDatabaseDamaged = "Die Tresor-Datenbank konnte nicht geöffnet werden. Sie ist möglicherweise beschädigt."
    override val lockKeyFileDamaged = "Die Schlüsseldatei des Tresors ist beschädigt und nicht lesbar."
    override val lockNoVault = "Auf diesem Gerät wurde kein Tresor gefunden."
    override val lockFailed = "Entsperren fehlgeschlagen."
    override fun lockUnexpected(detail: String) = "Entsperren fehlgeschlagen: $detail"
    override val startupSlowTitle = "Im abgesicherten Modus gestartet"
    override val startupSlowBody =
        "Ein Teil des Starts wurde nicht rechtzeitig fertig. Du kannst trotzdem mit deiner Passphrase entsperren; falls das scheitert, starte die App neu."

    // -- onboarding
    override val onboardingIntro = "Verstecke verschlüsselte Notizen, Sprachmemos und Dateien in gewöhnlichen Bildern."
    override val onboardingLocalTitle = "Alles bleibt auf diesem Handy"
    override val onboardingLocalBody =
        "Die App hat überhaupt keinen Internetzugriff: keine Konten, keine Cloud, keine Analyse. Das heißt auch, dass dir niemand deinen Tresor wiederherstellen kann."
    override val onboardingKeyTitle = "Deine Passphrase ist der einzige Schlüssel"
    override val onboardingKeyBody =
        "Wenn du sie vergisst, ist der Inhalt für immer verloren. Bilder mit einem Geheimnis verlieren es, sobald sie neu gespeichert, komprimiert oder bearbeitet werden. Lass sie deshalb unverändert."
    override val onboardingCreateMyVault = "Meinen Tresor erstellen"
    override val onboardingChoosePassphrase = "Passphrase wählen"
    override val onboardingChooseBody =
        "Ein paar zufällige Wörter funktionieren am besten. Sie schützt alles, also mach sie lang; du tippst sie zum Entsperren des Tresors ein."
    override val onboardingRepeat = "Passphrase wiederholen"
    override fun onboardingStrength(label: String) = "Stärke: $label"
    override val onboardingDiffer = "Die beiden Eingaben unterscheiden sich."
    override val onboardingCreateVault = "Tresor erstellen"
    override val onboardingSecuring = "Dein Tresor wird gesichert ..."
    override val onboardingTuning = "Dein Gerät stimmt die Schlüsselableitung auf die eigene Geschwindigkeit ab. Das dauert einige Sekunden."
    override val onboardingGestureTitle = "Schnell entsperren mit dem Gesicht"
    override val onboardingGestureBody =
        "Entsperre, indem du eine geheime Folge aus Blinzeln, Blicken, Augenbrauen-Heben oder Lächeln ausführst, statt zu tippen. Die Kamera startet nur, wenn du die Taste tippst, und es wird kein Bild aufgezeichnet. Einige Proportionen deines Gesichts werden verschlüsselt auf diesem Handy gespeichert, damit jemand, der deine Folge nachmacht, abgewiesen wird."
    override val onboardingConvenienceTitle = "Eine Erleichterung, kein Ersatz"
    override val onboardingConvenienceBody =
        "Eine Gestenfolge ist ein Geheimnis, das du ausführst. Wer dir zusieht, kann sie lernen, und die zusätzliche Gesichtsprüfung ist nur grob: Sie weist deutlich andere Gesichter ab, keine Doppelgänger. Gesten sind viel schwächer als eine Passphrase. Deine Passphrase funktioniert immer."
    override val onboardingSetUpGestures = "Gesten-Entsperrung einrichten"
    override val onboardingNotNow = "Jetzt nicht"
    override fun onboardingCreateFailed(detail: String) = "Der Tresor konnte nicht erstellt werden: $detail"

    // -- dashboard
    override val dashboardLockNow = "Jetzt sperren"
    override val dashboardUnlocked = "Tresor entsperrt"
    override val dashboardHiddenItems = "Versteckte Einträge"
    override val dashboardCarrierPictures = "Trägerbilder"
    override val dashboardDataStored = "Gespeicherte Daten"
    override fun dashboardPreviousUnlock(dateTime: String) = "Letztes Entsperren: $dateTime"
    override val dashboardHide = "Etwas verstecken"
    override val dashboardReveal = "Aufdecken"
    override val dashboardRecent = "Zuletzt versteckt"
    override val dashboardEmpty =
        "Noch nichts versteckt. Mit „Etwas verstecken“ legst du eine Notiz, ein Sprachmemo oder eine Datei in einem Bild ab."
    override val dashboardSecurity = "Sicherheit"
    override val dashboardKeyUnreadable = "Die Schlüsseldatei des Tresors konnte nicht gelesen werden."
    override val dashboardPassphraseProtection = "Passphrase-Schutz"
    override fun dashboardPassphraseBody(memoryMiB: Int, passes: Int, lanes: Int) =
        "Argon2id, $memoryMiB MiB Speicher, $passes Durchläufe, $lanes Spuren. Bei der Einrichtung auf dieses Handy abgestimmt: Jeder Rateversuch kostet einen Angreifer auf vergleichbarer Hardware etwa eine Sekunde, auf einer GPU-Farm weit mehr. Wähle eine lange Passphrase."
    override val dashboardGestureOffTitle = "Gesten-Entsperrung ist aus"
    override val dashboardGestureOffBody = "Entsperre mit einer geheimen Folge aus Blinzeln, Blicken und Mimik, statt zu tippen."
    override val dashboardSetUp = "Einrichten"
    override val dashboardGestureSwitchedOffTitle = "Gesten-Entsperrung abgeschaltet"
    override val dashboardGestureSwitchedOffBody = "Zu viele Fehlversuche. Sie schaltet sich beim nächsten Entsperren mit der Passphrase wieder ein."
    override val dashboardGestureOnTitle = "Gesten-Entsperrung ist an"
    override fun dashboardGestureOnBody(steps: Int, attempts: Int) = "$steps Gesten. $attempts Fehlversuche erlaubt, bevor sie sich abschaltet."
    override val dashboardOfflineTitle = "Komplett offline"
    override val dashboardOfflineBody = "Diese App hat keine Netzwerkberechtigung. Nichts, was du versteckst, verlässt dieses Gerät."

    // -- vault list and item
    override val vaultTitle = "Tresor"
    override val vaultRevealFromPicture = "Aus einem Bild aufdecken"
    override val vaultEmpty = "Deine versteckten Einträge erscheinen hier, jeder in einem unauffälligen Bild in deiner Fotogalerie."
    override val vaultHideFab = "Etwas verstecken"
    override val carrierMissing = "Bild wurde gelöscht oder verschoben: Der Eintrag ist verloren"
    override val carrierModified = "Bild wurde verändert: Der Eintrag ist möglicherweise nicht lesbar"
    override val itemFallbackTitle = "Eintrag"
    override val itemDelete = "Löschen"
    override val itemGone = "Dieser Eintrag existiert nicht mehr."
    override fun itemInPicture(name: String, width: Int, height: Int) = "In $name (${width}×$height)"
    override fun itemHiddenOn(dateTime: String) = "Versteckt am $dateTime"
    override val itemReveal = "Aufdecken"
    override val itemReading = "Bild wird gelesen ..."
    override val itemHideAgain = "Wieder verbergen"
    override val itemCouldNotReveal = "Aufdecken fehlgeschlagen"
    override val itemNothingReadable =
        "Aus diesem Bild ließ sich nichts lesen. Es wurde wahrscheinlich nach dem Verstecken bearbeitet, komprimiert oder neu gespeichert."
    override val itemPictureUnavailable = "Das Bild konnte nicht geöffnet werden. Es wurde möglicherweise gelöscht oder verschoben."
    override val itemSavedCopy = "Kopie gespeichert."
    override val itemDeleteTitle = "Diesen Eintrag löschen?"
    override val itemDeleteBody =
        "Der versteckte Eintrag und sein Trägerbild werden aus dem Tresor und aus deiner Fotogalerie entfernt. Das lässt sich nicht rückgängig machen."
    override val itemDeleteConfirm = "Löschen"
    override val payloadSaveCopy = "Kopie speichern ..."
    override val payloadImageDescription = "Aufgedecktes Bild"
    override val payloadImageFailed = "Dieses Bild konnte nicht angezeigt werden. Mit „Kopie speichern“ kannst du es anderswo öffnen."
    override val payloadAudioFailed = "Dieses Audio konnte nicht abgespielt werden. Mit „Kopie speichern“ kannst du es anderswo öffnen."
    override val payloadPlay = "Abspielen"
    override val payloadPause = "Pause"

    // -- embed
    override val embedTitle = "Etwas verstecken"
    override val embedHeadReady = "Bereit"
    override val embedHeadReadyDetail = "Prüfe die Angaben und verstecke es dann."
    override val embedHeadChoose = "Wähle ein Bild und was versteckt werden soll"
    override val embedHeadChooseDetail = "Beides bleibt auf diesem Handy."
    override val embedWorkingDetail = "Das kann einige Sekunden dauern."
    override val embedDoneHead = "Versteckt"
    override val embedDoneDetail = "Es liegt jetzt in deinem Tresor."
    override val embedFailedHead = "Nichts wurde gespeichert"
    override val embedFailedDetail = "Details siehe unten."
    override val embedStepReading = "Inhalt wird gelesen"
    override val embedStepPreparing = "Bild wird vorbereitet"
    override val embedStepHiding = "Verschlüsseln und verstecken"
    override val embedStepVerifying = "Prüfen, ob es sich zurücklesen lässt"
    override val embedStepSaving = "Speichern"
    override val embedPictureSection = "Bild"
    override val embedPictureIntro =
        "Dein Geheimnis wird in den Pixeln eines gewöhnlichen Bildes versteckt. Kryptex speichert eine neue PNG-Kopie in deiner Fotogalerie; das Original bleibt unverändert."
    override val embedSelectedPicture = "Ausgewähltes Bild"
    override fun embedRoomFor(size: String) = "Platz für etwa $size"
    override fun embedScaledDown(megapixels: Int) = "Auf $megapixels Megapixel verkleinert"
    override val embedOpening = "Öffne ..."
    override val embedChoosePicture = "Bild wählen"
    override val embedChooseAnotherPicture = "Anderes Bild wählen"
    override val embedPayloadSection = "Was verstecken"
    override val embedNoteLabel = "Deine Notiz"
    override val embedChooseFile = "Datei wählen"
    override val embedChooseAnotherFile = "Andere Datei wählen"
    override val embedUnknownSize = "Größe unbekannt"
    override fun embedMeterOk(size: String, room: String) = "$size von etwa $room, die passen"
    override fun embedMeterOver(size: String, room: String) =
        "$size ist mehr als die $room, die sicher passen. Mit guter Komprimierung kann es trotzdem klappen; sonst wähle ein größeres Bild."
    override val embedNameSection = "Benennen"
    override val embedTitleLabel = "Titel"
    override val embedTitleHint = "Nur innerhalb von Kryptex sichtbar."
    override val embedHideButton = "Im Bild verstecken"
    override val embedCouldNotUse = "Das ließ sich nicht verwenden"
    override val embedCouldNotHide = "Verstecken fehlgeschlagen"
    override val embedStaysBody =
        "Kein Netzwerk, keine Cloud. Was du versteckst, wird mit AES-256-GCM unter einem Zufallsschlüssel versiegelt, der nur in deinem verschlüsselten Tresor existiert."
    override fun embedDoneTitle(savedName: String) = "Versteckt in $savedName"
    override fun embedDoneBody(folder: String, usage: String) = "Gespeichert in $folder in deiner Fotogalerie, mit $usage des Platzes in diesem Bild."
    override val embedKeepTitle = "Lass dieses Bild genau so, wie es ist"
    override val embedKeepBody =
        "Bearbeiten, Zuschneiden, Komprimieren oder Senden über einen Messenger zerstört das Versteckte. Erwäge, das Originalbild zu löschen: Mit beiden Bildern ist die Änderung leicht zu erkennen."
    override val embedOpenItem = "Eintrag öffnen"
    override val embedHideAnother = "Etwas anderes verstecken"
    override val embedDefaultNote = "Notiz"
    override val embedDefaultItem = "Eintrag"
    override val embedDefaultPicture = "Bild"
    override val embedErrNotPicture = "Diese Datei ist kein Bild, das Kryptex lesen kann."
    override val embedErrPictureTooLarge = "Dieses Bild ist zu groß, als dass dieses Handy es öffnen könnte."
    override val embedErrPictureOpen = "Das Bild konnte nicht geöffnet werden."
    override val embedErrNotAudio = "Das ist keine Audiodatei. Nutze „Datei“, um beliebige Dateien zu verstecken."
    override val embedErrNotImage = "Das ist keine Bilddatei. Nutze „Datei“, um beliebige Dateien zu verstecken."
    override val embedErrEmpty = "Diese Datei ist leer."
    override fun embedErrFileTooBig(megabytes: Int) = "Diese Datei ist größer als $megabytes MB, dem Maximum, das Kryptex verstecken kann."
    override val embedErrChooseFirst = "Wähle zuerst, was versteckt werden soll."
    override fun embedErrTooSmall(capacity: String, needed: String) =
        "Dieses Bild ist dafür zu klein: Es fasst etwa $capacity, hier werden $needed benötigt. Wähle ein größeres Bild oder etwas Kleineres."
    override val embedErrVerify = "Sicherheitsprüfung fehlgeschlagen: Die versteckten Daten ließen sich nicht zurücklesen, deshalb wurde nichts gespeichert."
    override val embedErrMemory = "Dieses Handy hat nicht genug freien Speicher für dieses Bild und diese Datei. Versuche kleinere."
    override fun embedErrIo(detail: String) = "Eine Datei konnte nicht gelesen oder gespeichert werden: $detail."
    override val embedErrLockedMidway = "Der Tresor wurde während des Versteckens gesperrt. Nichts wurde gespeichert."
    override fun embedErrFailed(detail: String) = "Verstecken fehlgeschlagen: $detail."
    override val embedErrPictureGone = "Das Bild konnte nicht geöffnet werden. Es wurde möglicherweise gelöscht oder verschoben."

    // -- extract
    override val extractTitle = "Aufdecken"
    override val extractHeadIdle = "Etwas aufdecken"
    override val extractHeadIdleDetail = "Wähle ein Bild, in dem etwas versteckt wurde."
    override val extractHeadFound = "Gefunden"
    override val extractHeadFoundDetail = "Auf diesem Handy entschlüsselt. Es wird nur aus dem Speicher angezeigt."
    override val extractHeadFailed = "Nichts aufgedeckt"
    override val extractHeadFailedDetail = "Details siehe unten."
    override val extractStepReading = "Bild wird gelesen"
    override val extractStepIdentifying = "Suche passenden Schlüssel"
    override val extractStepDecrypting = "Entschlüsseln"
    override val extractIntro =
        "Wähle das PNG-Bild selbst, genau so, wie Kryptex es gespeichert hat. Kryptex findet den passenden Schlüssel in deinem verschlüsselten Tresor; ein Bild, das bearbeitet, komprimiert oder über einen Messenger gesendet wurde, lässt sich nicht mehr lesen."
    override val extractChoose = "Bild wählen"
    override val extractTryAnother = "Anderes Bild versuchen"
    override val extractCouldNot = "Aufdecken fehlgeschlagen"
    override val extractStaysBody =
        "Kein Netzwerk, keine Cloud. Der Eintrag wird nur im Speicher entschlüsselt und gelöscht, sobald du diesen Bildschirm verlässt oder der Tresor sperrt."
    override val extractHideAgain = "Wieder verbergen"
    override val extractErrNotPng = "Diese Datei ist kein PNG-Bild, das Kryptex lesen kann."
    override fun extractErrTooBig(megabytes: Int) = "Diese Datei ist größer als $megabytes MB."
    override val extractErrMemory = "Dieses Handy hat nicht genug freien Speicher, um das Bild zu öffnen."
    override fun extractErrIo(detail: String) = "Das Bild konnte nicht gelesen werden: $detail."
    override val extractErrLockedMidway = "Der Tresor wurde während des Lesens gesperrt."
    override fun extractErrFailed(detail: String) = "Lesen fehlgeschlagen: $detail."
    override val extractErrDamaged =
        "Dies ist ein von deinem Tresor gespeichertes Bild, aber der versteckte Eintrag ließ sich nicht entschlüsseln. Die Daten sind möglicherweise beschädigt."
    override val extractErrNothing =
        "Aus diesem Bild ließ sich mit keinem Schlüssel deines Tresors etwas lesen. Es enthält nichts für diesen Tresor, oder es wurde nach dem Verstecken bearbeitet, komprimiert oder neu gespeichert."

    // -- settings
    override val settingsTitle = "Einstellungen"
    override val settingsLanguageTitle = "Sprache"
    override val settingsLanguageHint = "Wird in deinem verschlüsselten Tresor gespeichert. Vor dem Entsperren folgt der Sperrbildschirm der Sprache deines Handys."
    override val settingsHelpTitle = "Hilfe-Blasen"
    override val settingsHelpBody = "Zeigt kurze Tipps in den Bereichen Verstecken, Aufdecken und Einstellungen."
    override val settingsHapticTitle = "Gesten-Vibration"
    override val settingsHapticBody = "Ein kurzer Impuls, sobald eine Geste deiner Folge erkannt wurde, damit du weißt, wann die nächste dran ist."
    override val settingsFaceCheckTitle = "Gesichtsprüfung"
    override val settingsFaceCheckBody =
        "Die Gesten-Entsperrung vergleicht zusätzlich die Proportionen deines Gesichts mit denen, die beim Einrichten der Gesten verschlüsselt gespeichert wurden, damit jemand, der deine Folge nachmacht, abgewiesen wird. Es ist eine grobe Prüfung: Ähnlich aussehende Personen können durchkommen, und schlechtes Licht kann dich abweisen. Mit einer älteren Version eingerichtete Gesten haben kein gespeichertes Gesicht; richte sie neu ein."
    override val settingsAutoLockTitle = "Automatische Sperre"
    override val settingsAutoLockHint =
        "Der Tresor sperrt immer sofort, wenn du die App verlässt oder der Bildschirm ausgeht. Dazu kommt ein Timer für den Fall, dass du den Bildschirm nicht mehr berührst, während Kryptex geöffnet bleibt."
    override fun autoLockLabel(seconds: Int) = when (seconds) {
        0 -> "Nur beim Verlassen der App"
        15 -> "Nach 15 Sekunden"
        30 -> "Nach 30 Sekunden"
        60 -> "Nach 1 Minute"
        120 -> "Nach 2 Minuten"
        300 -> "Nach 5 Minuten"
        600 -> "Nach 10 Minuten"
        else -> "Nach $seconds Sekunden"
    }
    override val settingsFolderTitle = "Wo Bilder gespeichert werden"
    override val settingsFolderHint = "Trägerbilder werden in einem gewöhnlichen Ordner mit kameraähnlichen Namen gespeichert, nie in einem Album namens Kryptex."
    override val settingsFolderSub = "Unterordner (optional)"
    override val settingsFolderNotAllowed = "Nicht erlaubt: .. \\ : * ? \" < > |"
    override fun settingsFolderSavedAs(folder: String) = "Gespeichert als $folder"
    override val settingsFolderSave = "Ordner speichern"
    override val settingsFolderRejected = "Verwende einen einfachen Ordnernamen. Diese Zeichen sind nicht erlaubt: \\ : * ? \" < > |"
    override fun settingsFolderSavedNotice(folder: String) = "Neue Bilder werden in $folder gespeichert."
    override val settingsFolderFailed = "Der Ordner konnte nicht gespeichert werden."
    override val settingsSaveFailed = "Die Einstellung konnte nicht gespeichert werden."
    override val settingsUnlockTitle = "Entsperrmethoden"
    override val settingsKeyUnreadable = "Die Schlüsseldatei des Tresors konnte nicht gelesen werden."
    override val settingsPassphrase = "Passphrase"
    override fun settingsPassphraseInfo(memoryMiB: Int, passes: Int, lanes: Int, created: String) =
        "Argon2id, $memoryMiB MiB, $passes Durchläufe, $lanes Spuren. Erstellt am $created."
    override val settingsChangePassphrase = "Passphrase ändern"
    override val settingsGestureUnlock = "Gesten-Entsperrung"
    override val settingsGestureOff = "Aus. Entsperre mit einer geheimen Folge aus Blinzeln, Blicken und Mimik, statt zu tippen."
    override val settingsSetUpGestures = "Gesten einrichten"
    override val settingsGestureLocked =
        "Nach zu vielen Fehlversuchen abgeschaltet. Sie schaltet sich beim nächsten Entsperren mit der Passphrase wieder ein."
    override fun settingsGestureOn(steps: Int, attempts: Int) = "An: $steps Gesten, $attempts Fehlversuche erlaubt, bevor sie sich abschaltet."
    override val settingsChangeGestures = "Gesten ändern"
    override val settingsRemove = "Entfernen"
    override val settingsMemoryTitle = "Speicher"
    override val settingsMemoryHint =
        "Beim Sperren werden die Schlüssel aus dem Speicher gelöscht, die Datenbank geschlossen und jede entschlüsselte Notiz, jedes Bild und jede Datei auf dem Bildschirm verworfen."
    override val settingsLockAndClear = "Sperren und Speicher leeren"
    override val settingsAlwaysOnTitle = "Immer aktiv"
    override val settingsAlwaysOnBody =
        "Screenshots, Bildschirmaufnahmen und die Vorschau im App-Wechsler sind blockiert. Die App hat keine Netzwerkberechtigung. Nichts wird gesichert oder synchronisiert, und der Tresor sperrt, sobald du die App verlässt."
    override val settingsEraseTitle = "Tresor löschen"
    override val settingsEraseHint =
        "Zerstört Schlüsseldatei, Geräteschlüssel und Datenbank. Alles Versteckte wird für immer unlesbar, selbst wenn ein Bild noch auf dem Handy liegt."
    override val settingsEraseButton = "Tresor löschen ..."
    override val settingsRemoveGestureTitle = "Gesten-Entsperrung entfernen?"
    override val settingsRemoveGestureBody = "Nur noch deine Passphrase öffnet den Tresor. Du kannst die Gesten jederzeit neu einrichten."
    override val settingsChangePassTitle = "Passphrase ändern"
    override val settingsCurrentPassphrase = "Aktuelle Passphrase"
    override val settingsNewPassphrase = "Neue Passphrase"
    override val settingsRepeatNewPassphrase = "Neue Passphrase wiederholen"
    override val settingsPassphrasesDiffer = "Die beiden Passphrasen unterscheiden sich."
    override val settingsNoRecovery = "Eine vergessene Passphrase lässt sich nicht wiederherstellen."
    override val settingsChange = "Ändern"
    override val settingsEraseDialogTitle = "Den Tresor löschen?"
    override val settingsEraseDialogBody = "Das lässt sich nicht rückgängig machen. Gib zur Bestätigung deine Passphrase ein."
    override val settingsErasePictures = "Auch die Trägerbilder aus meiner Fotogalerie löschen"
    override val settingsEraseConfirm = "Alles löschen"
    override val settingsErrNotCurrent = "Das ist nicht deine aktuelle Passphrase."
    override val settingsErrNotYours = "Das ist nicht deine Passphrase."
    override val settingsPassphraseChanged = "Passphrase geändert. Eine eingerichtete Gesten-Entsperrung funktioniert weiter."
    override fun settingsPassphraseChangeFailed(detail: String) = "Die Passphrase konnte nicht geändert werden: $detail."
    override val settingsGestureRemoved = "Gesten-Entsperrung entfernt. Jetzt öffnet nur noch deine Passphrase den Tresor."
    override fun settingsGestureRemoveFailed(detail: String) = "Die Gesten-Entsperrung konnte nicht entfernt werden: $detail."
    override fun settingsEraseFailed(detail: String) = "Der Tresor konnte nicht gelöscht werden: $detail."

    // -- gesture unlock
    override val gestureTitleUnlock = "Mit Gesten entsperren"
    override val gestureTitleSetup = "Gesten-Entsperrung"
    override val gestureChooseLengthTitle = "Wie lang soll deine Folge sein?"
    override fun gestureChooseLengthBody(steps: Int) = "Du führst $steps Gesten hintereinander aus, gewählt aus diesen zehn:"
    override fun gestureCount(steps: Int) = "$steps Gesten"
    override fun gestureEntropyTitle(bits: Int, short: Boolean) = if (short) "Etwa $bits Bit: eher kurz" else "Etwa $bits Bit"
    override fun gestureEntropyBody(maxAttempts: Int) =
        "Eine Gestenfolge lässt sich leichter erraten als eine gute Passphrase, und Zuschauer können sie lernen. " +
            "Länger ist stärker. $maxAttempts Fehlversuche schalten die Gesten-Entsperrung ab, bis du deine Passphrase benutzt."
    override val gestureContinue = "Weiter"
    override val gestureReadyTitle = "Gesten-Entsperrung ist bereit"
    override val gestureReadyBody =
        "Tippe beim nächsten Mal auf dem Sperrbildschirm auf „Mit Gesten entsperren“. Deine Passphrase funktioniert weiterhin jederzeit."
    override val gestureDone = "Fertig"
    override val gestureCameraOff = "Kamera ist aus"
    override val gestureChecking = "Prüfe ..."
    override val gestureStartCamera = "Kamera starten"
    override val gesturePrivacyNote =
        "Die Kamera bleibt aus, bis du die Taste tippst. Es wird kein Bild aufgezeichnet oder gespeichert; nur einige Gesichtsproportionen werden gemessen, und beim Einrichten der Gesten werden sie verschlüsselt auf diesem Handy gespeichert."
    override val gestureHeadlineUnlock = "Führe deine Folge aus"
    override val gestureHeadlineSaving = "Speichere ..."
    override val gestureHeadlineRecord = "Folge aufnehmen"
    override val gestureHeadlineConfirm = "Folge bestätigen"
    override fun gestureInstructionReady(steps: Int) = "Tippe die Taste und führe dann deine $steps Gesten nacheinander aus."
    override fun gestureInstructionRecord(steps: Int) =
        "Tippe die Taste und führe dann $steps Gesten deiner Wahl nacheinander aus. Entspanne dein Gesicht zwischen den Gesten."
    override fun gestureInstructionConfirm(steps: Int) = "Tippe die Taste und wiederhole genau dieselben $steps Gesten."
    override val gestureStarting = "Kamera wird gestartet ..."
    override val gestureHoldStill = "Halte still, mit entspanntem Gesicht ..."
    override val gestureFindFace = "Suche dein Gesicht. Halte das Handy auf Augenhöhe."
    override val gestureFaceLost = "Dein Gesicht ist nicht im Bild."
    override val gestureRelax = "Entspanne dein Gesicht ..."
    override fun gestureProgress(current: Int, total: Int) = "Geste $current von $total"
    override val gestureFinished = "Fertig."
    override val gestureFailedGeneric = "Das hat nicht geklappt."
    override fun gestureProgressDescription(done: Int, steps: Int) = "$done von $steps Gesten erkannt"
    override fun gestureName(gesture: Gesture) = when (gesture) {
        Gesture.LONG_BLINK -> "Langer Lidschlag"
        Gesture.WINK_LEFT -> "Linkes Auge zwinkern"
        Gesture.WINK_RIGHT -> "Rechtes Auge zwinkern"
        Gesture.GAZE_LEFT -> "Nach links schauen"
        Gesture.GAZE_RIGHT -> "Nach rechts schauen"
        Gesture.GAZE_UP -> "Nach oben schauen"
        Gesture.GAZE_DOWN -> "Nach unten schauen"
        Gesture.BROW_RAISE -> "Augenbrauen heben"
        Gesture.MOUTH_OPEN -> "Mund öffnen"
        Gesture.SMILE -> "Lächeln"
    }
    override fun gestureFailure(reason: FailureReason) = when (reason) {
        FailureReason.TIMEOUT -> "Die Zeit ist abgelaufen. Tippe die Taste, um es erneut zu versuchen."
        FailureReason.IDLE -> "Eine Weile wurde keine Geste erkannt. Tippe die Taste, um es erneut zu versuchen."
        FailureReason.NO_FRAMES -> "Die Kamera hat keine Bilder geliefert. Tippe die Taste, um es erneut zu versuchen."
        FailureReason.PERMISSION_DENIED -> "Für die Gesten-Entsperrung wird die Kameraberechtigung benötigt."
        FailureReason.NO_FRONT_CAMERA -> "Dieses Gerät hat keine Frontkamera."
        FailureReason.CAMERA_ERROR -> "Die Kamera konnte nicht verwendet werden. Schließe andere Apps, die sie nutzen, und versuche es erneut."
        FailureReason.MODEL_ERROR -> "Das Gesichtsmodell konnte nicht geladen werden."
    }
    override val gestureMsgPermissionDenied =
        "Für die Gesten-Entsperrung wird die Kameraberechtigung benötigt. Du kannst weiterhin deine Passphrase benutzen."
    override val gestureMsgRecorded = "Aufgenommen. Wiederhole jetzt dieselbe Folge, um sie zu bestätigen."
    override val gestureMsgMismatch = "Die beiden Folgen stimmten nicht überein. Fangen wir von vorn an."
    override fun gestureMsgSaveFailed(detail: String) = "Die Folge konnte nicht gespeichert werden: $detail"
    override fun gestureMsgWrong(attemptsLeft: Int?) = when (attemptsLeft) {
        null -> "Das war es nicht."
        0 -> "Das war es nicht. Die Gesten-Entsperrung ist jetzt abgeschaltet."
        1 -> "Das war es nicht. Noch ein Versuch, dann schaltet sich die Gesten-Entsperrung ab."
        else -> "Das war es nicht. Noch $attemptsLeft Versuche, dann schaltet sich die Gesten-Entsperrung ab."
    }
    override val gestureMsgFaceInconsistent =
        "Dein Gesicht wurde nicht beide Male klar erkannt. Halte das Handy auf Augenhöhe, sorge für gutes Licht, schau in die Kamera und fang von vorn an."
    override val gestureFallbackDbOpen = "Die Tresor-Datenbank konnte nicht geöffnet werden."
    override val gestureFallbackTooManyAttempts = "Zu viele Fehlversuche. Benutze deine Passphrase."
    override val gestureFallbackLocked = "Die Gesten-Entsperrung ist abgeschaltet. Benutze deine Passphrase."
    override val gestureFallbackUnavailable = "Die Gesten-Entsperrung ist auf diesem Gerät nicht mehr verfügbar. Benutze deine Passphrase."
    override val gestureFallbackNotEnrolled = "Es ist keine Gestenfolge eingerichtet."
    override val gestureFallbackUnreadable = "Der Tresor konnte nicht gelesen werden."
    override val showPassphrase = "Passphrase anzeigen"
    override val hidePassphrase = "Passphrase verbergen"

    // -- help bubbles
    override val helpEmbedPicture =
        "Wähle ein gewöhnliches Foto. Je größer es ist, desto mehr passt hinein. Kryptex speichert eine neue PNG-Kopie und lässt dein Original in Ruhe."
    override val helpEmbedPayload =
        "Wähle, was hineinkommt: eine getippte Notiz oder eine Audio-, Bild- oder beliebige andere Datei. Der Balken zeigt, wie viel vom Platz des Bildes nötig ist."
    override val helpEmbedFinish =
        "Der Titel ist nur für dich. Lass das neue Bild nach dem Verstecken unverändert: Bearbeiten oder Senden zerstört den Inhalt."
    override val helpExtract =
        "Wähle das PNG, das Kryptex gespeichert hat. Kryptex findet den richtigen Schlüssel selbst in deinem Tresor; es wird nichts getippt und nichts verlässt das Handy."
    override val helpExtractFormat =
        "Nur das unveränderte PNG funktioniert. Fotos aus einem Messenger, Screenshots oder bearbeitete Kopien lassen sich nicht lesen."
    override val helpSettingsAutoLock = "Ein kürzerer Timer ist sicherer. Beim Verlassen der App oder Ausschalten des Bildschirms sperrt der Tresor immer sofort."
    override val helpSettingsFolder = "Wähle einen unauffälligen Ordner, damit die Trägerbilder zwischen deinen anderen Fotos nicht auffallen."
    override val helpSettingsUnlock = "Nur deine Passphrase kann den Tresor wiederherstellen. Gesten sind eine Erleichterung obendrauf."
    override val helpSettingsErase = "Löschen lässt sich nicht rückgängig machen. Ohne die Schlüsseldatei kann nichts Verbleibendes je entschlüsselt werden."
}
