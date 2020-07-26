package com.litus_animae.refitted;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.litus_animae.refitted.models.ExerciseViewModel;

import java.lang.ref.WeakReference;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class WorkoutCalendarViewActivity extends AppCompatActivity {

    private static final String TAG = "WorkoutCalendarViewActi";
    private GoogleSignInOptions googleSignInOptions;
    private FirebaseAuth mAuth;
    private ExerciseViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ExerciseViewModel.class);

        setContentView(R.layout.activity_workout_calendar_view);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ((CollapsingToolbarLayout) findViewById(R.id.toolbar_layout))
                .setTitle("Athlean-X");

        Switch planSwitch = findViewById(R.id.switch1);
        planSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            SharedPreferences prefs = getSharedPreferences("RefittedMainPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            if (isChecked) {
                editor.putString("workout", "Inferno Size");
            } else {
                editor.putString("workout", "AX1");
            }
            editor.apply();
        });

        SharedPreferences prefs = getSharedPreferences("RefittedMainPrefs", MODE_PRIVATE);
        planSwitch.setChecked(!prefs.getString("workout", "AX1").equals("AX1"));

        RecyclerView list = findViewById(R.id.calendar_recycler);
        list.setAdapter(new CalendarAdapter(84, new WeakReference<>(planSwitch)));
        list.setLayoutManager(new LinearLayoutManager(this));

        mAuth = FirebaseAuth.getInstance();
        googleSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .requestIdToken(getString(R.string.default_web_client_id))
                .build();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            updateUI(currentUser);
        } else {
            GoogleSignInClient mGoogleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions);

            Task<GoogleSignInAccount> silentSignInTask = mGoogleSignInClient.silentSignIn();
            if (silentSignInTask.isSuccessful()) {
                handleSignInResult(silentSignInTask);
            } else {
                silentSignInTask.addOnCompleteListener(new OnCompleteListener<GoogleSignInAccount>() {
                    @Override
                    public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
                        handleSignInResult(task);
                    }
                });
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + acct.getId());

        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d(TAG, "signInWithCredential:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        updateUI(user);
                    } else {
                        // If sign in fails, display a message to the user.
                        Log.w(TAG, "signInWithCredential:failure", task.getException());
                        //Snackbar.make(findViewById(R.id.main_layout), "Authentication Failed.", Snackbar.LENGTH_SHORT).show();
                        //updateUI(null);
                        doFullSignIn();
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == GoogleSignInStatusCodes.SUCCESS) {
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);

            // Signed in successfully, show authenticated UI.
            firebaseAuthWithGoogle(account);
        } catch (ApiException e) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());

            // just loop
            doFullSignIn();
        }
    }

    private void doFullSignIn() {
        GoogleSignInClient mGoogleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions);
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, GoogleSignInStatusCodes.SUCCESS);
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences prefs = getSharedPreferences("RefittedMainPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("lastActivity", getClass().getName());
        editor.apply();
    }

    private void updateUI(FirebaseUser account) {
        try {
            SharedPreferences prefs = getSharedPreferences("RefittedMainPrefs", MODE_PRIVATE);
            Class<?> activityClass = Class.forName(
                    prefs.getString("lastActivity", getClass().getName()));

            Intent intent = new Intent(this, activityClass);
            String workout = prefs.getString("workout", "-1");
            int day = prefs.getInt("day", -1);
            intent.putExtra("workout", workout);
            intent.putExtra("day", day);
            if (activityClass != getClass() && day > 0 && !workout.equals("-1")) {
                startActivity(intent);
            }
        } catch (ClassNotFoundException ex) {
            Log.e(TAG, "onCreate: bad class reference in shared preferences", ex);
        } catch (Exception ex) {
            Log.e(TAG, "onCreate: shared preferences error", ex);
        }
    }
}
