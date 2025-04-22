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
    BrokerUrl: string,
    username: string,
    password: string,
    topicTOSubscribe: string,
    topicTOpublish: string,
    authTopicToSubscribe: string,
    authPayload: {
      parameters: {
        header: string,
      }
    },
    messageToPublishForAlerts: {},
    messageToPublishForGasComsumtion: {},
  }): Promise<void>;

  /**
   * Request notification permissions.
   */
  requestNotificationPermission(): Promise<{ granted: boolean }>;

  /**
   * Listen for MQTT messages from native background service.
   */
  addListener(
    eventName: 'onMqttMessage',
    listenerFunc: (data: { message: string }) => void
  ): Promise<PluginListenerHandle>;
}
