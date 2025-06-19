package com.mobicloud.plugins.backgroundservice;

public interface MqttConnectionCallback {
  void onConnectionSuccess();
  void onConnectionFailure(Throwable exception);
}

