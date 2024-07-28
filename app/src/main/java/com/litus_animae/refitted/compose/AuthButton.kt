package com.litus_animae.refitted.compose

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
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
fun AuthButton(
  modifier: Modifier = Modifier,
  handleAuthSuccess: (GetCredentialResponse) -> Unit,
  handleAuthFailure: (GetCredentialException) -> Unit,
  handleDeAuth: () -> Unit,
  authedEmail: String?
) {
  val webClientId = stringResource(R.string.default_web_client_id)
  val request = remember(webClientId) {
    val googleIdOption = GetSignInWithGoogleOption.Builder(webClientId)
//    .setNonce(<nonce string to use when generating a Google ID token>)
      .build()

    GetCredentialRequest.Builder()
      .addCredentialOption(googleIdOption)
      .build()
  }

  val context: Context = LocalContext.current
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val coroutineScope = rememberCoroutineScope()
  val actionDescription = remember(authedEmail) {
    if (authedEmail == null) {
      "Sign in with Google"
    } else {
      "Sign out"
    }
  }

  Column(
    modifier,
    horizontalAlignment = Alignment.End
  ) {
    AnimatedContent(authedEmail, label = "SignedInAs") {
      if (it != null) {
        Text("Signed in as $authedEmail", Modifier.padding(bottom = 10.dp))
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      AnimatedContent(authedEmail, label = "AuthCTA") {
        if (it == null) {
          Text("Sign in for more workouts", Modifier.padding(end = 10.dp))
        }
      }

      Row(
        Modifier
          .clip(RoundedCornerShape(20.dp))
          .border(0.0f.dp, Color.Black, RoundedCornerShape(20.dp))
          .background(Color.White)
          .clickable(
            onClickLabel = actionDescription,
            role = Role.Button
          ) {
            if (authedEmail == null) {
              coroutineScope.launch {
                try {
                  val result = credentialManager.getCredential(
                    request = request,
                    context = context,
                  )
                  handleAuthSuccess(result)
                } catch (e: GetCredentialException) {
                  handleAuthFailure(e)
                }
              }
            } else {
              handleDeAuth()
            }
          }
          .padding(end = 12.dp)
          .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          painter = painterResource(id = R.drawable.google_icon_g),
          "Sign in with Google",
          tint = Color.Unspecified
        )
        val style = remember {
          TextStyle(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontFamily = FontFamily(Font(R.font.roboto_medium)),
            color = Color(0xFF1F1F1F)
          )
        }
        AnimatedContent(authedEmail, label = "AuthActionText") {
          if (it == null) {
            Text("Sign in with Google", style = style)
          } else {
            Text("Sign Out", style = style)
          }
        }
      }
    }
  }
}

@Preview
@Composable
private fun SignInButtonPreview() {
  Row(Modifier.background(Color.LightGray)) {
    AuthButton(
      handleAuthSuccess = {},
      handleAuthFailure = {},
      handleDeAuth = {},
      authedEmail = null
    )
  }
}

@Preview
@Composable
private fun SignOutButtonPreview() {
  Row(Modifier.background(Color.LightGray)) {
    AuthButton(
      handleAuthSuccess = {},
      handleAuthFailure = {},
      handleDeAuth = {},
      authedEmail = "x"
    )
  }
}
