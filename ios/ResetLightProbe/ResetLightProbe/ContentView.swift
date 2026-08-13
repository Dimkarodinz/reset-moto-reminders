import SwiftUI

/// Maintainer research tool, deliberately English-only: one button per
/// candidate channel, a status line, the journal, and a share button for
/// the JSONL file. Adapter-only — this app never sends a motorcycle
/// diagnostic command.
struct ContentView: View {
    @StateObject private var session = ProbeSession()

    var body: some View {
        NavigationStack {
            List {
                Section("Adapter-only ATI probe") {
                    Text(
                        "Power the vLinker MC-IOS (bench power is fine — no motorcycle needed). "
                        + "Each channel can be probed once per app launch; relaunch to retry."
                    )
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                    Button("Probe primary channel (18F0 split)") {
                        session.probePrimaryChannel()
                    }
                    .disabled(!canProbe(session.primaryChannelName))

                    Button("Probe alternate channel (custom characteristic)") {
                        session.probeAlternateChannel()
                    }
                    .disabled(!canProbe(session.alternateChannelName))
                }

                Section("Status") {
                    Text(statusText)
                        .fontWeight(session.phase.isTerminal ? .semibold : .regular)
                    if let response = session.responseText {
                        Text(response)
                            .font(.system(.body, design: .monospaced))
                    }
                }

                Section("Journal") {
                    if let url = session.journalURL {
                        ShareLink(item: url) {
                            Label("Share journal (JSONL)", systemImage: "square.and.arrow.up")
                        }
                    }
                    ForEach(Array(session.journalLines.enumerated()), id: \.offset) { _, line in
                        Text(line)
                            .font(.system(.caption, design: .monospaced))
                    }
                }
            }
            .navigationTitle("ResetLight Probe")
        }
    }

    private func canProbe(_ channelName: String) -> Bool {
        (session.phase == .idle || session.phase.isTerminal)
            && !session.probedChannels.contains(channelName)
    }

    private var statusText: String {
        switch session.phase {
        case .idle: return "Idle — choose a channel to probe."
        case .bluetoothUnavailable: return "Bluetooth is unavailable. Check permission and that Bluetooth is on."
        case .scanning: return "Scanning for the adapter…"
        case .connecting: return "Connecting…"
        case .discovering: return "Discovering services…"
        case .enablingNotifications: return "Enabling notifications…"
        case .awaitingResponse: return "ATI sent — waiting for the response…"
        case .complete: return "Response received — share the journal."
        case .noResponse: return "No (complete) response. Preserve the journal; try the other channel in a new run."
        case .failed(let reason): return "Failed: \(reason)"
        }
    }
}
