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

    private BroadcastReceiver logReceiver;

    private BroadcastReceiver notificationReceiver;
    private BackgroundService bobj;

    @PluginMethod
    public void StartBackgroundService(PluginCall call) throws JSONException {
        String baseURL = call.getString("baseURL");
        String basicAuth = call.getString("basicAUTH");
        String apiSuffix = call.getString("apiSuffix");
        String deviceUUID = call.getString("deviceUUID");
        String deviceType = call.getString("deviceType");
        JSObject authPayload = call.getObject("authPayload");
        String macAddress = call.getString("macAddress");

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

        if ("BLE".equals(deviceType)) {
            serviceIntent.putExtra("macAddress", macAddress);
        }

        getContext().startService(serviceIntent);
        this.bobj = new BackgroundService(getContext());

        if (notificationReceiver == null) {
          notificationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
              String notification = intent.getStringExtra("notification");

              JSObject notificationData = new JSObject();
              notificationData.put("notification", notification);
              notifyListeners("onNotification", notificationData);
            }
          };
          LocalBroadcastManager.getInstance(getContext()).registerReceiver(notificationReceiver, new IntentFilter("com.mobicloud.notifcationLogs"));
        }

      if (logReceiver == null) {
        logReceiver = new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            String LOG = intent.getStringExtra("LOG");
            String TAG = intent.getStringExtra("TAG");
            JSObject logData = new JSObject();

            logData.put("LOG", LOG);
            logData.put("TAG",TAG);
            notifyListeners("onLogs", logData);
          }
        };
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(logReceiver, new IntentFilter("com.mobicloud.logs"));
      }
        call.resolve();
    }

    @PluginMethod
    public void connectBleDevice(PluginCall call) {
      String macAddress = call.getString("macAddress");

      if (macAddress == null || macAddress.isEmpty()) {
        call.reject("Device ID is required");
        return;
      }

      BackgroundService service = BackgroundService.instance;

      if (service == null) {
        call.reject("BackgroundService is not running. Ensure the service is started.");
        return;
      }

      try {
        service.connectToDevice(macAddress);
        call.resolve();
      } catch (Exception e) {
        Log.e("BackgroundService", "Error connecting to BLE device", e);
        call.reject("BLE connection failed: " + e.getMessage());
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

        if (mqttReceiver == null) {
          mqttReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
              String mqttPublishStatus = intent.getStringExtra("mqttPublishStatus");

              JSObject data = new JSObject();

              data.put("mqttPublishStatus", mqttPublishStatus);
              notifyListeners("onMqttMessage", data);
            }
          };
          LocalBroadcastManager.getInstance(getContext()).registerReceiver(mqttReceiver, new IntentFilter("com.mobicloud.MQTT_MESSAGE"));
        }

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

      // Register BroadcastReceiver if not already


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
    }

    @Override
    protected void handleOnDestroy() {
        if (mqttReceiver != null) {
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mqttReceiver);
            mqttReceiver = null;
        }
    }
}
