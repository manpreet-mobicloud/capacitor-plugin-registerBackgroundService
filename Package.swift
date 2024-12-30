// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapacitorPluginBackgroundservice",
    platforms: [.iOS(.v13)],
    products: [
        .library(
            name: "CapacitorPluginBackgroundservice",
            targets: ["BackgroundServicePlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", branch: "main")
    ],
    targets: [
        .target(
            name: "BackgroundServicePlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/BackgroundServicePlugin"),
        .testTarget(
            name: "BackgroundServicePluginTests",
            dependencies: ["BackgroundServicePlugin"],
            path: "ios/Tests/BackgroundServicePluginTests")
    ]
)