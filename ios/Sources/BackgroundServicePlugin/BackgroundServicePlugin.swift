import Foundation
import Capacitor
import BackgroundTasks
import UserNotifications
import CoreBluetooth

@objc(BackgroundServicePlugin)
public class BackgroundServicePlugin: CAPPlugin,CAPBridgedPlugin,CBCentralManagerDelegate,UNUserNotificationCenterDelegate {
    
    private var centralManager: CBCentralManager!
    public let identifier = "BackgroundServicePlugin"
    public let jsName = "BackgroundService"

    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "load", returnType: CAPPluginReturnPromise),
                CAPPluginMethod(name: "requestNotificationPermission", returnType: CAPPluginReturnPromise),

    ]

    @objc public override func load() {
        super.load()
        UNUserNotificationCenter.current().delegate = self
        centralManager = CBCentralManager(delegate: self, queue: nil)
        requestBluetoothPermission();
    }

    @objc func requestNotificationPermission(_ call: CAPPluginCall) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { (granted, error) in
            if let error = error {
                call.reject("Permission request failed: \(error.localizedDescription)")
                return
            }

            if granted {
                call.resolve(["granted": true])
            } else {
                call.resolve(["granted": false])
            }
        }
    }

    @objc func requestBluetoothPermission() {
        if centralManager.state == .poweredOff {
            sendBluetoothNotification()
        } else {
            updateNotification(content: "Service is running in the background.")
        }
    }

    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            print("Bluetooth is powered on.")
            updateNotification(content: "Service is running in the background.")
        case .poweredOff:
            print("Bluetooth is powered off.")
            sendBluetoothNotification()
        default:
            break
        }
    }

    @objc func sendBluetoothNotification() {
        let content = UNMutableNotificationContent()
        content.title = "Bluetooth is Disabled"
        content.body = "Bluetooth is Disabled. Tap to Enable Bluetooth."
        content.sound = .default

        let openSettingsAction = UNNotificationAction(
            identifier: "openSettings",
            title: "Open Bluetooth Settings",
            options: .foreground
        )

        let category = UNNotificationCategory(
            identifier: "bluetoothCategory",
            actions: [openSettingsAction],
            intentIdentifiers: [],
            options: []
        )

        UNUserNotificationCenter.current().setNotificationCategories([category])
        content.categoryIdentifier = "bluetoothCategory"

        let request = UNNotificationRequest(
            identifier: "bluetoothDisabledNotification",
            content: content,
            trigger: nil
        )

        UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
    }

    @objc func updateNotification(content: String) {
        let notificationContent = UNMutableNotificationContent()
        notificationContent.title = "Background Service"
        notificationContent.body = content
        notificationContent.sound = .default

        let request = UNNotificationRequest(
            identifier: "backgroundServiceNotification",
            content: notificationContent,
            trigger: nil
        )

        UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
    }

    public func userNotificationCenter(_ center: UNUserNotificationCenter,
                                       willPresent notification: UNNotification,
                                       withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        // Display the notification even when in foreground
        completionHandler([.alert,.badge,.sound])
      }
    
    public func userNotificationCenter(_ center: UNUserNotificationCenter,
                                   didReceive response: UNNotificationResponse,
                                   withCompletionHandler completionHandler: @escaping () -> Void) {
    // Handle notification tapped in the background
    let userInfo = response.notification.request.content.userInfo
    print("Notification received in background with info: \(userInfo)")
    
    // Add custom behavior if needed
    completionHandler()
}
}
