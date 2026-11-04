import SwiftUI

struct ConsentView: View {
    @EnvironmentObject private var model: AppModel
    @State private var ageRange: StudentAgeRange = .under13
    @State private var parentConfirmed = false
    @State private var policyAccepted = false
    @State private var showingPolicy = false

    var body: some View {
        ZStack {
            SkyBackground()
            ScrollView {
                VStack(spacing: 22) {
                    LKLogo(size: 112)

                    VStack(spacing: 6) {
                        Text("Welcome to LK Practice")
                            .font(.lkTitle(32))
                            .foregroundStyle(LKColor.ink)
                            .multilineTextAlignment(.center)
                            .accessibilityAddTraits(.isHeader)
                        Text("Practice Makes Progress.")
                            .font(.headline.weight(.heavy))
                            .foregroundStyle(LKColor.deepSky)
                    }

                    VStack(alignment: .leading, spacing: 14) {
                        Label("Family-first and local", systemImage: "house.fill")
                            .font(.title3.weight(.bold))
                        ConsentLine("Practice plans and progress stay in this app’s private storage.")
                        ConsentLine("There is no LK Practice account, advertising, analytics, or tracking.")
                        ConsentLine("Parent Space controls assignments, profiles, sharing, and deletion.")
                        ConsentLine("Assigned YouTube videos connect to YouTube only when opened.")
                        ConsentLine("You can erase all app data at any time.")
                    }
                    .padding(20)
                    .cloudCard()

                    VStack(alignment: .leading, spacing: 14) {
                        Text("Student age range")
                            .font(.headline)
                        Picker("Student age range", selection: $ageRange) {
                            ForEach(StudentAgeRange.allCases) { range in
                                Text(range.title).tag(range)
                            }
                        }
                        .pickerStyle(.segmented)

                        Toggle(
                            "I am a parent, guardian, or adult setting up this app.",
                            isOn: $parentConfirmed
                        )
                        Toggle(
                            "I have read and accept the privacy policy.",
                            isOn: $policyAccepted
                        )

                        Button("Read the privacy policy") {
                            showingPolicy = true
                        }
                        .fontWeight(.semibold)
                    }
                    .padding(20)
                    .cloudCard()

                    Button {
                        model.acceptPrivacy(ageRange: ageRange)
                    } label: {
                        Label("Continue", systemImage: "arrow.right.circle.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(SunnyButtonStyle())
                    .disabled(!parentConfirmed || !policyAccepted)
                    .opacity(parentConfirmed && policyAccepted ? 1 : 0.55)
                }
                .frame(maxWidth: 680)
                .padding(.horizontal, 22)
                .padding(.vertical, 28)
                .frame(maxWidth: .infinity)
            }
        }
        .sheet(isPresented: $showingPolicy) {
            PrivacyPolicyView()
        }
    }
}

private struct ConsentLine: View {
    let text: String

    init(_ text: String) {
        self.text = text
    }

    var body: some View {
        Label {
            Text(text)
                .foregroundStyle(LKColor.muted)
        } icon: {
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(LKColor.deepSky)
        }
    }
}

struct PrivacyPolicyView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("LK Practice Privacy Policy")
                        .font(.lkTitle(28))
                        .foregroundStyle(LKColor.ink)
                    Text("Policy version \(ApplePrivacyPolicy.version)")
                        .font(.caption)
                        .foregroundStyle(LKColor.muted)

                    ForEach(ApplePrivacyPolicy.sections) { section in
                        VStack(alignment: .leading, spacing: 7) {
                            Text(section.title)
                                .font(.headline)
                                .foregroundStyle(LKColor.ink)
                            Text(section.body)
                                .foregroundStyle(LKColor.muted)
                        }
                    }
                }
                .frame(maxWidth: 720, alignment: .leading)
                .padding(24)
                .frame(maxWidth: .infinity)
            }
            .background(LKColor.cream)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
