# Aidroid Remaster 0.2.0-alpha.6 — adaptive icon and motion polish

## Launcher icon

- Replaced the old vector approximation with the approved faceted crystal mark from concept A.
- Added density-specific foreground layers and legacy launcher PNGs.
- Adaptive launcher resources now use a separate dark gradient background and transparent foreground layer, allowing Android/OEM launcher masks to shape the icon instead of drawing a square inside the icon.
- Added a monochrome adaptive layer for themed icons on supported Android versions.
- The in-app brand mark uses the same transparent foreground artwork.

## Conversation experience

- Room messages are now treated as `null` until the first Flow emission arrives. Existing conversations show a lightweight loading state instead of briefly displaying the “С чего начнём?” new-chat welcome panel.
- Chat-list to conversation transitions use fade, horizontal slide, and subtle scale motion.
- Main navigation transitions use slower, smoother horizontal movement and fade.
- Lazy message items animate their appearance, disappearance, and placement.
- The first history load positions the list directly at the newest message; subsequent new messages animate the scroll to the bottom.
- New-chat welcome content has a soft fade, vertical lift, and scale-in.
- Shared press-scale feedback is used for key custom action surfaces, in addition to the existing animations on navigation, chat rows, cards, and the send button.

## Compatibility notes

- Room entities, attachment metadata, attachment storage, repository methods, chat operations, and the database migration path are unchanged from alpha.5.
- Version code: 6.
- Version name: 0.2.0-alpha.6.
- Full Android Gradle build must be verified by CI before installing the APK.
