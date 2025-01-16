package com.mobicloud.plugins.backgroundservice;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class BackgroundService extends Service {
  private static final String CHANNEL_ID = "BackgroundServiceChannel";
  private static final int NOTIFICATION_ID = 1;
  private BluetoothAdapter bluetoothAdapter;
  private BluetoothStateReceiver bluetoothStateReceiver;

  @Override
  public void onCreate() {
    super.onCreate();
    Log.d("BackgroundService", "Service created");

    // Create Notification Channel
    createNotificationChannel();

    // Initialize Bluetooth Monitoring
    setupBluetoothMonitoring();

    // Start the service as a foreground service
    startForegroundServiceCompat();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    super.onStartCommand(intent, Service.START_FLAG_REDELIVERY,1);
    Log.d("BackgroundService", "Service started");
    System.out.println("Inside onStartCommnad");
    checkInitialBluetoothState();
    return START_STICKY; // Ensures service restarts if killed
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null; // Not using binding
  }

  private void createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel serviceChannel = new NotificationChannel(
              CHANNEL_ID,
              "Background Service",
              NotificationManager.IMPORTANCE_HIGH
      );
      NotificationManager manager = getSystemService(NotificationManager.class);
      if (manager != null) {
        manager.createNotificationChannel(serviceChannel);
      }
    }
  }

  private void startForegroundServiceCompat() {
    Intent notificationIntent = new Intent(this, com.mobicloud.plugins.MainActivity.class);
    PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
    );

    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
//            .setContentTitle("FE Enhancement")
            .setContentText("Service is running in the background.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true);

    startForeground(NOTIFICATION_ID, notificationBuilder.build());
    System.out.println("Service is running in the background");
  }

  private void setupBluetoothMonitoring() {
    BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    checkInitialBluetoothState();
    if (bluetoothManager != null) {
      bluetoothAdapter = bluetoothManager.getAdapter();
    }

    if (bluetoothAdapter == null) {
      Log.e("BackgroundService", "Bluetooth not supported on this device.");
      return;
    }

    bluetoothStateReceiver = new BluetoothStateReceiver();
    IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
    registerReceiver(bluetoothStateReceiver, filter);
  }

  private void checkInitialBluetoothState() {
    if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
      updateNotification("", "Service is running in the background.");
    } else {
      sendBluetoothNotification();
    }
  }

  private void sendBluetoothNotification() {
    Intent intent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
    PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
    );

    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
//            .setContentTitle("FE Enhancement")
            .setContentText("Bluetooth is Disabled. Tap to Enable Bluetooth.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);

    NotificationManager manager = getSystemService(NotificationManager.class);
    if (manager != null) {
      manager.notify(NOTIFICATION_ID, builder.build());
    }
  }

  private class BluetoothStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
        int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
        if (state == BluetoothAdapter.STATE_ON) {
          System.out.println("Services are running in background and bluetooth is turned on");
          updateNotification("", "Service is running in the background.");

        } else if (state == BluetoothAdapter.STATE_OFF) {
          System.out.println("Bluetooth is turned off");
          sendBluetoothNotification();
        }
      }
    }
  }

  private void updateNotification(String title, String content) {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true);

    NotificationManager manager = getSystemService(NotificationManager.class);
    if (manager != null) {
      manager.notify(NOTIFICATION_ID, builder.build());
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    System.out.println("Appication Forcefully Destroyed2");
    Log.d("BackgroundService", "Service destroyed");

    if (bluetoothStateReceiver != null) {
      unregisterReceiver(bluetoothStateReceiver);
    }

  }

}
