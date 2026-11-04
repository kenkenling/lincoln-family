import LKPracticeCore
import SwiftUI

struct SummaryView: View {
    @EnvironmentObject private var model: AppModel
    @State private var selectedRecording: RecordingReview?

    var body: some View {
        List {
            Section {
                WeeklySummaryRow()
            }

            Section("Practice Sessions") {
                if model.sessions.isEmpty {
                    ContentUnavailableView(
                        "No Practice History",
                        systemImage: "clock.arrow.circlepath",
                        description: Text("Completed sessions will appear here.")
                    )
                } else {
                    ForEach(model.sessions) { session in
                        PracticeSessionSummaryRow(
                            session: session,
                            onOpenRecording: { reference in
                                guard let fileURL = model.recordingURL(for: reference) else {
                                    return
                                }
                                selectedRecording = RecordingReview(
                                    reference: reference,
                                    fileURL: fileURL,
                                    displayName: model.recordingDisplayName(for: reference)
                                )
                            }
                        )
                    }
                }
            }

            if !model.assignmentHistory.isEmpty {
                Section("Assignment Changes") {
                    ForEach(model.assignmentHistory.prefix(20)) { history in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(history.previousAssignment.title)
                                .font(.headline)
                            Text("Previous revision \(history.revision) · \(shortTimestamp(history.changedAtMillis))")
                                .font(.caption)
                                .foregroundStyle(LKColor.muted)
                            Text(
                                "\(shortDate(history.previousAssignment.startDate)) – \(shortDate(history.previousAssignment.endDate)) · \(goalLabel(history.previousAssignment))"
                            )
                            .font(.subheadline)
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
        }
        .navigationTitle("Summary")
        .fullScreenCover(item: $selectedRecording) { recording in
            PracticeRecordingViewer(
                fileURL: recording.fileURL,
                displayName: recording.displayName
            )
        }
    }

    private func shortTimestamp(_ millis: Int64) -> String {
        Date(timeIntervalSince1970: Double(millis) / 1_000)
            .formatted(.dateTime.month(.abbreviated).day().hour().minute())
    }
}

private struct WeeklySummaryRow: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("This Week")
                .font(.title3.weight(.heavy))
            HStack {
                ForEach(Array(model.weeklyPractice.enumerated()), id: \.element.date) { index, day in
                    VStack(spacing: 5) {
                        Text(["M", "T", "W", "T", "F", "S", "S"][index])
                            .font(.caption2.weight(.bold))
                        Circle()
                            .fill(day.practiced ? LKColor.sunYellow : LKColor.cloudBlue)
                            .frame(width: 26, height: 26)
                        Text("\(day.seconds / 60)m")
                            .font(.caption2)
                            .foregroundStyle(LKColor.muted)
                    }
                    if index < 6 {
                        Spacer()
                    }
                }
            }
        }
        .padding(.vertical, 8)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            "\(model.weeklyPractice.filter(\.practiced).count) of 7 practice days this week."
        )
    }
}

private struct PracticeSessionSummaryRow: View {
    @EnvironmentObject private var model: AppModel
    let session: PracticeSession
    let onOpenRecording: (String) -> Void

    private var assignmentTitle: String {
        model.allAssignments.first { $0.id == session.assignmentID }?.title ??
            "Past assignment"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text(assignmentTitle)
                        .font(.headline)
                    Text(friendlyDate(session.date))
                        .font(.caption)
                        .foregroundStyle(LKColor.muted)
                }
                Spacer()
                if session.studentInitiated {
                    Text("Self-directed")
                        .font(.caption2.weight(.bold))
                        .padding(.horizontal, 9)
                        .padding(.vertical, 5)
                        .background(LKColor.lavenderCloud, in: Capsule())
                }
            }

            HStack(spacing: 15) {
                Label(durationLabel(session.elapsedSeconds), systemImage: "timer")
                Label("\(session.repetitions)", systemImage: "repeat")
                Label(
                    session.goalMet ? "Done" : "Ended",
                    systemImage: session.goalMet ? "checkmark.circle.fill" : "circle"
                )
            }
            .font(.subheadline)
            .foregroundStyle(LKColor.muted)

            HStack {
                Label(session.masteryStatus.title, systemImage: session.masteryStatus.systemImage)
                    .foregroundStyle(
                        session.masteryStatus == .teacherApproved
                            ? LKColor.deepSky
                            : LKColor.muted
                    )
                Spacer()
                if !session.modesUsed.isEmpty {
                    Text(
                        PracticeMode.allCases
                            .filter(session.modesUsed.contains)
                            .map(\.kidTitle)
                            .joined(separator: " → ")
                    )
                    .font(.caption)
                    .foregroundStyle(LKColor.muted)
                }
            }
            .font(.caption)

            if let recordingPath = session.recordingPath {
                if model.recordingExists(for: recordingPath) {
                    Button {
                        onOpenRecording(recordingPath)
                    } label: {
                        Label("Watch Student Take", systemImage: "play.rectangle.fill")
                    }
                    .buttonStyle(.bordered)
                    .accessibilityHint("Opens the private video recorded during this session.")
                } else {
                    Label(
                        "Student take is unavailable on this device.",
                        systemImage: "video.slash.fill"
                    )
                    .font(.caption)
                    .foregroundStyle(LKColor.muted)
                }
            }
        }
        .padding(.vertical, 5)
        .accessibilityElement(children: .contain)
    }
}

private struct RecordingReview: Identifiable {
    let reference: String
    let fileURL: URL
    let displayName: String

    var id: String {
        reference
    }
}
