package com.mobicloud.plugins.backgroundservice;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class BackgroundService extends Service {
  private static final String CHANNEL_ID = "BackgroundServiceChannel";
  private static final int NOTIFICATION_ID = 1;
  private BluetoothAdapter bluetoothAdapter;
  private BluetoothStateReceiver bluetoothStateReceiver;

  @Override
  public void onCreate() {
    super.onCreate();
    checkInitialBluetoothState();
    createNotificationChannel();
    setupBluetoothMonitoring();
      try {
          startForegroundService();
      } catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
      }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.d("BackgroundService", "Service started");

    // Ensure Bluetooth state is checked again on restart
    checkInitialBluetoothState();

    return START_STICKY;  // Ensures service restarts if killed
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private void createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel serviceChannel = new NotificationChannel(
        CHANNEL_ID,
        "Background Service Channel",
        NotificationManager.IMPORTANCE_DEFAULT
      );
      NotificationManager manager = getSystemService(NotificationManager.class);
      manager.createNotificationChannel(serviceChannel);
    }
  }

  private void startForegroundService() throws ClassNotFoundException {
    Intent notificationIntent = new Intent(this, com.mobicloud.plugins.MainActivity.class);
    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("Gas Regulator")
      .setContentText("Services are running in the background.")
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentIntent(pendingIntent)
      .setOngoing(true)  // Makes the notification persistent
      .setPriority(NotificationCompat.PRIORITY_HIGH);

    startForeground(NOTIFICATION_ID, notificationBuilder.build());  // Starts the service in the foreground
  }

  private void setupBluetoothMonitoring() {
    BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    bluetoothAdapter = bluetoothManager.getAdapter();

    if (bluetoothAdapter == null) {
      Log.e("BackgroundService", "Bluetooth not supported on this device.");
      return;
    }

    bluetoothStateReceiver = new BluetoothStateReceiver();
    IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
    registerReceiver(bluetoothStateReceiver, filter);

    // Initial check
    checkInitialBluetoothState();
  }

  private class BluetoothStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
        int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
        if (state == BluetoothAdapter.STATE_ON) {
          updateForegroundNotification("Gas Regulator", "Services are running in the background.");
          removeBluetoothNotification();
        } else if (state == BluetoothAdapter.STATE_OFF) {
          updateForegroundNotification("Gas Regulator - Bluetooth Alert", "Please turn on your Bluetooth.");
          sendBluetoothNotification();
        }
      }
    }
  }

  private void checkInitialBluetoothState() {
    if (bluetoothAdapter != null) {
      if (bluetoothAdapter.isEnabled()) {
        updateForegroundNotification("Gas Regulator", "Services are running in the background.");
        removeBluetoothNotification();
      } else {
        updateForegroundNotification("Gas Regulator - Bluetooth Alert", "Please turn on your Bluetooth.");
        sendBluetoothNotification();
      }
    }
  }

  @SuppressLint("ObsoleteSdkInt")
  private void sendBluetoothNotification() {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("Gas Regulator - Bluetooth Alert")
      .setContentText("Please turn on your Bluetooth.")
      .setSmallIcon(R.mipmap.ic_launcher)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setOngoing(true) // Makes the notification persistent
      .setContentIntent(getBluetoothSettingsPendingIntent()); // Redirect to Bluetooth settings

    NotificationManager manager = null;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
      manager = getSystemService(NotificationManager.class);
    }
    if (manager != null) {
      manager.notify(NOTIFICATION_ID, builder.build());
    }
  }

  @SuppressLint("ObsoleteSdkInt")
  private void updateForegroundNotification(String title, String content) {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle(title)
      .setContentText(content)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setOngoing(true) // Keeps the notification persistent
      .setPriority(NotificationCompat.PRIORITY_HIGH);

    NotificationManager manager = null;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
      manager = getSystemService(NotificationManager.class);
    }
    if (manager != null) {
      manager.notify(NOTIFICATION_ID, builder.build());
    }
  }

  @SuppressLint("ObsoleteSdkInt")
  private void removeBluetoothNotification() 
  {
    NotificationManager manager = null;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) 
    {
      manager = getSystemService(NotificationManager.class);
    }
    if (manager != null) 
    {
      manager.cancel(NOTIFICATION_ID);
    }
  }

  private PendingIntent getBluetoothSettingsPendingIntent() 
  {
    Intent intent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
    return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
  }

  @Override
  public void onDestroy() 
  {
    super.onDestroy();
    if (bluetoothStateReceiver != null) 
    {
      unregisterReceiver(bluetoothStateReceiver); // Unregister Bluetooth receiver
    }
    restartService();
  }

  private void restartService() 
  {
      Intent restartServiceIntent = new Intent(getApplicationContext(), BackgroundService.class);
      PendingIntent restartServicePendingIntent = PendingIntent.getService(this,1,restartServiceIntent,PendingIntent.FLAG_IMMUTABLE);

      AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
      if (alarmManager != null) 
      {
        long restartTime = System.currentTimeMillis() + 1000; // Restart after 1 second
        alarmManager.set(AlarmManager.RTC_WAKEUP, restartTime, restartServicePendingIntent);
      }
  }
}
