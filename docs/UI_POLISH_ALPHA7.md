# UI Polish — 0.2.0-alpha.7

## Light theme
- Retuned the light color scheme and translucent glass surfaces for stronger text contrast.
- Reduced the glass panel inner highlight and removed the rectangular full-size shimmer overlay from the home hero card.
- Unified outlined text field colors for composer and rename dialogs.
- Styled rename/delete dialogs with rounded surfaces and explicit text colors.

## Composer and attachment picker
- Replaced the small dropdown with a Material 3 modal bottom sheet containing Gallery, Camera, and File actions.
- System pickers are launched after the sheet begins dismissing.
- Attachment errors appear in an animated inline alert; opening stored-file errors use the same path.

## Messages
- New messages receive a fade/slide/scale entrance; lazy list items animate placement and removal.
- HTTP and HTTPS URLs are represented with `LinkAnnotation.Url` and open through Compose's URI handler.
- Long text is initially limited to eight lines with animated “Показать ещё” / “Свернуть” controls.
- Message menu supports copy and delete. Message deletion removes linked files from app-private storage before deleting the Room row.
- Message send and delete failures are shown in animated feedback; failed message sends keep the draft and pending attachments.

## Data layer
- Added `ChatDao.deleteMessage` and `ChatRepository.deleteMessage`.
- No Room schema change is required for this alpha: existing `messages` fields already include `attachmentsJson`.

## Verification
- Source and XML structure reviewed.
- Full Android Gradle build has not been run in this environment (no Android SDK / Gradle installation). Run `gradle :app:testDebugUnitTest` and `gradle :app:assembleDebug` in GitHub Actions before installing the APK.
