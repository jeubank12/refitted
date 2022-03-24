package com.litus_animae.refitted.compose

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.SignInButton
import com.litus_animae.refitted.R
import com.litus_animae.refitted.models.UserViewModel
import com.litus_animae.refitted.util.AndroidLogUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun SignInUser(
    userViewModel: UserViewModel = viewModel(),
    signedInContent: @Composable () -> Unit
) {
    val launcher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {
            Log.d(
                "SignInUser",
                "Got activity result: ${GoogleSignInStatusCodes.getStatusCodeString(it.resultCode)}"
            )
            if (it.resultCode == GoogleSignInStatusCodes.SUCCESS ||
                it.resultCode == GoogleSignInStatusCodes.SUCCESS_CACHE
            ) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(it.data)
                Log.v("SignInUser", "Got google account, handling...")
                userViewModel.handleSignInResult(task)
            }
        }
    // TODO how to keep this private and not have the resolution error
    val webClientId = stringResource(R.string.default_web_client_id)
    val googleSignInOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(webClientId)
            .build()
    }
    val context = LocalContext.current

    val isUserLoggedIn by userViewModel.isUserLoggedIn.collectAsState()

    if (isUserLoggedIn) {
        signedInContent()
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val appName = stringResource(id = R.string.app_name)
                        Text(appName)
                    },
                    backgroundColor = MaterialTheme.colors.primary
                )
            }) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AndroidView(factory = ::SignInButton,
                    update = {
                        it.setSize(SignInButton.SIZE_WIDE)
                        it.setOnClickListener {
                            val googleClient = GoogleSignIn.getClient(context, googleSignInOptions)
                            launcher.launch(googleClient.signInIntent)
                        }
                    })
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Preview
@Composable
fun PreviewSignIn() {
    MaterialTheme {
        SignInUser(UserViewModel(AndroidLogUtil)) {

        }
    }
}