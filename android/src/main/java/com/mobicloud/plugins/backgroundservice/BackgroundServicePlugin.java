package com.mobicloud.plugins.backgroundservice;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(name = "BackgroundService")
public class BackgroundServicePlugin extends Plugin {

    private BroadcastReceiver mqttReceiver;
    private BackgroundService bobj;

    @PluginMethod
    public void StartBackgroundService(PluginCall call) throws JSONException {
        String baseURL = call.getString("baseURL");
        String basicAuth = call.getString("basicAUTH");
        String apiSuffix = call.getString("apiSuffix");
        String deviceUUID = call.getString("deviceUUID");
        String deviceType = call.getString("deviceType");
        JSObject authPayload = call.getObject("authPayload");

        JSObject apiPayload = new JSObject(authPayload.toString());
        String payload = apiPayload.toString();
        Intent serviceIntent = new Intent(getContext(), BackgroundService.class);

        System.out.println("Base Url is: "+baseURL);

        serviceIntent.putExtra("baseURL", baseURL);
        serviceIntent.putExtra("basicAuth", basicAuth);
        serviceIntent.putExtra("apiSuffix", apiSuffix);
        serviceIntent.putExtra("deviceUUID", deviceUUID);
        serviceIntent.putExtra("deviceType", deviceType);
        serviceIntent.putExtra("apiPayload", payload);

//        if ("BLE".equals(deviceType)) {
//            serviceIntent.putExtra("macAddress", macAddress);
//        }

        getContext().startService(serviceIntent);
        this.bobj = new BackgroundService(getContext());

        call.resolve();
    }

    @PluginMethod
    public void connectBleDevice(PluginCall call) {
      String macAddress = call.getString("macAddress");

      if (macAddress == null || macAddress.isEmpty()) {
        call.reject("Device ID is required");
        return;
      }

      if (bobj == null) {
        call.reject("BackgroundService is not initialized. Ensure the service is started.");
        return;
      }

      try {
        bobj.connectToDevice(macAddress);
      }
      catch (Exception e) {
        System.out.println("Error Occured while connecting to device");
        call.reject(String.valueOf(e));
      }
    }
    @PluginMethod
    public void connectMqtt(PluginCall call) {
        String brokerUrl = call.getString("BrokerUrl");
        String username = call.getString("username");
        String password = call.getString("password");

        if (bobj == null) {
            call.reject("BackgroundService is not initialized. Ensure the service is started.");
            return;
        }

        JSObject res = new JSObject();
        bobj.connectToBroker(brokerUrl, username, password, new MqttConnectionCallback() {

            @Override
            public void onConnectionSuccess() {
                res.put("isMqttConnected", true);
                call.resolve(res);
            }

            @Override
            public void onConnectionFailure(Throwable exception) {
                call.reject(exception.toString());
            }
        });
    }

    @PluginMethod
    public void subscribeToTopic(PluginCall call) {
        String topicToSubscribe = call.getString("topicTOSubscribe");
        JSObject res = new JSObject();

        if (bobj == null) {
            call.reject("BackgroundService is not initialized. Ensure the service is started.");
            return;
        }

        bobj.subscribeToTopic(topicToSubscribe, new MqttDownlinkCallBack() {

            @Override
            public void onSuccess() {
                res.put("isSubscriptionSuccess", true);
                call.resolve(res);
            }

            @Override
            public void onFailure(Throwable exception) {
                call.reject(exception.toString());
            }
        });
    }

    @PluginMethod
    public void subscribeToAuthTopic(PluginCall call) {
        String topicToSubscribe = call.getString("authTopicToSubscribe");
        JSObject res = new JSObject();

        if (bobj == null) {
            call.reject("BackgroundService is not initialized. Ensure the service is started.");
            return;
        }

        bobj.subscribeToAuthTopic(topicToSubscribe, new MqttDownlinkCallBack() {

            @Override
            public void onSuccess() {
                res.put("isSubscriptionSuccess", true);
                call.resolve(res);
            }

            @Override
            public void onFailure(Throwable exception) {
                call.reject(exception.toString());
            }
        });
    }

    @PluginMethod
    public void publishMessage(PluginCall call) {
        String topicToPublish = call.getString("topicToPublish");
        String payloadMessage = call.getString("payload");

        if(payloadMessage == null) {
          Log.e("BackgroundService","Empty Payload Message");
          call.reject("Empty payload Message");
          return;
        } else {
          Log.d("BackgroundService", "Publish message payload is: "+payloadMessage);
        }

        if (bobj == null) {
            call.reject("BackgroundService is not initialized. Ensure the service is started.");
            return;
        }

        bobj.publishMessage(topicToPublish, payloadMessage, new MqttUplinkCallBack() {
            @Override
            public void onSuccess() {
                JSObject res = new JSObject();
                res.put("isMessagePublished", true);
                call.resolve(res);
            }

            @Override
            public void onFailure(Throwable exception) {
                call.reject(exception.toString());
            }
        });

        // Register BroadcastReceiver if not already
        if (mqttReceiver == null) {
            mqttReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String mqttMessage = intent.getStringExtra("message");
                    JSObject data = new JSObject();
                    data.put("message", mqttMessage);
                    notifyListeners("onMqttMessage", data);
                }
            };
            LocalBroadcastManager.getInstance(getContext()).registerReceiver(mqttReceiver, new IntentFilter("com.mobicloud.MQTT_MESSAGE"));
        }
    }

    @Override
    protected void handleOnDestroy() {
        if (mqttReceiver != null) {
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mqttReceiver);
            mqttReceiver = null;
        }
    }
}
