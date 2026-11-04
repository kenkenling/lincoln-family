import LKPracticeCore
import SwiftUI
import UniformTypeIdentifiers

struct AssignmentMediaEditor: View {
    @EnvironmentObject private var model: AppModel
    @Binding var draft: AssignmentDraft
    @State private var showingVideoImporter = false
    @State private var showingSheetMusicImporter = false
    @State private var showingVideoPreview = false
    @State private var showingSheetMusicPreview = false
    @State private var importingVideo = false
    @State private var importingSheetMusic = false

    var body: some View {
        Group {
            Section {
                Picker("Reference source", selection: sourceTypeBinding) {
                    ForEach(SourceType.allCases, id: \.self) { sourceType in
                        Text(sourceType.title).tag(sourceType)
                    }
                }

                switch draft.sourceType {
                case .none:
                    Text("Optional. Add a YouTube link or a video file for Listen and Play Along.")
                        .font(.caption)
                        .foregroundStyle(LKColor.muted)
                case .youtube:
                    TextField("YouTube link", text: referenceURLBinding)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                        .autocorrectionDisabled()
                    if let value = draft.referenceURL, !value.isEmpty {
                        if YouTubeReference.videoID(from: value) == nil {
                            Label("Enter a valid YouTube, Shorts, or youtu.be link.", systemImage: "exclamationmark.triangle.fill")
                                .font(.caption)
                                .foregroundStyle(LKColor.danger)
                        } else {
                            Button {
                                showingVideoPreview = true
                            } label: {
                                Label("Preview Reference Video", systemImage: "play.rectangle")
                            }
                        }
                    }
                case .localVideo:
                    localVideoControls
                }

                if draft.sourceType != .none {
                    Stepper(
                        "Start video at \(durationLabel(draft.referenceStartSeconds))",
                        value: $draft.referenceStartSeconds,
                        in: 0...7_200,
                        step: 5
                    )
                }
            } header: {
                Text("Reference Video")
            } footer: {
                Text("Video is optional. Device files are copied into LK Practice’s private storage.")
            }

            Section {
                if let reference = draft.sheetMusicReference {
                    attachmentLabel(
                        name: model.localMediaDisplayName(for: reference),
                        available: model.localMediaExists(for: reference),
                        systemImage: "doc.richtext"
                    )
                    HStack {
                        Button {
                            showingSheetMusicPreview = true
                        } label: {
                            Label("Preview", systemImage: "eye")
                        }
                        .disabled(!model.localMediaExists(for: reference))

                        Spacer()

                        Button(role: .destructive) {
                            draft.sheetMusicReference = nil
                        } label: {
                            Label("Remove", systemImage: "trash")
                        }
                    }
                    Button {
                        showingSheetMusicImporter = true
                    } label: {
                        Label("Replace Sheet Music", systemImage: "arrow.triangle.2.circlepath")
                    }
                } else {
                    Button {
                        showingSheetMusicImporter = true
                    } label: {
                        Label("Attach PDF or Image", systemImage: "paperclip")
                    }
                }

                if importingSheetMusic {
                    ProgressView("Copying sheet music…")
                }
            } header: {
                Text("Sheet Music")
            } footer: {
                Text("PDF, PNG, JPG, HEIC, and WebP files up to 50 MB are supported.")
            }
        }
        .fileImporter(
            isPresented: $showingVideoImporter,
            allowedContentTypes: [.movie],
            allowsMultipleSelection: false,
            onCompletion: handleVideoImport
        )
        .fileImporter(
            isPresented: $showingSheetMusicImporter,
            allowedContentTypes: [.pdf, .image],
            allowsMultipleSelection: false,
            onCompletion: handleSheetMusicImport
        )
        .sheet(isPresented: $showingVideoPreview) {
            NavigationStack {
                ReferenceVideoPanel(
                    sourceType: draft.sourceType,
                    referenceURL: draft.referenceURL,
                    startSeconds: draft.referenceStartSeconds,
                    localFileURL: model.localMediaURL(for: draft.referenceURL)
                )
                .padding(20)
                .frame(maxWidth: 820, maxHeight: .infinity, alignment: .top)
                .background(SkyBackground())
                .navigationTitle("Reference Video")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") {
                            showingVideoPreview = false
                        }
                    }
                }
            }
        }
        .fullScreenCover(isPresented: $showingSheetMusicPreview) {
            if
                let reference = draft.sheetMusicReference,
                let fileURL = model.localMediaURL(for: reference)
            {
                SheetMusicViewer(
                    fileURL: fileURL,
                    displayName: model.localMediaDisplayName(for: reference)
                )
            }
        }
    }

    @ViewBuilder
    private var localVideoControls: some View {
        if let reference = draft.referenceURL {
            attachmentLabel(
                name: model.localMediaDisplayName(for: reference),
                available: model.localMediaExists(for: reference),
                systemImage: "film"
            )
            HStack {
                Button {
                    showingVideoPreview = true
                } label: {
                    Label("Preview", systemImage: "play.rectangle")
                }
                .disabled(!model.localMediaExists(for: reference))

                Spacer()

                Button(role: .destructive) {
                    draft.referenceURL = nil
                } label: {
                    Label("Remove", systemImage: "trash")
                }
            }
            Button {
                showingVideoImporter = true
            } label: {
                Label("Replace Video File", systemImage: "arrow.triangle.2.circlepath")
            }
        } else {
            Button {
                showingVideoImporter = true
            } label: {
                Label("Choose Video File", systemImage: "film.stack")
            }
        }

        if importingVideo {
            ProgressView("Copying reference video…")
        }
    }

    private func attachmentLabel(
        name: String,
        available: Bool,
        systemImage: String
    ) -> some View {
        Label {
            VStack(alignment: .leading, spacing: 2) {
                Text(name)
                    .lineLimit(1)
                if !available {
                    Text("Private copy missing—reattach this file.")
                        .font(.caption)
                        .foregroundStyle(LKColor.danger)
                }
            }
        } icon: {
            Image(systemName: available ? systemImage : "exclamationmark.triangle.fill")
                .foregroundStyle(available ? LKColor.deepSky : LKColor.danger)
        }
    }

    private var sourceTypeBinding: Binding<SourceType> {
        Binding {
            draft.sourceType
        } set: { newValue in
            guard newValue != draft.sourceType else {
                return
            }
            draft.sourceType = newValue
            draft.referenceURL = nil
            draft.referenceStartSeconds = 0
        }
    }

    private var referenceURLBinding: Binding<String> {
        Binding {
            draft.referenceURL ?? ""
        } set: { value in
            draft.referenceURL = value.isEmpty ? nil : value
        }
    }

    private func handleVideoImport(_ result: Result<[URL], Error>) {
        switch result {
        case let .success(urls):
            guard let sourceURL = urls.first else {
                return
            }
            importingVideo = true
            Task {
                if let reference = await model.importReferenceVideo(from: sourceURL) {
                    draft.sourceType = .localVideo
                    draft.referenceURL = reference
                    showingVideoPreview = true
                }
                importingVideo = false
            }
        case let .failure(error):
            reportImporterError(error)
        }
    }

    private func handleSheetMusicImport(_ result: Result<[URL], Error>) {
        switch result {
        case let .success(urls):
            guard let sourceURL = urls.first else {
                return
            }
            importingSheetMusic = true
            Task {
                if let reference = await model.importSheetMusic(from: sourceURL) {
                    draft.sheetMusicReference = reference
                    showingSheetMusicPreview = true
                }
                importingSheetMusic = false
            }
        case let .failure(error):
            reportImporterError(error)
        }
    }

    private func reportImporterError(_ error: Error) {
        if error is CancellationError {
            return
        }
        if let cocoaError = error as? CocoaError, cocoaError.code == .userCancelled {
            return
        }
        model.errorMessage = error.localizedDescription
    }
}
