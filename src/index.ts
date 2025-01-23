import { registerPlugin } from '@capacitor/core';

import type { BackgroundServicePlugin } from './definitions';

const MqttService = registerPlugin<BackgroundServicePlugin>('MqttService', {
  web: () => import('./web').then((m) => new m.BackgroundServiceWeb()),
});

export * from './definitions';
export { MqttService };
