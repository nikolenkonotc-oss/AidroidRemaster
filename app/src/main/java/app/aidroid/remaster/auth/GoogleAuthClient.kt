package app.aidroid.remaster.auth

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import app.aidroid.remaster.R
import java.security.SecureRandom

data class GoogleAccountData(
    val displayName: String,
    val email: String,
    val photoUrl: String?
)

class GoogleAuthClient(context: Context) {
    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(appContext)

    suspend fun signIn(activity: Activity): GoogleAccountData {
        val webClientId = appContext.getString(R.string.google_web_client_id).trim()
        require(webClientId.endsWith(".apps.googleusercontent.com")) {
            "Google-вход ещё не настроен. Добавь Web Client ID в strings.xml по инструкции docs/GOOGLE_AUTH_SETUP.md."
        }

        val option = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(createSecureNonce())
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val response = credentialManager.getCredential(
            context = MutableContextWrapper(activity),
            request = request
        )
        val credential = response.credential
        require(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            "Google вернул неподдерживаемый тип учётных данных."
        }

        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        return GoogleAccountData(
            displayName = googleCredential.displayName?.takeIf { it.isNotBlank() }
                ?: googleCredential.givenName?.takeIf { it.isNotBlank() }
                ?: googleCredential.email.substringBefore("@"),
            email = googleCredential.email,
            photoUrl = googleCredential.profilePictureUri?.toString()
        )
    }

    suspend fun signOut() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }

    private fun createSecureNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }
}
