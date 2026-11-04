@preconcurrency import AVFoundation
import Combine
import SwiftUI
import UIKit

@MainActor
final class StudentCameraRecorder: NSObject, ObservableObject {
    @Published private(set) var cameraAuthorized = false
    @Published private(set) var microphoneAuthorized = false
    @Published private(set) var isPreparing = false
    @Published private(set) var isReady = false
    @Published private(set) var isStarting = false
    @Published private(set) var isRecording = false
    @Published private(set) var countdown = 0
    @Published private(set) var statusMessage = "Preparing your camera…"

    let captureSession = AVCaptureSession()

    private let movieOutput = AVCaptureMovieFileOutput()
    private var activeDestination: RecordingDestination?
    private var activeCameraPosition: AVCaptureDevice.Position = .unspecified
    private var countdownTask: Task<Void, Never>?
    private var onSaved: ((RecordingDestination) -> Void)?
    private var onFailed: ((RecordingDestination) -> Void)?
    private var stopSessionAfterRecording = false

    var isBusy: Bool {
        countdown > 0 || isStarting || isRecording
    }

    var previewShouldMirror: Bool {
        activeCameraPosition == .front
    }

    var cameraPermissionDenied: Bool {
        AVCaptureDevice.authorizationStatus(for: .video) == .denied ||
            AVCaptureDevice.authorizationStatus(for: .video) == .restricted
    }

    func prepare() async {
        guard !isPreparing, !isReady else {
            if isReady, !captureSession.isRunning {
                captureSession.startRunning()
            }
            return
        }

        isPreparing = true
        statusMessage = "Checking camera access…"
        cameraAuthorized = await Self.requestAccessIfNeeded(for: .video)
        guard cameraAuthorized else {
            isPreparing = false
            statusMessage = "Camera access is needed to show and record the student."
            return
        }

        microphoneAuthorized = await Self.requestAccessIfNeeded(for: .audio)

        do {
            try configureCaptureSession()
            captureSession.startRunning()
            isReady = true
            statusMessage = microphoneAuthorized
                ? "Camera and microphone are ready."
                : "Camera is ready. Microphone access is off, so the take will be silent."
        } catch {
            statusMessage = error.localizedDescription
        }
        isPreparing = false
    }

    func beginRecording(
        destination: RecordingDestination,
        skipCountdown: Bool,
        onSaved: @escaping (RecordingDestination) -> Void,
        onFailed: @escaping (RecordingDestination) -> Void
    ) {
        guard isReady, !isBusy else {
            return
        }
        self.onSaved = onSaved
        self.onFailed = onFailed
        activeDestination = destination
        countdownTask?.cancel()

        if skipCountdown {
            startRecording()
            return
        }

        statusMessage = "Get ready…"
        countdownTask = Task { [weak self] in
            guard let self else {
                return
            }
            for value in stride(from: 3, through: 1, by: -1) {
                guard !Task.isCancelled else {
                    return
                }
                countdown = value
                try? await Task.sleep(for: .seconds(1))
            }
            guard !Task.isCancelled else {
                return
            }
            countdown = 0
            startRecording()
        }
    }

    func stopRecording() {
        if countdown > 0 {
            countdownTask?.cancel()
            countdownTask = nil
            countdown = 0
            if let destination = activeDestination {
                onFailed?(destination)
            }
            clearActiveRecording()
            statusMessage = "Recording canceled."
            return
        }
        guard movieOutput.isRecording || isStarting else {
            return
        }
        statusMessage = "Saving your take…"
        movieOutput.stopRecording()
    }

    func stopCaptureSession() {
        let hadCountdown = countdown > 0
        countdownTask?.cancel()
        countdownTask = nil
        countdown = 0
        if hadCountdown {
            if let destination = activeDestination {
                onFailed?(destination)
            }
            clearActiveRecording()
            statusMessage = "Recording canceled because the camera screen closed."
        }
        if movieOutput.isRecording || isStarting {
            stopSessionAfterRecording = true
            movieOutput.stopRecording()
        } else if captureSession.isRunning {
            captureSession.stopRunning()
        }
    }

    private func configureCaptureSession() throws {
        guard !isReady else {
            return
        }

        captureSession.beginConfiguration()
        defer {
            captureSession.commitConfiguration()
        }
        captureSession.sessionPreset = .high
        captureSession.automaticallyConfiguresApplicationAudioSession = false

        guard let camera = Self.preferredCamera() else {
            throw CameraRecorderError.noCamera
        }
        let videoInput = try AVCaptureDeviceInput(device: camera)
        guard captureSession.canAddInput(videoInput) else {
            throw CameraRecorderError.cameraUnavailable
        }
        captureSession.addInput(videoInput)
        activeCameraPosition = camera.position

        if microphoneAuthorized, let microphone = AVCaptureDevice.default(for: .audio) {
            let audioInput = try AVCaptureDeviceInput(device: microphone)
            if captureSession.canAddInput(audioInput) {
                captureSession.addInput(audioInput)
            }
        }

        guard captureSession.canAddOutput(movieOutput) else {
            throw CameraRecorderError.recordingUnavailable
        }
        captureSession.addOutput(movieOutput)
    }

    private func startRecording() {
        guard
            let destination = activeDestination,
            isReady,
            !movieOutput.isRecording
        else {
            return
        }

        do {
            if FileManager.default.fileExists(atPath: destination.fileURL.path) {
                try FileManager.default.removeItem(at: destination.fileURL)
            }
            try configureAudioSession()
            configureVideoConnection()
            isStarting = true
            statusMessage = "Starting recording…"
            movieOutput.startRecording(
                to: destination.fileURL,
                recordingDelegate: self
            )
        } catch {
            onFailed?(destination)
            clearActiveRecording()
            statusMessage = "The recording could not start: \(error.localizedDescription)"
        }
    }

    private func configureAudioSession() throws {
        guard microphoneAuthorized else {
            return
        }
        let audioSession = AVAudioSession.sharedInstance()
        try audioSession.setCategory(
            .playAndRecord,
            mode: .videoRecording,
            options: [.defaultToSpeaker, .allowBluetoothHFP, .mixWithOthers]
        )
        try audioSession.setActive(true)
    }

    private func configureVideoConnection() {
        guard let connection = movieOutput.connection(with: .video) else {
            return
        }
        let angle = Self.currentVideoRotationAngle()
        if connection.isVideoRotationAngleSupported(angle) {
            connection.videoRotationAngle = angle
        }
        if connection.isVideoMirroringSupported {
            connection.automaticallyAdjustsVideoMirroring = false
            connection.isVideoMirrored = activeCameraPosition == .front
        }
    }

    private func clearActiveRecording() {
        activeDestination = nil
        onSaved = nil
        onFailed = nil
        isStarting = false
        isRecording = false
    }

    private func finishRecording(outputURL: URL, error: Error?) {
        guard let destination = activeDestination else {
            return
        }
        let errorAllowsSavedRecording = (error as NSError?)?
            .userInfo[AVErrorRecordingSuccessfullyFinishedKey] as? Bool == true
        let savedSuccessfully = (error == nil || errorAllowsSavedRecording) &&
            FileManager.default.fileExists(atPath: outputURL.path)

        if savedSuccessfully {
            statusMessage = "Take saved privately."
            onSaved?(destination)
        } else {
            try? FileManager.default.removeItem(at: outputURL)
            statusMessage = error.map {
                "The recording could not be saved: \($0.localizedDescription)"
            } ?? "The recording could not be saved."
            onFailed?(destination)
        }
        clearActiveRecording()

        if stopSessionAfterRecording {
            stopSessionAfterRecording = false
            captureSession.stopRunning()
        }
    }

    private static func requestAccessIfNeeded(
        for mediaType: AVMediaType
    ) async -> Bool {
        switch AVCaptureDevice.authorizationStatus(for: mediaType) {
        case .authorized:
            return true
        case .notDetermined:
            return await AVCaptureDevice.requestAccess(for: mediaType)
        case .denied, .restricted:
            return false
        @unknown default:
            return false
        }
    }

    private static func preferredCamera() -> AVCaptureDevice? {
        let discovery = AVCaptureDevice.DiscoverySession(
            deviceTypes: [.builtInWideAngleCamera],
            mediaType: .video,
            position: .front
        )
        return discovery.devices.first ??
            AVCaptureDevice.default(
                .builtInWideAngleCamera,
                for: .video,
                position: .back
            )
    }

    private static func currentVideoRotationAngle() -> CGFloat {
        let orientation = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first(where: { $0.activationState == .foregroundActive })?
            .interfaceOrientation
        return switch orientation {
        case .portrait:
            90
        case .portraitUpsideDown:
            270
        case .landscapeLeft:
            0
        case .landscapeRight:
            180
        default:
            90
        }
    }
}

extension StudentCameraRecorder: AVCaptureFileOutputRecordingDelegate {
    nonisolated func fileOutput(
        _ output: AVCaptureFileOutput,
        didStartRecordingTo fileURL: URL,
        from connections: [AVCaptureConnection]
    ) {
        Task { @MainActor [weak self] in
            self?.isStarting = false
            self?.isRecording = true
            self?.statusMessage = "Recording. Tap Stop when this take is finished."
        }
    }

    nonisolated func fileOutput(
        _ output: AVCaptureFileOutput,
        didFinishRecordingTo outputFileURL: URL,
        from connections: [AVCaptureConnection],
        error: Error?
    ) {
        Task { @MainActor [weak self] in
            self?.finishRecording(outputURL: outputFileURL, error: error)
        }
    }
}

private enum CameraRecorderError: Error, LocalizedError {
    case noCamera
    case cameraUnavailable
    case recordingUnavailable

    var errorDescription: String? {
        switch self {
        case .noCamera:
            return "No usable camera was found on this device."
        case .cameraUnavailable:
            return "The camera is currently unavailable."
        case .recordingUnavailable:
            return "Video recording is currently unavailable."
        }
    }
}

struct StudentCameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    let isMirrored: Bool

    func makeUIView(context: Context) -> CameraPreviewView {
        let view = CameraPreviewView()
        view.previewLayer.session = session
        view.isMirrored = isMirrored
        return view
    }

    func updateUIView(_ view: CameraPreviewView, context: Context) {
        if view.previewLayer.session !== session {
            view.previewLayer.session = session
        }
        view.isMirrored = isMirrored
        view.updateConnection()
    }
}

final class CameraPreviewView: UIView {
    var isMirrored = true

    override class var layerClass: AnyClass {
        AVCaptureVideoPreviewLayer.self
    }

    var previewLayer: AVCaptureVideoPreviewLayer {
        layer as! AVCaptureVideoPreviewLayer
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        previewLayer.videoGravity = .resizeAspectFill
        backgroundColor = .black
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        previewLayer.videoGravity = .resizeAspectFill
        backgroundColor = .black
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        updateConnection()
    }

    func updateConnection() {
        guard let connection = previewLayer.connection else {
            return
        }
        let orientation = window?.windowScene?.interfaceOrientation
        let angle: CGFloat = switch orientation {
        case .portrait:
            90
        case .portraitUpsideDown:
            270
        case .landscapeLeft:
            0
        case .landscapeRight:
            180
        default:
            90
        }
        if connection.isVideoRotationAngleSupported(angle) {
            connection.videoRotationAngle = angle
        }
        if connection.isVideoMirroringSupported {
            connection.automaticallyAdjustsVideoMirroring = false
            connection.isVideoMirrored = isMirrored
        }
    }
}
