import Foundation

@objc public class BackgroundService: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}
