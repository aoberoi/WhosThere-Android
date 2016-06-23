package me.aoberoi.whosthere.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
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

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.OpentokError;
import com.opentok.android.Publisher;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

import me.aoberoi.whosthere.R;
import me.aoberoi.whosthere.constants.CallConstants;

// TODO: backgrounding support
// TODO: "back" button ends the call

// The Call activity should be used in a standalone task (not part of the application's
// back stack). It does not support backgrounding, and therefore does not implement
// the onSaveInstance() callback nor restore any previous state in onCreate(). When
// this activity transitions to being stopped, it destroys all of its instance data
// and gracefully disconnects the stateful connections it carries. (TODO)
public class CallActivity extends AppCompatActivity implements Session.SessionListener {

    private static final String TAG = "CallActivity";

    private String mCallId;

    // TODO: define a data type that holds call data
    //       possibly turn it into a full model with behavior as well
    private String mCallKey;
    private String mCallSessionId;
    private String mCallToken;
    private boolean mIsSender = false;
    private String mSenderUserId;
    private String mRecipientUserId;
    private Long mInitiatedAt;
    private Long mEndedAt;
    private String mRequestId;

    private Ringtone mRingtone;

    private DatabaseReference mCallEndedAtRef;

    private Session mCallSession;
    private Publisher mPublisher;
    private Subscriber mSubscriber;

    private TextView mStatusTextView;
    private FrameLayout mPublisherContainer;
    private FrameLayout mSubscriberContainer;
    private Button mEndCallButton;
    private LinearLayout mIncomingActionsBar;

    private enum CallState {
        INITIALIZING,
        INVITATION_OUTGOING,
        INVITATION_INCOMING,
        CALL_IN_PROGRESS,
        CALL_ENDED
    }

    private CallState mCallState;

    private ValueEventListener mEndCallListener = new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            Log.d(TAG, "end call data change: " + dataSnapshot.getValue());
            mEndedAt = (Long) dataSnapshot.getValue();
            if (mEndedAt != null) {
                Log.d(TAG, "tearing down the call");
                if (mCallSession != null && mCallSession.getConnection() != null) {
                    mCallSession.disconnect();
                }

                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(CallActivity.this);
                Set<String> callRequestsWaitedOn = preferences.getStringSet(CallConstants.CALL_REQUESTS_WAITED_ON_BY_ACTIVITIES, null);
                if (callRequestsWaitedOn != null) {
                    if (callRequestsWaitedOn.contains(mRequestId)) {
                        callRequestsWaitedOn.remove(mRequestId);
                        preferences.edit()
                                .putStringSet(CallConstants.CALL_REQUESTS_WAITED_ON_BY_ACTIVITIES, callRequestsWaitedOn)
                                .apply();
                    }
                }

                Handler automaticDismissHandler = new Handler();
                automaticDismissHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        CallActivity.this.finish();
                    }
                }, 10000);

                setUserInterfaceState(CallState.CALL_ENDED);
            }
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {
            Log.e(TAG, "end call data error: " + databaseError.getMessage());
            // TODO: handle error
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        mStatusTextView = (TextView) findViewById(R.id.statusTextView);
        mPublisherContainer = (FrameLayout) findViewById(R.id.publisherContainer);
        mSubscriberContainer = (FrameLayout) findViewById(R.id.subscriberContainer);
        mEndCallButton = (Button) findViewById(R.id.endButton);
        mIncomingActionsBar = (LinearLayout) findViewById(R.id.incomingActionsBar);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        // window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        Intent intent = getIntent();
        mCallId = intent.getStringExtra(CallConstants.EXTRA_CALL_ID);
        String callDetails = intent.getStringExtra(CallConstants.EXTRA_CALL_DETAILS);
        Log.d(TAG, "call id: " + mCallId);

        mCallEndedAtRef = FirebaseDatabase.getInstance().getReference("calls/" + mCallId + "/endedAt");
        mCallEndedAtRef.addValueEventListener(mEndCallListener);

        try {
            JSONObject callDetailsJSON = new JSONObject(callDetails);

            mCallKey = callDetailsJSON.getString("opentokKey");
            mCallSessionId = callDetailsJSON.getString("opentokSession");
            mCallToken = callDetailsJSON.getString("opentokToken");
            mSenderUserId = callDetailsJSON.getString("from");
            mRecipientUserId = callDetailsJSON.getString("to");
            mInitiatedAt = callDetailsJSON.getLong("initiatedAt");
            mRequestId = callDetailsJSON.getString("requestId");
            mIsSender = mSenderUserId.equals(callDetailsJSON.getString("id"));

            setupCall();
            setUserInterfaceState(CallState.INITIALIZING);
        } catch (JSONException exception) {
            // TODO: display an error
            Log.e(TAG, "Error parsing call details: " + exception.getMessage());
        }

    }

    @Override
    // TODO: ringtone sound only for recipient and a "ring back" sound on the sender
    // TODO: use MediaPlayer and AudioAttributes
    // TODO: query system for mute/ringer/vibrate settings and mimic them.
    protected void onStart() {
        super.onStart();
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        mRingtone = RingtoneManager.getRingtone(getApplicationContext(), notification);
        mRingtone.play();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // TODO: end any call that arrives during this call
    }

    private void setupCall() {
        mCallSession = new Session(this, mCallKey, mCallSessionId);
        mCallSession.setSessionListener(this);
        mCallSession.connect(mCallToken);
    }

    private void tearDownCall() {
        // this will asynchronously trigger mEndCallListener, which will complete
        // the tear down
        mCallEndedAtRef.setValue(ServerValue.TIMESTAMP);
    }

    public void endCall(View endCallButton) {
        mRingtone.stop();
        tearDownCall();
    }

    public void acceptCall(View acceptCallButton) {
        mRingtone.stop();
        startPublishing();
        setUserInterfaceState(CallState.CALL_IN_PROGRESS);
    }

    public void declineCall(View declineCallButton) {
        tearDownCall();
    }

    private void startPublishing() {
        mPublisher = new Publisher(this);
        mCallSession.publish(mPublisher);
    }

    private void populatePublisherUserInterface() {
        if (mPublisherContainer.getChildCount() == 0) {
            mPublisher.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
            SurfaceView publisherView = (SurfaceView) mPublisher.getView();
            mPublisherContainer.addView(publisherView);
            publisherView.setZOrderOnTop(true);
        }
        mPublisherContainer.setVisibility(View.VISIBLE);
    }

    private void populateSubscriberUserInterface() {
        if (mSubscriber != null && mSubscriberContainer.getChildCount() == 0) {
            mSubscriber.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
            mSubscriberContainer.addView(mSubscriber.getView());
        }
    }

    private void updateState(CallState state) {
        if (mCallState != state) {
            switch (mCallState) {
                case INITIALIZING:
                    break;
                case INVITATION_OUTGOING:
                    break;
                case INVITATION_INCOMING:
                    break;
                case CALL_IN_PROGRESS:
                    break;
                case CALL_ENDED:
                    break;
                default: // null
                    break;
            }
        }
        mCallState = state;
    }

    private void setUserInterfaceState(CallState state) {
        switch(state) {
            case INVITATION_OUTGOING:
                populatePublisherUserInterface();

                mEndCallButton.setVisibility(View.VISIBLE);
                break;
            case INVITATION_INCOMING:
                // this case applies both before and after the subscriber has been created
                if (mSubscriber != null) {
                    mStatusTextView.setVisibility(View.GONE);
                    populateSubscriberUserInterface();
                }
                mIncomingActionsBar.setVisibility(View.VISIBLE);
                break;
            case CALL_IN_PROGRESS:
                populateSubscriberUserInterface();
                populatePublisherUserInterface();

                // This was false for the recipient, but should now be true
                mSubscriber.setSubscribeToAudio(true);

                mStatusTextView.setVisibility(View.GONE);
                mIncomingActionsBar.setVisibility(View.GONE);
                mEndCallButton.setVisibility(View.VISIBLE);
                break;
            case CALL_ENDED:
                // TODO: turn keepScreenOn off
                // TODO: possibly dismiss this activity if and incoming call was declined
                mStatusTextView.setText("Call Ended");

                mPublisherContainer.removeAllViews();
                mSubscriberContainer.removeAllViews();

                mStatusTextView.setVisibility(View.VISIBLE);
                mIncomingActionsBar.setVisibility(View.GONE);
                mEndCallButton.setVisibility(View.GONE);
                break;
            case INITIALIZING:
            default:
                // this case applies before the call data has arrived, and after it has arrived but before the session has connected
                // TODO: reset all the other parts of the UI
                if (mIsSender) {
                    mStatusTextView.setText("Calling...");
                } else {
                    mStatusTextView.setText("Incoming call from " + mSenderUserId);
                }
                break;
        }
        mCallState = state;
    }

    @Override
    public void onConnected(Session session) {
        if (mIsSender) {
            // TODO: publish without audio so that ring back is still audible
            startPublishing();
            setUserInterfaceState(CallState.INVITATION_OUTGOING);
        } else {
            setUserInterfaceState(CallState.INVITATION_INCOMING);
        }
    }

    @Override
    public void onDisconnected(Session session) {
        // assume that the state changes occured when the "end" or "decline" buttons
        // were pressed
        // object cleanup
        mCallSession = null;
        mPublisher = null;
    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {
        if (mSubscriber == null) {
            // TODO: maybe make this its own method
            mSubscriber = new Subscriber(this, stream);
            mCallSession.subscribe(mSubscriber);
            if (mIsSender) {
                // call has been accepted
                mRingtone.stop();
                setUserInterfaceState(CallState.CALL_IN_PROGRESS);
            } else {
                // stream is from incoming invitation
                setUserInterfaceState(CallState.INVITATION_INCOMING);
                mSubscriber.setSubscribeToAudio(false);
            }
        } else {
            // TODO: why would this happen?
            Log.d(TAG, "Stream received when not expecting one");
        }
    }

    @Override
    public void onStreamDropped(Session session, Stream stream) {
        // TODO: detect if this was part of a normal end call flow, or an error condition
        // object cleanup
        mSubscriber = null;
    }

    @Override
    public void onError(Session session, OpentokError error) {
        // TODO: handle errors
        Log.e(TAG, "A session error occurred: " + error.getMessage());
    }

}
