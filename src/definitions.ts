export interface BackgroundServicePlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
