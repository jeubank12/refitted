package com.litus_animae.refitted.ui.compose.calendar

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.credentials.CustomCredential
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.litus_animae.refitted.data.models.WorkoutPlan
import com.litus_animae.refitted.ui.compose.AuthButton
import com.litus_animae.refitted.ui.compose.util.appBarColors
import com.litus_animae.refitted.ui.models.UserViewModel
import com.litus_animae.refitted.ui.models.WorkoutViewModel
import kotlinx.coroutines.launch

/**
 * The List side of the Calendar screen's List-Detail split: the previously-drawer-only
 * [WorkoutPlanMenu] plus the sign-in row, now with its own [TopAppBar]. At Compact width this
 * only appears once explicitly opened, as the sole full-screen pane, so its bar carries the
 * full "Workouts" + last-refreshed + refresh action. At Medium+ it's shown permanently
 * alongside [WorkoutDetailPane], so the bar drops to a single line (title only) to match
 * [WorkoutDetailPane]'s bar height, with the refresh action and last-refreshed text moving
 * into the body instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutPlanListPane(
  modifier: Modifier = Modifier,
  workoutModel: WorkoutViewModel,
  userModel: UserViewModel,
  snackbarHostState: SnackbarHostState,
  showBackButton: Boolean,
  onBack: () -> Unit,
  // True when this pane is shown alongside WorkoutDetailPane (Medium+ width) - the refresh
  // action moves next to "Last Refreshed At" in the body instead of the bar, since it's the
  // only bar actions difference between this pane's TopAppBar and WorkoutDetailPane's.
  wideLayout: Boolean,
  onSelect: (WorkoutPlan) -> Unit,
  onCreateCustom: () -> Unit,
  onRenameRequest: (WorkoutPlan) -> Unit,
) {
  val workoutPlanPagingItems = workoutModel.workouts.collectAsLazyPagingItems()
  val workoutPlanError = workoutModel.workoutError
  LaunchedEffect(workoutPlanError) {
    if (workoutPlanError != null)
      snackbarHostState.showSnackbar(
        workoutPlanError,
        duration = SnackbarDuration.Indefinite
      )
  }
  val userError = userModel.userError
  LaunchedEffect(userError) {
    if (userError != null)
      snackbarHostState.showSnackbar(
        userError,
        duration = SnackbarDuration.Indefinite
      )
  }

  val lastRefresh by workoutModel.workoutsLastRefreshed.collectAsStateWithLifecycle(initialValue = "")

  Scaffold(
    modifier = modifier,
    contentWindowInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout),
    topBar = {
      TopAppBar(
        title = {
          if (wideLayout) {
            // TODO localize
            Text("Workouts")
          } else {
            Column {
              // TODO localize
              Text("Workouts", style = MaterialTheme.typography.titleLarge)
              // TODO localize
              Text(
                "Last Refreshed At: $lastRefresh",
                style = MaterialTheme.typography.labelMedium
              )
            }
          }
        },
        windowInsets = TopAppBarDefaults.windowInsets.union(
          WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
        ),
        colors = appBarColors(),
        navigationIcon = {
          if (showBackButton) {
            IconButton(onBack) {
              Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                // TODO localize
                "back to workouts calendar"
              )
            }
          }
        },
        actions = {
          if (!wideLayout) {
            IconButton(onClick = { workoutPlanPagingItems.refresh() }) {
              // TODO localize
              Icon(Icons.Default.Refresh, "refresh")
            }
          }
        }
      )
    }
  ) { contentPadding ->
    Column(Modifier.padding(contentPadding)) {
      WorkoutPlanMenu(
        Modifier.weight(1f),
        // Already shown in the bar itself when not wideLayout - not repeated in the body.
        lastRefresh = if (wideLayout) lastRefresh else null,
        workoutPlanPagingItems,
        workoutPlanError,
        onRefresh = if (wideLayout) ({ workoutPlanPagingItems.refresh() }) else null,
        onSelect = onSelect,
        onCreateCustom = onCreateCustom,
        onRenameRequest = onRenameRequest
      )
      Row(
        Modifier
          .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
          .padding(start = 10.dp, end = 10.dp, top = 10.dp)
      ) {
        val currentEmail by userModel.userEmail.collectAsStateWithLifecycle(initialValue = null)
        val coroutineScope = rememberCoroutineScope()
        var signInClicked by remember { mutableStateOf(false) }

        LaunchedEffect(currentEmail) {
          if (signInClicked) {
            workoutPlanPagingItems.refresh()
          }
        }

        AuthButton(
          Modifier.fillMaxWidth(),
          handleAuthSuccess = {
            signInClicked = true
            // Handle the successfully returned credential.
            when (val credential = it.credential) {
              // GoogleIdToken credential
              is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                  userModel.handleSignIn(credential.data)
                }
                else {
                  // Catch any unrecognized custom credential type here.
                  Log.e("handleAuthSuccess", "Unexpected type of credential")
                }
              }

              else -> {
                // Catch any unrecognized credential type here.
                Log.e("handleAuthSuccess", "Unexpected type of credential")
              }
            }
          }, handleAuthFailure = {
            coroutineScope.launch {
              it.message?.let { it1 -> snackbarHostState.showSnackbar(it1) }
            }
          },
          handleDeAuth = { userModel.handleSignOut() },
          authedEmail = currentEmail,
          userModel.googleWebClientId,
        )
      }
    }
  }
}
