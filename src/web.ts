import { WebPlugin } from '@capacitor/core';

import type { BackgroundServicePlugin } from './definitions';

export class BackgroundServiceWeb extends WebPlugin implements BackgroundServicePlugin {
  connectToBroker(): Promise<void> {
    throw new Error('Method not implemented.');
  }
  subscribeToTopic(_options: { topic: string; }): Promise<void> {
    throw new Error('Method not implemented.');
  }
  publishMessage(_options: { message: string; }): Promise<void> {
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
