import LKPracticeCore
import SwiftUI

struct InitialProfileSetupView: View {
    @EnvironmentObject private var model: AppModel
    @State private var name = ""
    @State private var instrument = "Violin"

    var body: some View {
        ZStack {
            SkyBackground()
            ScrollView {
                VStack(spacing: 22) {
                    LKLogo(size: 104)
                    Text("Who will practice?")
                        .font(.lkTitle(32))
                        .foregroundStyle(LKColor.ink)
                        .accessibilityAddTraits(.isHeader)
                    Text("A parent can add more student profiles later.")
                        .foregroundStyle(LKColor.muted)

                    VStack(spacing: 16) {
                        TextField("Student name", text: $name)
                            .textFieldStyle(.roundedBorder)
                        TextField("Instrument", text: $instrument)
                            .textFieldStyle(.roundedBorder)
                        Button {
                            _ = model.addProfile(name: name, instrument: instrument)
                        } label: {
                            Label("Create Practice Profile", systemImage: "music.note")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(SunnyButtonStyle())
                        .disabled(
                            name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                                instrument.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        )
                    }
                    .padding(22)
                    .cloudCard()
                }
                .frame(maxWidth: 560)
                .padding(24)
                .frame(maxWidth: .infinity)
            }
        }
    }
}

struct ProfileEditorView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var model: AppModel
    let profile: ChildProfile?
    @State private var name: String
    @State private var instrument: String

    init(profile: ChildProfile? = nil) {
        self.profile = profile
        _name = State(initialValue: profile?.name ?? "")
        _instrument = State(initialValue: profile?.instrument ?? "Violin")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Student") {
                    TextField("Name", text: $name)
                    TextField("Instrument", text: $instrument)
                }
            }
            .navigationTitle(profile == nil ? "Add Profile" : "Edit Profile")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let saved: Bool
                        if var profile {
                            profile.name = name
                            profile.instrument = instrument
                            saved = model.updateProfile(profile)
                        } else {
                            saved = model.addProfile(name: name, instrument: instrument)
                        }
                        if saved {
                            dismiss()
                        }
                    }
                    .disabled(
                        name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                            instrument.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    )
                }
            }
        }
    }
}

struct ProfilesView: View {
    @EnvironmentObject private var model: AppModel
    @State private var editingProfile: ChildProfile?
    @State private var addingProfile = false
    @State private var profileToDelete: ChildProfile?

    var body: some View {
        List {
            Section {
                ForEach(model.profiles) { profile in
                    HStack(spacing: 14) {
                        ProfileAvatar(profile: profile)
                        VStack(alignment: .leading) {
                            Text(profile.name)
                                .font(.headline)
                            Text(profile.instrument)
                                .foregroundStyle(LKColor.muted)
                        }
                        Spacer()
                        if model.activeProfile?.id == profile.id {
                            Label("Active", systemImage: "checkmark.circle.fill")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(LKColor.deepSky)
                        }
                    }
                    .contentShape(Rectangle())
                    .onTapGesture { model.selectProfile(profile.id) }
                    .swipeActions(edge: .trailing) {
                        Button("Delete", role: .destructive) {
                            profileToDelete = profile
                        }
                        Button("Edit") {
                            editingProfile = profile
                        }
                        .tint(LKColor.deepSky)
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityHint("Double tap to make this the active profile.")
                }
            } header: {
                Text("Student Profiles")
            } footer: {
                Text("Each profile has separate assignments, progress, and history.")
            }
        }
        .navigationTitle("Profiles")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    addingProfile = true
                } label: {
                    Label("Add Profile", systemImage: "person.badge.plus")
                }
            }
        }
        .sheet(isPresented: $addingProfile) {
            ProfileEditorView()
                .environmentObject(model)
        }
        .sheet(item: $editingProfile) { profile in
            ProfileEditorView(profile: profile)
                .environmentObject(model)
        }
        .alert(
            "Delete \(profileToDelete?.name ?? "this profile")?",
            isPresented: Binding(
                get: { profileToDelete != nil },
                set: { if !$0 { profileToDelete = nil } }
            )
        ) {
            Button("Cancel", role: .cancel) { profileToDelete = nil }
            Button("Delete Profile", role: .destructive) {
                if let profileToDelete {
                    model.deleteProfile(profileToDelete.id)
                }
                profileToDelete = nil
            }
        } message: {
            Text("This removes the profile’s assignments and practice history from this device.")
        }
    }
}
