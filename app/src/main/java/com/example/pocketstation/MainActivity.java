package com.example.pocketstation;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // Initialises variables
    private TextView notificationTextView;

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("fml", "fml la");
        super.onCreate(savedInstanceState);

        openNotificationAccessSettings(); // Enable notifications
        openBluetoothPermissionSettings(); // Enable bluetooth access

        // Set the UI of the app
        setContentView(R.layout.activity_main);
        notificationTextView = findViewById(R.id.notificationTextView);

        // Register receiver for BLE connection status
        IntentFilter filter = new IntentFilter("com.example.pocketstation.BLE_STATUS");
        registerReceiver(bleStatusReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    // Receiver to listen for connection status updates
    private final BroadcastReceiver bleStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isConnected = intent.getBooleanExtra("connected", false);
            String deviceName = intent.getStringExtra("deviceName");
            if (isConnected) {
                notificationTextView.setText("Connected to BLE: " + deviceName);
            } else {
                notificationTextView.setText("Disconnected from BLE");
            }
        }
    };

    public void openNotificationAccessSettings() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        startActivity(intent);
    }

    public void openBluetoothPermissionSettings() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }
}
