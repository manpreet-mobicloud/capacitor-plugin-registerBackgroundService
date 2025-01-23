export interface BackgroundServicePlugin {

    requestNotificationPermission(): Promise<{ granted: boolean }>;
    
}
