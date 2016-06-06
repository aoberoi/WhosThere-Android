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
import com.google.firebase.iid.FirebaseInstanceId;

import java.util.HashMap;

import me.aoberoi.whosthere.R;
import me.aoberoi.whosthere.constants.RegistrationConstants;

public class MainActivity extends AppCompatActivity {

    // NOTE: This application is built with the assumption that every user is
    // only using one device. In order to work without that assumption, the
    // database structure would need to be modified and synchronization would
    // need to be bidirectional.

    private static final String TAG = "MainActivity";
    private static final int PERMISSIONS_REQUEST_CAMERA = 100;
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 200;

    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private FirebaseUser mUser;
    private DatabaseReference mCallRequestsRef;
    private DatabaseReference mUsersRef;
    private String mDeviceToken;

    private TextView loggedInStateLabel;
    private Button callButton;
    private EditText recipientContactIdField;


    private BroadcastReceiver mTokenRefreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String refreshedToken = intent.getStringExtra(RegistrationConstants.EXTRA_ID_TOKEN);
            Log.d(TAG, "mTokenRefreshReceiver:onReceive:token:" + refreshedToken);
            setDeviceToken(refreshedToken);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup UI
        setContentView(R.layout.activity_main);
        loggedInStateLabel = (TextView) findViewById(R.id.loggedInStateLabel);
        callButton = (Button) findViewById(R.id.callButton);
        recipientContactIdField = (EditText) findViewById(R.id.recipientContactIdField);

        // Setup Firebase database references
        mCallRequestsRef = FirebaseDatabase.getInstance().getReference("callRequests");
        mUsersRef = FirebaseDatabase.getInstance().getReference("users");

        // Setup data
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String storedDeviceToken = preferences.getString(RegistrationConstants.REGISTRATION_TOKEN, null);
        setDeviceToken(storedDeviceToken);

        // Setup Firebase authentication
        mAuth = FirebaseAuth.getInstance();
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // User is signed in
                    Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid());
                    setUser(user);
                } else {
                    // User is signed out
                    Log.d(TAG, "onAuthStateChanged:signed_out:");
                }
            }
        };

        // Attach listeners
        callButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callButton.setEnabled(false);
                recipientContactIdField.setEnabled(false);

                HashMap<String, Object> callRequest = new HashMap<String, Object>();
                callRequest.put("to", recipientContactIdField.getText().toString().trim());
                callRequest.put("from", mUser.getUid());
                callRequest.put("timestamp", ServerValue.TIMESTAMP);
                mCallRequestsRef.push().setValue(callRequest, new DatabaseReference.CompletionListener() {
                    @Override
                    public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                        if (databaseError != null) {
                            Log.e(TAG, "clickListener:setValue:onCompleteError " + databaseError.getMessage() + " " + databaseError.getDetails());
                            callButton.setEnabled(true);
                            recipientContactIdField.setEnabled(true);
                            recipientContactIdField.selectAll();
                            // TODO: inspect actual error to display a more appropriate message
                            // TODO: localize
                            Toast.makeText(MainActivity.this, "Invalid Contact ID, try again.", Toast.LENGTH_SHORT).show();
                        } else {
                            Log.d(TAG, "clickListener:setValue:onComplete");
                        }
                    }
                });
                // TODO: show an "in progress" UI
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);


        int cameraPermissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA);
        if (cameraPermissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    PERMISSIONS_REQUEST_CAMERA);
        }
        int audioPermissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO);
        if (audioPermissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSIONS_REQUEST_RECORD_AUDIO);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mUser == null) {
            mAuth.signInAnonymously().addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    Log.d(TAG, "signInAnonymously:onComplete:" + task.isSuccessful());

                    // If sign in fails, display a message to the user. If sign in succeeds
                    // the auth state listener will be notified and logic to handle the
                    // signed in user can be handled in the listener.
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "signInAnonymously", task.getException());
                        Toast.makeText(MainActivity.this, "Authentication failed.",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        String deviceToken = FirebaseInstanceId.getInstance().getId();
                        long deviceTokenCreationTime = FirebaseInstanceId.getInstance().getCreationTime();
                    }
                }
            });
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
                mTokenRefreshReceiver, new IntentFilter(RegistrationConstants.ACTION_TOKEN_REFRESHED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mTokenRefreshReceiver);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {
                    Toast.makeText(MainActivity.this, "You denied permission to the camera.", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            case PERMISSIONS_REQUEST_RECORD_AUDIO: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {
                    Toast.makeText(MainActivity.this, "You denied permission to the microphone.", Toast.LENGTH_SHORT).show();
                }
                return;
            }

        }
    }

    private void setUser(FirebaseUser user) {
        if (user != mUser) {
            mUser = user;
            // TODO: use a shorter "contact ID" because these strings can get super long
            //       we would need to save this to the database and therefore this method
            //       would become asynchronous
            if (mUser != null) {
                callButton.setEnabled(true);
                loggedInStateLabel.setText(user.getUid());
                syncRemoteUser();
            } else {
                callButton.setEnabled(false);
                // TODO: store this in a strings resource, so that it isn't
                //       duplicated in the layout resource
                loggedInStateLabel.setText("Not Logged In");
            }
        }
    }

    private void setDeviceToken(String token) {
        if (token != mDeviceToken) {
            mDeviceToken = token;

            if (mDeviceToken != null) {
                syncRemoteUser();
            }
        }
    }

    private void syncRemoteUser() {
        Log.d(TAG, "syncRemoteUser");
        if (mUser != null && mDeviceToken != null) {
            mUsersRef.child(mUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    Log.d(TAG, "syncRemoteUser:readSuccess");
                    String value = (String) dataSnapshot.getValue();
                    if (!mDeviceToken.equals(value)) {
                        mUsersRef.child(mUser.getUid()).setValue(mDeviceToken, new DatabaseReference.CompletionListener() {
                            @Override
                            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                                if (databaseError == null) {
                                    Log.d(TAG, "syncRemoteUser:writeSuccess");
                                } else {
                                    Log.e(TAG, "syncRemoteUser:writeFail " + databaseError.getMessage());
                                }
                            }
                        });
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.d(TAG, "syncRemoteUser:readFail " + databaseError.getMessage());
                }
            });
        }
    }


}
