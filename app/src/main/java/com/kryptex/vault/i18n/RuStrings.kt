package com.kryptex.vault.i18n

import com.kryptex.vault.stego.PayloadType
import com.kryptex.vault.ui.onboarding.PassphraseProblem
import com.kryptex.vault.ui.onboarding.Strength
import com.kryptex.vault.vision.FailureReason
import com.kryptex.vault.vision.Gesture
import java.util.Locale

/**
 * Wording notes: «хранилище» = vault, «парольная фраза» = passphrase, «изображение-носитель» =
 * carrier picture. Counts are phrased as "Label: N" so no Russian plural forms are needed.
 */
object RuStrings : AppStrings {
    override val locale: Locale = Locale.forLanguageTag("ru")
    override val byteUnits = listOf("Б", "КБ", "МБ", "ГБ")

    // -- shared
    override val back = "Назад"
    override val cancel = "Отмена"
    override val dismiss = "Закрыть"
    override val noticeDone = "Готово"
    override val noticeNotDone = "Не выполнено"
    override val vaultLocked = "Хранилище заблокировано."
    override val vaultLockedTryAgain = "Хранилище заблокировано. Разблокируйте его и повторите попытку."
    override val staysOnPhoneTitle = "Остаётся на этом телефоне"
    override val helpDescription = "Справка"
    override fun payloadType(type: PayloadType) = when (type) {
        PayloadType.TEXT -> "Заметка"
        PayloadType.AUDIO -> "Аудио"
        PayloadType.IMAGE -> "Изображение"
        PayloadType.FILE -> "Файл"
    }
    override fun strength(strength: Strength) = when (strength) {
        Strength.TOO_WEAK -> "Слишком слабая"
        Strength.WEAK -> "Слабая"
        Strength.FAIR -> "Средняя"
        Strength.STRONG -> "Надёжная"
        Strength.EXCELLENT -> "Отличная"
    }
    override fun passphraseProblem(problem: PassphraseProblem, minLength: Int) = when (problem) {
        PassphraseProblem.TOO_SHORT -> "Минимальная длина: $minLength символов."
        PassphraseProblem.TOO_FEW_CHARACTERS -> "Используйте больше разных символов."
        PassphraseProblem.TOO_COMMON -> "Это слишком легко угадать."
        PassphraseProblem.SEQUENTIAL -> "Избегайте простых последовательностей вроде «abcdefgh» или «12345678»."
    }

    // -- splash, navigation
    override val splashTagline = "Офлайн-хранилище"
    override val navDashboard = "Обзор"
    override val navVault = "Хранилище"
    override val navSettings = "Настройки"

    // -- lock
    override val lockLocked = "Заблокировано"
    override val lockPassphrase = "Парольная фраза"
    override val lockUnlock = "Разблокировать"
    override val lockUnlocking = "Разблокировка..."
    override val lockUseGestures = "Разблокировать жестами"
    override val lockGestureSwitchedOff =
        "Разблокировка жестами отключена после слишком многих неудачных попыток. Разблокировка парольной фразой включит её снова."
    override val lockWrongPassphrase = "Неверная парольная фраза."
    override val lockDatabaseDamaged = "Не удалось открыть базу данных хранилища. Возможно, она повреждена."
    override val lockKeyFileDamaged = "Файл ключей хранилища повреждён и не читается."
    override val lockNoVault = "На этом устройстве хранилище не найдено."
    override val lockFailed = "Не удалось разблокировать."
    override fun lockUnexpected(detail: String) = "Не удалось разблокировать: $detail"
    override val startupSlowTitle = "Запуск в безопасном режиме"
    override val startupSlowBody =
        "Часть запуска не завершилась вовремя. Вы по-прежнему можете разблокировать хранилище парольной фразой; если не получится, перезапустите приложение."

    // -- onboarding
    override val onboardingIntro = "Прячьте зашифрованные заметки, голосовые записи и файлы внутри обычных изображений."
    override val onboardingLocalTitle = "Всё остаётся на этом телефоне"
    override val onboardingLocalBody =
        "У приложения вообще нет доступа в интернет: ни учётных записей, ни облака, ни аналитики. Это также значит, что никто не сможет восстановить ваше хранилище за вас."
    override val onboardingKeyTitle = "Парольная фраза — единственный ключ"
    override val onboardingKeyBody =
        "Если вы забудете её, содержимое будет потеряно навсегда. Изображения со спрятанным секретом теряют его при повторном сохранении, сжатии или редактировании, поэтому не изменяйте их."
    override val onboardingCreateMyVault = "Создать хранилище"
    override val onboardingChoosePassphrase = "Выберите парольную фразу"
    override val onboardingChooseBody =
        "Лучше всего подходят несколько случайных слов. Она защищает всё, поэтому сделайте её длинной; вы будете вводить её для разблокировки хранилища."
    override val onboardingRepeat = "Повторите парольную фразу"
    override fun onboardingStrength(label: String) = "Надёжность: $label"
    override val onboardingDiffer = "Введённые значения не совпадают."
    override val onboardingCreateVault = "Создать хранилище"
    override val onboardingSecuring = "Защита хранилища..."
    override val onboardingTuning = "Устройство подбирает параметры получения ключа под свою скорость. Это займёт несколько секунд."
    override val onboardingGestureTitle = "Быстрая разблокировка лицом"
    override val onboardingGestureBody =
        "Разблокируйте хранилище секретной последовательностью морганий, взглядов, поднятий бровей или улыбок вместо ввода текста. Камера включается только после нажатия кнопки, ничего не записывается."
    override val onboardingConvenienceTitle = "Удобство, а не замена"
    override val onboardingConvenienceBody =
        "Последовательность жестов — это секрет, который вы выполняете, а не биометрическая проверка. Тот, кто наблюдает за вами, может её запомнить, и она намного слабее парольной фразы. Парольная фраза работает всегда."
    override val onboardingSetUpGestures = "Настроить разблокировку жестами"
    override val onboardingNotNow = "Не сейчас"
    override fun onboardingCreateFailed(detail: String) = "Не удалось создать хранилище: $detail"

    // -- dashboard
    override val dashboardLockNow = "Заблокировать"
    override val dashboardUnlocked = "Хранилище разблокировано"
    override val dashboardHiddenItems = "Спрятано записей"
    override val dashboardCarrierPictures = "Изображений-носителей"
    override val dashboardDataStored = "Объём данных"
    override fun dashboardPreviousUnlock(dateTime: String) = "Предыдущая разблокировка: $dateTime"
    override val dashboardHide = "Спрятать"
    override val dashboardReveal = "Извлечь"
    override val dashboardRecent = "Недавно спрятано"
    override val dashboardEmpty =
        "Пока ничего не спрятано. Нажмите «Спрятать», чтобы поместить заметку, голосовую запись или файл внутрь изображения."
    override val dashboardSecurity = "Безопасность"
    override val dashboardKeyUnreadable = "Не удалось прочитать файл ключей хранилища."
    override val dashboardPassphraseProtection = "Защита парольной фразой"
    override fun dashboardPassphraseBody(memoryMiB: Int, passes: Int, lanes: Int) =
        "Argon2id, память: $memoryMiB МиБ, проходов: $passes, потоков: $lanes. Подобрано под этот телефон при настройке: каждая попытка подбора стоит злоумышленнику около секунды на сопоставимом оборудовании и намного больше на ферме GPU. Выбирайте длинную парольную фразу."
    override val dashboardGestureOffTitle = "Разблокировка жестами выключена"
    override val dashboardGestureOffBody = "Разблокируйте секретной последовательностью морганий, взглядов и мимики вместо ввода текста."
    override val dashboardSetUp = "Настроить"
    override val dashboardGestureSwitchedOffTitle = "Разблокировка жестами отключена"
    override val dashboardGestureSwitchedOffBody = "Слишком много неверных попыток. Она снова включится при следующей разблокировке парольной фразой."
    override val dashboardGestureOnTitle = "Разблокировка жестами включена"
    override fun dashboardGestureOnBody(steps: Int, attempts: Int) = "Жестов в последовательности: $steps. Допустимо неверных попыток до отключения: $attempts."
    override val dashboardOfflineTitle = "Полностью офлайн"
    override val dashboardOfflineBody = "У приложения нет разрешения на доступ к сети. Ничего из спрятанного не покидает это устройство."

    // -- vault list and item
    override val vaultTitle = "Хранилище"
    override val vaultRevealFromPicture = "Извлечь из изображения"
    override val vaultEmpty = "Здесь появятся ваши спрятанные записи, каждая внутри обычного на вид изображения в вашей галерее."
    override val vaultHideFab = "Спрятать"
    override val carrierMissing = "Изображение удалено или перемещено: запись утеряна"
    override val carrierModified = "Изображение изменено: запись может не читаться"
    override val itemFallbackTitle = "Запись"
    override val itemDelete = "Удалить"
    override val itemGone = "Эта запись больше не существует."
    override fun itemInPicture(name: String, width: Int, height: Int) = "В $name (${width}×$height)"
    override fun itemHiddenOn(dateTime: String) = "Спрятано: $dateTime"
    override val itemReveal = "Извлечь"
    override val itemReading = "Чтение изображения..."
    override val itemHideAgain = "Скрыть снова"
    override val itemCouldNotReveal = "Не удалось извлечь"
    override val itemNothingReadable =
        "Из этого изображения ничего не удалось прочитать. Вероятно, после того как запись была спрятана, его отредактировали, сжали или пересохранили."
    override val itemPictureUnavailable = "Не удалось открыть изображение. Возможно, оно удалено или перемещено."
    override val itemSavedCopy = "Копия сохранена."
    override val itemDeleteTitle = "Удалить эту запись?"
    override val itemDeleteBody =
        "Спрятанная запись и её изображение-носитель будут удалены из хранилища и из вашей галереи. Это действие нельзя отменить."
    override val itemDeleteConfirm = "Удалить"
    override val payloadSaveCopy = "Сохранить копию..."
    override val payloadImageDescription = "Извлечённое изображение"
    override val payloadImageFailed = "Не удалось показать это изображение. Нажмите «Сохранить копию», чтобы открыть его в другом приложении."
    override val payloadAudioFailed = "Не удалось воспроизвести это аудио. Нажмите «Сохранить копию», чтобы открыть его в другом приложении."
    override val payloadPlay = "Воспроизвести"
    override val payloadPause = "Пауза"

    // -- embed
    override val embedTitle = "Спрятать"
    override val embedHeadReady = "Готово к запуску"
    override val embedHeadReadyDetail = "Проверьте данные и спрячьте."
    override val embedHeadChoose = "Выберите изображение и что спрятать"
    override val embedHeadChooseDetail = "И то и другое остаётся на этом телефоне."
    override val embedWorkingDetail = "Это может занять несколько секунд."
    override val embedDoneHead = "Спрятано"
    override val embedDoneDetail = "Теперь это в вашем хранилище."
    override val embedFailedHead = "Ничего не сохранено"
    override val embedFailedDetail = "Подробности ниже."
    override val embedStepReading = "Чтение данных"
    override val embedStepPreparing = "Подготовка изображения"
    override val embedStepHiding = "Шифрование и сокрытие"
    override val embedStepVerifying = "Проверка обратного чтения"
    override val embedStepSaving = "Сохранение"
    override val embedPictureSection = "Изображение"
    override val embedPictureIntro =
        "Ваш секрет прячется в пикселях обычного изображения. Kryptex сохраняет новую копию в формате PNG в вашей галерее; оригинал не изменяется."
    override val embedSelectedPicture = "Выбранное изображение"
    override fun embedRoomFor(size: String) = "Вместимость: около $size"
    override fun embedScaledDown(megapixels: Int) = "Уменьшено до $megapixels мегапикселей"
    override val embedOpening = "Открытие..."
    override val embedChoosePicture = "Выбрать изображение"
    override val embedChooseAnotherPicture = "Выбрать другое изображение"
    override val embedPayloadSection = "Что спрятать"
    override val embedNoteLabel = "Ваша заметка"
    override val embedChooseFile = "Выбрать файл"
    override val embedChooseAnotherFile = "Выбрать другой файл"
    override val embedUnknownSize = "Размер неизвестен"
    override fun embedMeterOk(size: String, room: String) = "$size из примерно $room, которые поместятся"
    override fun embedMeterOver(size: String, room: String) =
        "$size — больше, чем $room, которые точно поместятся. При хорошем сжатии всё же может получиться; иначе выберите изображение побольше."
    override val embedNameSection = "Название"
    override val embedTitleLabel = "Название"
    override val embedTitleHint = "Видно только внутри Kryptex."
    override val embedHideButton = "Спрятать в изображении"
    override val embedCouldNotUse = "Не удалось использовать"
    override val embedCouldNotHide = "Не удалось спрятать"
    override val embedStaysBody =
        "Ни сети, ни облака. Спрятанное запечатывается алгоритмом AES-256-GCM случайным ключом, который существует только внутри вашего зашифрованного хранилища."
    override fun embedDoneTitle(savedName: String) = "Спрятано в $savedName"
    override fun embedDoneBody(folder: String, usage: String) = "Сохранено в папке $folder в вашей галерее; занято $usage вместимости изображения."
    override val embedKeepTitle = "Не изменяйте это изображение"
    override val embedKeepBody =
        "Редактирование, обрезка, сжатие или отправка через мессенджер уничтожают спрятанное. Рассмотрите удаление исходного изображения: при наличии обоих изменение легко заметить."
    override val embedOpenItem = "Открыть запись"
    override val embedHideAnother = "Спрятать что-то ещё"
    override val embedDefaultNote = "Заметка"
    override val embedDefaultItem = "Запись"
    override val embedDefaultPicture = "Изображение"
    override val embedErrNotPicture = "Этот файл не является изображением, которое может прочитать Kryptex."
    override val embedErrPictureTooLarge = "Это изображение слишком велико, чтобы телефон мог его открыть."
    override val embedErrPictureOpen = "Не удалось открыть изображение."
    override val embedErrNotAudio = "Это не аудиофайл. Выберите «Файл», чтобы спрятать файл любого типа."
    override val embedErrNotImage = "Это не файл изображения. Выберите «Файл», чтобы спрятать файл любого типа."
    override val embedErrEmpty = "Этот файл пуст."
    override fun embedErrFileTooBig(megabytes: Int) = "Этот файл больше $megabytes МБ — максимума, который может спрятать Kryptex."
    override val embedErrChooseFirst = "Сначала выберите, что спрятать."
    override fun embedErrTooSmall(capacity: String, needed: String) =
        "Это изображение слишком мало: оно вмещает около $capacity, а нужно $needed. Выберите изображение побольше или данные поменьше."
    override val embedErrVerify = "Проверка безопасности не пройдена: спрятанные данные не удалось прочитать обратно, поэтому ничего не сохранено."
    override val embedErrMemory = "На телефоне не хватает свободной памяти для этого изображения и файла. Попробуйте меньшие."
    override fun embedErrIo(detail: String) = "Не удалось прочитать или сохранить файл: $detail."
    override val embedErrLockedMidway = "Хранилище заблокировалось во время сокрытия. Ничего не сохранено."
    override fun embedErrFailed(detail: String) = "Не удалось спрятать: $detail."
    override val embedErrPictureGone = "Не удалось открыть изображение. Возможно, оно удалено или перемещено."

    // -- extract
    override val extractTitle = "Извлечь"
    override val extractHeadIdle = "Извлечь спрятанное"
    override val extractHeadIdleDetail = "Выберите изображение, в котором что-то спрятано."
    override val extractHeadFound = "Найдено"
    override val extractHeadFoundDetail = "Расшифровано на этом телефоне. Показывается только из памяти."
    override val extractHeadFailed = "Ничего не извлечено"
    override val extractHeadFailedDetail = "Подробности ниже."
    override val extractStepReading = "Чтение изображения"
    override val extractStepIdentifying = "Поиск подходящего ключа"
    override val extractStepDecrypting = "Расшифровка"
    override val extractIntro =
        "Выберите само PNG-изображение в том виде, в каком его сохранил Kryptex. Kryptex найдёт подходящий ключ в вашем зашифрованном хранилище; изображение, которое отредактировали, сжали или отправили через мессенджер, прочитать уже нельзя."
    override val extractChoose = "Выбрать изображение"
    override val extractTryAnother = "Попробовать другое изображение"
    override val extractCouldNot = "Не удалось извлечь"
    override val extractStaysBody =
        "Ни сети, ни облака. Запись расшифровывается только в памяти и стирается, когда вы покидаете этот экран или хранилище блокируется."
    override val extractHideAgain = "Скрыть снова"
    override val extractErrNotPng = "Этот файл не является PNG-изображением, которое может прочитать Kryptex."
    override fun extractErrTooBig(megabytes: Int) = "Этот файл больше $megabytes МБ."
    override val extractErrMemory = "На телефоне не хватает свободной памяти, чтобы открыть это изображение."
    override fun extractErrIo(detail: String) = "Не удалось прочитать изображение: $detail."
    override val extractErrLockedMidway = "Хранилище заблокировалось во время чтения."
    override fun extractErrFailed(detail: String) = "Не удалось прочитать: $detail."
    override val extractErrDamaged =
        "Это изображение, сохранённое вашим хранилищем, но спрятанную в нём запись не удалось расшифровать. Возможно, данные повреждены."
    override val extractErrNothing =
        "Из этого изображения не удалось ничего прочитать ни одним ключом вашего хранилища. В нём нет ничего для этого хранилища, либо после сокрытия его отредактировали, сжали или пересохранили."

    // -- settings
    override val settingsTitle = "Настройки"
    override val settingsLanguageTitle = "Язык"
    override val settingsLanguageHint = "Сохраняется в вашем зашифрованном хранилище. До разблокировки экран блокировки использует язык телефона."
    override val settingsHelpTitle = "Подсказки"
    override val settingsHelpBody = "Показывать короткие советы на экранах «Спрятать», «Извлечь» и «Настройки»."
    override val settingsAutoLockTitle = "Автоблокировка"
    override val settingsAutoLockHint =
        "Хранилище всегда блокируется сразу, как только вы выходите из приложения или экран гаснет. Таймер добавляет блокировку на случай, когда вы перестаёте касаться экрана, оставив Kryptex открытым."
    override fun autoLockLabel(seconds: Int) = when (seconds) {
        0 -> "Только при выходе из приложения"
        15 -> "Через 15 секунд"
        30 -> "Через 30 секунд"
        60 -> "Через 1 минуту"
        120 -> "Через 2 минуты"
        300 -> "Через 5 минут"
        600 -> "Через 10 минут"
        else -> "Через $seconds с"
    }
    override val settingsFolderTitle = "Куда сохраняются изображения"
    override val settingsFolderHint = "Изображения-носители сохраняются в обычную папку с названиями как у фото с камеры, но никогда в альбом с именем Kryptex."
    override val settingsFolderSub = "Вложенная папка (необязательно)"
    override val settingsFolderNotAllowed = "Недопустимо: .. \\ : * ? \" < > |"
    override fun settingsFolderSavedAs(folder: String) = "Будет сохранено в $folder"
    override val settingsFolderSave = "Сохранить папку"
    override val settingsFolderRejected = "Используйте простое имя папки. Эти символы недопустимы: \\ : * ? \" < > |"
    override fun settingsFolderSavedNotice(folder: String) = "Новые изображения будут сохраняться в $folder."
    override val settingsFolderFailed = "Не удалось сохранить папку."
    override val settingsSaveFailed = "Не удалось сохранить настройку."
    override val settingsUnlockTitle = "Способы разблокировки"
    override val settingsKeyUnreadable = "Не удалось прочитать файл ключей хранилища."
    override val settingsPassphrase = "Парольная фраза"
    override fun settingsPassphraseInfo(memoryMiB: Int, passes: Int, lanes: Int, created: String) =
        "Argon2id, $memoryMiB МиБ, проходов: $passes, потоков: $lanes. Создана: $created."
    override val settingsChangePassphrase = "Изменить парольную фразу"
    override val settingsGestureUnlock = "Разблокировка жестами"
    override val settingsGestureOff = "Выключена. Разблокируйте секретной последовательностью морганий, взглядов и мимики вместо ввода текста."
    override val settingsSetUpGestures = "Настроить жесты"
    override val settingsGestureLocked =
        "Отключена после слишком многих неверных попыток. Снова включится при следующей разблокировке парольной фразой."
    override fun settingsGestureOn(steps: Int, attempts: Int) = "Включена. Жестов: $steps; допустимо неверных попыток до отключения: $attempts."
    override val settingsChangeGestures = "Изменить жесты"
    override val settingsRemove = "Удалить"
    override val settingsMemoryTitle = "Память"
    override val settingsMemoryHint =
        "При блокировке ключи шифрования стираются из памяти, база данных закрывается, а все расшифрованные на экране заметки, изображения и файлы сбрасываются."
    override val settingsLockAndClear = "Заблокировать и очистить память"
    override val settingsAlwaysOnTitle = "Всегда включено"
    override val settingsAlwaysOnBody =
        "Снимки экрана, запись экрана и превью в списке приложений заблокированы. У приложения нет разрешения на доступ к сети. Ничего не копируется в резерв и не синхронизируется, а хранилище блокируется при каждом выходе из приложения."
    override val settingsEraseTitle = "Стереть хранилище"
    override val settingsEraseHint =
        "Уничтожает файл ключей, ключ устройства и базу данных. Всё спрятанное станет нечитаемым навсегда, даже если изображение останется на телефоне."
    override val settingsEraseButton = "Стереть хранилище..."
    override val settingsRemoveGestureTitle = "Отключить разблокировку жестами?"
    override val settingsRemoveGestureBody = "Хранилище будет открываться только парольной фразой. Жесты можно настроить снова в любой момент."
    override val settingsChangePassTitle = "Изменить парольную фразу"
    override val settingsCurrentPassphrase = "Текущая парольная фраза"
    override val settingsNewPassphrase = "Новая парольная фраза"
    override val settingsRepeatNewPassphrase = "Повторите новую парольную фразу"
    override val settingsPassphrasesDiffer = "Парольные фразы не совпадают."
    override val settingsNoRecovery = "Забытую парольную фразу восстановить невозможно."
    override val settingsChange = "Изменить"
    override val settingsEraseDialogTitle = "Стереть хранилище?"
    override val settingsEraseDialogBody = "Это действие нельзя отменить. Для подтверждения введите парольную фразу."
    override val settingsErasePictures = "Также удалить изображения-носители из моей галереи"
    override val settingsEraseConfirm = "Стереть всё"
    override val settingsErrNotCurrent = "Это не ваша текущая парольная фраза."
    override val settingsErrNotYours = "Это не ваша парольная фраза."
    override val settingsPassphraseChanged = "Парольная фраза изменена. Разблокировка жестами, если она настроена, продолжит работать."
    override fun settingsPassphraseChangeFailed(detail: String) = "Не удалось изменить парольную фразу: $detail."
    override val settingsGestureRemoved = "Разблокировка жестами отключена. Теперь хранилище открывает только парольная фраза."
    override fun settingsGestureRemoveFailed(detail: String) = "Не удалось отключить разблокировку жестами: $detail."
    override fun settingsEraseFailed(detail: String) = "Не удалось стереть хранилище: $detail."

    // -- gesture unlock
    override val gestureTitleUnlock = "Разблокировка жестами"
    override val gestureTitleSetup = "Жесты для разблокировки"
    override val gestureChooseLengthTitle = "Какой длины должна быть ваша последовательность?"
    override fun gestureChooseLengthBody(steps: Int) = "Длина последовательности: $steps. Жесты выбираются из этих десяти:"
    override fun gestureCount(steps: Int) = "Жестов: $steps"
    override fun gestureEntropyTitle(bits: Int, short: Boolean) = if (short) "Около $bits бит: маловато" else "Около $bits бит"
    override fun gestureEntropyBody(maxAttempts: Int) =
        "Последовательность жестов угадать проще, чем хорошую парольную фразу, и наблюдатель может её запомнить. " +
            "Чем длиннее, тем надёжнее. Неверных попыток до отключения разблокировки жестами: $maxAttempts. " +
            "Она снова включится после ввода парольной фразы."
    override val gestureContinue = "Продолжить"
    override val gestureReadyTitle = "Разблокировка жестами готова"
    override val gestureReadyBody =
        "В следующий раз нажмите «Разблокировать жестами» на экране блокировки. Парольная фраза по-прежнему работает в любой момент."
    override val gestureDone = "Готово"
    override val gestureCameraOff = "Камера выключена"
    override val gestureChecking = "Проверка..."
    override val gestureStartCamera = "Включить камеру"
    override val gesturePrivacyNote = "Камера выключена, пока вы не нажмёте кнопку; ничего не записывается и не сохраняется."
    override val gestureHeadlineUnlock = "Выполните последовательность"
    override val gestureHeadlineSaving = "Сохранение..."
    override val gestureHeadlineRecord = "Запишите последовательность"
    override val gestureHeadlineConfirm = "Подтвердите последовательность"
    override fun gestureInstructionReady(steps: Int) = "Нажмите кнопку, затем выполните свои жесты один за другим (всего: $steps)."
    override fun gestureInstructionRecord(steps: Int) =
        "Нажмите кнопку, затем выполните на свой выбор жесты один за другим (всего: $steps). Расслабляйте лицо между ними."
    override fun gestureInstructionConfirm(steps: Int) = "Нажмите кнопку и повторите точно такие же жесты (всего: $steps)."
    override val gestureStarting = "Запуск камеры..."
    override val gestureHoldStill = "Замрите с расслабленным лицом..."
    override val gestureFindFace = "Ищем ваше лицо. Держите телефон на уровне глаз."
    override val gestureFaceLost = "Лица не видно в кадре."
    override val gestureRelax = "Расслабьте лицо..."
    override fun gestureProgress(current: Int, total: Int) = "Жест $current из $total"
    override val gestureFinished = "Готово."
    override val gestureFailedGeneric = "Не получилось."
    override fun gestureProgressDescription(done: Int, steps: Int) = "Распознано жестов: $done из $steps"
    override fun gestureName(gesture: Gesture) = when (gesture) {
        Gesture.LONG_BLINK -> "Долгое моргание"
        Gesture.WINK_LEFT -> "Подмигнуть левым глазом"
        Gesture.WINK_RIGHT -> "Подмигнуть правым глазом"
        Gesture.GAZE_LEFT -> "Посмотреть влево"
        Gesture.GAZE_RIGHT -> "Посмотреть вправо"
        Gesture.GAZE_UP -> "Посмотреть вверх"
        Gesture.GAZE_DOWN -> "Посмотреть вниз"
        Gesture.BROW_RAISE -> "Поднять брови"
        Gesture.MOUTH_OPEN -> "Открыть рот"
        Gesture.SMILE -> "Улыбнуться"
    }
    override fun gestureFailure(reason: FailureReason) = when (reason) {
        FailureReason.TIMEOUT -> "Время вышло. Нажмите кнопку, чтобы попробовать снова."
        FailureReason.IDLE -> "Долгое время не распознавался ни один жест. Нажмите кнопку, чтобы попробовать снова."
        FailureReason.NO_FRAMES -> "Камера не передала изображение. Нажмите кнопку, чтобы попробовать снова."
        FailureReason.PERMISSION_DENIED -> "Для разблокировки жестами нужно разрешение на использование камеры."
        FailureReason.NO_FRONT_CAMERA -> "На этом устройстве нет фронтальной камеры."
        FailureReason.CAMERA_ERROR -> "Не удалось использовать камеру. Закройте другие приложения, которые её используют, и повторите попытку."
        FailureReason.MODEL_ERROR -> "Не удалось загрузить модель распознавания лица."
    }
    override val gestureMsgPermissionDenied =
        "Для разблокировки жестами нужно разрешение на использование камеры. Вы по-прежнему можете использовать парольную фразу."
    override val gestureMsgRecorded = "Записано. Теперь повторите ту же последовательность для подтверждения."
    override val gestureMsgMismatch = "Две последовательности не совпали. Начнём заново."
    override fun gestureMsgSaveFailed(detail: String) = "Не удалось сохранить последовательность: $detail"
    override fun gestureMsgWrong(attemptsLeft: Int?) = when (attemptsLeft) {
        null -> "Не то."
        0 -> "Не то. Разблокировка жестами теперь отключена."
        1 -> "Не то. Осталась одна попытка, затем разблокировка жестами отключится."
        else -> "Не то. Осталось попыток до отключения разблокировки жестами: $attemptsLeft."
    }
    override val gestureFallbackDbOpen = "Не удалось открыть базу данных хранилища."
    override val gestureFallbackTooManyAttempts = "Слишком много неверных попыток. Используйте парольную фразу."
    override val gestureFallbackLocked = "Разблокировка жестами отключена. Используйте парольную фразу."
    override val gestureFallbackUnavailable = "Разблокировка жестами больше недоступна на этом устройстве. Используйте парольную фразу."
    override val gestureFallbackNotEnrolled = "Последовательность жестов не настроена."
    override val gestureFallbackUnreadable = "Не удалось прочитать хранилище."
    override val showPassphrase = "Показать парольную фразу"
    override val hidePassphrase = "Скрыть парольную фразу"

    // -- help bubbles
    override val helpEmbedPicture =
        "Выберите обычное фото. Чем оно больше, тем больше в нём можно спрятать. Kryptex сохраняет новую копию в PNG и не трогает оригинал."
    override val helpEmbedPayload =
        "Выберите, что поместить внутрь: набранную заметку либо аудио, изображение или любой другой файл. Шкала показывает, какую часть вместимости изображения это займёт."
    override val helpEmbedFinish =
        "Название нужно только вам. После сокрытия не изменяйте новое изображение: редактирование или отправка уничтожают содержимое."
    override val helpExtract =
        "Выберите PNG, который сохранил Kryptex. Нужный ключ Kryptex найдёт в хранилище сам; ничего вводить не нужно, и ничего не покидает телефон."
    override val helpExtractFormat =
        "Работает только неизменённый PNG. Фото из мессенджера, снимки экрана или отредактированные копии прочитать нельзя."
    override val helpSettingsAutoLock = "Чем короче таймер, тем безопаснее. При выходе из приложения или выключении экрана хранилище всегда блокируется сразу."
    override val helpSettingsFolder = "Выберите обычную на вид папку, чтобы изображения-носители не выделялись среди ваших других фото."
    override val helpSettingsUnlock = "Восстановить хранилище может только ваша парольная фраза. Жесты — лишь удобство сверх неё."
    override val helpSettingsErase = "Стирание нельзя отменить. Без файла ключей ничего из оставшегося никогда не удастся расшифровать."
}
