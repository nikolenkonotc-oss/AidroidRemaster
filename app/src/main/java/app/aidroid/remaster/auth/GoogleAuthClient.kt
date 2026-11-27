package app.aidroid.remaster.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
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

        // First try Credential Manager's account bottom sheet, including accounts
        // that have not previously authorized this app.
        val accountOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val accountRequest = GetCredentialRequest.Builder()
            .addCredentialOption(accountOption)
            .build()

        // Some devices do not expose the bottom sheet (for example, when Google
        // sign-in prompts are disabled). The explicit Sign in with Google flow is
        // the supported fallback for that situation.
        val response = try {
            credentialManager.getCredential(
                context = activity,
                request = accountRequest
            )
        } catch (noAccount: NoCredentialException) {
            val buttonOption = GetSignInWithGoogleOption.Builder(webClientId).build()
            val buttonRequest = GetCredentialRequest.Builder()
                .addCredentialOption(buttonOption)
                .build()

            try {
                credentialManager.getCredential(
                    context = activity,
                    request = buttonRequest
                )
            } catch (fallbackError: NoCredentialException) {
                throw IllegalStateException(
                    "Google не предоставил доступный аккаунт. Проверь, что на телефоне добавлен и активен Google-аккаунт, а Google Play services обновлены. Затем повтори вход.",
                    fallbackError
                )
            }
        }

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
