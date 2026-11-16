package com.kryptex.vault.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.kryptex.vault.stego.PayloadType
import com.kryptex.vault.ui.gesture.GestureFallback
import com.kryptex.vault.ui.gesture.GestureMessage
import com.kryptex.vault.ui.onboarding.PassphraseProblem
import com.kryptex.vault.ui.onboarding.Strength
import com.kryptex.vault.vision.FailureReason
import com.kryptex.vault.vision.Gesture
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Every piece of text the user can see, in one language. One implementation per [AppLanguage]:
 * the compiler refuses a language that is missing a string.
 *
 * Screens read the active set from [LocalStrings]; code outside Compose (ViewModels) reads
 * `AppContainer.strings`.
 */
interface AppStrings {
    val locale: Locale
    val byteUnits: List<String>

    // -- shared ----------------------------------------------------------------------------------
    val back: String
    val cancel: String
    val dismiss: String
    val noticeDone: String
    val noticeNotDone: String
    val vaultLocked: String
    val vaultLockedTryAgain: String
    val staysOnPhoneTitle: String
    val helpDescription: String
    fun payloadType(type: PayloadType): String
    fun strength(strength: Strength): String
    fun passphraseProblem(problem: PassphraseProblem, minLength: Int): String

    // -- splash, navigation ----------------------------------------------------------------------
    val splashTagline: String
    val navDashboard: String
    val navVault: String
    val navSettings: String

    // -- lock ------------------------------------------------------------------------------------
    val lockLocked: String
    val lockPassphrase: String
    val lockUnlock: String
    val lockUnlocking: String
    val lockUseGestures: String
    val lockGestureSwitchedOff: String
    val lockWrongPassphrase: String
    val lockDatabaseDamaged: String
    val lockKeyFileDamaged: String
    val lockNoVault: String
    val lockFailed: String
    fun lockUnexpected(detail: String): String
    val startupSlowTitle: String
    val startupSlowBody: String

    // -- onboarding ------------------------------------------------------------------------------
    val onboardingIntro: String
    val onboardingLocalTitle: String
    val onboardingLocalBody: String
    val onboardingKeyTitle: String
    val onboardingKeyBody: String
    val onboardingCreateMyVault: String
    val onboardingChoosePassphrase: String
    val onboardingChooseBody: String
    val onboardingRepeat: String
    fun onboardingStrength(label: String): String
    val onboardingDiffer: String
    val onboardingCreateVault: String
    val onboardingSecuring: String
    val onboardingTuning: String
    val onboardingGestureTitle: String
    val onboardingGestureBody: String
    val onboardingConvenienceTitle: String
    val onboardingConvenienceBody: String
    val onboardingSetUpGestures: String
    val onboardingNotNow: String
    fun onboardingCreateFailed(detail: String): String

    // -- dashboard -------------------------------------------------------------------------------
    val dashboardLockNow: String
    val dashboardUnlocked: String
    val dashboardHiddenItems: String
    val dashboardCarrierPictures: String
    val dashboardDataStored: String
    fun dashboardPreviousUnlock(dateTime: String): String
    val dashboardHide: String
    val dashboardReveal: String
    val dashboardRecent: String
    val dashboardEmpty: String
    val dashboardSecurity: String
    val dashboardKeyUnreadable: String
    val dashboardPassphraseProtection: String
    fun dashboardPassphraseBody(memoryMiB: Int, passes: Int, lanes: Int): String
    val dashboardGestureOffTitle: String
    val dashboardGestureOffBody: String
    val dashboardSetUp: String
    val dashboardGestureSwitchedOffTitle: String
    val dashboardGestureSwitchedOffBody: String
    val dashboardGestureOnTitle: String
    fun dashboardGestureOnBody(steps: Int, attempts: Int): String
    val dashboardOfflineTitle: String
    val dashboardOfflineBody: String

    // -- vault list and item ---------------------------------------------------------------------
    val vaultTitle: String
    val vaultRevealFromPicture: String
    val vaultEmpty: String
    val vaultHideFab: String
    val carrierMissing: String
    val carrierModified: String
    val itemFallbackTitle: String
    val itemDelete: String
    val itemGone: String
    fun itemInPicture(name: String, width: Int, height: Int): String
    fun itemHiddenOn(dateTime: String): String
    val itemReveal: String
    val itemReading: String
    val itemHideAgain: String
    val itemCouldNotReveal: String
    val itemNothingReadable: String
    val itemPictureUnavailable: String
    val itemSavedCopy: String
    val itemDeleteTitle: String
    val itemDeleteBody: String
    val itemDeleteConfirm: String
    val payloadSaveCopy: String
    val payloadImageDescription: String
    val payloadImageFailed: String
    val payloadAudioFailed: String
    val payloadPlay: String
    val payloadPause: String

    // -- embed -----------------------------------------------------------------------------------
    val embedTitle: String
    val embedHeadReady: String
    val embedHeadReadyDetail: String
    val embedHeadChoose: String
    val embedHeadChooseDetail: String
    val embedWorkingDetail: String
    val embedDoneHead: String
    val embedDoneDetail: String
    val embedFailedHead: String
    val embedFailedDetail: String
    val embedStepReading: String
    val embedStepPreparing: String
    val embedStepHiding: String
    val embedStepVerifying: String
    val embedStepSaving: String
    val embedPictureSection: String
    val embedPictureIntro: String
    val embedSelectedPicture: String
    fun embedRoomFor(size: String): String
    fun embedScaledDown(megapixels: Int): String
    val embedOpening: String
    val embedChoosePicture: String
    val embedChooseAnotherPicture: String
    val embedPayloadSection: String
    val embedNoteLabel: String
    val embedChooseFile: String
    val embedChooseAnotherFile: String
    val embedUnknownSize: String
    fun embedMeterOk(size: String, room: String): String
    fun embedMeterOver(size: String, room: String): String
    val embedNameSection: String
    val embedTitleLabel: String
    val embedTitleHint: String
    val embedHideButton: String
    val embedCouldNotUse: String
    val embedCouldNotHide: String
    val embedStaysBody: String
    fun embedDoneTitle(savedName: String): String
    fun embedDoneBody(folder: String, usage: String): String
    val embedKeepTitle: String
    val embedKeepBody: String
    val embedOpenItem: String
    val embedHideAnother: String
    val embedDefaultNote: String
    val embedDefaultItem: String
    val embedDefaultPicture: String
    val embedErrNotPicture: String
    val embedErrPictureTooLarge: String
    val embedErrPictureOpen: String
    val embedErrNotAudio: String
    val embedErrNotImage: String
    val embedErrEmpty: String
    fun embedErrFileTooBig(megabytes: Int): String
    val embedErrChooseFirst: String
    fun embedErrTooSmall(capacity: String, needed: String): String
    val embedErrVerify: String
    val embedErrMemory: String
    fun embedErrIo(detail: String): String
    val embedErrLockedMidway: String
    fun embedErrFailed(detail: String): String
    val embedErrPictureGone: String

    // -- extract ---------------------------------------------------------------------------------
    val extractTitle: String
    val extractHeadIdle: String
    val extractHeadIdleDetail: String
    val extractHeadFound: String
    val extractHeadFoundDetail: String
    val extractHeadFailed: String
    val extractHeadFailedDetail: String
    val extractStepReading: String
    val extractStepIdentifying: String
    val extractStepDecrypting: String
    val extractIntro: String
    val extractChoose: String
    val extractTryAnother: String
    val extractCouldNot: String
    val extractStaysBody: String
    val extractHideAgain: String
    val extractErrNotPng: String
    fun extractErrTooBig(megabytes: Int): String
    val extractErrMemory: String
    fun extractErrIo(detail: String): String
    val extractErrLockedMidway: String
    fun extractErrFailed(detail: String): String
    val extractErrDamaged: String
    val extractErrNothing: String

    // -- settings --------------------------------------------------------------------------------
    val settingsTitle: String
    val settingsLanguageTitle: String
    val settingsLanguageHint: String
    val settingsHelpTitle: String
    val settingsHelpBody: String
    val settingsAutoLockTitle: String
    val settingsAutoLockHint: String
    fun autoLockLabel(seconds: Int): String
    val settingsFolderTitle: String
    val settingsFolderHint: String
    val settingsFolderSub: String
    val settingsFolderNotAllowed: String
    fun settingsFolderSavedAs(folder: String): String
    val settingsFolderSave: String
    val settingsFolderRejected: String
    fun settingsFolderSavedNotice(folder: String): String
    val settingsFolderFailed: String
    val settingsSaveFailed: String
    val settingsUnlockTitle: String
    val settingsKeyUnreadable: String
    val settingsPassphrase: String
    fun settingsPassphraseInfo(memoryMiB: Int, passes: Int, lanes: Int, created: String): String
    val settingsChangePassphrase: String
    val settingsGestureUnlock: String
    val settingsGestureOff: String
    val settingsSetUpGestures: String
    val settingsGestureLocked: String
    fun settingsGestureOn(steps: Int, attempts: Int): String
    val settingsChangeGestures: String
    val settingsRemove: String
    val settingsMemoryTitle: String
    val settingsMemoryHint: String
    val settingsLockAndClear: String
    val settingsAlwaysOnTitle: String
    val settingsAlwaysOnBody: String
    val settingsEraseTitle: String
    val settingsEraseHint: String
    val settingsEraseButton: String
    val settingsRemoveGestureTitle: String
    val settingsRemoveGestureBody: String
    val settingsChangePassTitle: String
    val settingsCurrentPassphrase: String
    val settingsNewPassphrase: String
    val settingsRepeatNewPassphrase: String
    val settingsPassphrasesDiffer: String
    val settingsNoRecovery: String
    val settingsChange: String
    val settingsEraseDialogTitle: String
    val settingsEraseDialogBody: String
    val settingsErasePictures: String
    val settingsEraseConfirm: String
    val settingsErrNotCurrent: String
    val settingsErrNotYours: String
    val settingsPassphraseChanged: String
    fun settingsPassphraseChangeFailed(detail: String): String
    val settingsGestureRemoved: String
    fun settingsGestureRemoveFailed(detail: String): String
    fun settingsEraseFailed(detail: String): String

    // -- gesture unlock --------------------------------------------------------------------------
    val gestureTitleUnlock: String
    val gestureTitleSetup: String
    val gestureChooseLengthTitle: String
    fun gestureChooseLengthBody(steps: Int): String
    fun gestureCount(steps: Int): String
    fun gestureEntropyTitle(bits: Int, short: Boolean): String
    fun gestureEntropyBody(maxAttempts: Int): String
    val gestureContinue: String
    val gestureReadyTitle: String
    val gestureReadyBody: String
    val gestureDone: String
    val gestureCameraOff: String
    val gestureChecking: String
    val gestureStartCamera: String
    val gesturePrivacyNote: String
    val gestureHeadlineUnlock: String
    val gestureHeadlineSaving: String
    val gestureHeadlineRecord: String
    val gestureHeadlineConfirm: String
    fun gestureInstructionReady(steps: Int): String
    fun gestureInstructionRecord(steps: Int): String
    fun gestureInstructionConfirm(steps: Int): String
    val gestureStarting: String
    val gestureHoldStill: String
    val gestureFindFace: String
    val gestureFaceLost: String
    val gestureRelax: String
    fun gestureProgress(current: Int, total: Int): String
    val gestureFinished: String
    val gestureFailedGeneric: String
    fun gestureProgressDescription(done: Int, steps: Int): String
    fun gestureName(gesture: Gesture): String
    fun gestureFailure(reason: FailureReason): String

    val gestureMsgPermissionDenied: String
    val gestureMsgRecorded: String
    val gestureMsgMismatch: String
    fun gestureMsgSaveFailed(detail: String): String
    fun gestureMsgWrong(attemptsLeft: Int?): String

    val gestureFallbackDbOpen: String
    val gestureFallbackTooManyAttempts: String
    val gestureFallbackLocked: String
    val gestureFallbackUnavailable: String
    val gestureFallbackNotEnrolled: String
    val gestureFallbackUnreadable: String

    val showPassphrase: String
    val hidePassphrase: String

    // -- help bubbles ----------------------------------------------------------------------------
    val helpEmbedPicture: String
    val helpEmbedPayload: String
    val helpEmbedFinish: String
    val helpExtract: String
    val helpExtractFormat: String
    val helpSettingsAutoLock: String
    val helpSettingsFolder: String
    val helpSettingsUnlock: String
    val helpSettingsErase: String

    // -- typed gesture messages (shared by every language) ---------------------------------------
    fun gestureMessage(message: GestureMessage): String = when (message) {
        GestureMessage.PermissionDenied -> gestureMsgPermissionDenied
        GestureMessage.Recorded -> gestureMsgRecorded
        GestureMessage.Mismatch -> gestureMsgMismatch
        is GestureMessage.SaveFailed -> gestureMsgSaveFailed(message.detail)
        is GestureMessage.Wrong -> gestureMsgWrong(message.attemptsLeft)
        is GestureMessage.Failure -> gestureFailure(message.reason)
    }

    fun gestureFallback(fallback: GestureFallback): String = when (fallback) {
        GestureFallback.DB_OPEN -> gestureFallbackDbOpen
        GestureFallback.TOO_MANY_ATTEMPTS -> gestureFallbackTooManyAttempts
        GestureFallback.LOCKED -> gestureFallbackLocked
        GestureFallback.UNAVAILABLE -> gestureFallbackUnavailable
        GestureFallback.NOT_ENROLLED -> gestureFallbackNotEnrolled
        GestureFallback.UNREADABLE -> gestureFallbackUnreadable
    }

    // -- formatting helpers (shared by every language) -------------------------------------------
    fun bytes(count: Long): String {
        if (count < 1024) return "$count ${byteUnits[0]}"
        var value = count / 1024.0
        var unit = 1
        while (value >= 1024 && unit < byteUnits.lastIndex) {
            value /= 1024
            unit++
        }
        return String.format(locale, if (value >= 100) "%.0f %s" else "%.1f %s", value, byteUnits[unit])
    }

    fun percent(fraction: Double): String = String.format(locale, "%.0f%%", fraction * 100)

    fun dateTime(epochMs: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(epochMs))

    fun date(epochMs: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(epochMs))
}

/** The strings of the active language. English until the root provides the real one. */
val LocalStrings = staticCompositionLocalOf<AppStrings> { EnStrings }
