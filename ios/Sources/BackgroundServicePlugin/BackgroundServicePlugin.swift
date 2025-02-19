import Foundation
import Capacitor
import BackgroundTasks
import UserNotifications
import CoreBluetooth
import CocoaMQTT
 
// BackgroundServicePlugin: A Capacitor plugin for managing MQTT connections, Bluetooth, and notifications.
@objc(BackgroundServicePlugin)
public class BackgroundServicePlugin: CAPPlugin, CAPBridgedPlugin, CBCentralManagerDelegate, UNUserNotificationCenterDelegate {
    
    // MQTT configuration
    private var mqttClient: CocoaMQTT?
    private let brokerURL = "platform.iot.tatacommunications.com" // MQTT broker URL
    private let brokerPort: UInt16 = 8883 // MQTT broker port (SSL)
    private let username = "fe_regulator" // MQTT username
    private let password = "FeRegulator@123" // MQTT password
 
    // Bluetooth central manager
    private var centralManager: CBCentralManager!
    public let identifier = "BackgroundServicePlugin" // Plugin identifier
    public let jsName = "MqttService" // JS name for the plugin
 
    // List of methods exposed to JavaScript
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "load", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestNotificationPermission", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "connectToBroker", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "subscribeToTopic", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "publishMessage", returnType: CAPPluginReturnPromise),
    ]
 
    // Called when the plugin is loaded
    @objc public override func load() {
        super.load()
        // Set the notification delegate and initialize the Bluetooth manager
        UNUserNotificationCenter.current().delegate = self
        centralManager = CBCentralManager(delegate: self, queue: nil)
        requestBluetoothPermission()
    }
 
    // Request user permission for notifications
    @objc func requestNotificationPermission(_ call: CAPPluginCall) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { (granted, error) in
            if let error = error {
                call.reject("Permission request failed: \(error.localizedDescription)")
                return
            }
            call.resolve(["granted": granted])
        }
    }
 
    // Request permission to use Bluetooth
    @objc func requestBluetoothPermission() {
        if centralManager.state == .poweredOff {
            sendBluetoothNotification() // Notify user if Bluetooth is disabled
        } else {
            updateNotification(content: "Service is running in the background.")
        }
    }
 
    // Handle Bluetooth state changes
    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            print("Bluetooth is powered on.")
            updateNotification(content: "Service is running in the background.")
        case .poweredOff:
            print("Bluetooth is powered off.")
            sendBluetoothNotification() // Notify user to enable Bluetooth
        default:
            break
        }
    }
 
    // Send a notification prompting the user to enable Bluetooth
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
 
    // Update the notification content
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
 
    // Handle foreground notification display
    public func userNotificationCenter(_ center: UNUserNotificationCenter,
                                       willPresent notification: UNNotification,
                                       withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.alert, .badge, .sound])
    }
 
    // Handle notification tapped while app is in the background
    public func userNotificationCenter(_ center: UNUserNotificationCenter,
                                       didReceive response: UNNotificationResponse,
                                       withCompletionHandler completionHandler: @escaping () -> Void) {
        print("Notification received in background with info: \(response.notification.request.content.userInfo)")
        completionHandler()
    }
 
    // Connect to the MQTT broker
    @objc func connectToBroker(_ call: CAPPluginCall) {
        let clientID = "FE-Regulator-client-\(UUID().uuidString)" // Generate a unique client ID
        mqttClient = CocoaMQTT(clientID: clientID, host: brokerURL, port: brokerPort)
        
        guard let mqttClient = mqttClient else {
            call.reject("Failed to initialize MQTT client")
            return
        }
        
        mqttClient.username = username
        mqttClient.password = password
        mqttClient.keepAlive = 10
        mqttClient.enableSSL = true
        mqttClient.autoReconnect = true
        
        mqttClient.didConnectAck = { [weak self] mqtt, ack in
            if ack == .accept {
                self?.notifyListeners("connected", data: ["status": "connected"], retainUntilConsumed: false)
                call.resolve(["status": "connected"])
            } else {
                call.reject("Failed to connect to MQTT broker")
            }
        }
        
        mqttClient.didDisconnect = { [weak self] mqtt, error in
            self?.notifyListeners("connectionLost", data: ["error": error?.localizedDescription ?? "Unknown error"], retainUntilConsumed: false)
        }
        
        mqttClient.didReceiveMessage = { [weak self] mqtt, message, id in
            self?.notifyListeners("messageReceived", data: [
                "topic": message.topic,
                "payload": message.string ?? ""
            ], retainUntilConsumed: true)
        }
        
        mqttClient.connect()
    }
 
    // Subscribe to an MQTT topic
    @objc func subscribeToTopic(_ call: CAPPluginCall) {
        guard let mqttClient = mqttClient, mqttClient.connState == .connected else {
            call.reject("MQTT client is not connected or initialized")
            return
        }
        
        guard let topic = call.getString("topic"), !topic.isEmpty else {
            call.reject("Topic is required")
            return
        }
        
        mqttClient.subscribe(topic, qos: .qos1)
        call.resolve(["status": "Subscribed to topic", "topic": topic])
    }
 
    // Publish a message to an MQTT topic
    @objc func publishMessage(_ call: CAPPluginCall) {
        guard let mqttClient = mqttClient, mqttClient.connState == .connected else {
            call.reject("MQTT client is not connected")
            return
        }
        
        guard let topic = call.getString("topic"), !topic.isEmpty else {
            call.reject("Topic is required")
            return
        }
        
        guard let messageObject = call.getObject("message"),
            let messageData = try? JSONSerialization.data(withJSONObject: messageObject, options: []),
            let messageString = String(data: messageData, encoding: .utf8) else {
            call.reject("Message serialization failed")
            return
        }
        
        mqttClient.publish(topic, withString: messageString, qos: .qos1, retained: false)
        call.resolve(["status": "Message published successfully", "topic": topic])
    }
}
 