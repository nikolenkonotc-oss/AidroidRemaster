package app.aidroid.remaster.data

import android.content.Context

data class AccountProfile(
    val accountType: String = "",
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val status: String = "На связи",
    val email: String = "",
    val avatarUri: String = "",
    val googlePhotoUrl: String = ""
) {
    val isSignedIn: Boolean get() = accountType == "local" || accountType == "google"
}

class AccountProfileStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "aidroid_account_profile",
        Context.MODE_PRIVATE
    )

    fun load(): AccountProfile = AccountProfile(
        accountType = preferences.getString("account_type", "").orEmpty(),
        displayName = preferences.getString("display_name", "").orEmpty(),
        username = preferences.getString("username", "").orEmpty(),
        bio = preferences.getString("bio", "").orEmpty(),
        status = preferences.getString("status", "На связи").orEmpty(),
        email = preferences.getString("email", "").orEmpty(),
        avatarUri = preferences.getString("avatar_uri", "").orEmpty(),
        googlePhotoUrl = preferences.getString("google_photo_url", "").orEmpty()
    )

    fun save(profile: AccountProfile) {
        preferences.edit()
            .putString("account_type", profile.accountType)
            .putString("display_name", profile.displayName)
            .putString("username", profile.username)
            .putString("bio", profile.bio)
            .putString("status", profile.status)
            .putString("email", profile.email)
            .putString("avatar_uri", profile.avatarUri)
            .putString("google_photo_url", profile.googlePhotoUrl)
            .apply()
    }
}
