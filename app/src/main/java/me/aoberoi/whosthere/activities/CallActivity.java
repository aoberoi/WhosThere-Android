package me.aoberoi.whosthere.activities;

import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Observable;
import java.util.Observer;

import me.aoberoi.whosthere.R;

import me.aoberoi.whosthere.models.Call;

// TODO: backgrounding support
// TODO: "back" button ends the call
// TODO: ringtone sound only for recipient and a "ring back" sound on the sender
// TODO: use MediaPlayer and AudioAttributes
// TODO: query system for mute/ringer/vibrate settings and mimic them.

// The Call activity should be used in a standalone task (not part of the application's
// back stack). It does not support backgrounding, and therefore does not implement
// the onSaveInstance() callback nor restore any previous state in onCreate(). When
// this activity transitions to being stopped, it destroys all of its instance data
// and gracefully disconnects the stateful connections it carries. (TODO)
public class CallActivity extends AppCompatActivity implements Observer {

    private static final String TAG = "CallActivity";

    /*
     * ------------------------------------------------------------------------
     * Activity Lifecycle
     * ------------------------------------------------------------------------
     */

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up user interface
        setContentView(R.layout.activity_call);
        mStatusTextView = (TextView) findViewById(R.id.statusTextView);
        mPublisherContainer = (FrameLayout) findViewById(R.id.publisherContainer);
        mSubscriberContainer = (FrameLayout) findViewById(R.id.subscriberContainer);
        mEndCallButton = (Button) findViewById(R.id.endCallButton);
        mIncomingActionsBar = (LinearLayout) findViewById(R.id.incomingActionsBar);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        // window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        mRingtone = RingtoneManager.getRingtone(getApplicationContext(), RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));

        mCall = Call.fromIntent(this, getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();

        mCall.begin();
        mCall.addObserver(this);

        updateInterface();
    }

    @Override
    protected void onStop() {
        super.onStop();

        mCall.deleteObserver(this);
    }

    /*
     * ------------------------------------------------------------------------
     * Call
     * ------------------------------------------------------------------------
     */

    private Call mCall;

    public void endCall(View view) {
        Log.d(TAG, "End call clicked");
        mCall.end();
    }

    public void acceptCall(View view) {
        Log.d(TAG, "Accept call clicked");
        mCall.setAccepted(true);

    }

    public void declineCall(View view) {
        Log.d(TAG, "Decline call clicked");
        mCall.setAccepted(false);
    }

    /*
     * ------------------------------------------------------------------------
     * User Interface
     * ------------------------------------------------------------------------
     */

    private TextView mStatusTextView;
    private FrameLayout mPublisherContainer;
    private FrameLayout mSubscriberContainer;
    private Button mEndCallButton;
    private LinearLayout mIncomingActionsBar;

    private Ringtone mRingtone;

    private boolean mDismissalScheduled = false;

    private void updateInterface() {
        if (mCall.isEnding()) {
            mStatusTextView.setText("Call Ended");
            mStatusTextView.setVisibility(View.VISIBLE);

            mEndCallButton.setVisibility(View.GONE);
            mIncomingActionsBar.setVisibility(View.GONE);

            if (mRingtone.isPlaying()) {
                mRingtone.stop();
                Log.d(TAG, "Ringtone stopped");
            }

            if (!mDismissalScheduled) {
                Handler automaticDismissHandler = new Handler();
                automaticDismissHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // TODO: how to find out if activity is "started" when this is run?
                        // will activity be destroyed when someone hits the back button before the timeout?
                        // try out receiving another call this way
                        CallActivity.this.finish();
                    }
                }, 10000);
                mDismissalScheduled = true;
                Log.d(TAG, "Automatic dismissal scheduled");
            }
        } else {

            if (mCall.getSubscriberView() == null) {
                if (mCall.isOutgoing()) {
                    mStatusTextView.setText("Calling " + mCall.getRecipientId());
                } else {
                    mStatusTextView.setText("Incoming call from " + mCall.getSenderId());
                }
                mStatusTextView.setVisibility(View.VISIBLE);
            } else {
                mStatusTextView.setVisibility(View.GONE);
            }


            if (mCall.isOutgoing() || mCall.isAccepted()) {
                mEndCallButton.setVisibility(View.VISIBLE);
                mIncomingActionsBar.setVisibility(View.GONE);
            } else {
                mEndCallButton.setVisibility(View.GONE);
                mIncomingActionsBar.setVisibility(View.VISIBLE);
            }

            if (!mCall.isAccepted()) {
                if (!mRingtone.isPlaying()) {
                    mRingtone.play();
                    Log.d(TAG, "Ringtone played");
                }
            } else {
                if (mRingtone.isPlaying()) {
                    mRingtone.stop();
                    Log.d(TAG, "Ringtone stopped");
                }
            }
        }

        SurfaceView subscriberView = mCall.getSubscriberView();
        if (subscriberView != null) {
            // Avoid re-rendering if the view is already in the container
            if (mSubscriberContainer.getChildCount() == 0) {
                mSubscriberContainer.addView(subscriberView);
                Log.d(TAG, "Subscriber view added");
            }
        } else {
            mSubscriberContainer.removeAllViews();
        }

        SurfaceView publisherView = mCall.getPublisherView();
        if (publisherView != null) {
            // Avoid re-rendering if the view is already in the container
            if (mPublisherContainer.getChildCount() == 0) {
                mPublisherContainer.addView(publisherView);
                mPublisherContainer.setVisibility(View.VISIBLE);
                Log.d(TAG, "Publisher view added");
            }
            publisherView.setZOrderOnTop(true);
        } else {
            mPublisherContainer.removeAllViews();
            mPublisherContainer.setVisibility(View.GONE);
        }
    }

    /*
     * ------------------------------------------------------------------------
     * Observer
     * ------------------------------------------------------------------------
     */

    public void update(Observable o, Object arg) {
        if (o == mCall) {
            updateInterface();
        }
    }
}
