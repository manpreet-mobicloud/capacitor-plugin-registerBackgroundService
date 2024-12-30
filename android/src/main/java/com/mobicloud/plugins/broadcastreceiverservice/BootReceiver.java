package com.mobicloud.plugins.broadcastreceiverservice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
      Log.d("BootReceiver", "Device booted, starting BackgroundService...");

      // Start the BackgroundService
      Intent serviceIntent = new Intent(context, com.mobicloud.plugins.backgroundservice.BackgroundService.class);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent);
      }
    }
  }
}
