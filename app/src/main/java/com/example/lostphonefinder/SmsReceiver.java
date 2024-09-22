package com.example.lostphonefinder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.widget.Toast;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus != null) {
                for (Object pdu : pdus) {
                    SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
                    String messageBody = smsMessage.getMessageBody();

                    // Check if the message contains "startD1"
                    if (messageBody.contains("startD1")) {
                        // Trigger the app to launch
                        Intent launchIntent = new Intent(context, MainActivity.class);
                        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(launchIntent);

                        Toast.makeText(context, "startD1 command received. Launching app.", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }
}
