# Aidroid Remaster 0.2.0-alpha.4 — Chat layout C + launcher icon A

## UI changes
- Reworked the chat-list header: title and count on the left, compact circular gradient add action on the right.
- Removed the oversized “Новый чат” pill from the chat-list header.
- Refined search field with a clear action.
- Replaced the text-only archive action with a two-segment “Все чаты / Архив” switch.
- Refined the empty state with a luminous emblem, clearer hierarchy, and a compact create-chat action.
- Reused the new crystal-star mark in the app header.

## Android launcher icon
- Added adaptive launcher icon resources for Android 12+.
- Added matching round adaptive icon.
- Added vector foreground mark and dark navy background.
- Connected `android:icon` and `android:roundIcon` in the manifest.

## Preserved behavior
The existing Room repository calls and chat actions remain connected: create, rename, pin/unpin, archive/restore, delete, open, and send message.

## Verification note
This source package was inspected and edited, but an Android Gradle build has not been run in this environment. Run the GitHub Actions workflow before installing the APK.
