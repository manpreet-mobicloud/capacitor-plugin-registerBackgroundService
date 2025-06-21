import type { PluginListenerHandle } from '@capacitor/core';

export interface BackgroundServicePlugin {
  /**
   * Start Background Service.
   */
  StartBackgroundService(options: {
    baseURL : string,
    basicAUTH: string,
    apiSuffix: string,
    deviceUUID: string,
    deviceType: string | null,
    macAddress: string | null,
    authPayload: {
      parameters: {
        header: string,
      }
    },
  }): Promise<void>;

  /**
   * Request notification permissions.
   */
  requestNotificationPermission(): Promise<{ granted: boolean }>;

  connectBleDevice(options:{
    macAddress:string | null,
  }): Promise<void>;

  connectMqtt(options:{
    BrokerUrl:string,
    iOSBrokerUrl:string,
    username:string,
    password:string,
  }): Promise<{isMqttConnected:boolean}>;

  publishMessage(options: {
    topicToPublish:string,
    payload: string
  }) : Promise<{isMessagePublished:boolean}>;

  subscribeToTopic(options: {
    topicTOSubscribe: string
  }) : Promise<{isSubscriptionSuccess: boolean}>

  subscribeToAuthTopic(options: {
    authTopicToSubscribe: string
  }) : Promise<{isSubscriptionSuccess: boolean}>

  /**
   * Listen for MQTT messages from native background service.
   */
  addListener(
    eventName: string,
    listenerFunc: (data: any) => void
  ): Promise<PluginListenerHandle>;
}
