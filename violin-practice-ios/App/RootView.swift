import SwiftUI

struct RootView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        Group {
            if !model.consentAccepted {
                ConsentView()
            } else if model.isLoading {
                ZStack {
                    SkyBackground()
                    ProgressView("Loading local practice data…")
                        .padding(24)
                        .cloudCard()
                }
            } else if model.profiles.isEmpty {
                InitialProfileSetupView()
            } else if model.activeAssignment != nil {
                PracticeSessionView()
            } else {
                MainShellView()
            }
        }
        .sheet(isPresented: $model.showingParentGate) {
            ParentGateView()
                .environmentObject(model)
        }
        .sheet(
            isPresented: Binding(
                get: { model.pendingReflectionSessionID != nil },
                set: { if !$0 { model.skipReflection() } }
            )
        ) {
            PracticeReflectionView()
                .environmentObject(model)
        }
        .alert(
            "LK Practice",
            isPresented: Binding(
                get: { model.errorMessage != nil },
                set: { if !$0 { model.errorMessage = nil } }
            )
        ) {
            Button("OK") { model.errorMessage = nil }
        } message: {
            Text(model.errorMessage ?? "")
        }
    }
}

struct MainShellView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        NavigationSplitView {
            List {
                if let profile = model.activeProfile {
                    Section("Practicing Today") {
                        Menu {
                            ForEach(model.profiles) { option in
                                Button {
                                    model.selectProfile(option.id)
                                } label: {
                                    if option.id == profile.id {
                                        Label(option.name, systemImage: "checkmark")
                                    } else {
                                        Text(option.name)
                                    }
                                }
                            }
                        } label: {
                            HStack(spacing: 12) {
                                ProfileAvatar(profile: profile, size: 42)
                                VStack(alignment: .leading) {
                                    Text(profile.name)
                                        .font(.headline)
                                    Text(profile.instrument)
                                        .font(.caption)
                                        .foregroundStyle(LKColor.muted)
                                }
                            }
                        }
                        .accessibilityLabel("Choose student profile. Current profile \(profile.name).")
                    }
                }

                Section {
                    Button {
                        model.mainArea = .today
                    } label: {
                        Label("Today’s Practice", systemImage: "sun.max.fill")
                    }
                    .foregroundStyle(model.mainArea == .today ? LKColor.deepSky : LKColor.ink)
                }

                Section("Grown-ups") {
                    if model.parentUnlocked {
                        ForEach(ParentSection.allCases) { section in
                            Button {
                                model.parentSection = section
                                model.mainArea = .parent
                            } label: {
                                Label(section.title, systemImage: section.systemImage)
                            }
                            .foregroundStyle(
                                model.mainArea == .parent && model.parentSection == section
                                    ? LKColor.deepSky
                                    : LKColor.ink
                            )
                        }
                        Button {
                            model.lockParentSpace()
                        } label: {
                            Label("Lock Parent Space", systemImage: "lock.fill")
                        }
                    } else {
                        Button {
                            model.openParentSpace()
                        } label: {
                            Label("Parent Space", systemImage: "lock.fill")
                        }
                    }
                }
            }
            .navigationTitle("LK Practice")
            .scrollContentBackground(.hidden)
            .background(LKColor.cream)
        } detail: {
            NavigationStack {
                if model.mainArea == .today {
                    TodayView()
                } else {
                    parentDetail
                }
            }
        }
        .tint(LKColor.deepSky)
    }

    @ViewBuilder
    private var parentDetail: some View {
        switch model.parentSection {
        case .assignments:
            AssignmentsView()
        case .summary:
            SummaryView()
        case .profiles:
            ProfilesView()
        case .privacy:
            PrivacyDataView()
        }
    }
}
