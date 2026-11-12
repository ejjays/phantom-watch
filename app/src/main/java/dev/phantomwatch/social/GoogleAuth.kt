package dev.phantomwatch.social

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dev.phantomwatch.core.AppLog
import kotlinx.coroutines.withTimeoutOrNull

internal object GoogleAuth {
    sealed interface Result {
        data class Token(val idToken: String) : Result
        data object Cancelled : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun signIn(context: Context, serverClientId: String): Result {
        AppLog.d("google auth start")
        return try {
            val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = withTimeoutOrNull(30_000) {
                CredentialManager.create(context).getCredential(context, request).credential
            }
            if (credential == null) {
                AppLog.d("google auth timeout")
                return Result.Failed("timeout")
            }
            val token = if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else {
                ""
            }
            if (token.isEmpty()) Result.Failed("unexpected credential ${credential.type}") else Result.Token(token)
        } catch (e: GetCredentialCancellationException) {
            AppLog.d("google auth cancelled ${e.message}")
            Result.Cancelled
        } catch (e: NoCredentialException) {
            AppLog.d("google auth no credential ${e.message}")
            Result.Failed("no google account")
        } catch (e: GetCredentialException) {
            AppLog.d("google auth ${e.javaClass.simpleName} ${e.message}")
            Result.Failed(e.message ?: e.javaClass.simpleName)
        } catch (e: Exception) {
            AppLog.d("google auth ${e.javaClass.simpleName} ${e.message}")
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}
