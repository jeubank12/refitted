package com.litus_animae.refitted.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.litus_animae.refitted.R
import kotlinx.coroutines.launch

@Composable
fun SignInButton(
  modifier: Modifier = Modifier,
  handleSuccess: (GetCredentialResponse) -> Unit,
  handleFailure: (GetCredentialException) -> Unit
) {
  val webClientId = stringResource(R.string.default_web_client_id)
  val googleIdOption = GetSignInWithGoogleOption.Builder(webClientId)
//    .setNonce(<nonce string to use when generating a Google ID token>)
    .build()

  val request: GetCredentialRequest = GetCredentialRequest.Builder()
    .addCredentialOption(googleIdOption)
    .build()

  val context = LocalContext.current
  // FIXME this should be remembered or put into the coroutine
  val credentialManager = CredentialManager.create(context)

  val coroutineScope = rememberCoroutineScope()

  Row(
    modifier
      .clip(RoundedCornerShape(20.dp))
      .border(0.0f.dp, Color.Black, RoundedCornerShape(20.dp))
      .background(Color.White)
      .clickable(
        onClickLabel = "Sign in with Google",
        role = Role.Button
      ) {
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
      .padding(end = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      painter = painterResource(id = R.drawable.google_icon_g),
      "Sign in with Google",
      tint = Color.Unspecified
    )
    Text(
      "Sign in with Google",
      fontSize = 14.sp,
      lineHeight = 20.sp,
      fontFamily = FontFamily(Font(R.font.roboto_medium)),
      color = Color(0xFF1F1F1F)
    )
  }
}

@Preview
@Composable
private fun SignInButtonPreview() {
  Row(Modifier.background(Color.LightGray)) {
    SignInButton(handleSuccess = {}) {}
  }
}
