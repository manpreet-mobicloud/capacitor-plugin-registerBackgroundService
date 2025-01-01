package com.mobicloud.plugins;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  private static final int PERMISSION_REQUEST_CODE = 1001;
  private long backPressedTime;  // Variable to track the time when back button was pressed
  private Toast backToast;       // Toast to inform user to press back again to exit

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Check and request necessary permissions at runtime
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (checkAndRequestPermissions()) {
        // Start the background service if permissions are granted
        startBluetoothBackgroundService();
      }
    }
  }

  // Function to check if all required permissions are granted, and request them if not
  @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
  private boolean checkAndRequestPermissions() {
    boolean isNotificationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    boolean isBluetoothScanGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
    boolean isBluetoothConnectGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    boolean isFineLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

    // Check if any permission is missing
    if (!isNotificationGranted || !isBluetoothScanGranted || !isBluetoothConnectGranted || !isFineLocationGranted) {
      // Request the missing permissions
      ActivityCompat.requestPermissions(this,
        new String[]{
          Manifest.permission.POST_NOTIFICATIONS,
          Manifest.permission.BLUETOOTH_SCAN,
          Manifest.permission.BLUETOOTH_CONNECT,
          Manifest.permission.ACCESS_FINE_LOCATION
        },
        PERMISSION_REQUEST_CODE);

      return false;  // Permissions are not yet granted, waiting for user action
    }
    return true; // All permissions are granted
  }

  // Callback to handle the permission request result
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == PERMISSION_REQUEST_CODE) {
      boolean allGranted = true;
      for (int result : grantResults) {
        if (result != PackageManager.PERMISSION_GRANTED) {
          allGranted = false;
          break;
        }
      }

      if (allGranted) {
        // Permissions are granted, start the service
        startBluetoothBackgroundService();
      } else {
        // Permission denied, show a message
        Toast.makeText(this, "Permissions required for Bluetooth, location, and notifications!", Toast.LENGTH_LONG).show();
      }
    }
  }

  // Function to start the background service based on the Android version
  private void startBluetoothBackgroundService() {
    Intent serviceIntent = new Intent(this, com.mobicloud.plugins.backgroundservice.BackgroundService.class);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      startForegroundService(serviceIntent);  // For Android 8.0 and above
    } else {
      startService(serviceIntent);  // For versions below Android 8.0
    }
  }

  // Handle back button press
  @Override
  public void onBackPressed() {
    if (backPressedTime + 2000 > System.currentTimeMillis()) {  // If back button is pressed twice within 2 seconds
      backToast.cancel();  // Dismiss the toast
      super.onBackPressed();  // Exit the app
      return;
    } else {
      backToast = Toast.makeText(getBaseContext(), "Press back again to exit", Toast.LENGTH_SHORT);
      backToast.show();  // Show message to the user
    }
    backPressedTime = System.currentTimeMillis();  // Record the time of back press
  }
}
