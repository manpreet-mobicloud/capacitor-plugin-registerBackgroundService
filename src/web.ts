import { WebPlugin } from '@capacitor/core';

import type { BackgroundServicePlugin } from './definitions';

export class BackgroundServiceWeb extends WebPlugin implements BackgroundServicePlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
