package com.example.lostphonefinder;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class LostPhoneService extends Service {

    private FusedLocationProviderClient fusedLocationClient;
    private Handler handler;
    private Runnable runnable;
    private static final int INTERVAL = 10 * 60 * 1000; // 10 minutes in milliseconds

    @Override
    public void onCreate() {
        super.onCreate();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                // Fetch location, battery, and IP details
                sendLocationDetails();
                handler.postDelayed(this, INTERVAL); // Re-run after each interval
            }
        };
        handler.post(runnable); // Start the task
    }

    private void sendLocationDetails() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // You can request permission here if necessary
            Toast.makeText(this, "Location permission not granted!", Toast.LENGTH_SHORT).show();
            return;
        }

        fusedLocationClient.getLastLocation().addOnCompleteListener(new OnCompleteListener<Location>() {
            @Override
            public void onComplete(@NonNull Task<Location> task) {
                if (task.isSuccessful() && task.getResult() != null) {
                    Location location = task.getResult();
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();
                    String ipAddress = getIPAddress();
                    String batteryLevel = getBatteryLevel();

                    // Prepare the message
                    String message = "Location: Lat: " + latitude + ", Lng: " + longitude +
                            "\nIP Address: " + ipAddress +
                            "\nBattery: " + batteryLevel;

                    // Send via SMS or save/send to server as per your requirement
                    sendSMS("9699600638", message); // Replace with recipient's number
                    Log.d("LocationService", "Details sent: " + message);
                } else {
                    Log.e("LocationService", "Unable to fetch location");
                }
            }
        });
    }

    // Method to get the local IP address
    private String getIPAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        if (isIPv4) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Log.e("LocationService", "Error fetching IP Address: " + ex.toString());
        }
        return "N/A"; // IP not available
    }

    // Method to get the battery level
    private String getBatteryLevel() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level / (float) scale * 100;
        return String.format("%.2f%%", batteryPct);
    }

    // Method to send SMS
    private void sendSMS(String phoneNumber, String message) {
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(phoneNumber, null, message, null, null);
        Log.d("LocationService", "SMS sent: " + message);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Keep service running until explicitly stopped
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(runnable); // Stop the handler when service is destroyed
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // This service does not support binding
    }
}
