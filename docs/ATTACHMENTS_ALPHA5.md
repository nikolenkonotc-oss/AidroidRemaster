# Chat attachments — 0.2.0-alpha.5

## Included
- Multiple images from the Android Photo Picker.
- Multiple documents through the Storage Access Framework.
- Camera capture through the system camera app and a private FileProvider URI.
- Local private copies under `files/attachments/`; message metadata is stored in Room.
- Existing Room v1 databases are migrated to v2 by adding `messages.attachmentsJson`.
- Attachments can be removed from the pending tray before sending, opened from message history, and removed from storage when their chat is deleted.

## Limits
- Up to 10 attachments per message.
- Up to 25 MB per file and 50 MB total per message.
- No broad media/storage or camera permission is requested.

## Scope
This alpha stores attachments locally alongside the message. It does not yet upload files to an AI provider or run image/document understanding; that will be connected through the provider/agent runtime later.
