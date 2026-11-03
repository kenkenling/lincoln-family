import SwiftUI

struct ParentGateView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var model: AppModel
    @State private var pin = ""
    @State private var confirmation = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 58))
                    .foregroundStyle(LKColor.deepSky)
                    .accessibilityHidden(true)
                Text(model.pinConfigured ? "Parent Space" : "Protect Parent Space")
                    .font(.lkTitle(28))
                    .foregroundStyle(LKColor.ink)
                Text(
                    model.pinConfigured
                        ? "Enter the four-digit parent PIN."
                        : "Create a four-digit PIN for assignments, profiles, summaries, sharing, and data deletion."
                )
                .foregroundStyle(LKColor.muted)
                .multilineTextAlignment(.center)

                SecureField(model.pinConfigured ? "Parent PIN" : "Create PIN", text: $pin)
                    .textFieldStyle(.roundedBorder)
                    .textContentType(.password)
                    .onChange(of: pin) { _, value in
                        pin = normalizedPIN(value)
                    }

                if !model.pinConfigured {
                    SecureField("Confirm PIN", text: $confirmation)
                        .textFieldStyle(.roundedBorder)
                        .textContentType(.password)
                        .onChange(of: confirmation) { _, value in
                            confirmation = normalizedPIN(value)
                        }
                }

                Button {
                    let success = model.pinConfigured
                        ? model.unlockParentSpace(pin)
                        : model.createParentPIN(pin, confirmation: confirmation)
                    if success {
                        dismiss()
                    }
                } label: {
                    Text(model.pinConfigured ? "Unlock" : "Create PIN")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(SunnyButtonStyle())
                .disabled(
                    pin.count != 4 ||
                        (!model.pinConfigured && confirmation.count != 4)
                )
            }
            .frame(maxWidth: 480)
            .padding(26)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(SkyBackground())
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private func normalizedPIN(_ value: String) -> String {
        String(value.filter(\.isNumber).prefix(4))
    }
}

struct PrivacyDataView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showingPolicy = false
    @State private var confirmingDelete = false

    var body: some View {
        List {
            Section("Privacy") {
                Label("Local-only practice data", systemImage: "iphone")
                Label("No advertising, analytics, or tracking", systemImage: "hand.raised.fill")
                Button {
                    showingPolicy = true
                } label: {
                    Label("Read Privacy Policy", systemImage: "doc.text")
                }
            }

            Section {
                Button(role: .destructive) {
                    confirmingDelete = true
                } label: {
                    Label("Delete All Data", systemImage: "trash.fill")
                }
            } footer: {
                Text("This permanently removes consent, the Parent PIN, profiles, assignments, practice history, and app-managed files from this device.")
            }
        }
        .navigationTitle("Privacy & Data")
        .sheet(isPresented: $showingPolicy) {
            PrivacyPolicyView()
        }
        .alert("Permanently delete all data?", isPresented: $confirmingDelete) {
            Button("Cancel", role: .cancel) {}
            Button("Delete Everything", role: .destructive) {
                Task {
                    await model.deleteAllData()
                }
            }
        } message: {
            Text("This cannot be undone.")
        }
    }
}
