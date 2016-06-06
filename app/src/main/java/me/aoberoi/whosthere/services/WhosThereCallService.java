package me.aoberoi.whosthere.services;

import android.content.Intent;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import me.aoberoi.whosthere.activities.CallActivity;
import me.aoberoi.whosthere.constants.CallConstants;

public class WhosThereCallService extends FirebaseMessagingService {
    private static final String TAG = "WhosThereCallService";

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Map<String, String> messageData = remoteMessage.getData();
        Log.d(TAG, "Message Data: " + messageData);

        String callId = messageData.keySet().iterator().next();
        String callDetails = messageData.get(callId);
        startCall(callId, callDetails);
    }

    // TODO: perhaps post a notification if the phone is not locked/inactive
//    private void sendNotification(String callId, String callDetails) {
//        Intent intent = new Intent(this, CallActivity.class);
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
//        intent.putExtra(CallConstants.EXTRA_CALL_ID, callId);
//        intent.putExtra(CallConstants.EXTRA_CALL_DETAILS, callDetails);
//        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, intent,
//                PendingIntent.FLAG_ONE_SHOT);
//
//        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
//        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
//                .setSmallIcon(R.drawable.ic_stat_ic_notification)
//                .setContentTitle("Incoming Video Call")
//                .setAutoCancel(true)
//                .setSound(defaultSoundUri)
//                .setPriority(NotificationCompat.PRIORITY_MAX)
//                .setCategory(NotificationCompat.CATEGORY_CALL)
//                .setContentIntent(pendingIntent);
//
//        NotificationManager notificationManager =
//                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//
//        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build());
//    }

    private void startCall(String callId, String callDetails) {
        Intent intent = new Intent(this, CallActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(CallConstants.EXTRA_CALL_ID, callId);
        intent.putExtra(CallConstants.EXTRA_CALL_DETAILS, callDetails);
        this.startActivity(intent);
    }
}
