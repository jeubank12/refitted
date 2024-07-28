package com.litus_animae.refitted.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.common.SignInButton
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.litus_animae.refitted.R
import kotlinx.coroutines.launch

@Composable
fun SignInButton(
  modifier: Modifier = Modifier,
  handleSuccess: (GetCredentialResponse) -> Unit,
  handleFailure: (GetCredentialException) -> Unit
) {
  val webClientId = stringResource(R.string.default_web_client_id)
  val googleIdOption = GetGoogleIdOption.Builder()
    .setFilterByAuthorizedAccounts(true)
    .setServerClientId(webClientId)
    .setAutoSelectEnabled(true)
//    .setNonce(<nonce string to use when generating a Google ID token>)
    .build()

  val request: GetCredentialRequest = GetCredentialRequest.Builder()
    .addCredentialOption(googleIdOption)
    .build()

  val context = LocalContext.current
  val credentialManager = CredentialManager.create(context)

  val coroutineScope = rememberCoroutineScope()

  AndroidView(factory = ::SignInButton,
    modifier = modifier,
    update = { button ->
      button.setSize(SignInButton.SIZE_WIDE)
      button.setOnClickListener {
        coroutineScope.launch {
          try {
            val result = credentialManager.getCredential(
              request = request,
              context = context,
            )
            handleSuccess(result)
          } catch (e: GetCredentialException) {
            handleFailure(e)
          }
        }
      }
    })
}
