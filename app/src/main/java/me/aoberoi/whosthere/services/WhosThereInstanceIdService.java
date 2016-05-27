package me.aoberoi.whosthere.services;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import me.aoberoi.whosthere.constants.RegistrationConstants;

public class WhosThereInstanceIdService extends FirebaseInstanceIdService {

    private static final String TAG = "WhosThereInstIdService";

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    @Override
    public void onTokenRefresh() {
       String refreshedToken = FirebaseInstanceId.getInstance().getToken();
        Log.d(TAG, "Refreshed token: " + refreshedToken);

        // Store token in preferences. The Activity will lookup the token from
        // the preferences when it is needed.
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.edit().putString(RegistrationConstants.REGISTRATION_TOKEN, refreshedToken).commit();

        // Send LocalBroadcast so that if the Activity is currently in the "Resumed"
        // state, it can be notified of the change.
        Intent intent = new Intent(RegistrationConstants.ACTION_TOKEN_REFRESHED);
        intent.putExtra(RegistrationConstants.EXTRA_ID_TOKEN, refreshedToken);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
