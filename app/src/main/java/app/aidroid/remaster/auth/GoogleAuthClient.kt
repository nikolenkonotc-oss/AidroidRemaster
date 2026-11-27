package app.aidroid.remaster.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import app.aidroid.remaster.R

data class GoogleAccountData(
    val displayName: String,
    val email: String,
    val photoUrl: String?,
    val idToken: String
)

class GoogleAuthClient(context: Context) {
    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(appContext)

    val isConfigured: Boolean
        get() = appContext.getString(R.string.google_web_client_id).trim()
            .endsWith(".apps.googleusercontent.com")

    suspend fun signIn(activity: Activity): GoogleAccountData {
        val webClientId = appContext.getString(R.string.google_web_client_id).trim()
        require(webClientId.endsWith(".apps.googleusercontent.com")) {
            "Google-вход не настроен. Добавь Web Client ID в strings.xml по инструкции docs/GOOGLE_AUTH_SETUP.md."
        }

        // Show all Google accounts on the device and require an explicit account choice.
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val response = credentialManager.getCredential(
            context = activity,
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
        val email = googleCredential.id.orEmpty()
        val idToken = googleCredential.idToken
        require(idToken.isNotBlank()) { "Google не вернул токен авторизации." }

        return GoogleAccountData(
            displayName = googleCredential.displayName?.takeIf { it.isNotBlank() }
                ?: googleCredential.givenName?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@").ifBlank { "Пользователь" },
            email = email,
            photoUrl = googleCredential.profilePictureUri?.toString(),
            idToken = idToken
        )
    }

    suspend fun signOut() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }
}
