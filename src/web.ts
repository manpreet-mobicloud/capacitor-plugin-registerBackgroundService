import { WebPlugin } from '@capacitor/core';

import type { BackgroundServicePlugin } from './definitions';

export class BackgroundServiceWeb extends WebPlugin implements BackgroundServicePlugin {
  StartBackgroundService(_options: { 
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
  }): Promise<void> {
    throw new Error('Method not implemented.');
  }

  connectToBroker(): Promise<void> {
    throw new Error('Method not implemented.');
  }
  subscribeToTopic(_options: { topic: string }): Promise<void> {
    throw new Error('Method not implemented.');
  }
  publishMessage(_options: { topic:string, message: JSON }): Promise<void> {
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
