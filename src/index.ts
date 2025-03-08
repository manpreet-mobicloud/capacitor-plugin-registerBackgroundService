import { registerPlugin } from '@capacitor/core';

import type { BackgroundServicePlugin } from './definitions';

const BackgroundService = registerPlugin<BackgroundServicePlugin>('BackgroundService', {
  web: () => import('./web').then((m) => new m.BackgroundServiceWeb()),
});

export * from './definitions';
export { BackgroundService };
