import LKPracticeCore
import SwiftUI

struct TodayView: View {
    @EnvironmentObject private var model: AppModel
    @State private var selectedModes: [String: PracticeMode] = [:]

    var body: some View {
        ZStack {
            SkyBackground()
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 18) {
                    ViewThatFits(in: .horizontal) {
                        HStack(alignment: .stretch, spacing: 18) {
                            TodayHero()
                            WeeklyDotsCard()
                        }
                        VStack(spacing: 18) {
                            TodayHero()
                            WeeklyDotsCard()
                        }
                    }

                    if model.todayAssignments.isEmpty {
                        EmptyCloud(
                            title: "No music missions today",
                            message: model.selfDirectedAssignments.isEmpty
                                ? "Ask a grown-up to add a practice assignment in Parent Space."
                                : "You can still revisit something you have practiced before.",
                            systemImage: "music.note.house.fill"
                        )
                    } else {
                        Text("Today’s music missions")
                            .font(.lkTitle(24))
                            .foregroundStyle(LKColor.ink)
                            .accessibilityAddTraits(.isHeader)

                        ForEach(Array(model.todayAssignments.enumerated()), id: \.element.id) {
                            index,
                            assignment in
                            AssignmentMissionCard(
                                number: index + 1,
                                assignment: assignment,
                                selectedMode: mode(for: assignment),
                                onSelectMode: { selectedModes[assignment.id] = $0 },
                                onStart: {
                                    model.startPractice(
                                        assignment,
                                        mode: mode(for: assignment)
                                    )
                                }
                            )
                        }
                    }

                    if shouldShowSelfDirected {
                        SelfDirectedCard()
                    }
                }
                .frame(maxWidth: 920)
                .padding(.horizontal, 20)
                .padding(.vertical, 18)
                .frame(maxWidth: .infinity)
            }
        }
        .navigationTitle("Today")
    }

    private var shouldShowSelfDirected: Bool {
        !model.selfDirectedAssignments.isEmpty &&
            (model.todayAssignments.isEmpty || model.allTodayAssignmentsComplete)
    }

    private func mode(for assignment: Assignment) -> PracticeMode {
        if let selected = selectedModes[assignment.id],
           assignment.enabledModes.contains(selected) {
            return selected
        }
        return PracticeMode.allCases.first(where: assignment.enabledModes.contains) ?? .playSolo
    }
}

private struct TodayHero: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 5) {
                    Text(model.greeting)
                        .font(.lkTitle(30))
                        .foregroundStyle(LKColor.ink)
                        .accessibilityAddTraits(.isHeader)
                    Text(friendlyDate(model.currentDate))
                        .foregroundStyle(LKColor.muted)
                }
                Spacer()
                Image(systemName: "sun.max.fill")
                    .font(.system(size: 42))
                    .foregroundStyle(LKColor.sunYellow)
                    .accessibilityHidden(true)
            }

            if let next = model.todayAssignments.first(where: { !model.goalMet(for: $0) }) {
                Text("Up next")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(LKColor.deepSky)
                    .textCase(.uppercase)
                Text(next.title)
                    .font(.title2.weight(.heavy))
                    .foregroundStyle(LKColor.ink)
                Text(goalLabel(next))
                    .foregroundStyle(LKColor.muted)
            } else if model.allTodayAssignmentsComplete {
                Label("Today’s plan is complete", systemImage: "checkmark.seal.fill")
                    .font(.headline)
                    .foregroundStyle(LKColor.deepSky)
            } else {
                Text("Small steps make strong musicians.")
                    .font(.headline)
                    .foregroundStyle(LKColor.deepSky)
            }
        }
        .padding(22)
        .frame(maxWidth: .infinity, minHeight: 190, alignment: .leading)
        .cloudCard(color: Color.white.opacity(0.90))
    }
}

private struct WeeklyDotsCard: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: 13) {
            Text("Your practice week")
                .font(.headline.weight(.heavy))
                .foregroundStyle(LKColor.ink)

            HStack {
                ForEach(Array(model.weeklyPractice.enumerated()), id: \.element.date) { index, day in
                    VStack(spacing: 6) {
                        Text(["M", "T", "W", "T", "F", "S", "S"][index])
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(LKColor.muted)
                        ZStack {
                            Circle()
                                .fill(day.practiced ? LKColor.sunYellow : LKColor.cloudBlue)
                                .frame(width: 28, height: 28)
                            if day.practiced {
                                Image(systemName: "checkmark")
                                    .font(.caption.weight(.black))
                                    .foregroundStyle(LKColor.ink)
                            }
                        }
                    }
                    if index < 6 {
                        Spacer(minLength: 4)
                    }
                }
            }

            Text("A gentle pattern, not a streak—rest days are okay.")
                .font(.caption)
                .foregroundStyle(LKColor.muted)
        }
        .padding(22)
        .frame(maxWidth: .infinity, minHeight: 190, alignment: .leading)
        .cloudCard(color: Color.white.opacity(0.90))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            "\(model.weeklyPractice.filter(\.practiced).count) of 7 practice days this week. A gentle pattern, not a streak; rest days are okay."
        )
    }
}

private struct AssignmentMissionCard: View {
    @EnvironmentObject private var model: AppModel
    let number: Int
    let assignment: Assignment
    let selectedMode: PracticeMode
    let onSelectMode: (PracticeMode) -> Void
    let onStart: () -> Void

    var body: some View {
        let progress = model.progress(for: assignment)
        let repetitions = progress.repetitionsByTaskID[assignment.id, default: 0]
        let seconds = progress.secondsByTaskID[assignment.id, default: 0]
        let completed = model.goalMet(for: assignment)

        VStack(alignment: .leading, spacing: 15) {
            HStack(alignment: .top, spacing: 13) {
                Text(completed ? "✓" : "\(number)")
                    .font(.title3.weight(.black))
                    .foregroundStyle(LKColor.ink)
                    .frame(width: 44, height: 44)
                    .background(completed ? LKColor.sunYellow : LKColor.cloudBlue, in: Circle())

                VStack(alignment: .leading, spacing: 4) {
                    Text(assignment.title)
                        .font(.title3.weight(.heavy))
                        .foregroundStyle(LKColor.ink)
                    Text("\(assignment.type.title) · \(goalLabel(assignment))")
                        .font(.subheadline)
                        .foregroundStyle(LKColor.muted)
                }
                Spacer()
            }

            if !assignment.details.isEmpty {
                Text(assignment.details)
                    .foregroundStyle(LKColor.ink)
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(LKColor.cloudBlue.opacity(0.7), in: RoundedRectangle(cornerRadius: 15))
            }

            HStack(spacing: 16) {
                Label("\(repetitions) reps", systemImage: "repeat")
                Label(durationLabel(seconds), systemImage: "timer")
            }
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(LKColor.muted)

            if assignment.enabledModes.count > 1 {
                Picker("Starting mode", selection: Binding(
                    get: { selectedMode },
                    set: onSelectMode
                )) {
                    ForEach(PracticeMode.allCases.filter(assignment.enabledModes.contains), id: \.self) {
                        mode in
                        Label(mode.kidTitle, systemImage: mode.systemImage)
                            .tag(mode)
                    }
                }
                .pickerStyle(.segmented)
            } else if let onlyMode = assignment.enabledModes.first {
                Label(onlyMode.kidTitle, systemImage: onlyMode.systemImage)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(LKColor.deepSky)
            }

            if completed {
                Button(action: onStart) {
                    Label("Practice Again", systemImage: "play.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(SkyButtonStyle())
            } else {
                Button(action: onStart) {
                    Label("Start Practice", systemImage: "play.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(SunnyButtonStyle())
            }
        }
        .padding(18)
        .cloudCard(color: completed ? LKColor.mintCloud : Color.white.opacity(0.94))
        .accessibilityElement(children: .combine)
        .accessibilityLabel(
            "\(assignment.title). \(completed ? "Completed today." : "Not completed.") \(repetitions) repetitions, \(durationLabel(seconds)) practiced."
        )
    }
}

private struct SelfDirectedCard: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(model.todayAssignments.isEmpty ? "Practice on your own" : "Keep practicing")
                .font(.title3.weight(.heavy))
                .foregroundStyle(LKColor.ink)
            Text("Choose something familiar. This still counts in your practice week.")
                .foregroundStyle(LKColor.muted)

            ForEach(model.selfDirectedAssignments) { assignment in
                Button {
                    let mode = PracticeMode.allCases.first(where: assignment.enabledModes.contains) ??
                        .playSolo
                    model.startPractice(assignment, mode: mode, studentInitiated: true)
                } label: {
                    HStack {
                        Image(systemName: "sparkles")
                        Text(assignment.title)
                        Spacer()
                        Image(systemName: "play.circle.fill")
                    }
                    .font(.headline)
                    .padding(13)
                    .foregroundStyle(LKColor.ink)
                    .background(Color.white.opacity(0.76), in: RoundedRectangle(cornerRadius: 15))
                }
                .buttonStyle(.plain)
            }

            Button {
                guard let assignment = model.selfDirectedAssignments.randomElement() else {
                    return
                }
                let mode = PracticeMode.allCases.first(where: assignment.enabledModes.contains) ??
                    .playSolo
                model.startPractice(assignment, mode: mode, studentInitiated: true)
            } label: {
                Label("Surprise Me!", systemImage: "dice.fill")
            }
            .buttonStyle(SkyButtonStyle())
        }
        .padding(20)
        .cloudCard(color: LKColor.lavenderCloud)
    }
}
