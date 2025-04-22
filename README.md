# capacitor-plugin-backgroundservice

This plugin is designed to start the application as a foreground service, allowing it to run continuously in the background even if the app is killed. It also ensures the service restarts automatically when the Android device is booted or restarted.

# Important Note

This plugin does not expose any explicit methods for use in JavaScript. All functionality is handled natively through Java code and configurations in the Android manifest file. You will need to set up the required native components to enable and customize the background service behavior.

## Install

```bash
npm install capacitor-plugin-backgroundservice
npx cap sync
```

## API

<docgen-index>

* [`StartBackgroundService(...)`](#startbackgroundservice)
* [`requestNotificationPermission()`](#requestnotificationpermission)
* [`addListener('onMqttMessage', ...)`](#addlisteneronmqttmessage-)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### StartBackgroundService(...)

```typescript
StartBackgroundService(options: { baseURL: string; basicAUTH: string; apiSuffix: string; deviceUUID: string; deviceType: string | null; macAddress: string | null; BrokerUrl: string; username: string; password: string; topicTOSubscribe: string; topicTOpublish: string; authTopicToSubscribe: string; authPayload: { parameters: { header: string; }; }; messageToPublishForAlerts: {}; messageToPublishForGasComsumtion: {}; }) => Promise<void>
```

Start Background Service.

| Param         | Type                                                                                                                                                                                                                                                                                                                                                                                                               |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **`options`** | <code>{ baseURL: string; basicAUTH: string; apiSuffix: string; deviceUUID: string; deviceType: string \| null; macAddress: string \| null; BrokerUrl: string; username: string; password: string; topicTOSubscribe: string; topicTOpublish: string; authTopicToSubscribe: string; authPayload: { parameters: { header: string; }; }; messageToPublishForAlerts: {}; messageToPublishForGasComsumtion: {}; }</code> |

--------------------


### requestNotificationPermission()

```typescript
requestNotificationPermission() => Promise<{ granted: boolean; }>
```

Request notification permissions.

**Returns:** <code>Promise&lt;{ granted: boolean; }&gt;</code>

--------------------


### addListener('onMqttMessage', ...)

```typescript
addListener(eventName: 'onMqttMessage', listenerFunc: (data: { message: string; }) => void) => Promise<PluginListenerHandle>
```

Listen for MQTT messages from native background service.

| Param              | Type                                                 |
| ------------------ | ---------------------------------------------------- |
| **`eventName`**    | <code>'onMqttMessage'</code>                         |
| **`listenerFunc`** | <code>(data: { message: string; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### Interfaces


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |

</docgen-api>
