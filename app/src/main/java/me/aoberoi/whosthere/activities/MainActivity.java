package me.aoberoi.whosthere.activities;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import me.aoberoi.whosthere.BuildConfig;
import me.aoberoi.whosthere.R;
import me.aoberoi.whosthere.constants.CallConstants;
import me.aoberoi.whosthere.constants.RegistrationConstants;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    /*
     * ------------------------------------------------------------------------
     * Activity Lifecycle
     * ------------------------------------------------------------------------
     */

    // TODO: onSaveInstanceState and onRestoreInstanceState to save "model" objects from the instance like user and token

    private SharedPreferences mPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up user interface
        setContentView(R.layout.activity_main);
        loggedInStateLabel = (TextView) findViewById(R.id.loggedInStateLabel);
        callButton = (Button) findViewById(R.id.callButton);
        recipientContactIdField = (EditText) findViewById(R.id.recipientContactIdField);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        requestSystemPermissions();

        // Kick off user authentication
        mAuth.addAuthStateListener(mAuthListener);
        if (mUser == null) {
            mAuth.signInAnonymously().addOnCompleteListener(this, mSignInCompleteListener);
        }

        // If a device token was stored by the WhosThereInstanceIdService when
        // this activity was not in the "resumed" state, then this activity
        // needs to load the stored device token from the mPreferences
        loadDeviceTokenFromPreferences();

        // Register to receive device token updates
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mTokenRefreshReceiver, new IntentFilter(RegistrationConstants.ACTION_TOKEN_REFRESHED));

        // When resuming from an ended call, make sure the call request in progress is cleared
        loadCallRequestState();
        mPreferences.registerOnSharedPreferenceChangeListener(this.mPreferenceChangedListener);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Stop listener from receiving user authentication updates while this
        // activity is paused.
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }

        // Unregister to receive device token updates while this activity is
        // paused. Any token changes will be persisted to the mPreferences.
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mTokenRefreshReceiver);

        // Stop listening for preference changes on that clear the call request in progress
        mPreferences.unregisterOnSharedPreferenceChangeListener(this.mPreferenceChangedListener);
    }


    /*
     * ------------------------------------------------------------------------
     * Call Requests
     * ------------------------------------------------------------------------
     */

    private DatabaseReference mCallRequestsRef = FirebaseDatabase.getInstance().getReference("callRequests");
    private String mCallRequestRecipient = "";
    private String mCallRequestInProgress = null;
    private Exception mCallRequestRecentFailure = null;
    private boolean mHasCallRequestReceivedResponse = false;

    public void createCallRequest(View callButton) {
        HashMap<String, Object> callRequest = new HashMap<String, Object>();
        mCallRequestRecipient = recipientContactIdField.getText().toString().trim();
        callRequest.put("to", mCallRequestRecipient);
        callRequest.put("from", mUser.getUid());
        callRequest.put("timestamp", ServerValue.TIMESTAMP);

        DatabaseReference newCallRequest = mCallRequestsRef.push();
        setCallRequestInProgress(newCallRequest.getKey());

        newCallRequest.setValue(callRequest, mCallRequestCreatedListener);

        updateInterface();
    }

    private DatabaseReference.CompletionListener mCallRequestCreatedListener = new DatabaseReference.CompletionListener() {
        @Override
        public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
            // There should never be more than one call request in progress, so a callback related to a different
            // call request than the one in progress is violated invariant
            if (BuildConfig.DEBUG && !(Objects.equals(databaseReference.getKey(), mCallRequestInProgress))) {
                throw new AssertionError(databaseReference.getKey());
            }

            if (databaseError != null) {
                Log.e(TAG, "Failed to create call request: " + databaseError.getMessage() + " " + databaseError.getDetails());
                mCallRequestRecentFailure = databaseError.toException();
                setCallRequestInProgress(null);
            } else {
                Log.d(TAG, "Created call request: " + databaseReference.getKey());
            }

            updateInterface();
        }
    };

    private SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangedListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            loadCallRequestState();
        }
    };

    private void setCallRequestInProgress(String callRequestId) {
        if (!Objects.equals(mCallRequestInProgress, callRequestId)) {

            Set<String> callRequestsWaitedOn = mPreferences.getStringSet(CallConstants.CALL_REQUESTS_WAITED_ON_BY_ACTIVITIES, null);

            if (callRequestId == null) {
                // Delete the callRequestId from the set, if it is present
                if (callRequestsWaitedOn != null) {
                    callRequestsWaitedOn.remove(mCallRequestInProgress);
                    mPreferences.edit()
                            .putStringSet(CallConstants.CALL_REQUESTS_WAITED_ON_BY_ACTIVITIES, callRequestsWaitedOn)
                            .apply();
                }
            } else {
                // Add the callRequestId to the set, if it is not present
                Set<String> combined;
                if (callRequestsWaitedOn == null) {
                    callRequestsWaitedOn = new HashSet<String>();
                }
                callRequestsWaitedOn.add(callRequestId);
                mPreferences.edit()
                        .putStringSet(CallConstants.CALL_REQUESTS_WAITED_ON_BY_ACTIVITIES, callRequestsWaitedOn)
                        .apply();
            }

            mCallRequestInProgress = callRequestId;
        }
    }

    private void loadCallRequestState() {
        Set<String> callRequestsWaitedOn = mPreferences.getStringSet(CallConstants.CALL_REQUESTS_WAITED_ON_BY_ACTIVITIES, null);

        if (mCallRequestInProgress != null &&
                callRequestsWaitedOn != null &&
                !callRequestsWaitedOn.contains(mCallRequestInProgress)) {
            setCallRequestInProgress(null);
            mCallRequestRecipient = "";
            updateInterface();
        }
    }

    /*
     * ------------------------------------------------------------------------
     * User Interface
     * ------------------------------------------------------------------------
     */

    private TextView loggedInStateLabel;
    private Button callButton;
    private EditText recipientContactIdField;

    private void updateInterface() {

        // Determine if the call form should be enabled or not
        if (mUser != null &&
                mCallRequestInProgress == null &&
                !mHasUserAndTokenSyncFailed &&
                !mHasUserAuthenticationFailed &&
                mDeniedPermissions.size() == 0) {

            // Enable the call form
            callButton.setEnabled(true);
            recipientContactIdField.setEnabled(true);
            if (!mCallRequestRecipient.equals(recipientContactIdField.getText())) {
                recipientContactIdField.setText(mCallRequestRecipient);
            }

            // Populate UI related to user identity
            loggedInStateLabel.setText(mUser.getUid());

            // Clear stale state related to call requests that are no longer in progress
            // TODO: hide UI that a call request is in progress
            if (mHasCallRequestReceivedResponse) {
                recipientContactIdField.setText("");
                mHasCallRequestReceivedResponse = false;
            }

            // Display failures related to call request that recently was in progress
            if (mCallRequestRecentFailure != null) {
                recipientContactIdField.selectAll();
                // TODO: inspect actual error to display a more appropriate message, localize
                Toast.makeText(MainActivity.this, "Invalid Contact ID, try again.", Toast.LENGTH_SHORT).show();
                mCallRequestRecentFailure = null;
            }

            // TODO: hide UI to re-request permissions
            // TODO: hide UI for user and token sync failure
            // TODO: hide UI for user authentication failure

        } else {

            // Disable the call form
            callButton.setEnabled(false);

            // Populate UI related to user identity
            if (mUser == null) {
                if (mHasUserAuthenticationFailed) {
                    // TODO: localize
                    // TODO: more helpful error conditions, retry?
                    loggedInStateLabel.setText("User Authentication Failure");
                } else {
                    // TODO: store this in a strings resource, localize
                    loggedInStateLabel.setText("Not Logged In");
                }
            }

            // Display state related to an in progress call request
            if (mCallRequestInProgress != null) {
                recipientContactIdField.setEnabled(false);
                // TODO: show UI that a call request is in progress
            }

            // Display failures related to system permissions
            if (mDeniedPermissions.size() > 0) {
                // TODO: localize
                String errorMessage;
                if (mDeniedPermissions.contains(Manifest.permission.RECORD_AUDIO) && mDeniedPermissions.contains(Manifest.permission.CAMERA)) {
                    // User denied both permissions
                    errorMessage = "Permissions for the camera and microphone are necessary for calls. ";
                } else if (mDeniedPermissions.contains(Manifest.permission.RECORD_AUDIO)) {
                    // User denied only RECORD_AUDIO
                    errorMessage = "Permission for the microphone is necessary for calls. ";
                } else {
                    // User denied only CAMERA
                    errorMessage = "Permission for the camera is necessary for calls. ";
                }
                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }

            // Display failures related to device registration
            if (mHasUserAndTokenSyncFailed) {
                // TODO: show UI for user and token sync failure
            }
        }
    }


    /*
     * ------------------------------------------------------------------------
     * User Authentication
     * ------------------------------------------------------------------------
     */

    private FirebaseUser mUser;
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private boolean mHasUserAuthenticationFailed = false;

    private OnCompleteListener<AuthResult> mSignInCompleteListener = new OnCompleteListener<AuthResult>() {
        @Override
        public void onComplete(@NonNull Task<AuthResult> task) {
            Log.d(TAG, "Sign in completed.");
            if (!task.isSuccessful()) {
                Log.w(TAG, "Sign in unsuccessful: ", task.getException());
                mHasUserAuthenticationFailed = true;
            } else {
                Log.d(TAG, "Sign in successful.");
                mHasUserAuthenticationFailed = false;
            }
            updateInterface();
        }
    };

    private FirebaseAuth.AuthStateListener mAuthListener = new FirebaseAuth.AuthStateListener() {
        @Override
        public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
                Log.d(TAG, "User signed in: " + user.getUid());
                setUser(user);
            } else {
                Log.d(TAG, "User signed out.");
                setUser(null);
            }
        }
    };

    private void setUser(FirebaseUser user) {
        if (user != mUser) {
            mUser = user;
            syncUserAndToken();
            updateInterface();
        }
    }

    /*
     * ------------------------------------------------------------------------
     * Device Registration
     * ------------------------------------------------------------------------
     */

    // NOTE: This application is built with the assumption that every user is
    // only using one device. In order to work without that assumption, the
    // database structure would need to be modified and synchronization would
    // need to be bidirectional.

    private boolean mHasUserAndTokenSyncFailed = false;
    private DatabaseReference mUsersRef = FirebaseDatabase.getInstance().getReference("users");
    private String mDeviceToken;

    private BroadcastReceiver mTokenRefreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String refreshedToken = intent.getStringExtra(RegistrationConstants.EXTRA_ID_TOKEN);
            Log.d(TAG, "Device token refreshed:" + refreshedToken);
            setDeviceToken(refreshedToken);
        }
    };

    private void loadDeviceTokenFromPreferences() {
        String storedDeviceToken = mPreferences.getString(RegistrationConstants.REGISTRATION_TOKEN, null);
        setDeviceToken(storedDeviceToken);
    }

    private void setDeviceToken(String token) {
        if (!Objects.equals(token, mDeviceToken)) {
            mDeviceToken = token;
            syncUserAndToken();
        }
    }

    private void syncUserAndToken() {
        if (mUser != null && mDeviceToken != null) {
            Log.d(TAG, "User and token present.");

            // Read any existing device token for this user
            mUsersRef.child(mUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    Log.d(TAG, "Remote user data read.");
                    String existingToken = (String) dataSnapshot.getValue();

                    // If the read remote token is not equal to the local token...
                    if (!mDeviceToken.equals(existingToken)) {
                        Log.d(TAG, "User and device token mismatch. Device token will be set.");

                        // Set the remote token to the local token
                        mUsersRef.child(mUser.getUid()).setValue(mDeviceToken, new DatabaseReference.CompletionListener() {
                            @Override
                            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                                if (databaseError == null) {
                                    Log.d(TAG, "Device token set for user.");
                                } else {
                                    Log.e(TAG, "Device token failed to be set for user. Error: " + databaseError.getMessage());
                                }
                                mHasUserAndTokenSyncFailed = (databaseError != null);
                                updateInterface();
                            }
                        });
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.d(TAG, "Remote user data failed to be read. Error: " + databaseError.getMessage());
                    mHasUserAndTokenSyncFailed = true;
                    updateInterface();
                }
            });
        }
    }


    /*
     * ------------------------------------------------------------------------
     * System Permissions
     * ------------------------------------------------------------------------
     */

    // TODO: extract this into a helper that can be used in both activities
    //       this is not strictly necessary since the user/token sync will not
    //       take place until this activity is launched. so a first time user
    //       will already need to interact with this activity and grant
    //       grant permissions. it would be useful for situations where the
    //       permission was revoked after already being granted once.

    private static final int PERMISSIONS_REQUEST_CAMERA_AND_RECORD_AUDIO = 100;
    private ArrayList<String> mDeniedPermissions = new ArrayList<>();

    private void requestSystemPermissions() {

        // Create a list of permissions that are not already granted
        ArrayList<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }

        // Request the permissions that are not already granted
        if (permissionsNeeded.size() != 0) {
            String[] permissionsToRequest = permissionsNeeded.toArray(new String[permissionsNeeded.size()]);

            // NOTE: even when some permissions are denied, since the system dialog
            // partially covers the activity, the activity's onResume() lifecycle
            // method will be called, and then the remaining permissions will be
            // requested. This effectively blocks the UI of the whole app until
            // permissions are granted.
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest,
                    PERMISSIONS_REQUEST_CAMERA_AND_RECORD_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CAMERA_AND_RECORD_AUDIO: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0) {

                    // Create a list of permissions that were denied
                    ArrayList<String> deniedPermissions = new ArrayList<>();
                    for (int i = 0; i < permissions.length; i++) {
                        if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                            deniedPermissions.add(permissions[i]);
                        }
                    }
                    mDeniedPermissions = deniedPermissions;
                    updateInterface();
                }
            }
            default:
                if (BuildConfig.DEBUG) {
                    throw new AssertionError("Request code enumeration not handled: " + requestCode);
                }
        }
    }
}
