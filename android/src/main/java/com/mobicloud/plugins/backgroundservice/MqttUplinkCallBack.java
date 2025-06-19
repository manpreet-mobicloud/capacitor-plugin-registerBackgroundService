package com.mobicloud.plugins.backgroundservice;

public interface MqttUplinkCallBack {
    void onSuccess();

    void onFailure(Throwable exception);
}
