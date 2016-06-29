package me.aoberoi.whosthere.models;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.SurfaceView;

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

import java.util.Observable;
import java.util.Set;

import me.aoberoi.whosthere.constants.CallConstants;

public class Call extends Observable implements Session.SessionListener {

    private static final String TAG = "Call";

    private Context mContext;

    private String mId;
    private String mRequestId;
    private String mUserId;
    private String mOpenTokKey;
    private String mOpenTokSessionId;
    private String mOpenTokToken;
    private String mSenderId;
    private String mRecipientId;
    private Long mInitiatedAt;
    private Long mEndedAt;

    private Session mOpenTokSession;
    private Publisher mOpenTokPublisher;
    private Subscriber mOpenTokSubscriber;

    private DatabaseReference mEndedAtRef;

    private boolean mIsEnding = false;
    private boolean mIsAccepted = false;
    private boolean mHasBegun = false;

    public Call(Context context) {

        mContext = context;
    }

    public static Call fromIntent(Context context, Intent callIntent) {
        Call call = new Call(context);

        call.setId(callIntent.getStringExtra(CallConstants.EXTRA_CALL_ID));

        String callDetails = callIntent.getStringExtra(CallConstants.EXTRA_CALL_DETAILS);
        try {
            JSONObject callDetailsJSON = new JSONObject(callDetails);

            call.setUserId(callDetailsJSON.getString("id"));
            call.setRequestId(callDetailsJSON.getString("requestId"));
            call.setOpenTokKey(callDetailsJSON.getString("opentokKey"));
            call.setOpenTokSessionId(callDetailsJSON.getString("opentokSession"));
            call.setOpenTokToken(callDetailsJSON.getString("opentokToken"));
            call.setSenderId(callDetailsJSON.getString("from"));
            call.setRecipientId(callDetailsJSON.getString("to"));
            call.setInitiatedAt(callDetailsJSON.getLong("initiatedAt"));
            call.setEndedAt(callDetailsJSON.getLong("endedAt"));

        } catch (JSONException exception) {
            // TODO: proper error handling
        }

        return call;
    }

    public String getId() {
        return mId;
    }

    public void setId(String id) {
        mId = id;

        mEndedAtRef = FirebaseDatabase.getInstance().getReference("calls/" + mId + "/endedAt");
        mEndedAtRef.addValueEventListener(mEndCallListener);

        setChanged();
        notifyObservers();

    }

    public String getUserId() {
        return mUserId;
    }

    private void setUserId(String userId) {
        mUserId = userId;

        setChanged();
        notifyObservers();
    }

    public String getRequestId() {
        return mRequestId;
    }

    private void setRequestId(String requestId) {
        mRequestId = requestId;

        setChanged();
        notifyObservers();
    }

    public String getOpenTokKey() {
        return mOpenTokKey;
    }

    private void setOpenTokKey(String opentokKey) {
        mOpenTokKey = opentokKey;

        setChanged();
        notifyObservers();
    }

    public String getOpenTokSessionId() {
        return mOpenTokSessionId;
    }

    private void setOpenTokSessionId(String opentokSessionId) {
        mOpenTokSessionId = opentokSessionId;

        setChanged();
        notifyObservers();
    }

    public String getOpenTokToken() {
        return mOpenTokToken;
    }

    private void setOpenTokToken(String opentokToken) {
        mOpenTokToken = opentokToken;

        setChanged();
        notifyObservers();
    }

    public String getSenderId() {
        return mSenderId;
    }

    private void setSenderId(String senderId) {
        mSenderId = senderId;

        setChanged();
        notifyObservers();
    }

    public String getRecipientId() {
        return mRecipientId;
    }

    private void setRecipientId(String recipientId) {
        mRecipientId = recipientId;

        setChanged();
        notifyObservers();
    }

    public Long getInitiatedAt() {
        return mInitiatedAt;
    }

    private void setInitiatedAt(Long initiatedAt) {
        mInitiatedAt = initiatedAt;

        setChanged();
        notifyObservers();
    }

    public Long getEndedAt() {
        return mEndedAt;
    }

    private void setEndedAt(Long endedAt) {
        mEndedAt = endedAt;

        setChanged();
        notifyObservers();
    }

    public boolean isIncoming() {
        return mRecipientId.equals(mUserId);
    }

    public boolean isOutgoing() {
        return !isIncoming();
    }

    public boolean isAccepted() {
        return mIsAccepted;
    }

    public void setAccepted(boolean accepted) {
        if (isIncoming()) {

            mIsAccepted = accepted;

            if (mIsAccepted) {
                setPublisher(new Publisher(mContext));
                mOpenTokSession.publish(getPublisher()); // this is a noop if the session is not connected

                Subscriber subscriber = getSubscriber();
                if (subscriber != null) {
                    subscriber.setSubscribeToAudio(true);
                }
            } else {
                end();
            }

            setChanged();
            notifyObservers();
        }
    }

    private void setRemoteAccepted(boolean accepted) {
        if (isOutgoing()) {
            mIsAccepted = accepted;

            Publisher publisher = getPublisher();
            if (publisher != null) {
                publisher.setPublishAudio(true);
            }

            setChanged();
            notifyObservers();
        }
    }

    public boolean isEnding() {
        return mIsEnding;
    }

    private void setEnding(boolean ending) {
        mIsEnding = ending;

        setChanged();
        notifyObservers();
    }

    private Publisher getPublisher() {
        return mOpenTokPublisher;
    }

    private void setPublisher(Publisher publisher) {
        if (publisher != null) {
            publisher.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FIT);
        }
        mOpenTokPublisher = publisher;

        setChanged();
        notifyObservers();
    }

    private Subscriber getSubscriber() {
        return mOpenTokSubscriber;
    }

    private void setSubscriber(Subscriber subscriber) {
        if (subscriber != null) {
            subscriber.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FIT);
        }
        mOpenTokSubscriber = subscriber;

        setChanged();
        notifyObservers();
    }


    public SurfaceView getPublisherView() {
        if (getPublisher() != null) {
            return (SurfaceView) getPublisher().getView();
        }
        return null;
    }

    public SurfaceView getSubscriberView() {
        if (getSubscriber() != null) {
            return (SurfaceView) getSubscriber().getView();
        }
        return null;
    }

    public void begin() {
        if (mHasBegun) {
            throw new RuntimeException("Cannot begin a call which was already begun.");
        }

        // Setup the OpenTok Session
        mOpenTokSession = new Session(mContext, mOpenTokKey, mOpenTokSessionId);
        mOpenTokSession.setSessionListener(this);
        mOpenTokSession.connect(mOpenTokToken);

        if (isOutgoing()) {
            Publisher publisher = new Publisher(mContext);
            publisher.setPublishAudio(false);
            setPublisher(publisher);
        }

        mHasBegun = true;
    }

    public void end() {
        if (isEnding()) {
            return;
        }

        mOpenTokSession.disconnect(); // NOTE: this is a noop if the session is already disconnected

        if (getEndedAt() == null) {
            Log.d(TAG, "Locally initiated end of call");
            mEndedAtRef.setValue(ServerValue.TIMESTAMP);
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        Set<String> callRequestsWaitedOn = preferences.getStringSet(CallConstants.CALL_REQUESTS_WAITED_ON_BY_ACTIVITIES, null);
        if (callRequestsWaitedOn != null) {
            if (callRequestsWaitedOn.contains(mRequestId)) {
                callRequestsWaitedOn.remove(mRequestId);
                preferences.edit()
                        .putStringSet(CallConstants.CALL_REQUESTS_WAITED_ON_BY_ACTIVITIES, callRequestsWaitedOn)
                        .apply();
            }
        }

        setEnding(true);
    }

    private ValueEventListener mEndCallListener = new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            Long endedAt = dataSnapshot.getValue(Long.class);
            Log.d(TAG, "End call data changed: " + endedAt);

            setEndedAt(endedAt);

            if (getEndedAt() != null && !isEnding()) {
                Log.d(TAG, "Remote initiated end of call");
                end();
            }
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {
            Log.e(TAG, "End call data error: " + databaseError.getMessage());
            // TODO: real error handling, set some property to show invalid state
        }
    };

    public void onConnected(Session session) {
        if (isEnding()) {
            return;
        }

        Publisher publisher = getPublisher();
        if (publisher != null) {
            if (isOutgoing() && isAccepted()) {
                publisher.setPublishAudio(true);
            }

            if (isOutgoing() || isAccepted()) {
                mOpenTokSession.publish(publisher);
            }
        }
    }

    public void onDisconnected(Session session) {
        if (isEnding()) {
            // Clean up
            mOpenTokSession = null;
            setPublisher(null);
            setSubscriber(null);
        } else {
            Log.w(TAG, "Session disconnected without call being ended.");
        }
    }

    public void onStreamReceived(Session session, Stream stream) {
        if (!isEnding() && getSubscriber() == null) {
            setSubscriber(new Subscriber(mContext, stream));

            if (isIncoming() && !isAccepted()) {
                getSubscriber().setSubscribeToAudio(false);
            }

            if (isOutgoing()) {
                setRemoteAccepted(true);
            }

            mOpenTokSession.subscribe(getSubscriber());
        }
    }

    public void onStreamDropped(Session session, Stream stream) {
        setSubscriber(null);
    }

    public void onError(Session session, OpentokError error) {
        // TODO: error handling
        if (!isEnding()) {
            Log.e(TAG, "Session error occurred: " + error.getMessage());
            end();
        }
    }
}
