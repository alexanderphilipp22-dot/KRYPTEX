package com.kryptex.vault.i18n

import com.kryptex.vault.stego.PayloadType
import com.kryptex.vault.ui.onboarding.PassphraseProblem
import com.kryptex.vault.ui.onboarding.Strength
import com.kryptex.vault.vision.FailureReason
import com.kryptex.vault.vision.Gesture
import java.util.Locale

object EnStrings : AppStrings {
    override val locale: Locale = Locale.ENGLISH
    override val byteUnits = listOf("B", "KB", "MB", "GB")

    // -- shared
    override val back = "Back"
    override val cancel = "Cancel"
    override val dismiss = "Dismiss"
    override val noticeDone = "Done"
    override val noticeNotDone = "Not done"
    override val vaultLocked = "The vault is locked."
    override val vaultLockedTryAgain = "The vault is locked. Unlock it and try again."
    override val staysOnPhoneTitle = "Stays on this phone"
    override val helpDescription = "Help"
    override fun payloadType(type: PayloadType) = when (type) {
        PayloadType.TEXT -> "Note"
        PayloadType.AUDIO -> "Audio"
        PayloadType.IMAGE -> "Image"
        PayloadType.FILE -> "File"
    }
    override fun strength(strength: Strength) = when (strength) {
        Strength.TOO_WEAK -> "Too weak"
        Strength.WEAK -> "Weak"
        Strength.FAIR -> "Fair"
        Strength.STRONG -> "Strong"
        Strength.EXCELLENT -> "Excellent"
    }
    override fun passphraseProblem(problem: PassphraseProblem, minLength: Int) = when (problem) {
        PassphraseProblem.TOO_SHORT -> "Use at least $minLength characters."
        PassphraseProblem.TOO_FEW_CHARACTERS -> "Use more different characters."
        PassphraseProblem.TOO_COMMON -> "That is too easy to guess."
        PassphraseProblem.SEQUENTIAL -> "Avoid simple runs like 'abcdefgh' or '12345678'."
    }

    // -- splash, navigation
    override val splashTagline = "Offline vault"
    override val navDashboard = "Dashboard"
    override val navVault = "Vault"
    override val navSettings = "Settings"

    // -- lock
    override val lockLocked = "Locked"
    override val lockPassphrase = "Passphrase"
    override val lockUnlock = "Unlock"
    override val lockUnlocking = "Unlocking..."
    override val lockUseGestures = "Unlock with gestures"
    override val lockGestureSwitchedOff =
        "Gesture unlock was switched off after too many failed attempts. Unlocking with your passphrase turns it back on."
    override val lockWrongPassphrase = "That passphrase is not correct."
    override val lockDatabaseDamaged = "The vault database could not be opened. It may be damaged."
    override val lockKeyFileDamaged = "The vault's key file is damaged and cannot be read."
    override val lockNoVault = "No vault found on this device."
    override val lockFailed = "Unlock failed."
    override fun lockUnexpected(detail: String) = "Unlock failed: $detail"
    override val startupSlowTitle = "Started in safe mode"
    override val startupSlowBody =
        "Part of the start-up did not finish in time. You can still unlock with your passphrase; if that fails, restart the app."

    // -- onboarding
    override val onboardingIntro = "Hide encrypted notes, voice memos and files inside ordinary pictures."
    override val onboardingLocalTitle = "Everything stays on this phone"
    override val onboardingLocalBody =
        "The app has no internet access at all: no accounts, no cloud, no analytics. That also means nobody can recover your vault for you."
    override val onboardingKeyTitle = "Your passphrase is the only key"
    override val onboardingKeyBody =
        "If you forget it, the contents are gone for good. Pictures that hide a secret lose it when they are re-saved, compressed or edited, so keep them as they are."
    override val onboardingCreateMyVault = "Create my vault"
    override val onboardingChoosePassphrase = "Choose a passphrase"
    override val onboardingChooseBody =
        "A few random words work best. It protects everything, so make it long; you will type it to unlock the vault."
    override val onboardingRepeat = "Repeat passphrase"
    override fun onboardingStrength(label: String) = "Strength: $label"
    override val onboardingDiffer = "The two entries differ."
    override val onboardingCreateVault = "Create vault"
    override val onboardingSecuring = "Securing your vault..."
    override val onboardingTuning = "Your device is tuning the key derivation to its own speed. This takes a few seconds."
    override val onboardingGestureTitle = "Quick unlock with your face"
    override val onboardingGestureBody =
        "Unlock by performing a secret sequence of blinks, glances, eyebrow raises or smiles instead of typing. The camera only starts when you tap the button, and no picture is recorded. A few proportions of your face are saved, encrypted, on this phone, so that someone who copies your sequence is turned away."
    override val onboardingConvenienceTitle = "A convenience, not a replacement"
    override val onboardingConvenienceBody =
        "A gesture sequence is a secret you perform. Anyone who watches you can learn it, and the face check on top is only rough: it turns away clearly different faces, not look-alikes. Gestures are far weaker than a passphrase. Your passphrase always works."
    override val onboardingSetUpGestures = "Set up gesture unlock"
    override val onboardingNotNow = "Not now"
    override fun onboardingCreateFailed(detail: String) = "The vault could not be created: $detail"

    // -- dashboard
    override val dashboardLockNow = "Lock now"
    override val dashboardUnlocked = "Vault unlocked"
    override val dashboardHiddenItems = "Hidden items"
    override val dashboardCarrierPictures = "Carrier pictures"
    override val dashboardDataStored = "Data stored"
    override fun dashboardPreviousUnlock(dateTime: String) = "Previous unlock: $dateTime"
    override val dashboardHide = "Hide something"
    override val dashboardReveal = "Reveal"
    override val dashboardRecent = "Recently hidden"
    override val dashboardEmpty =
        "Nothing hidden yet. Use \"Hide something\" to put a note, a voice memo or a file inside a picture."
    override val dashboardSecurity = "Security"
    override val dashboardKeyUnreadable = "The vault's key file could not be read."
    override val dashboardPassphraseProtection = "Passphrase protection"
    override fun dashboardPassphraseBody(memoryMiB: Int, passes: Int, lanes: Int) =
        "Argon2id, $memoryMiB MiB memory, $passes passes, $lanes lanes. Tuned to this phone at setup so each guess costs an attacker about a second of work on comparable hardware, or far more on a GPU farm: choose a long passphrase."
    override val dashboardGestureOffTitle = "Gesture unlock is off"
    override val dashboardGestureOffBody = "Unlock with a secret sequence of blinks, glances and expressions instead of typing."
    override val dashboardSetUp = "Set up"
    override val dashboardGestureSwitchedOffTitle = "Gesture unlock switched off"
    override val dashboardGestureSwitchedOffBody = "Too many wrong attempts. It turns back on the next time you unlock with your passphrase."
    override val dashboardGestureOnTitle = "Gesture unlock is on"
    override fun dashboardGestureOnBody(steps: Int, attempts: Int) = "$steps gestures. $attempts wrong attempts allowed before it switches off."
    override val dashboardOfflineTitle = "Fully offline"
    override val dashboardOfflineBody = "This app has no network permission. Nothing you hide ever leaves this device."

    // -- vault list and item
    override val vaultTitle = "Vault"
    override val vaultRevealFromPicture = "Reveal from a picture"
    override val vaultEmpty = "Your hidden items appear here, each one stored inside an ordinary-looking picture in your photo library."
    override val vaultHideFab = "Hide something"
    override val carrierMissing = "Picture was deleted or moved: the item is lost"
    override val carrierModified = "Picture was changed: the item may be unreadable"
    override val itemFallbackTitle = "Item"
    override val itemDelete = "Delete"
    override val itemGone = "This item no longer exists."
    override fun itemInPicture(name: String, width: Int, height: Int) = "In $name (${width}×$height)"
    override fun itemHiddenOn(dateTime: String) = "Hidden $dateTime"
    override val itemReveal = "Reveal"
    override val itemReading = "Reading the picture..."
    override val itemHideAgain = "Hide again"
    override val itemCouldNotReveal = "Could not reveal"
    override val itemNothingReadable =
        "Nothing could be read from this picture. It was probably edited, compressed or re-saved after the item was hidden."
    override val itemPictureUnavailable = "The picture could not be opened. It may have been deleted or moved."
    override val itemSavedCopy = "Saved a copy."
    override val itemDeleteTitle = "Delete this item?"
    override val itemDeleteBody =
        "The hidden item and its carrier picture are removed from the vault and from your photo library. This cannot be undone."
    override val itemDeleteConfirm = "Delete"
    override val payloadSaveCopy = "Save a copy..."
    override val payloadImageDescription = "Revealed image"
    override val payloadImageFailed = "This image could not be displayed. Use \"Save a copy\" to open it elsewhere."
    override val payloadAudioFailed = "This audio could not be played. Use \"Save a copy\" to open it elsewhere."
    override val payloadPlay = "Play"
    override val payloadPause = "Pause"

    // -- embed
    override val embedTitle = "Hide something"
    override val embedHeadReady = "Ready"
    override val embedHeadReadyDetail = "Check the details, then hide it."
    override val embedHeadChoose = "Choose a picture and what to hide"
    override val embedHeadChooseDetail = "Both stay on this phone."
    override val embedWorkingDetail = "This can take a few seconds."
    override val embedDoneHead = "Hidden"
    override val embedDoneDetail = "It is in your vault now."
    override val embedFailedHead = "Nothing was saved"
    override val embedFailedDetail = "Details are below."
    override val embedStepReading = "Reading what to hide"
    override val embedStepPreparing = "Preparing the picture"
    override val embedStepHiding = "Encrypting and hiding"
    override val embedStepVerifying = "Checking it can be read back"
    override val embedStepSaving = "Saving"
    override val embedPictureSection = "Picture"
    override val embedPictureIntro =
        "Your secret is hidden in the pixels of an ordinary picture. Kryptex saves a new PNG copy to your photo library; the original is not changed."
    override val embedSelectedPicture = "Selected picture"
    override fun embedRoomFor(size: String) = "Room for about $size"
    override fun embedScaledDown(megapixels: Int) = "Scaled down to $megapixels megapixels"
    override val embedOpening = "Opening..."
    override val embedChoosePicture = "Choose a picture"
    override val embedChooseAnotherPicture = "Choose another picture"
    override val embedPayloadSection = "What to hide"
    override val embedNoteLabel = "Your note"
    override val embedChooseFile = "Choose a file"
    override val embedChooseAnotherFile = "Choose another file"
    override val embedUnknownSize = "Unknown size"
    override fun embedMeterOk(size: String, room: String) = "$size of about $room that fits"
    override fun embedMeterOver(size: String, room: String) =
        "$size is more than the $room that surely fits. It can still work if it compresses well; otherwise choose a larger picture."
    override val embedNameSection = "Name it"
    override val embedTitleLabel = "Title"
    override val embedTitleHint = "Only visible inside Kryptex."
    override val embedHideButton = "Hide in picture"
    override val embedCouldNotUse = "Could not use that"
    override val embedCouldNotHide = "Could not hide it"
    override val embedStaysBody =
        "No network, no cloud. What you hide is sealed with AES-256-GCM under a random key that exists only inside your encrypted vault."
    override fun embedDoneTitle(savedName: String) = "Hidden in $savedName"
    override fun embedDoneBody(folder: String, usage: String) = "Saved in $folder in your photo library, using $usage of that picture's room."
    override val embedKeepTitle = "Keep that picture exactly as it is"
    override val embedKeepBody =
        "Editing, cropping, compressing or sending it through a messenger destroys what is hidden in it. Consider deleting the original picture: having both makes the change easy to spot."
    override val embedOpenItem = "Open item"
    override val embedHideAnother = "Hide something else"
    override val embedDefaultNote = "Note"
    override val embedDefaultItem = "Item"
    override val embedDefaultPicture = "Picture"
    override val embedErrNotPicture = "That file is not a picture Kryptex can read."
    override val embedErrPictureTooLarge = "That picture is too large for this phone to open."
    override val embedErrPictureOpen = "That picture could not be opened."
    override val embedErrNotAudio = "That is not an audio file. Use \"File\" to hide any kind of file."
    override val embedErrNotImage = "That is not a picture file. Use \"File\" to hide any kind of file."
    override val embedErrEmpty = "That file is empty."
    override fun embedErrFileTooBig(megabytes: Int) = "That file is larger than $megabytes MB, the most Kryptex can hide."
    override val embedErrChooseFirst = "Choose what to hide first."
    override fun embedErrTooSmall(capacity: String, needed: String) =
        "This picture is too small for that: it can hold about $capacity and this needs $needed. Choose a larger picture or something smaller."
    override val embedErrVerify = "Safety check failed: the hidden data could not be read back, so nothing was saved."
    override val embedErrMemory = "This phone does not have enough free memory for that picture and file. Try smaller ones."
    override fun embedErrIo(detail: String) = "Could not read or save a file: $detail."
    override val embedErrLockedMidway = "The vault locked while hiding. Nothing was saved."
    override fun embedErrFailed(detail: String) = "Hiding failed: $detail."
    override val embedErrPictureGone = "That picture could not be opened. It may have been deleted or moved."

    // -- extract
    override val extractTitle = "Reveal"
    override val extractHeadIdle = "Reveal something"
    override val extractHeadIdleDetail = "Choose a picture that something was hidden in."
    override val extractHeadFound = "Found it"
    override val extractHeadFoundDetail = "Decrypted on this phone. It is shown from memory only."
    override val extractHeadFailed = "Nothing revealed"
    override val extractHeadFailedDetail = "Details are below."
    override val extractStepReading = "Reading the picture"
    override val extractStepIdentifying = "Looking for a matching key"
    override val extractStepDecrypting = "Decrypting"
    override val extractIntro =
        "Choose the PNG picture itself, exactly as Kryptex saved it. Kryptex finds the matching key in your encrypted vault; a picture that was edited, compressed or sent through a messenger cannot be read any more."
    override val extractChoose = "Choose a picture"
    override val extractTryAnother = "Try another picture"
    override val extractCouldNot = "Could not reveal"
    override val extractStaysBody =
        "No network, no cloud. The item is decrypted in memory only and is wiped when you leave this screen or the vault locks."
    override val extractHideAgain = "Hide again"
    override val extractErrNotPng = "That file is not a PNG picture Kryptex can read."
    override fun extractErrTooBig(megabytes: Int) = "That file is larger than $megabytes MB."
    override val extractErrMemory = "This phone does not have enough free memory to open that picture."
    override fun extractErrIo(detail: String) = "The picture could not be read: $detail."
    override val extractErrLockedMidway = "The vault locked while reading."
    override fun extractErrFailed(detail: String) = "Reading failed: $detail."
    override val extractErrDamaged =
        "This is a picture saved by your vault, but its hidden item could not be decrypted. The data may be damaged."
    override val extractErrNothing =
        "Nothing could be read from this picture with any key in your vault. It holds nothing for this vault, or it was edited, compressed or re-saved after something was hidden in it."

    // -- settings
    override val settingsTitle = "Settings"
    override val settingsLanguageTitle = "Language"
    override val settingsLanguageHint = "Saved in your encrypted vault. Before you unlock, the lock screen follows your phone's language."
    override val settingsHelpTitle = "Help bubbles"
    override val settingsHelpBody = "Show short tips on the Hide, Reveal and Settings screens."
    override val settingsHapticTitle = "Gesture vibration"
    override val settingsHapticBody = "A short pulse each time a gesture of your sequence is recognised, so you know when to do the next one."
    override val settingsFaceCheckTitle = "Face check"
    override val settingsFaceCheckBody =
        "Gesture unlock also compares your face proportions with the ones saved, encrypted, when you set gestures up, so someone who copies your sequence is turned away. It is a rough check: look-alikes may pass, and poor light may turn you away. Gestures set up with an older version have no saved face; set them up again."
    override val settingsAutoLockTitle = "Auto-lock"
    override val settingsAutoLockHint =
        "The vault always locks the moment you leave the app or the screen turns off. This adds a timer for when you stop touching the screen while Kryptex stays open."
    override fun autoLockLabel(seconds: Int) = when (seconds) {
        0 -> "Only when I leave the app"
        15 -> "After 15 seconds"
        30 -> "After 30 seconds"
        60 -> "After 1 minute"
        120 -> "After 2 minutes"
        300 -> "After 5 minutes"
        600 -> "After 10 minutes"
        else -> "After $seconds seconds"
    }
    override val settingsFolderTitle = "Where pictures are saved"
    override val settingsFolderHint = "Carrier pictures are saved under an ordinary folder with camera-style names, never in an album called Kryptex."
    override val settingsFolderSub = "Sub-folder (optional)"
    override val settingsFolderNotAllowed = "Not allowed: .. \\ : * ? \" < > |"
    override fun settingsFolderSavedAs(folder: String) = "Saved as $folder"
    override val settingsFolderSave = "Save folder"
    override val settingsFolderRejected = "Use a plain folder name. These characters are not allowed: \\ : * ? \" < > |"
    override fun settingsFolderSavedNotice(folder: String) = "New pictures will be saved in $folder."
    override val settingsFolderFailed = "The folder could not be saved."
    override val settingsSaveFailed = "The setting could not be saved."
    override val settingsUnlockTitle = "Unlock methods"
    override val settingsKeyUnreadable = "The vault's key file could not be read."
    override val settingsPassphrase = "Passphrase"
    override fun settingsPassphraseInfo(memoryMiB: Int, passes: Int, lanes: Int, created: String) =
        "Argon2id, $memoryMiB MiB, $passes passes, $lanes lanes. Created $created."
    override val settingsChangePassphrase = "Change passphrase"
    override val settingsGestureUnlock = "Gesture unlock"
    override val settingsGestureOff = "Off. Unlock with a secret sequence of blinks, glances and expressions instead of typing."
    override val settingsSetUpGestures = "Set up gestures"
    override val settingsGestureLocked =
        "Switched off after too many wrong attempts. It turns back on the next time you unlock with your passphrase."
    override fun settingsGestureOn(steps: Int, attempts: Int) = "On: $steps gestures, $attempts wrong attempts allowed before it switches off."
    override val settingsChangeGestures = "Change gestures"
    override val settingsRemove = "Remove"
    override val settingsMemoryTitle = "Memory"
    override val settingsMemoryHint =
        "Locking wipes the encryption keys from memory, closes the database and discards every note, picture and file that was decrypted on screen."
    override val settingsLockAndClear = "Lock and clear memory"
    override val settingsAlwaysOnTitle = "Always on"
    override val settingsAlwaysOnBody =
        "Screenshots, screen recording and the app-switcher preview are blocked. The app has no network permission. Nothing is backed up or synced, and the vault locks whenever you leave the app."
    override val settingsEraseTitle = "Erase vault"
    override val settingsEraseHint =
        "Destroys the key file, the device key and the database. Everything hidden becomes unreadable for good, even if a picture is still on the phone."
    override val settingsEraseButton = "Erase vault..."
    override val settingsRemoveGestureTitle = "Remove gesture unlock?"
    override val settingsRemoveGestureBody = "Only your passphrase will open the vault. You can set gestures up again at any time."
    override val settingsChangePassTitle = "Change passphrase"
    override val settingsCurrentPassphrase = "Current passphrase"
    override val settingsNewPassphrase = "New passphrase"
    override val settingsRepeatNewPassphrase = "Repeat new passphrase"
    override val settingsPassphrasesDiffer = "The two passphrases differ."
    override val settingsNoRecovery = "There is no way to recover a forgotten passphrase."
    override val settingsChange = "Change"
    override val settingsEraseDialogTitle = "Erase the vault?"
    override val settingsEraseDialogBody = "This cannot be undone. Enter your passphrase to confirm."
    override val settingsErasePictures = "Also delete the carrier pictures from my photo library"
    override val settingsEraseConfirm = "Erase everything"
    override val settingsErrNotCurrent = "That is not your current passphrase."
    override val settingsErrNotYours = "That is not your passphrase."
    override val settingsPassphraseChanged = "Passphrase changed. Gesture unlock, if set up, keeps working."
    override fun settingsPassphraseChangeFailed(detail: String) = "The passphrase could not be changed: $detail."
    override val settingsGestureRemoved = "Gesture unlock removed. Only your passphrase opens the vault now."
    override fun settingsGestureRemoveFailed(detail: String) = "Gesture unlock could not be removed: $detail."
    override fun settingsEraseFailed(detail: String) = "The vault could not be erased: $detail."

    // -- gesture unlock
    override val gestureTitleUnlock = "Unlock with gestures"
    override val gestureTitleSetup = "Gesture unlock"
    override val gestureChooseLengthTitle = "How long should your sequence be?"
    override fun gestureChooseLengthBody(steps: Int) = "You will perform $steps gestures in a row, chosen from these ten:"
    override fun gestureCount(steps: Int) = "$steps gestures"
    override fun gestureEntropyTitle(bits: Int, short: Boolean) = if (short) "About $bits bits: on the short side" else "About $bits bits"
    override fun gestureEntropyBody(maxAttempts: Int) =
        "A gesture sequence is easier to guess than a good passphrase, and onlookers can learn it. " +
            "Longer is stronger. $maxAttempts wrong tries switch gesture unlock off until you use your passphrase."
    override val gestureContinue = "Continue"
    override val gestureReadyTitle = "Gesture unlock is ready"
    override val gestureReadyBody =
        "Next time, tap \"Unlock with gestures\" on the lock screen. Your passphrase still works at any time."
    override val gestureDone = "Done"
    override val gestureCameraOff = "Camera is off"
    override val gestureChecking = "Checking..."
    override val gestureStartCamera = "Start camera"
    override val gesturePrivacyNote =
        "The camera is off until you tap the button. No picture is recorded or stored; only a few face proportions are measured, and setting gestures up saves them, encrypted, on this phone."
    override val gestureHeadlineUnlock = "Perform your sequence"
    override val gestureHeadlineSaving = "Saving..."
    override val gestureHeadlineRecord = "Record your sequence"
    override val gestureHeadlineConfirm = "Confirm your sequence"
    override fun gestureInstructionReady(steps: Int) = "Tap the button, then perform your $steps gestures one after another."
    override fun gestureInstructionRecord(steps: Int) =
        "Tap the button, then perform $steps gestures of your choice, one after another. Relax your face between them."
    override fun gestureInstructionConfirm(steps: Int) = "Tap the button and repeat exactly the same $steps gestures."
    override val gestureStarting = "Starting the camera..."
    override val gestureHoldStill = "Hold still with a relaxed face..."
    override val gestureFindFace = "Looking for your face. Hold the phone at eye level."
    override val gestureFaceLost = "Your face is not in view."
    override val gestureRelax = "Relax your face..."
    override fun gestureProgress(current: Int, total: Int) = "Gesture $current of $total"
    override val gestureFinished = "Done."
    override val gestureFailedGeneric = "That did not work."
    override fun gestureProgressDescription(done: Int, steps: Int) = "$done of $steps gestures recognised"
    override fun gestureName(gesture: Gesture) = when (gesture) {
        Gesture.LONG_BLINK -> "Long blink"
        Gesture.WINK_LEFT -> "Wink left eye"
        Gesture.WINK_RIGHT -> "Wink right eye"
        Gesture.GAZE_LEFT -> "Look left"
        Gesture.GAZE_RIGHT -> "Look right"
        Gesture.GAZE_UP -> "Look up"
        Gesture.GAZE_DOWN -> "Look down"
        Gesture.BROW_RAISE -> "Raise eyebrows"
        Gesture.MOUTH_OPEN -> "Open mouth"
        Gesture.SMILE -> "Smile"
    }
    override fun gestureFailure(reason: FailureReason) = when (reason) {
        FailureReason.TIMEOUT -> "Time ran out. Tap the button to try again."
        FailureReason.IDLE -> "No gesture was recognised for a while. Tap the button to try again."
        FailureReason.NO_FRAMES -> "The camera did not deliver any pictures. Tap the button to try again."
        FailureReason.PERMISSION_DENIED -> "Camera permission is needed for gesture unlock."
        FailureReason.NO_FRONT_CAMERA -> "This device has no front camera."
        FailureReason.CAMERA_ERROR -> "The camera could not be used. Close other apps that use it and try again."
        FailureReason.MODEL_ERROR -> "The face model could not be loaded."
    }
    override val gestureMsgPermissionDenied = "Camera permission is needed for gesture unlock. You can still use your passphrase."
    override val gestureMsgRecorded = "Recorded. Now repeat the same sequence to confirm it."
    override val gestureMsgMismatch = "The two sequences did not match. Let's start again."
    override fun gestureMsgSaveFailed(detail: String) = "The sequence could not be saved: $detail"
    override fun gestureMsgWrong(attemptsLeft: Int?) = when (attemptsLeft) {
        null -> "That was not it."
        0 -> "That was not it. Gesture unlock is now switched off."
        1 -> "That was not it. One attempt left before gesture unlock switches off."
        else -> "That was not it. $attemptsLeft attempts left before gesture unlock switches off."
    }
    override val gestureMsgFaceInconsistent =
        "Your face was not read clearly both times. Hold the phone at eye level in good light, face the camera and start again."
    override val gestureFallbackDbOpen = "The vault database could not be opened."
    override val gestureFallbackTooManyAttempts = "Too many failed attempts. Use your passphrase."
    override val gestureFallbackLocked = "Gesture unlock is switched off. Use your passphrase."
    override val gestureFallbackUnavailable = "Gesture unlock is not available on this device any more. Use your passphrase."
    override val gestureFallbackNotEnrolled = "No gesture sequence is set up."
    override val gestureFallbackUnreadable = "The vault could not be read."
    override val showPassphrase = "Show passphrase"
    override val hidePassphrase = "Hide passphrase"

    // -- help bubbles
    override val helpEmbedPicture =
        "Pick an ordinary photo. The bigger it is, the more it can hide. Kryptex saves a new PNG copy and leaves your original alone."
    override val helpEmbedPayload =
        "Choose what goes inside: a typed note, or an audio, picture or any other file. The bar shows how much of the picture's room it needs."
    override val helpEmbedFinish =
        "The title is only for you. After hiding, keep the new picture unchanged: editing or sending it destroys what is inside."
    override val helpExtract =
        "Pick the PNG that Kryptex saved. Kryptex finds the right key in your vault by itself; nothing is typed and nothing leaves the phone."
    override val helpExtractFormat =
        "Only the untouched PNG works. Photos from a messenger, screenshots or edited copies cannot be read."
    override val helpSettingsAutoLock = "A shorter timer is safer. Leaving the app or turning the screen off always locks the vault at once."
    override val helpSettingsFolder = "Choose a folder that looks ordinary, so the carrier pictures blend in with your other photos."
    override val helpSettingsUnlock = "Your passphrase is the only thing that can recover the vault. Gestures are a convenience on top of it."
    override val helpSettingsErase = "Erasing cannot be undone. Without the key file nothing that remains can ever be decrypted."
}
