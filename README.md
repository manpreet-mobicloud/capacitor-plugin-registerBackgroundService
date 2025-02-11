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
* [`connectToBroker(...)`](#connecttobroker)
* [`subscribeToTopic(...)`](#subscribetotopic)
* [`publishMessage(...)`](#publishmessage)
* [`requestNotificationPermission()`](#requestnotificationpermission)
* [Interfaces](#interfaces)

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


### connectToBroker(...)

```typescript
connectToBroker(options: { BrokerUrl: string; username: string; password: string; }) => Promise<void>
```

Connect to the MQTT broker.

| Param         | Type                                                                    |
| ------------- | ----------------------------------------------------------------------- |
| **`options`** | <code>{ BrokerUrl: string; username: string; password: string; }</code> |

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
publishMessage(options: { topic: string; message: JSON; }) => Promise<void>
```

Publish a message to an MQTT topic.

| Param         | Type                                                               | Description                |
| ------------- | ------------------------------------------------------------------ | -------------------------- |
| **`options`** | <code>{ topic: string; message: <a href="#json">JSON</a>; }</code> | :string, message: string } |

--------------------


### requestNotificationPermission()

```typescript
requestNotificationPermission() => Promise<{ granted: boolean; }>
```

Request notification permissions.
Requests the user's permission to send notifications.

**Returns:** <code>Promise&lt;{ granted: boolean; }&gt;</code>

--------------------


### Interfaces


#### JSON

An intrinsic object that provides functions to convert JavaScript values to and from the JavaScript Object Notation (<a href="#json">JSON</a>) format.

| Method        | Signature                                                                                                                                  | Description                                                                                    |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------- |
| **parse**     | (text: string, reviver?: ((this: any, key: string, value: any) =&gt; any) \| undefined) =&gt; any                                          | Converts a JavaScript Object Notation (<a href="#json">JSON</a>) string into an object.        |
| **stringify** | (value: any, replacer?: ((this: any, key: string, value: any) =&gt; any) \| undefined, space?: string \| number \| undefined) =&gt; string | Converts a JavaScript value to a JavaScript Object Notation (<a href="#json">JSON</a>) string. |
| **stringify** | (value: any, replacer?: (string \| number)[] \| null \| undefined, space?: string \| number \| undefined) =&gt; string                     | Converts a JavaScript value to a JavaScript Object Notation (<a href="#json">JSON</a>) string. |

</docgen-api>
