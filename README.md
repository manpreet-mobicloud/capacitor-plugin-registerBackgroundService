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

* [`echo(...)`](#echo)
* [`connectToBroker()`](#connecttobroker)
* [`subscribeToTopic(...)`](#subscribetotopic)
* [`publishMessage(...)`](#publishmessage)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### echo(...)

```typescript
echo(options: { value: string; }) => Promise<{ value: string; }>
```

Echo the given string.

| Param         | Type                            | Description |
| ------------- | ------------------------------- | ----------- |
| **`options`** | <code>{ value: string; }</code> | : string }  |

**Returns:** <code>Promise&lt;{ value: string; }&gt;</code>

--------------------


### connectToBroker()

```typescript
connectToBroker() => Promise<void>
```

Connect to the MQTT broker.

--------------------


### subscribeToTopic(...)

```typescript
subscribeToTopic(options: { topic: string; }) => Promise<void>
```

Subscribe to an MQTT topic.

| Param         | Type                            | Description |
| ------------- | ------------------------------- | ----------- |
| **`options`** | <code>{ topic: string; }</code> | : string }  |

--------------------


### publishMessage(...)

```typescript
publishMessage(options: { topic: string; message: string; }) => Promise<void>
```

Publish a message to an MQTT topic.

| Param         | Type                                             | Description |
| ------------- | ------------------------------------------------ | ----------- |
| **`options`** | <code>{ topic: string; message: string; }</code> | : string }  |

--------------------

</docgen-api>
