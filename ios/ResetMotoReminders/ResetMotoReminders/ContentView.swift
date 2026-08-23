import ResetMotoCore
import SwiftUI

struct ContentView: View {
  @ObservedObject var session: AdapterSession
  @State private var distanceUnit: DistanceUnit = .kilometres
  @State private var distance = "10000"
  @State private var nextServiceDate =
    Calendar.current.date(byAdding: .year, value: 1, to: Date()) ?? Date()
  @State private var confirmClear = false
  @State private var confirmReset = false
  @State private var inputMessage: String?

  var body: some View {
    ZStack {
      Color(red: 0.04, green: 0.06, blue: 0.08).ignoresSafeArea()
      ScrollView {
        VStack(alignment: .leading, spacing: 16) {
          Text("RESET MOTO REMINDERS")
            .font(.custom("AvenirNextCondensed-HeavyItalic", size: 34, relativeTo: .largeTitle))
            .tracking(0.8)
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .foregroundStyle(.white)

          connectionCard

          if session.state.isReady {
            dashboardCard
            dtcCard
            clearCard
            serviceCard
          }

          Text(
            "Unofficial tool. Clearing codes does not repair faults. Resetting a reminder does not perform maintenance."
          )
          .font(.footnote)
          .foregroundStyle(.secondary)
        }
        .frame(maxWidth: 640, alignment: .leading)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
      }

      if session.operationRunning {
        Color.black.opacity(0.45).ignoresSafeArea()
        VStack(spacing: 12) {
          ProgressView()
          Text(session.operationTitle ?? "Working…")
            .fontWeight(.semibold)
        }
        .padding(24)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
      }
    }
    .confirmationDialog(
      "Clear confirmed trouble codes?",
      isPresented: $confirmClear,
      titleVisibility: .visible
    ) {
      Button("Clear trouble codes", role: .destructive) { session.clearDTCs() }
      Button("Cancel", role: .cancel) {}
    } message: {
      Text(clearConfirmationText)
    }
    .confirmationDialog(
      "Reset service reminder?",
      isPresented: $confirmReset,
      titleVisibility: .visible
    ) {
      Button("Reset reminder") { performReset() }
      Button("Cancel", role: .cancel) {}
    } message: {
      Text(resetConfirmationText)
    }
  }

  private var connectionCard: some View {
    Card {
      Text(session.state.title)
        .font(.title3.weight(.semibold))
      if let identity = session.adapterIdentity {
        Text(identity).font(.subheadline.monospaced()).foregroundStyle(.secondary)
      } else if session.state == .disconnected {
        Text("Power the motorcycle and vLinker MC-IOS, then connect.")
          .foregroundStyle(.secondary)
      }
      if session.state.isBusy { ProgressView() }
      Button {
        session.state.isReady || session.state.isBusy ? session.disconnect() : session.connect()
      } label: {
        ActionButtonLabel(connectionButtonTitle)
      }
      .buttonStyle(.borderedProminent)
      .controlSize(.large)
      .disabled(session.operationRunning)
    }
  }

  private var dashboardCard: some View {
    Card {
      Text("Motorcycle").font(.headline)
      Text(session.dashboardStatus).foregroundStyle(.secondary)
      if let dashboard = session.dashboard {
        LabeledContent("Odometer", value: "\(dashboard.odometerKilometres) km")
        LabeledContent("Dashboard status", value: dashboard.statusASCII)
      }
      Button {
        session.readDashboard()
      } label: {
        ActionButtonLabel("Read motorcycle")
      }
      .buttonStyle(.borderedProminent)
      .controlSize(.large)
    }
  }

  private var dtcCard: some View {
    Card {
      Text("Trouble codes").font(.headline)
      Text(session.dtcStatus).foregroundStyle(.secondary)
      ForEach(session.dtcs) { dtc in
        VStack(alignment: .leading, spacing: 3) {
          Text(dtc.code).font(.body.monospaced().weight(.semibold))
          Text(dtc.message).font(.subheadline).foregroundStyle(.secondary)
        }
        .padding(.vertical, 3)
      }
      Button {
        session.readDTCs()
      } label: {
        ActionButtonLabel("Read trouble codes")
      }
      .buttonStyle(.borderedProminent)
      .controlSize(.large)
    }
  }

  private var clearCard: some View {
    Card {
      HStack {
        Text("Clear trouble codes").font(.headline)
        Text("BETA")
          .font(.caption2.bold())
          .padding(.horizontal, 7)
          .padding(.vertical, 3)
          .background(Color.orange.opacity(0.2), in: Capsule())
          .foregroundStyle(.orange)
      }
      Text(
        "Clears confirmed DTC memory after security access. It does not repair the underlying fault."
      )
      .font(.subheadline)
      .foregroundStyle(.secondary)
      if !session.hasReadDTCs {
        Text("Read and review the current trouble codes first.")
          .font(.footnote)
          .foregroundStyle(.orange)
      }
      Button(role: .destructive) {
        confirmClear = true
      } label: {
        ActionButtonLabel("Clear trouble codes")
      }
      .buttonStyle(.bordered)
      .controlSize(.large)
      .disabled(
        !DTCActionPolicy.canClear(hasCurrentRead: session.hasReadDTCs, count: session.dtcs.count))
    }
  }

  private var serviceCard: some View {
    Card {
      Text("Service reminder").font(.headline)
      Text(session.serviceStatus).foregroundStyle(.secondary)
      Picker("Dashboard unit", selection: $distanceUnit) {
        Text("Kilometres").tag(DistanceUnit.kilometres)
        Text("Miles").tag(DistanceUnit.miles)
      }
      .pickerStyle(.segmented)
      TextField("Interval", text: $distance)
        .keyboardType(.numberPad)
        .textFieldStyle(.roundedBorder)
      DatePicker(
        "Next service date",
        selection: $nextServiceDate,
        in: Date()...(Calendar.current.date(byAdding: .year, value: 2, to: Date()) ?? Date()),
        displayedComponents: .date
      )
      Text("Use 100-unit steps, from 100 to 25,500. Check the iPhone date and time first.")
        .font(.footnote)
        .foregroundStyle(.secondary)
      if session.dashboard == nil {
        Text("Read the motorcycle first so the current odometer is visible before confirmation.")
          .font(.footnote)
          .foregroundStyle(.orange)
      }
      if let inputMessage { Text(inputMessage).font(.footnote).foregroundStyle(.red) }
      Button {
        guard validDistance != nil else {
          inputMessage = "Enter a value from 100 to 25,500 in 100-unit steps."
          return
        }
        inputMessage = nil
        confirmReset = true
      } label: {
        ActionButtonLabel("Reset service reminder")
      }
      .buttonStyle(.borderedProminent)
      .controlSize(.large)
      .disabled(session.dashboard == nil)
    }
  }

  private var validDistance: Int? {
    guard let value = Int(distance), value >= 100, value <= 25_500, value.isMultiple(of: 100) else {
      return nil
    }
    return value
  }

  private var unitLabel: String { distanceUnit == .kilometres ? "km" : "miles" }

  private var connectionButtonTitle: String {
    if session.state.isBusy { return "Cancel" }
    return session.state.isReady ? "Disconnect" : "Connect"
  }

  private var clearConfirmationText: String {
    let codes = session.dtcs.map(\.code).joined(separator: ", ")
    return "Clear \(codes)? Diagnostic evidence will be erased. Existing faults are not repaired."
  }

  private var resetConfirmationText: String {
    let odometer = session.dashboard.map { "Current odometer: \($0.odometerKilometres) km. " } ?? ""
    return odometer
      + "Set \(distance) \(unitLabel) and \(nextServiceDate.formatted(date: .abbreviated, time: .omitted)). Check that the iPhone date and time are correct."
  }

  private func performReset() {
    guard let value = validDistance else { return }
    session.resetService(distance: value, unit: distanceUnit, date: nextServiceDate)
  }
}

private struct ActionButtonLabel: View {
  let title: String

  init(_ title: String) { self.title = title }

  var body: some View {
    Text(title)
      .font(.headline)
      .frame(maxWidth: .infinity, minHeight: 28, alignment: .center)
  }
}

private struct Card<Content: View>: View {
  @ViewBuilder let content: Content

  init(@ViewBuilder content: () -> Content) { self.content = content() }

  var body: some View {
    VStack(alignment: .leading, spacing: 12) { content }
      .frame(maxWidth: .infinity, alignment: .leading)
      .padding(16)
      .background(Color(red: 0.08, green: 0.11, blue: 0.14), in: RoundedRectangle(cornerRadius: 16))
      .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.08)))
  }
}
