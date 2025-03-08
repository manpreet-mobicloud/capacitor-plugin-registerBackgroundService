export interface BackgroundServicePlugin {
  /**
   * Start Background Service.
   */
  StartBackgroundService(options: {
    deviceId: string,
    BrokerUrl: string,
    username: string,
    password: string,
    topicTOSubscribe: string,
    topicTOpublish: string,
    messageTOPublish: {
      deviceId: string | null,
      message: string
    }
  }): Promise<void>;

  /**
     * Request notification permissions.
     * Requests the user's permission to send notifications.
     * 
     * @returns A promise that resolves with the result of the permission request.
     *          - `granted`: A boolean indicating whether the permission was granted.
     */
  requestNotificationPermission(): Promise<{ granted: boolean }>;

}
