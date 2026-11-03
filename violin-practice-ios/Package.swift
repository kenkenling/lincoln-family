// swift-tools-version: 6.2

import PackageDescription

let package = Package(
    name: "LKPractice",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: [
        .library(
            name: "LKPracticeCore",
            targets: ["LKPracticeCore"]
        ),
        .library(
            name: "LKPracticeAppSupport",
            targets: ["LKPracticeAppSupport"]
        ),
        .executable(
            name: "LKPracticeCoreChecks",
            targets: ["LKPracticeCoreChecks"]
        ),
    ],
    targets: [
        .target(
            name: "LKPracticeCore"
        ),
        .executableTarget(
            name: "LKPracticeCoreChecks",
            dependencies: ["LKPracticeCore"],
            path: "Checks"
        ),
        .target(
            name: "LKPracticeAppSupport",
            dependencies: ["LKPracticeCore"],
            path: "AppSupport"
        ),
        .testTarget(
            name: "LKPracticeCoreTests",
            dependencies: ["LKPracticeCore"]
        ),
    ]
)
