package me.aoberoi.whosthere.activities;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
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

import java.util.Map;

import me.aoberoi.whosthere.R;
import me.aoberoi.whosthere.constants.CallConstants;

// TODO: get a loud ringtone that repeats and vibrations
// TODO: make incoming call's subscriber video-only
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

    private DatabaseReference mCallRef;

    private Session mCallSession;
    private Publisher mPublisher;
    private Subscriber mSubscriber;

    private TextView mStatusTextView;
    private FrameLayout mPublisherContainer;
    private FrameLayout mSubscriberContainer;
    private Button mEndCallButton;
    private LinearLayout mIncomingActionsBar;

    private enum UserInterfaceState {
        INITIALIZING,
        INVITATION_OUTGOING,
        INVITATION_INCOMING,
        CALL_IN_PROGRESS,
        CALL_ENDED
    }

    private UserInterfaceState mUserInterfaceState;

    private ValueEventListener mEndCallListener = new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            Log.d(TAG, "end call data change: " + dataSnapshot.getValue());
            // TODO: handle call end
            if (dataSnapshot.getValue() != null) {
                Log.d(TAG, "tearing down the call");
                // TODO: check if connected
                if (mCallSession != null) {
                    mCallSession.disconnect();
                }
                setUserInterfaceState(UserInterfaceState.CALL_ENDED);
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
        Log.d(TAG, "call id: " + mCallId);

        mCallRef = FirebaseDatabase.getInstance().getReference("calls/" + mCallId);
        // TODO: consider seeding this with data from FCM message. this can save time
        mCallRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // TODO: this is some super fragile parsing
                Map<String, Object> callDetails = (Map<String, Object>) dataSnapshot.getValue();
                mCallKey = (String) callDetails.get("opentokKey");
                mCallSessionId = (String) callDetails.get("opentokSession");
                Map<String, Object> callTo = (Map<String, Object>) callDetails.get("to");
                Map<String, Object> callFrom = (Map<String, Object>) callDetails.get("from");
                if (callTo.get("opentokToken") == null) {
                    mIsSender = true;
                    mCallToken = (String) callFrom.get("opentokToken");
                } else {
                    mIsSender = false;
                    mCallToken = (String) callTo.get("opentokToken");
                }
                mSenderUserId = (String) callFrom.get("user");
                mRecipientUserId = (String) callTo.get("user");
                mInitiatedAt = (Long) callDetails.get("initiatedAt");


                setupCall();
                // this behaves as an "update" for the UI once the call data has loaded
                setUserInterfaceState(mUserInterfaceState);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // TODO: display an error
            }
        });
        mCallRef.child("endedAt").addValueEventListener(mEndCallListener);

        setUserInterfaceState(UserInterfaceState.INITIALIZING);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void setupCall() {
        mCallSession = new Session(this, mCallKey, mCallSessionId);
        mCallSession.setSessionListener(this);
        mCallSession.connect(mCallToken);
    }

    private void tearDownCall() {
        // this will asynchronously trigger mEndCallListener, which will complete
        // the tear down
        // TODO: store in ivar?
        mCallRef.child("endedAt").setValue(ServerValue.TIMESTAMP);
    }

    public void endCall(View endCallButton) {
        tearDownCall();
    }

    public void acceptCall(View acceptCallButton) {
        startPublishing();
        setUserInterfaceState(UserInterfaceState.CALL_IN_PROGRESS);
    }

    public void declineCall(View declineCallButton) {
        tearDownCall();
    }

    private void startPublishing() {
        mPublisher = new Publisher(this);
        mCallSession.publish(mPublisher);
    }

    private void populatePublisherUserInterface() {
        mPublisherContainer.setVisibility(View.VISIBLE);
        if (mPublisherContainer.getChildCount() == 0) {
            mPublisher.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
            mPublisherContainer.addView(mPublisher.getView());
        }
    }

    private void populateSubscriberUserInterface() {
        if (mSubscriberContainer.getChildCount() == 0) {
            mSubscriber.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
            mSubscriberContainer.addView(mSubscriber.getView());
        }
    }

    private void setUserInterfaceState(UserInterfaceState state) {
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

                mStatusTextView.setVisibility(View.GONE);
                mIncomingActionsBar.setVisibility(View.GONE);
                mEndCallButton.setVisibility(View.VISIBLE);
                break;
            case CALL_ENDED:
                // TODO: turn keepScreenOn off
                // TODO: possibly dismiss this activity if and incoming call was declined
                mStatusTextView.setText("Call Ended");
                // publisher and subscriber container will be emptied as the session disconnect completes
                mStatusTextView.setVisibility(View.VISIBLE);
                mIncomingActionsBar.setVisibility(View.GONE);
                mEndCallButton.setVisibility(View.GONE);
                break;
            case INITIALIZING:
            default:
                // this case applies before the call data has arrived, and after it has arrived but before the session has connected
                // TODO: reset all the other parts of the UI
                if (mCallSession == null) {
                    mStatusTextView.setText("Loading...");
                } else {
                    if (mIsSender) {
                        mStatusTextView.setText("Calling...");
                    } else {
                        mStatusTextView.setText("Incoming call from " + mSenderUserId);
                    }
                }
                break;
        }
    }

    @Override
    public void onConnected(Session session) {
        if (mIsSender) {
            startPublishing();
            setUserInterfaceState(UserInterfaceState.INVITATION_OUTGOING);
        } else {
            setUserInterfaceState(UserInterfaceState.INVITATION_INCOMING);
        }
    }

    @Override
    public void onDisconnected(Session session) {
        // assume that the state changes occured when the "end" or "decline" buttons
        // were pressed
        // object cleanup
        mCallSession = null;
        mPublisher = null;
        mPublisherContainer.removeAllViews();
    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {
        if (mSubscriber == null) {
            // TODO: maybe make this its own method
            mSubscriber = new Subscriber(this, stream);
            mCallSession.subscribe(mSubscriber);
            if (mIsSender) {
                // call has been accepted
                setUserInterfaceState(UserInterfaceState.CALL_IN_PROGRESS);
            } else {
                // stream is from incoming invitation
                setUserInterfaceState(UserInterfaceState.INVITATION_INCOMING);
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
        mSubscriberContainer.removeAllViews();
    }

    @Override
    public void onError(Session session, OpentokError error) {
        // TODO: handle errors
        Log.e(TAG, "A session error occurred: " + error.getMessage());
    }

}
