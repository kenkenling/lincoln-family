import LKPracticeCore
import SwiftUI

extension PracticeMode {
    var kidTitle: String {
        title
    }

    var systemImage: String {
        switch self {
        case .listen: "ear.fill"
        case .playAlong: "person.2.wave.2.fill"
        case .playSolo: "music.note"
        }
    }
}

extension AssignmentType {
    var title: String {
        switch self {
        case .technique: "Technique"
        case .scales: "Scales"
        case .repertoire: "Repertoire"
        case .sightReading: "Sight Reading"
        case .other: "Other"
        }
    }
}

extension PracticeGoalType {
    var title: String {
        switch self {
        case .repetitions: "Repetitions"
        case .minutes: "Minutes"
        case .repetitionsAndMinutes: "Repetitions + Minutes"
        }
    }
}

extension MasteryStatus {
    var title: String {
        switch self {
        case .notAssessed: "Not assessed"
        case .studentConfident: "Student feels confident"
        case .teacherApproved: "Teacher approved"
        }
    }

    var systemImage: String {
        switch self {
        case .notAssessed: "star"
        case .studentConfident: "star.leadinghalf.filled"
        case .teacherApproved: "star.fill"
        }
    }
}

func friendlyDate(_ date: DateKey) -> String {
    date.date(in: .localCalendar).formatted(
        .dateTime.weekday(.wide).month(.abbreviated).day().year()
    )
}

func shortDate(_ date: DateKey) -> String {
    date.date(in: .localCalendar).formatted(.dateTime.month(.abbreviated).day())
}

func durationLabel(_ seconds: Int) -> String {
    let safe = max(0, seconds)
    return String(format: "%d:%02d", safe / 60, safe % 60)
}

func goalLabel(_ assignment: Assignment) -> String {
    switch assignment.goalType {
    case .repetitions:
        return "\(assignment.targetRepetitions) repetitions"
    case .minutes:
        return "\(assignment.targetMinutes) minutes"
    case .repetitionsAndMinutes:
        return "\(assignment.targetRepetitions) repetitions + \(assignment.targetMinutes) minutes"
    }
}

struct EmptyCloud: View {
    let title: String
    let message: String
    var systemImage = "cloud.fill"

    var body: some View {
        ContentUnavailableView {
            Label(title, systemImage: systemImage)
                .font(.title3.weight(.bold))
                .foregroundStyle(LKColor.ink)
        } description: {
            Text(message)
                .foregroundStyle(LKColor.muted)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .cloudCard()
    }
}

struct ProfileAvatar: View {
    let profile: ChildProfile
    var size: CGFloat = 48

    var body: some View {
        Text(profile.name.trimmingCharacters(in: .whitespacesAndNewlines).first.map {
            String($0).uppercased()
        } ?? "♪")
        .font(.system(size: size * 0.38, weight: .black, design: .rounded))
        .foregroundStyle(LKColor.ink)
        .frame(width: size, height: size)
        .background(LKColor.cloudBlue, in: Circle())
        .accessibilityHidden(true)
    }
}
