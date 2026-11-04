import AVFoundation
import AVKit
import LKPracticeCore
import PDFKit
import SwiftUI
import WebKit

struct ReferenceVideoPanel: View {
    let sourceType: SourceType
    let referenceURL: String?
    let startSeconds: Int
    let localFileURL: URL?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            player
                .frame(maxWidth: .infinity)
                .aspectRatio(16 / 9, contentMode: .fit)
                .background(Color.black)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))

            HStack {
                Label("Teacher reference", systemImage: "play.rectangle.fill")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(LKColor.ink)
                Spacer()
                if startSeconds > 0 {
                    Text("Starts at \(durationLabel(startSeconds))")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(LKColor.muted)
                }
            }

            if sourceType == .youtube, let externalYouTubeURL {
                Link(destination: externalYouTubeURL) {
                    Label("Open in YouTube if playback is unavailable", systemImage: "arrow.up.right.square")
                        .font(.caption.weight(.semibold))
                }
            }
        }
        .padding(12)
        .cloudCard()
    }

    @ViewBuilder
    private var player: some View {
        switch sourceType {
        case .none:
            MediaUnavailableView(
                title: "No reference video",
                message: "A grown-up can attach one from Parent Space."
            )
        case .youtube:
            if let videoID = referenceURL.flatMap(YouTubeReference.videoID(from:)) {
                YouTubeWebPlayer(videoID: videoID, startSeconds: startSeconds)
                    .accessibilityLabel("Teacher reference video from YouTube")
            } else {
                MediaUnavailableView(
                    title: "YouTube link unavailable",
                    message: "Ask a grown-up to edit this assignment."
                )
            }
        case .localVideo:
            if let localFileURL, FileManager.default.fileExists(atPath: localFileURL.path) {
                LocalReferenceVideoPlayer(
                    fileURL: localFileURL,
                    startSeconds: startSeconds
                )
            } else {
                MediaUnavailableView(
                    title: "Video needs reattachment",
                    message: "The app’s private copy is missing."
                )
            }
        }
    }

    private var externalYouTubeURL: URL? {
        guard let referenceURL else {
            return nil
        }
        if let url = URL(string: referenceURL), url.scheme != nil {
            return url
        }
        guard let videoID = YouTubeReference.videoID(from: referenceURL) else {
            return nil
        }
        return URL(string: "https://www.youtube.com/watch?v=\(videoID)")
    }
}

struct StudentCameraPanel: View {
    @ObservedObject var recorder: StudentCameraRecorder
    let startLabel: String
    let destinationProvider: () -> RecordingDestination?
    let onSaved: (RecordingDestination) -> Void
    let onFailed: (RecordingDestination) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            cameraSurface
                .frame(maxWidth: .infinity)
                .aspectRatio(16 / 9, contentMode: .fit)
                .background(Color.black)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))

            HStack {
                Label("Student camera", systemImage: "video.fill")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(LKColor.ink)
                Spacer()
                if recorder.isRecording {
                    Label("REC", systemImage: "record.circle.fill")
                        .font(.caption.weight(.heavy))
                        .foregroundStyle(LKColor.danger)
                }
            }

            Text(recorder.statusMessage)
                .font(.caption)
                .foregroundStyle(LKColor.muted)
                .accessibilityAddTraits(.updatesFrequently)

            actionButton
        }
        .padding(12)
        .cloudCard()
        .task {
            await recorder.prepare()
        }
    }

    @ViewBuilder
    private var cameraSurface: some View {
        ZStack {
            Color.black

            if recorder.isReady {
                StudentCameraPreview(
                    session: recorder.captureSession,
                    isMirrored: recorder.previewShouldMirror
                )
                    .accessibilityLabel("Live student camera preview")
            } else {
                VStack(spacing: 10) {
                    Image(systemName: recorder.cameraPermissionDenied
                        ? "video.slash.fill"
                        : "video.fill")
                        .font(.largeTitle)
                    Text(recorder.statusMessage)
                        .font(.subheadline.weight(.semibold))
                        .multilineTextAlignment(.center)
                }
                .foregroundStyle(.white)
                .padding(20)
            }

            if recorder.countdown > 0 {
                Text("\(recorder.countdown)")
                    .font(.system(size: 74, weight: .black, design: .rounded))
                    .foregroundStyle(.white)
                    .shadow(radius: 8)
                    .accessibilityLabel("Recording starts in \(recorder.countdown)")
            }

            if recorder.isRecording {
                HStack(spacing: 6) {
                    Circle()
                        .fill(Color.red)
                        .frame(width: 11, height: 11)
                    Text("RECORDING")
                        .font(.caption2.weight(.black))
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .background(.black.opacity(0.68), in: Capsule())
                .padding(10)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                .accessibilityHidden(true)
            }
        }
    }

    @ViewBuilder
    private var actionButton: some View {
        if recorder.cameraPermissionDenied {
            Button {
                guard let settingsURL = URL(string: UIApplication.openSettingsURLString) else {
                    return
                }
                UIApplication.shared.open(settingsURL)
            } label: {
                Label("Open Camera Settings", systemImage: "gear")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(SkyButtonStyle())
        } else if recorder.isBusy {
            Button {
                recorder.stopRecording()
            } label: {
                Label(
                    recorder.countdown > 0 ? "Cancel" : "Stop & Save",
                    systemImage: "stop.circle.fill"
                )
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(SunnyButtonStyle())
        } else {
            Button {
                guard let destination = destinationProvider() else {
                    return
                }
                recorder.beginRecording(
                    destination: destination,
                    skipCountdown: reduceMotion,
                    onSaved: onSaved,
                    onFailed: onFailed
                )
            } label: {
                Label(startLabel, systemImage: "record.circle")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(SunnyButtonStyle())
            .disabled(!recorder.isReady || recorder.isPreparing)
            .opacity(recorder.isReady && !recorder.isPreparing ? 1 : 0.55)
        }
    }
}

private struct LocalReferenceVideoPlayer: View {
    let startSeconds: Int
    @State private var player: AVPlayer

    init(fileURL: URL, startSeconds: Int) {
        self.startSeconds = startSeconds
        _player = State(initialValue: AVPlayer(url: fileURL))
    }

    var body: some View {
        VideoPlayer(player: player)
            .onAppear {
                seekToAssignedStart()
            }
            .onDisappear {
                player.pause()
            }
            .accessibilityLabel("Teacher reference video")
    }

    private func seekToAssignedStart() {
        let start = CMTime(seconds: Double(max(0, startSeconds)), preferredTimescale: 600)
        player.seek(to: start, toleranceBefore: .zero, toleranceAfter: .zero)
    }
}

private struct YouTubeWebPlayer: UIViewRepresentable {
    let videoID: String
    let startSeconds: Int

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = .all
        configuration.websiteDataStore = .nonPersistent()

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.isOpaque = false
        webView.backgroundColor = .black
        webView.scrollView.isScrollEnabled = false
        loadIfNeeded(in: webView, coordinator: context.coordinator)
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        loadIfNeeded(in: webView, coordinator: context.coordinator)
    }

    private func loadIfNeeded(in webView: WKWebView, coordinator: Coordinator) {
        let key = "\(videoID)|\(startSeconds)"
        guard coordinator.loadedKey != key else {
            return
        }
        coordinator.loadedKey = key

        let safeStart = max(0, startSeconds)
        let html = """
        <!doctype html>
        <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
            <style>
              html, body, iframe { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; border: 0; overflow: hidden; }
            </style>
          </head>
          <body>
            <iframe
              src="https://www.youtube-nocookie.com/embed/\(videoID)?playsinline=1&rel=0&controls=1&start=\(safeStart)"
              title="Teacher reference video"
              allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture"
              allowfullscreen>
            </iframe>
          </body>
        </html>
        """
        webView.loadHTMLString(
            html,
            baseURL: URL(string: "https://www.youtube-nocookie.com")
        )
    }

    final class Coordinator {
        var loadedKey: String?
    }
}

struct PracticeRecordingViewer: View {
    @Environment(\.dismiss) private var dismiss
    let fileURL: URL
    let displayName: String
    @State private var player: AVPlayer

    init(fileURL: URL, displayName: String) {
        self.fileURL = fileURL
        self.displayName = displayName
        _player = State(initialValue: AVPlayer(url: fileURL))
    }

    var body: some View {
        NavigationStack {
            VideoPlayer(player: player)
                .background(Color.black)
                .navigationTitle("Practice Take")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("Done") {
                            dismiss()
                        }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        ShareLink(item: fileURL) {
                            Label("Share", systemImage: "square.and.arrow.up")
                        }
                    }
                }
                .safeAreaInset(edge: .bottom) {
                    Text(displayName)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.8))
                        .padding(10)
                        .frame(maxWidth: .infinity)
                        .background(Color.black)
                }
                .onAppear {
                    player.play()
                }
                .onDisappear {
                    player.pause()
                }
        }
    }
}

struct SheetMusicViewer: View {
    @Environment(\.dismiss) private var dismiss
    let fileURL: URL
    let displayName: String

    var body: some View {
        NavigationStack {
            content
                .background(Color(hex: 0x263238))
                .navigationTitle("Sheet Music")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("Done") {
                            dismiss()
                        }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        ShareLink(item: fileURL) {
                            Label("Share", systemImage: "square.and.arrow.up")
                        }
                    }
                }
        }
    }

    @ViewBuilder
    private var content: some View {
        if !FileManager.default.fileExists(atPath: fileURL.path) {
            SheetMusicUnavailableView(
                message: "LK Practice’s private copy of this attachment is missing."
            )
        } else if fileURL.pathExtension.lowercased() == "pdf" {
            if let document = PDFDocument(url: fileURL), document.pageCount > 0 {
                PDFDocumentView(document: document)
                    .accessibilityLabel("\(displayName), \(document.pageCount) page sheet music PDF")
            } else {
                SheetMusicUnavailableView(message: "This PDF could not be opened.")
            }
        } else if let image = UIImage(contentsOfFile: fileURL.path) {
            ZoomableSheetMusicImage(image: image)
                .accessibilityLabel("\(displayName), sheet music image")
        } else {
            SheetMusicUnavailableView(message: "This sheet-music image could not be opened.")
        }
    }
}

private struct PDFDocumentView: UIViewRepresentable {
    let document: PDFDocument

    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.autoScales = true
        view.displayMode = .singlePageContinuous
        view.displayDirection = .vertical
        view.displaysPageBreaks = true
        view.pageBreakMargins = UIEdgeInsets(top: 10, left: 8, bottom: 10, right: 8)
        view.backgroundColor = UIColor(red: 38 / 255, green: 50 / 255, blue: 56 / 255, alpha: 1)
        view.document = document
        return view
    }

    func updateUIView(_ view: PDFView, context: Context) {
        if view.document !== document {
            view.document = document
            view.autoScales = true
        }
    }
}

private struct ZoomableSheetMusicImage: UIViewRepresentable {
    let image: UIImage

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> UIScrollView {
        let scrollView = UIScrollView()
        scrollView.delegate = context.coordinator
        scrollView.minimumZoomScale = 1
        scrollView.maximumZoomScale = 6
        scrollView.backgroundColor = UIColor(red: 38 / 255, green: 50 / 255, blue: 56 / 255, alpha: 1)

        let imageView = context.coordinator.imageView
        imageView.image = image
        imageView.contentMode = .scaleAspectFit
        imageView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(imageView)

        NSLayoutConstraint.activate([
            imageView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            imageView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            imageView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            imageView.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor),
            imageView.heightAnchor.constraint(equalTo: scrollView.frameLayoutGuide.heightAnchor),
        ])
        return scrollView
    }

    func updateUIView(_ scrollView: UIScrollView, context: Context) {
        if context.coordinator.imageView.image !== image {
            context.coordinator.imageView.image = image
            scrollView.setZoomScale(1, animated: false)
        }
    }

    final class Coordinator: NSObject, UIScrollViewDelegate {
        let imageView = UIImageView()

        func viewForZooming(in scrollView: UIScrollView) -> UIView? {
            imageView
        }
    }
}

private struct MediaUnavailableView: View {
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "play.slash.fill")
                .font(.largeTitle)
            Text(title)
                .font(.headline)
            Text(message)
                .font(.caption)
                .multilineTextAlignment(.center)
        }
        .foregroundStyle(.white)
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }
}

private struct SheetMusicUnavailableView: View {
    let message: String

    var body: some View {
        ContentUnavailableView {
            Label("Sheet music unavailable", systemImage: "doc.questionmark")
                .foregroundStyle(.white)
        } description: {
            Text(message)
                .foregroundStyle(.white.opacity(0.82))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
