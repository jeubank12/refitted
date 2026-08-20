package com.litus_animae.refitted.ui.compose.calendar

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

/**
 * The List side of the Calendar screen's List-Detail split: the previously-drawer-only
 * [WorkoutPlanMenu] plus the sign-in row, now with its own [TopAppBar]. At Compact width, or at
 * Medium+ width with enough height to spare, this only appears once explicitly opened, as the
 * sole full-screen pane, so its bar carries the full "Workouts" + last-refreshed + refresh
 * action. At Medium+ width with constrained height (landscape) it's shown permanently alongside
 * [WorkoutDetailPane], so the bar drops to a single line (title only) to match
 * [WorkoutDetailPane]'s bar height, with the refresh action and last-refreshed text moving
 * into the body instead.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
fun WorkoutPlanListPane(
  modifier: Modifier = Modifier,
  workoutModel: WorkoutViewModel,
  userModel: UserViewModel,
  snackbarHostState: SnackbarHostState,
  showBackButton: Boolean,
  onBack: () -> Unit,
  // True when shown alongside WorkoutDetailPane AND height is constrained (landscape) - the
  // refresh action moves next to "Last Refreshed At" in the body instead of the bar, since it's
  // the only bar actions difference between this pane's TopAppBar and WorkoutDetailPane's.
  wideLayout: Boolean,
  selectedWorkoutName: String?,
  onSelect: (WorkoutPlan) -> Unit,
  onCreateCustom: () -> Unit,
  onRenameRequest: (WorkoutPlan) -> Unit,
  /** Whether a display cutout's actual bounds overlap this pane - see calendar/Main.kt, which
   * measures both panes' real bounds against `rememberDisplayCutoutBoundingRects()` rather than
   * assuming a cutout affects whichever pane owns a given screen edge. */
  affectedByCutout: Boolean = true,
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

  // The header is mostly decorative title text next to a real scrollable plan list below it -
  // let it slide away as that list scrolls, reclaiming its height, and reappear on scroll-up.
  val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

  Scaffold(
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    // affectedByCutout (calendar/Main.kt, measured against this pane's actual bounds) is false
    // whenever the cutout is nowhere near this pane - e.g. when both panes show side by side and
    // it's over WorkoutDetailPane instead - so consuming the full cutout here would pad width
    // this pane doesn't need to give up.
    contentWindowInsets = WindowInsets.navigationBars.union(
      if (affectedByCutout) WindowInsets.displayCutout else WindowInsets(0, 0, 0, 0)
    ),
    topBar = {
      TopAppBar(
        scrollBehavior = scrollBehavior,
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
        // Only shrink in wideLayout - the compact/portrait title is two lines and needs the
        // default height.
        expandedHeight = if (wideLayout) 48.dp else TopAppBarDefaults.TopAppBarExpandedHeight,
        windowInsets = TopAppBarDefaults.windowInsets.union(
          if (affectedByCutout) {
            WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
          } else {
            WindowInsets(0, 0, 0, 0)
          }
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
    // The sign-in row floats over the list instead of taking its own row - besides reclaiming
    // a row of height, content now naturally scrolls out from under it, which (together with
    // the fade band below) gives the list a visible scrollability hint it otherwise lacked.
    // AuthButton's compact pill fits in one line, but its full mode stacks a "Signed in as"
    // caption above the pill/CTA row - needs more room, or that stack gets crushed into
    // whatever height a single-line bar reserved.
    val authBarHeight = if (wideLayout) 56.dp else 88.dp
    // Bottom to top: solidHeight is fully opaque background (guarantees the floating button
    // never sits on visible list text), fadeHeight transitions back to transparent, then
    // transparentHeight passes content through as-is - same banding PagerInstruction's pinned
    // set counter uses over its scrolling text.
    val transparentHeight = 8.dp
    val fadeHeight = 40.dp
    val solidHeight = authBarHeight - fadeHeight
    val totalFadeHeight = transparentHeight + fadeHeight + solidHeight
    val fadeStartFraction = transparentHeight / totalFadeHeight
    val fadeEndFraction = (transparentHeight + fadeHeight) / totalFadeHeight
    val paneBackground = MaterialTheme.colorScheme.background

    Box(Modifier.padding(contentPadding).fillMaxSize()) {
      WorkoutPlanMenu(
        Modifier.fillMaxSize(),
        // Already shown in the bar itself when not wideLayout - not repeated in the body.
        lastRefresh = if (wideLayout) lastRefresh else null,
        workoutPlanPagingItems,
        workoutPlanError,
        selectedWorkoutName = selectedWorkoutName,
        onRefresh = if (wideLayout) ({ workoutPlanPagingItems.refresh() }) else null,
        onSelect = onSelect,
        onCreateCustom = onCreateCustom,
        onRenameRequest = onRenameRequest,
        // So the last row can scroll clear of the floating sign-in button below.
        contentPadding = PaddingValues(bottom = authBarHeight)
      )

      Box(
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .height(totalFadeHeight)
          .background(
            Brush.verticalGradient(
              0f to paneBackground.copy(alpha = 0f),
              fadeStartFraction to paneBackground.copy(alpha = 0f),
              fadeEndFraction to paneBackground,
              1f to paneBackground
            )
          )
      )

      Row(
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          // min, not exact - a tight height would clamp full-mode's caption+pill stack down to
          // whatever compact's single-line pill needed, crushing/clipping it instead of just
          // reserving less fade/padding than the content actually uses.
          .heightIn(min = authBarHeight)
          .windowInsetsPadding(
            if (affectedByCutout) {
              WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
            } else {
              WindowInsets(0, 0, 0, 0)
            }
          )
          .padding(start = 10.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
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
          compact = wideLayout,
        )
      }
    }
  }
}
