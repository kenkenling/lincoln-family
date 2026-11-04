import LKPracticeCore
import SwiftUI

struct PracticeSessionView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showingSheetMusic = false

    var body: some View {
        ZStack {
            SkyBackground()
            if let assignment = model.activeAssignment {
                ScrollView {
                    VStack(spacing: 18) {
                        sessionHeader(assignment)
                        modeSelector(assignment)
                        practicePanel(assignment)
                        sheetMusicPanel(assignment)
                        progressPanel(assignment)
                        actionPanel(assignment)
                    }
                    .frame(maxWidth: 820)
                    .padding(20)
                    .frame(maxWidth: .infinity)
                }
            } else {
                ProgressView()
            }
        }
        .fullScreenCover(isPresented: $showingSheetMusic) {
            if
                let assignment = model.activeAssignment,
                let reference = assignment.sheetMusicReference,
                let fileURL = model.localMediaURL(for: reference)
            {
                SheetMusicViewer(
                    fileURL: fileURL,
                    displayName: model.localMediaDisplayName(for: reference)
                )
            }
        }
    }

    private func sessionHeader(_ assignment: Assignment) -> some View {
        HStack(spacing: 14) {
            Image(systemName: "music.note")
                .font(.title)
                .foregroundStyle(LKColor.deepSky)
                .frame(width: 54, height: 54)
                .background(LKColor.cloudBlue, in: Circle())
            VStack(alignment: .leading, spacing: 4) {
                Text(assignment.title)
                    .font(.lkTitle(27))
                    .foregroundStyle(LKColor.ink)
                Text(goalLabel(assignment))
                    .foregroundStyle(LKColor.muted)
            }
            Spacer()
        }
        .padding(18)
        .cloudCard()
    }

    @ViewBuilder
    private func modeSelector(_ assignment: Assignment) -> some View {
        if assignment.enabledModes.count > 1 {
            VStack(alignment: .leading, spacing: 10) {
                Text("Choose any practice step")
                    .font(.headline.weight(.heavy))
                HStack {
                    ForEach(PracticeMode.allCases.filter(assignment.enabledModes.contains), id: \.self) {
                        mode in
                        Button {
                            model.switchMode(mode)
                        } label: {
                            VStack(spacing: 6) {
                                Image(systemName: mode.systemImage)
                                Text(mode.kidTitle)
                                    .font(.caption.weight(.bold))
                            }
                            .frame(maxWidth: .infinity, minHeight: 68)
                            .foregroundStyle(LKColor.ink)
                            .background(
                                model.engine?.selectedMode == mode
                                    ? LKColor.sunYellow
                                    : LKColor.cloudBlue,
                                in: RoundedRectangle(cornerRadius: 16)
                            )
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(
                            "\(mode.kidTitle)\(model.engine?.selectedMode == mode ? ", selected" : "")"
                        )
                    }
                }
            }
            .padding(18)
            .cloudCard()
        }
    }

    @ViewBuilder
    private func practicePanel(_ assignment: Assignment) -> some View {
        if selectedMode != .playSolo, assignment.sourceType != .none {
            ReferenceVideoPanel(
                sourceType: assignment.sourceType,
                referenceURL: assignment.referenceURL,
                startSeconds: assignment.referenceStartSeconds,
                localFileURL: model.localMediaURL(for: assignment.referenceURL)
            )
        } else {
            instructionPanel(assignment)
        }
    }

    private func instructionPanel(_ assignment: Assignment) -> some View {
        VStack(spacing: 15) {
            Image(systemName: panelImage)
                .font(.system(size: 60))
                .foregroundStyle(LKColor.deepSky)
            Text(panelTitle)
                .font(.title2.weight(.heavy))
                .foregroundStyle(LKColor.ink)
            Text(panelMessage(for: assignment))
                .multilineTextAlignment(.center)
                .foregroundStyle(LKColor.muted)
        }
        .frame(maxWidth: .infinity, minHeight: 220)
        .padding(20)
        .cloudCard(color: LKColor.cloudBlue.opacity(0.82))
    }

    @ViewBuilder
    private func sheetMusicPanel(_ assignment: Assignment) -> some View {
        if let reference = assignment.sheetMusicReference {
            if model.localMediaExists(for: reference) {
                Button {
                    showingSheetMusic = true
                } label: {
                    HStack(spacing: 13) {
                        Image(systemName: "doc.richtext.fill")
                            .font(.title2)
                            .foregroundStyle(LKColor.deepSky)
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Open Sheet Music")
                                .font(.headline.weight(.heavy))
                                .foregroundStyle(LKColor.ink)
                            Text(model.localMediaDisplayName(for: reference))
                                .font(.caption)
                                .foregroundStyle(LKColor.muted)
                                .lineLimit(1)
                        }
                        Spacer()
                        Image(systemName: "arrow.up.left.and.arrow.down.right")
                            .foregroundStyle(LKColor.deepSky)
                    }
                    .padding(17)
                    .frame(maxWidth: .infinity)
                    .cloudCard(color: LKColor.lavenderCloud)
                }
                .buttonStyle(.plain)
                .accessibilityHint("Opens a zoomable full-screen sheet-music viewer.")
            } else {
                Label(
                    "Sheet music needs to be reattached in Parent Space.",
                    systemImage: "exclamationmark.triangle.fill"
                )
                .foregroundStyle(LKColor.danger)
                .padding(17)
                .frame(maxWidth: .infinity, alignment: .leading)
                .cloudCard(color: LKColor.lavenderCloud)
            }
        }
    }

    private func progressPanel(_ assignment: Assignment) -> some View {
        HStack(spacing: 14) {
            TimelineView(.periodic(from: .now, by: 1)) { context in
                MetricTile(
                    title: "Time",
                    value: durationLabel(model.elapsedPracticeSeconds(at: context.date)),
                    systemImage: "timer"
                )
            }
            MetricTile(
                title: "Repetitions",
                value: "\(model.activeSession?.repetitions ?? 0)",
                systemImage: "repeat"
            )
        }
        .accessibilityElement(children: .combine)
    }

    private func actionPanel(_ assignment: Assignment) -> some View {
        VStack(spacing: 12) {
            if assignment.goalType != .minutes {
                Button {
                    model.recordRepetition()
                } label: {
                    Label("I Played One", systemImage: "plus.circle.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(SunnyButtonStyle())
            }

            HStack(spacing: 12) {
                Button {
                    if model.practicePaused {
                        model.resumePractice()
                    } else {
                        model.pausePractice()
                    }
                } label: {
                    Label(
                        model.practicePaused ? "Resume" : "Pause",
                        systemImage: model.practicePaused ? "play.fill" : "pause.fill"
                    )
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(SkyButtonStyle())

                Button {
                    model.finishPractice()
                } label: {
                    Label("Finish", systemImage: "checkmark.circle.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(SkyButtonStyle())
            }
        }
        .padding(.bottom, 14)
    }

    private var selectedMode: PracticeMode {
        model.engine?.selectedMode ?? .playSolo
    }

    private var panelImage: String {
        switch selectedMode {
        case .listen: "ear.and.waveform"
        case .playAlong: "person.2.wave.2"
        case .playSolo: "video.fill"
        }
    }

    private var panelTitle: String {
        switch selectedMode {
        case .listen: "Listen first"
        case .playAlong: "Play along"
        case .playSolo: "Play solo"
        }
    }

    private func panelMessage(for assignment: Assignment) -> String {
        if selectedMode == .playSolo {
            return "Focus on your sound, posture, rhythm, and the goal for this mission."
        }
        if assignment.sourceType == .none {
            return "No reference video is attached yet. You can still listen to a grown-up or switch practice steps."
        }
        return "Use the reference to listen carefully, then complete the assigned work."
    }
}

private struct MetricTile: View {
    let title: String
    let value: String
    let systemImage: String

    var body: some View {
        VStack(spacing: 7) {
            Image(systemName: systemImage)
                .foregroundStyle(LKColor.deepSky)
            Text(value)
                .font(.lkTitle(28))
                .monospacedDigit()
                .foregroundStyle(LKColor.ink)
            Text(title)
                .font(.caption.weight(.bold))
                .foregroundStyle(LKColor.muted)
        }
        .frame(maxWidth: .infinity, minHeight: 126)
        .cloudCard()
    }
}

struct PracticeReflectionView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var reflection: PracticeReflection = .okay
    @State private var confident = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 22) {
                Text("How did that feel?")
                    .font(.lkTitle(29))
                    .foregroundStyle(LKColor.ink)
                HStack {
                    reflectionButton(.easy, emoji: "😊", title: "Easy")
                    reflectionButton(.okay, emoji: "🙂", title: "Okay")
                    reflectionButton(.tricky, emoji: "🧩", title: "Tricky")
                }
                Toggle("I feel confident playing this.", isOn: $confident)
                    .padding(17)
                    .cloudCard(color: LKColor.lavenderCloud)

                Text("Finishing the task and mastering the music are tracked separately.")
                    .font(.footnote)
                    .foregroundStyle(LKColor.muted)
                    .multilineTextAlignment(.center)

                Button {
                    model.submitReflection(
                        reflection: reflection,
                        studentFeelsConfident: confident
                    )
                    dismiss()
                } label: {
                    Text("Save Reflection")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(SunnyButtonStyle())
            }
            .frame(maxWidth: 560)
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(SkyBackground())
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Skip") {
                        model.skipReflection()
                        dismiss()
                    }
                }
            }
        }
    }

    private func reflectionButton(
        _ value: PracticeReflection,
        emoji: String,
        title: String
    ) -> some View {
        Button {
            reflection = value
        } label: {
            VStack(spacing: 7) {
                Text(emoji)
                    .font(.system(size: 34))
                Text(title)
                    .font(.headline)
            }
            .frame(maxWidth: .infinity, minHeight: 96)
            .foregroundStyle(LKColor.ink)
            .background(
                reflection == value ? LKColor.sunYellow : LKColor.cloudBlue,
                in: RoundedRectangle(cornerRadius: 18)
            )
        }
        .buttonStyle(.plain)
    }
}
