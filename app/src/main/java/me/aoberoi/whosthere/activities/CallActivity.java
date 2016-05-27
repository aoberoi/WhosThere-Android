package me.aoberoi.whosthere.activities;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import me.aoberoi.whosthere.R;

public class CallActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);
        checkGooglePlayServicesAvailability();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkGooglePlayServicesAvailability();
    }

    protected void checkGooglePlayServicesAvailability() {
        // TODO: perform check, if it fails, call GooglePlayServicesUtil.getErrorDialog()
    }
}
