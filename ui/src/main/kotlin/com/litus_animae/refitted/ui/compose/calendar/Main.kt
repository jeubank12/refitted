package com.litus_animae.refitted.ui.compose.calendar

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CustomCredential
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.litus_animae.refitted.data.models.WorkoutPlan
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.compose.AuthButton
import com.litus_animae.refitted.ui.compose.Changelog
import com.litus_animae.refitted.ui.compose.util.LoadingView
import com.litus_animae.refitted.ui.compose.util.appBarColors
import com.litus_animae.refitted.ui.models.UserViewModel
import com.litus_animae.refitted.ui.models.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Calendar(
  modifier: Modifier = Modifier,
  navigateToWorkoutDay: (WorkoutPlan, Int, editing: Boolean) -> Unit,
  workoutModel: WorkoutViewModel = viewModel(),
  userModel: UserViewModel = viewModel()
) {
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val snackbarHostState = remember { SnackbarHostState() }
  val scaffoldScope = rememberCoroutineScope()
  var showCreateCustomDialog by rememberSaveable { mutableStateOf(false) }
  var showCopyDayDialog by rememberSaveable { mutableStateOf(false) }
  var showAddMenu by rememberSaveable { mutableStateOf(false) }
  var editMode by rememberSaveable { mutableStateOf(false) }
  // Rename/delete dialog targets, shared between the calendar overflow menu and the plan-list
  // row's long-press/swipe actions - transient dialog state, not worth surviving process death.
  var renameTarget by remember { mutableStateOf<String?>(null) }
  var renameError by remember { mutableStateOf<String?>(null) }
  var deleteTarget by remember { mutableStateOf<String?>(null) }

  val selectedWorkoutPlan by workoutModel.currentWorkout.collectAsState(
    initial = workoutModel.savedStateLastWorkoutPlan,
    Dispatchers.IO
  )
  // A freshly created custom plan has nothing to "use" yet - land straight in edit mode.
  LaunchedEffect(selectedWorkoutPlan?.workout) {
    if (selectedWorkoutPlan?.isCustom == true && selectedWorkoutPlan?.totalDays == 0) {
      editMode = true
    }
  }
  val savedSelectedPlanLoading = workoutModel.savedStateLoading
  val completedDaysLoading = workoutModel.completedDaysLoading

  val completedDays by workoutModel.completedDays.collectAsState(
    initial = emptyMap(),
    Dispatchers.IO
  )

  val shouldShowChangelog by userModel.shouldShowChangelog()
    .collectAsStateWithLifecycle(initialValue = false)
  if (shouldShowChangelog) {
    Changelog { userModel.setChangelogShown() }
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      // Zero insets: the drawer's own header row already handles the status bar/cutout inset
      // itself (see its windowInsetsPadding below) - ModalDrawerSheet's own default inset
      // reservation just leaves a white gap above it since that reserved strip uses the sheet's
      // surface color, not our header's background.
      ModalDrawerSheet(drawerShape = MaterialTheme.shapes.medium, windowInsets = WindowInsets(0, 0, 0, 0)) {
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
        WorkoutPlanMenu(
          Modifier.weight(1f),
          lastRefresh,
          workoutPlanPagingItems,
          workoutPlanError,
          onSelect = {
            scaffoldScope.launch { drawerState.close() }
            if (it.workout != selectedWorkoutPlan?.workout) {
              editMode = false
              workoutModel.loadWorkoutDaysCompleted(it)
            }
          },
          onCreateCustom = {
            scaffoldScope.launch { drawerState.close() }
            showCreateCustomDialog = true
          },
          onRenameRequest = {
            renameTarget = it.workout
            renameError = null
          }
        )
        Row(
          Modifier
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout))
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
  ) {
    Scaffold(
      // navigationBars alone leaves a side-mounted camera cutout unhandled once rotated to
      // landscape.
      contentWindowInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout),
      modifier = modifier,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        TopAppBar(
          title = {
            val appName = stringResource(id = R.string.app_name)
            if (selectedWorkoutPlan != null) Text(selectedWorkoutPlan!!.workout)
            else Text(appName)
          },
          windowInsets = TopAppBarDefaults.windowInsets.union(
            WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
          ),
          colors = appBarColors(),
          navigationIcon = {
            IconButton(
              {
                scaffoldScope.launch {
                  if (drawerState.isClosed) drawerState.open()
                  else drawerState.close()
                }
              }
            ) {
            Icon(
              Icons.Default.Menu,
              // TODO localize
              "menu"
            )
          }
        },
        actions = {
          if (selectedWorkoutPlan != null) {
            val (expanded, setExpanded) = rememberSaveable { mutableStateOf(false) }
            val (alerted, setAlerted) = rememberSaveable { mutableStateOf(false) }
            IconButton({ setExpanded(!expanded) }) {
              // TODO localize
              Icon(Icons.Default.MoreVert, "workout menu")
            }
            DropdownMenu(
              expanded = expanded,
              onDismissRequest = { setExpanded(false) }) {
              if (selectedWorkoutPlan?.isCustom == true && !editMode) {
                Text(
                  "Edit plan",
                  Modifier
                    .fillMaxWidth()
                    .clickable {
                      editMode = true
                      setExpanded(false)
                    }
                    .padding(start = 5.dp, end = 15.dp)
                    .padding(vertical = 5.dp))
              }
              if (selectedWorkoutPlan?.isCustom == true) {
                // TODO localize
                Text(
                  "Rename plan",
                  Modifier
                    .fillMaxWidth()
                    .clickable {
                      renameTarget = selectedWorkoutPlan!!.workout
                      renameError = null
                      setExpanded(false)
                    }
                    .padding(start = 5.dp, end = 15.dp)
                    .padding(vertical = 5.dp))
                // TODO localize
                Text(
                  "Delete plan",
                  Modifier
                    .fillMaxWidth()
                    .clickable {
                      deleteTarget = selectedWorkoutPlan!!.workout
                      setExpanded(false)
                    }
                    .padding(start = 5.dp, end = 15.dp)
                    .padding(vertical = 5.dp))
              }
              Text(
                "Reset workout",
                Modifier
                  .fillMaxWidth()
                  .clickable {
                    setAlerted(true)
                    setExpanded(false)
                  }
                  .padding(start = 5.dp, end = 15.dp)
                  .padding(vertical = 5.dp))
              val isAdmin by userModel.userIsAdmin.collectAsStateWithLifecycle(initialValue = false)
              if (isAdmin) {
                Text(
                  "Crash",
                  Modifier
                    .fillMaxWidth()
                    .clickable {
                      throw RuntimeException("Test Crash")
                    }
                    .padding(start = 5.dp, end = 15.dp)
                    .padding(vertical = 5.dp))
              }
            }
            if (alerted) {
              AlertDialog(
                onDismissRequest = { setAlerted(false) },
                // TODO localize
                title = { Text("Reset Workout Completion") },
                text = { Text("This will reset your completed days. Are you sure? (This does not remove records of your previous exercise sets") },
                confirmButton = {
                  Button(onClick = {
                    workoutModel.resetWorkoutCompletion(
                      selectedWorkoutPlan!!
                    )
                    setAlerted(false)
                  }) {
                    Text("Yes")
                  }
                },
                dismissButton = {
                  Button(onClick = { setAlerted(false) }) {
                    Text("No")
                  }
                })
            }
          }
        }
      )
    },
    floatingActionButton = {
      if (selectedWorkoutPlan?.isCustom == true && editMode) {
        // FAB + its menu need to share one layout node in this slot - as two loose siblings,
        // Scaffold measured the slot's width from both and threw the FAB's End position off,
        // rendering it on the left.
        Box {
          FloatingActionButton(onClick = { showAddMenu = true }) {
            // TODO localize
            Icon(Icons.Default.Add, "add to plan")
          }
          DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
            Text(
              // TODO localize
              "New day",
              Modifier
                .fillMaxWidth()
                .clickable {
                  workoutModel.addDay(selectedWorkoutPlan!!)
                  showAddMenu = false
                }
                .padding(start = 5.dp, end = 15.dp)
                .padding(vertical = 5.dp))
            if (selectedWorkoutPlan!!.totalDays > 0) {
              Text(
                // TODO localize
                "Copy from…",
                Modifier
                  .fillMaxWidth()
                  .clickable {
                    showAddMenu = false
                    showCopyDayDialog = true
                  }
                  .padding(start = 5.dp, end = 15.dp)
                  .padding(vertical = 5.dp))
            }
            Text(
              // TODO localize
              "Rest day",
              Modifier
                .fillMaxWidth()
                .clickable {
                  workoutModel.addRestDay(selectedWorkoutPlan!!)
                  showAddMenu = false
                }
                .padding(start = 5.dp, end = 15.dp)
                .padding(vertical = 5.dp))
          }
        }
      }
    },
    floatingActionButtonPosition = FabPosition.End
    ) { contentPadding ->
    if (savedSelectedPlanLoading || (selectedWorkoutPlan != null && completedDaysLoading)) {
      Surface(
        Modifier
          .fillMaxSize()
          .padding(contentPadding)
      ) {
        LoadingView()
      }
    } else if (selectedWorkoutPlan == null) {
      // TODO instruction page
      Row(
        Modifier
          .padding(contentPadding)
          .padding(start = 10.dp, top = 10.dp)
          .fillMaxWidth()
      ) {
        Text("Open the menu to pick a workout")
      }
    } else {
      WorkoutCalendar(
        selectedWorkoutPlan!!,
        completedDays,
        contentPadding = contentPadding,
        editMode = editMode,
        onExitEdit = { editMode = false },
        onSaveStartDate = { workoutModel.setStartDate(selectedWorkoutPlan!!, it) },
        onClearDay = { day -> workoutModel.clearDay(selectedWorkoutPlan!!, day) },
        onSetDayRest = { day, isRest -> workoutModel.setDayRest(selectedWorkoutPlan!!, day, isRest) },
        onEditDay = { day ->
          navigateToWorkoutDay(selectedWorkoutPlan!!, day, true)
          workoutModel.setLastViewedDay(selectedWorkoutPlan!!, day)
        }
      ) {
        navigateToWorkoutDay(selectedWorkoutPlan!!, it, false)
        workoutModel.setLastViewedDay(selectedWorkoutPlan!!, it)
      }
    }
    }
  }

  if (showCreateCustomDialog) {
    NewCustomWorkoutDialog(
      onDismissRequest = { showCreateCustomDialog = false },
      onCreate = {
        workoutModel.createCustomWorkout(it)
        showCreateCustomDialog = false
      }
    )
  }

  if (showCopyDayDialog && selectedWorkoutPlan != null) {
    CopyDayDialog(
      totalDays = selectedWorkoutPlan!!.totalDays,
      onDismissRequest = { showCopyDayDialog = false },
      onCopy = { fromDay ->
        workoutModel.copyDay(selectedWorkoutPlan!!, fromDay)
        showCopyDayDialog = false
      }
    )
  }

  renameTarget?.let { target ->
    RenamePlanDialog(
      currentName = target,
      errorMessage = renameError,
      onDismissRequest = {
        renameTarget = null
        renameError = null
      },
      onRename = { newName ->
        renameError = null
        workoutModel.renameCustomWorkout(
          target,
          newName,
          onSuccess = { renameTarget = null },
          onError = { message -> renameError = message }
        )
      },
      onDelete = {
        renameTarget = null
        renameError = null
        deleteTarget = target
      }
    )
  }

  deleteTarget?.let { target ->
    DeletePlanConfirmDialog(
      planName = target,
      onDismissRequest = { deleteTarget = null },
      onConfirm = {
        workoutModel.deleteCustomWorkout(target)
        if (selectedWorkoutPlan?.workout == target) {
          editMode = false
        }
        deleteTarget = null
      }
    )
  }
}
