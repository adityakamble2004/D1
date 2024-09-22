package com.example.lostphonefinder;

import android.Manifest;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.MediaPlayer;
import android.os.BatteryManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private static final int SMS_PERMISSION_REQUEST_CODE = 101;
    private MediaPlayer mediaPlayer; // MediaPlayer instance
    private static final String ADMIN_RECEIVER_ACTION = "com.example.lostphonefinder.ADMIN_RECEIVER";
    private static final String LOCATION_HISTORY_DB = "LocationHistory.db";
    private static final int REQUEST_CODE_ENABLE_ADMIN = 1;
    public static  int btn_enable_device_admin = 1000004;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize buttons
        Button findPhoneButton = findViewById(R.id.btn_find_phone);
        Button playSoundButton = findViewById(R.id.btn_play_sound);
        Button sendLocationButton = findViewById(R.id.btn_send_location);
        Button enableDeviceAdminButton = findViewById(R.id.btn_enable_device_admin); // Add this button in your layout

        Intent serviceIntent = new Intent(this, LostPhoneService.class);
        startService(serviceIntent);

        // Initialize FusedLocationProviderClient for location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize MediaPlayer
        mediaPlayer = MediaPlayer.create(this, R.raw.alert_sound); // Replace with your sound file name

        // Set button click listeners
        findPhoneButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            LOCATION_PERMISSION_REQUEST_CODE);
                } else {
                    getLastLocation(); // Get the location if permission is granted
                }
            }
        });

        playSoundButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mediaPlayer != null) {
                    mediaPlayer.start(); // Play the sound
                } else {
                    Toast.makeText(MainActivity.this, "Error playing sound", Toast.LENGTH_SHORT).show();
                }
            }
        });

        sendLocationButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.SEND_SMS},
                            SMS_PERMISSION_REQUEST_CODE);
                } else {
                    sendLocationViaSMS();
                }
            }
        });

        enableDeviceAdminButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                enableDeviceAdmin(); // Call this method to prompt user to enable Device Admin
            }
        });
    }

    private void enableDeviceAdmin() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, DeviceAdminReceiver.class);
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Additional explanation of why this is needed.");
        startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ENABLE_ADMIN) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Device Admin enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Device Admin enabling failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void sendLocationViaSMS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnCompleteListener(new OnCompleteListener<Location>() {
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            Location location = task.getResult();
                            String locationString = "Lat: " + location.getLatitude() + ", Lng: " + location.getLongitude();
                            String phoneNumber = "9699600638"; // Change this to the recipient's phone number
                            sendSMS(phoneNumber, "Lost Phone Location: " + locationString);

                            // Send location to the web server
                            sendLocationToServer(location.getLatitude(), location.getLongitude());

                            // Save location to history
                            saveLocationToHistory(location.getLatitude(), location.getLongitude());

                            // Send battery level
                            sendBatteryLevel(phoneNumber);

                        } else {
                            Toast.makeText(MainActivity.this, "Unable to get location, trying again...", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void sendSMS(String phoneNumber, String message) {
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(phoneNumber, null, message, null, null);
        Toast.makeText(MainActivity.this, "Location sent via SMS", Toast.LENGTH_SHORT).show();
    }

    private void sendLocationToServer(final double latitude, final double longitude) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("https://yourserver.com/location");

                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; utf-8");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setDoOutput(true);

                    JSONObject locationData = new JSONObject();
                    locationData.put("latitude", latitude);
                    locationData.put("longitude", longitude);

                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = locationData.toString().getBytes("utf-8");
                        os.write(input, 0, input.length);
                    }

                    final int responseCode = conn.getResponseCode();
                    Log.d("HTTP Response", "Response Code: " + responseCode);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                Toast.makeText(MainActivity.this, "Location sent to server successfully!", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(MainActivity.this, "Failed to send location to server.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

                    conn.disconnect();

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Error sending location to server.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void saveLocationToHistory(double latitude, double longitude) {
        Log.d("Location History", "Saved location: Lat: " + latitude + ", Lng: " + longitude);
    }

    private void sendBatteryLevel(String phoneNumber) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level / (float) scale * 100;
        String message = "Battery Level: " + batteryPct + "%";
        sendSMS(phoneNumber, message);
    }

    // Method to remotely lock or erase the device
    private void lockOrEraseDevice() {
        // Implement DeviceAdmin features here
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendLocationViaSMS();
            } else {
                Toast.makeText(this, "SMS permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnCompleteListener(new OnCompleteListener<Location>() {
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            Location location = task.getResult();
                            String locationString = "Lat: " + location.getLatitude() + ", Lng: " + location.getLongitude();
                            Toast.makeText(MainActivity.this, "Location: " + locationString, Toast.LENGTH_LONG).show();
                            Log.d("Location", locationString);
                        } else {
                            Toast.makeText(MainActivity.this, "Unable to get location, trying again...", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
    @Override
    protected void onResume() {
        super.onResume();

        // Automatically perform tasks when the app is launched
        sendLocationViaSMS();
        sendBatteryLevel();
        // Add other tasks here
    }

    private void sendBatteryLevel() {

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release(); // Release the MediaPlayer when the activity is destroyed
        }
    }
}