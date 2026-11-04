import LKPracticeCore
import SwiftUI

struct AssignmentsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var draft = AssignmentDraft.new(on: .today())
    @State private var showingEditor = false

    private var activeAssignments: [Assignment] {
        model.allAssignments.filter { !$0.isArchived }
    }

    private var archivedAssignments: [Assignment] {
        model.allAssignments.filter(\.isArchived)
    }

    var body: some View {
        List {
            if activeAssignments.isEmpty {
                Section {
                    ContentUnavailableView(
                        "No Assignments",
                        systemImage: "music.note.list",
                        description: Text("Add a practice mission for \(model.activeProfile?.name ?? "the student").")
                    )
                }
            } else {
                Section("Active Assignments") {
                    ForEach(activeAssignments) { assignment in
                        AssignmentManagementRow(
                            assignment: assignment,
                            onEdit: {
                                draft = AssignmentDraft(assignment: assignment)
                                showingEditor = true
                            },
                            onArchive: {
                                model.archiveAssignment(assignment.id)
                            },
                            restore: false
                        )
                    }
                }
            }

            if !archivedAssignments.isEmpty {
                Section("Archived") {
                    ForEach(archivedAssignments) { assignment in
                        AssignmentManagementRow(
                            assignment: assignment,
                            onEdit: {
                                draft = AssignmentDraft(assignment: assignment)
                                showingEditor = true
                            },
                            onArchive: {
                                model.restoreAssignment(assignment.id)
                            },
                            restore: true
                        )
                    }
                }
            }
        }
        .navigationTitle("Assignments")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    draft = .new(on: model.currentDate)
                    showingEditor = true
                } label: {
                    Label("Add Assignment", systemImage: "plus")
                }
            }
        }
        .sheet(isPresented: $showingEditor) {
            AssignmentEditorView(draft: $draft)
                .environmentObject(model)
        }
    }
}

private struct AssignmentManagementRow: View {
    let assignment: Assignment
    let onEdit: () -> Void
    let onArchive: () -> Void
    let restore: Bool

    var body: some View {
        HStack(spacing: 13) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(LKColor.deepSky)
                .frame(width: 42, height: 42)
                .background(LKColor.cloudBlue, in: Circle())
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(assignment.title)
                    .font(.headline)
                Text(dateRangeLabel)
                    .font(.subheadline)
                    .foregroundStyle(LKColor.muted)
                Text("\(assignment.type.title) · \(goalLabel(assignment)) · revision \(assignment.revision)")
                    .font(.caption)
                    .foregroundStyle(LKColor.muted)
                if assignment.sourceType != .none || assignment.sheetMusicReference != nil {
                    Text(mediaSummary)
                        .font(.caption)
                        .foregroundStyle(LKColor.deepSky)
                }
            }
            Spacer()
            Menu {
                Button("Edit", systemImage: "pencil", action: onEdit)
                Button(
                    restore ? "Restore" : "Archive",
                    systemImage: restore ? "arrow.uturn.backward" : "archivebox",
                    action: onArchive
                )
            } label: {
                Image(systemName: "ellipsis.circle")
                    .font(.title3)
            }
            .accessibilityLabel("Actions for \(assignment.title)")
        }
        .padding(.vertical, 5)
        .accessibilityElement(children: .combine)
    }

    private var icon: String {
        switch assignment.type {
        case .technique: "wand.and.stars"
        case .scales: "arrow.up.and.down"
        case .repertoire: "music.quarternote.3"
        case .sightReading: "eyeglasses"
        case .other: "music.note"
        }
    }

    private var dateRangeLabel: String {
        if assignment.startDate == assignment.endDate {
            return friendlyDate(assignment.startDate)
        }
        return "\(shortDate(assignment.startDate)) – \(shortDate(assignment.endDate))"
    }

    private var mediaSummary: String {
        var parts: [String] = []
        if assignment.sourceType != .none {
            parts.append(
                assignment.sourceType == .youtube
                    ? "YouTube reference"
                    : "Video reference"
            )
        }
        if assignment.sheetMusicReference != nil {
            parts.append("Sheet music")
        }
        return parts.joined(separator: " · ")
    }
}

struct AssignmentEditorView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var model: AppModel
    @Binding var draft: AssignmentDraft

    var body: some View {
        NavigationStack {
            Form {
                Section("Music Mission") {
                    TextField("Title", text: $draft.title)
                    TextField("Coach or parent note", text: $draft.details, axis: .vertical)
                        .lineLimit(2...5)
                    Picker("Type", selection: $draft.type) {
                        ForEach(AssignmentType.allCases, id: \.self) { type in
                            Text(type.title).tag(type)
                        }
                    }
                }

                Section {
                    DatePicker(
                        "Start date",
                        selection: startDateBinding,
                        displayedComponents: .date
                    )
                    DatePicker(
                        "End date",
                        selection: endDateBinding,
                        in: endDateRange,
                        displayedComponents: .date
                    )

                    HStack {
                        Button("This Week") {
                            draft.startDate = model.currentDate.mondayOfWeek
                            draft.endDate = draft.startDate.adding(days: 6)
                        }
                        Button("Next 7 Days") {
                            draft.startDate = model.currentDate
                            draft.endDate = model.currentDate.adding(days: 6)
                        }
                    }

                    Text("Assignments can cover up to 30 days. Each day tracks its own progress.")
                        .font(.caption)
                        .foregroundStyle(LKColor.muted)
                } header: {
                    Text("Schedule")
                }

                Section("Goal") {
                    Picker("Goal type", selection: $draft.goalType) {
                        ForEach(PracticeGoalType.allCases, id: \.self) { type in
                            Text(type.title).tag(type)
                        }
                    }
                    if draft.goalType != .minutes {
                        Stepper(
                            "Repetitions: \(draft.targetRepetitions)",
                            value: $draft.targetRepetitions,
                            in: 1...50
                        )
                    }
                    if draft.goalType != .repetitions {
                        Stepper(
                            "Minutes: \(draft.targetMinutes)",
                            value: $draft.targetMinutes,
                            in: 1...120
                        )
                    }
                }

                Section {
                    ForEach(PracticeMode.allCases, id: \.self) { mode in
                        Toggle(
                            isOn: Binding(
                                get: { draft.enabledModes.contains(mode) },
                                set: { enabled in
                                    if enabled {
                                        draft.enabledModes.insert(mode)
                                    } else if draft.enabledModes.count > 1 {
                                        draft.enabledModes.remove(mode)
                                    }
                                }
                            )
                        ) {
                            Label(mode.kidTitle, systemImage: mode.systemImage)
                        }
                    }
                } header: {
                    Text("Practice Steps")
                } footer: {
                    Text("At least one step stays enabled. Students can switch freely during practice.")
                }

                AssignmentMediaEditor(draft: $draft)
            }
            .navigationTitle(draft.editingID == nil ? "Add Assignment" : "Edit Assignment")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        model.cleanupUnreferencedMedia()
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        if model.saveAssignment(draft) {
                            dismiss()
                        }
                    }
                    .disabled(
                        draft.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                            draft.enabledModes.isEmpty ||
                            !draft.hasValidReferenceVideo ||
                            !AssignmentDates.isValid(
                                from: draft.startDate,
                                to: draft.endDate
                            )
                    )
                }
            }
        }
        .interactiveDismissDisabled()
    }

    private var startDateBinding: Binding<Date> {
        Binding {
            draft.startDate.date(in: DateKey.localCalendar)
        } set: { value in
            let newStart = DateKey(date: value)
            draft.startDate = newStart
            if draft.endDate < newStart {
                draft.endDate = newStart
            } else if AssignmentDates.inclusiveDayCount(
                from: newStart,
                to: draft.endDate
            ) > AssignmentDates.maximumRangeDays {
                draft.endDate = newStart.adding(days: 29)
            }
        }
    }

    private var endDateBinding: Binding<Date> {
        Binding {
            draft.endDate.date(in: DateKey.localCalendar)
        } set: { value in
            draft.endDate = DateKey(date: value)
        }
    }

    private var endDateRange: ClosedRange<Date> {
        let start = draft.startDate.date(in: DateKey.localCalendar)
        let end = draft.startDate.adding(days: 29).date(in: DateKey.localCalendar)
        return start...end
    }
}
