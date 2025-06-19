import { WebPlugin } from '@capacitor/core';

import type { BackgroundServicePlugin } from './definitions';

export class BackgroundServiceWeb extends WebPlugin implements BackgroundServicePlugin {
  StartBackgroundService(_options: {
    baseURL: string,
    basicAUTH: string,
    apiSuffix: string,
    deviceUUID: string,
    deviceType: string | null,
    // macAddress: string | null,
    authPayload: {
      parameters: {
        header: string,
      }
    },
  }): Promise<void> {
    throw new Error('Method not implemented.');
  }

  connectBleDevice(_options: { macAddress: string; }): Promise<void> {
    throw new Error('Method not implemented.');
  }

  connectMqtt(_options: { BrokerUrl: string; iOSBrokerUrl: string; username: string; password: string; }): Promise<{ isMqttConnected: boolean; }> {
    throw new Error('Method not implemented.');
  }

  subscribeToTopic(_options: { topicTOSubscribe: string; }): Promise<{ isSubscriptionSuccess: boolean; }> {
    throw new Error('Method not implemented.');
  }
  subscribeToAuthTopic(_options: { authTopicToSubscribe: string; }): Promise<{ isSubscriptionSuccess: boolean; }> {
    throw new Error('Method not implemented.');
  }

  publishMessage(_options: { topicToPublish: string; payload: string; }): Promise<{ isMessagePublished: boolean; }> {
    throw new Error('Method not implemented.');
  }

  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }

  async requestNotificationPermission(): Promise<{ granted: boolean }> {
    return { granted: false };
  }

}
