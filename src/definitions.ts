export interface BackgroundServicePlugin {

    /**
     * Echo the given string.
     * @param options { value: string }
     */
    echo(options: { value: string }): Promise<{ value: string }>;
  
    /**
     * Connect to the MQTT broker.
     */
    connectToBroker(): Promise<void>;
  
    /**
     * Subscribe to an MQTT topic.
     * @param options { topic: string }
     */
    subscribeToTopic(options: { topic: string }): Promise<void>;
  
    /**
     * Publish a message to an MQTT topic.
     * @param options { message: string }
     */
    publishMessage(options: { topic:string, message: string }): Promise<void>;

  /**
     * Request notification permissions.
     * Requests the user's permission to send notifications.
     * 
     * @returns A promise that resolves with the result of the permission request.
     *          - `granted`: A boolean indicating whether the permission was granted.
     */
    requestNotificationPermission(): Promise<{ granted: boolean }>;
    
}
