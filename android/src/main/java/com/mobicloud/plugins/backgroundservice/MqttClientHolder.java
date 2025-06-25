package com.mobicloud.plugins.backgroundservice;

import org.eclipse.paho.client.mqttv3.MqttClient;

import info.mqtt.android.service.MqttAndroidClient;

public class MqttClientHolder {
  private static MqttAndroidClient mqttClient;

  public static void setClient(MqttAndroidClient client) {
    mqttClient = client;
  }

  public static MqttAndroidClient getClient() {
    return mqttClient;
  }

  public static boolean isConnected() {
    return mqttClient != null && mqttClient.isConnected();
  }

  public static void clear() {
    mqttClient = null;
  }
}
