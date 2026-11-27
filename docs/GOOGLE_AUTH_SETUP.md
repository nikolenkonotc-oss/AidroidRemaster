# Google sign-in setup

Aidroid Remaster uses Android Credential Manager and the current Sign in with Google flow.

## Configure OAuth

1. Create or select a project in Google Cloud Console and configure the OAuth consent screen.
2. Create an **Android OAuth client** for each application ID you plan to use:
   - Debug: `app.aidroid.remaster.debug`
   - Release: `app.aidroid.remaster`
3. Add the SHA-1 signing certificate fingerprint for each build. From the project root on Windows run `gradlew signingReport`; use the matching debug or release variant.
4. Create an OAuth client of type **Web application**. Copy its client ID (the value ending in `.apps.googleusercontent.com`).
5. Put that Web client ID in `app/src/main/res/values/strings.xml` as the value of `google_web_client_id`.

The Web client ID is an OAuth identifier, not a private key. Never add a service-account JSON file or private secret to the Android app.

## Current scope and security boundary

The Google Credential Manager flow retrieves a Google ID token and basic profile details. Aidroid currently uses the returned name, email and optional photo URL to populate the on-device profile; the ID token is not stored.

This is a **device-local profile**, not yet a cloud account or cross-device identity session. Before enabling cloud sync, shared account data, or server-protected features, add a backend endpoint and verify each ID token on that backend (signature, issuer, audience and expiry). Never trust a client-supplied email or user ID as proof of identity.
