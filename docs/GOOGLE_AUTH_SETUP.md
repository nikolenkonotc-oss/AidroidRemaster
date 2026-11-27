# Google sign-in and cloud sync setup

Aidroid Remaster uses Android Credential Manager for the Google account chooser and Firebase Authentication + Cloud Firestore for the cloud identity and sync. Local-only profiles remain available.

## 1. Register the Android app in Firebase

1. Create or select a project in the Firebase console.
2. Add an Android app for the debug package ID: `app.aidroid.remaster.debug`.
3. Add the SHA-1 fingerprint for the debug signing key. From the repository root on Windows run `gradlew signingReport`.
4. For a release build, also register `app.aidroid.remaster` and add its release signing fingerprint.
5. In **Authentication → Sign-in method**, enable **Google** and save.
6. Create a **Cloud Firestore** database.
7. Create a **Cloud Storage** bucket if you want custom profile avatars to sync between devices.

## 2. Configure OAuth

1. In Google Cloud / Firebase project settings, create an OAuth client of type **Web application**.
2. Copy its client ID (ending in `.apps.googleusercontent.com`). This is the server client ID used by Credential Manager and Firebase Auth.
3. In Firebase project settings, find the Android app configuration values:
   - `api_key.current_key` → `firebase_api_key`
   - `mobilesdk_app_id` → `firebase_application_id`
   - `project_info.project_id` → `firebase_project_id`
   - `project_info.storage_bucket` → `firebase_storage_bucket`
4. Put these values in `app/src/main/res/values/strings.xml`. Keep the resource names unchanged.

The Firebase API key and OAuth client ID identify the app/project; they are not service-account credentials. Do not put a service-account JSON file, private key, or server secret in the Android app. Restrict the API key to the Android package name and signing certificate where supported.

The project initializes Firebase from these resource values, so `google-services.json` and the Google Services Gradle plugin are not required.

## 3. Apply owner-only security rules

Use these Firestore rules so a signed-in user can only access their own account data:

```text
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

For custom avatars, use these Cloud Storage rules:

```text
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /users/{userId}/profile/{fileName} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

Do not deploy broad public read/write rules.

## 4. What syncs

- Google identity is verified by Firebase Authentication; the ID token is exchanged for a Firebase session and is not stored by Aidroid.
- Profile fields and a custom profile avatar are saved under the signed-in Firebase UID.
- Chat titles, archive/pin state, text messages and deletion markers sync through Firestore.
- Chat/message IDs are stable UUIDs, so two devices can merge the same conversation.
- Room remains the local source for immediate/offline app usage. Firestore's Android SDK queues supported offline changes and synchronizes them when connectivity returns.
- **Attachment files are not uploaded in this iteration.** Existing photo/file/camera attachments stay in the app's private storage on the device where they were added. Message text and chat history still sync.

## 5. Local-only mode

If Firebase or OAuth values are blank, the app continues to build and local-only profiles remain available. The Google sign-in button stays unavailable until both OAuth and Firebase are configured.

For a quick setup check, build with:

```bash
gradlew :app:testDebugUnitTest
gradlew :app:assembleDebug
```
