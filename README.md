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

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### StartBackgroundService(...)

```typescript
StartBackgroundService(options: { deviceId: string; BrokerUrl: string; username: string; password: string; topicTOSubscribe: string; topicTOpublish: string; messageTOPublish: { deviceId: string | null; message: string; }; }) => Promise<void>
```

Start Background Service.

| Param         | Type                                                                                                                                                                                                          |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`options`** | <code>{ deviceId: string; BrokerUrl: string; username: string; password: string; topicTOSubscribe: string; topicTOpublish: string; messageTOPublish: { deviceId: string \| null; message: string; }; }</code> |

--------------------


### requestNotificationPermission()

```typescript
requestNotificationPermission() => Promise<{ granted: boolean; }>
```

Request notification permissions.
Requests the user's permission to send notifications.

**Returns:** <code>Promise&lt;{ granted: boolean; }&gt;</code>

--------------------

</docgen-api>
