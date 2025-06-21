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
* [`connectBleDevice(...)`](#connectbledevice)
* [`connectMqtt(...)`](#connectmqtt)
* [`publishMessage(...)`](#publishmessage)
* [`subscribeToTopic(...)`](#subscribetotopic)
* [`subscribeToAuthTopic(...)`](#subscribetoauthtopic)
* [`addListener(string, ...)`](#addlistenerstring-)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### StartBackgroundService(...)

```typescript
StartBackgroundService(options: { baseURL: string; basicAUTH: string; apiSuffix: string; deviceUUID: string; deviceType: string | null; macAddress: string | null; authPayload: { parameters: { header: string; }; }; }) => Promise<void>
```

Start Background Service.

| Param         | Type                                                                                                                                                                                                   |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **`options`** | <code>{ baseURL: string; basicAUTH: string; apiSuffix: string; deviceUUID: string; deviceType: string \| null; macAddress: string \| null; authPayload: { parameters: { header: string; }; }; }</code> |

--------------------


### requestNotificationPermission()

```typescript
requestNotificationPermission() => Promise<{ granted: boolean; }>
```

Request notification permissions.

**Returns:** <code>Promise&lt;{ granted: boolean; }&gt;</code>

--------------------


### connectBleDevice(...)

```typescript
connectBleDevice(options: { macAddress: string | null; }) => Promise<void>
```

| Param         | Type                                         |
| ------------- | -------------------------------------------- |
| **`options`** | <code>{ macAddress: string \| null; }</code> |

--------------------


### connectMqtt(...)

```typescript
connectMqtt(options: { BrokerUrl: string; iOSBrokerUrl: string; username: string; password: string; }) => Promise<{ isMqttConnected: boolean; }>
```

| Param         | Type                                                                                          |
| ------------- | --------------------------------------------------------------------------------------------- |
| **`options`** | <code>{ BrokerUrl: string; iOSBrokerUrl: string; username: string; password: string; }</code> |

**Returns:** <code>Promise&lt;{ isMqttConnected: boolean; }&gt;</code>

--------------------


### publishMessage(...)

```typescript
publishMessage(options: { topicToPublish: string; payload: string; }) => Promise<{ isMessagePublished: boolean; }>
```

| Param         | Type                                                      |
| ------------- | --------------------------------------------------------- |
| **`options`** | <code>{ topicToPublish: string; payload: string; }</code> |

**Returns:** <code>Promise&lt;{ isMessagePublished: boolean; }&gt;</code>

--------------------


### subscribeToTopic(...)

```typescript
subscribeToTopic(options: { topicTOSubscribe: string; }) => Promise<{ isSubscriptionSuccess: boolean; }>
```

| Param         | Type                                       |
| ------------- | ------------------------------------------ |
| **`options`** | <code>{ topicTOSubscribe: string; }</code> |

**Returns:** <code>Promise&lt;{ isSubscriptionSuccess: boolean; }&gt;</code>

--------------------


### subscribeToAuthTopic(...)

```typescript
subscribeToAuthTopic(options: { authTopicToSubscribe: string; }) => Promise<{ isSubscriptionSuccess: boolean; }>
```

| Param         | Type                                           |
| ------------- | ---------------------------------------------- |
| **`options`** | <code>{ authTopicToSubscribe: string; }</code> |

**Returns:** <code>Promise&lt;{ isSubscriptionSuccess: boolean; }&gt;</code>

--------------------


### addListener(string, ...)

```typescript
addListener(eventName: string, listenerFunc: (data: any) => void) => Promise<PluginListenerHandle>
```

Listen for MQTT messages from native background service.

| Param              | Type                                |
| ------------------ | ----------------------------------- |
| **`eventName`**    | <code>string</code>                 |
| **`listenerFunc`** | <code>(data: any) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### Interfaces


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |

</docgen-api>
