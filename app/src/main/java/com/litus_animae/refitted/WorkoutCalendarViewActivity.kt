package com.litus_animae.refitted

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.MaterialTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.litus_animae.refitted.compose.Theme
import com.litus_animae.refitted.compose.Top
import com.litus_animae.refitted.models.WorkoutViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.FlowPreview

@FlowPreview
@AndroidEntryPoint
class WorkoutCalendarViewActivity : AppCompatActivity() {
    private lateinit var googleSignInOptions: GoogleSignInOptions
    private lateinit var mAuth: FirebaseAuth
    private lateinit var launchSignInForResult: ActivityResultLauncher<Intent>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(colors = Theme.darkColors) {
                Top()
            }
        }

        mAuth = FirebaseAuth.getInstance()
        googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(getString(R.string.default_web_client_id))
            .build()

        launchSignInForResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
                if (result.resultCode == GoogleSignInStatusCodes.SUCCESS) {
                    // The Task returned from this call is always completed, no need to attach
                    // a listener.
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    handleSignInResult(task)
                }
            }

        val currentUser = mAuth.currentUser
        if (currentUser != null) {
            updateUI()
        } else {
            val mGoogleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions)
            val silentSignInTask = mGoogleSignInClient.silentSignIn()
            if (silentSignInTask.isSuccessful) {
                handleSignInResult(silentSignInTask)
            } else {
                silentSignInTask.addOnCompleteListener { task -> handleSignInResult(task) }
            }
        }
    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount?) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + acct!!.id)
        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task: Task<AuthResult?> ->
                if (task.isSuccessful && mAuth.currentUser != null) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")
                    updateUI()
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    //Snackbar.make(findViewById(R.id.main_layout), "Authentication Failed.", Snackbar.LENGTH_SHORT).show();
                    //updateUI(null);
                    doFullSignIn()
                }
            }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)

            // Signed in successfully, show authenticated UI.
            firebaseAuthWithGoogle(account)
        } catch (e: ApiException) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w(TAG, "signInResult:failed code=" + e.statusCode)

            // just loop
            doFullSignIn()
        }
    }

    private fun doFullSignIn() {
        val mGoogleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions)
        val signInIntent = mGoogleSignInClient.signInIntent
        launchSignInForResult.launch(signInIntent)
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("RefittedMainPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("lastActivity", javaClass.name)
        editor.apply()
    }

    private fun updateUI() {
        try {
            val prefs = getSharedPreferences("RefittedMainPrefs", Context.MODE_PRIVATE)
            val activityClass = Class.forName(
                prefs.getString("lastActivity", javaClass.name)!!
            )
            val intent = Intent(this, activityClass)
            val workout = prefs.getString("workout", "-1")!!
            val day = prefs.getInt("day", -1)
            intent.putExtra("workout", workout)
            intent.putExtra("day", day)
            if (activityClass != javaClass && day > 0 && workout != "-1") {
                startActivity(intent)
            }
        } catch (ex: ClassNotFoundException) {
            Log.e(TAG, "onCreate: bad class reference in shared preferences", ex)
        } catch (ex: Exception) {
            Log.e(TAG, "onCreate: shared preferences error", ex)
        }
    }

    companion object {
        private const val TAG = "WorkoutCalendarViewActi"
    }
}