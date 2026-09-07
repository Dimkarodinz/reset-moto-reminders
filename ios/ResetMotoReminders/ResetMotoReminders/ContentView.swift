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
  @FocusState private var distanceFieldFocused: Bool

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
            if session.dashboardReadAvailable { dashboardCard }
            if session.dtcReadAvailable { dtcCard }
            if session.dtcClearAvailable { clearCard }
            if session.serviceResetAvailable { serviceCard }
          }

          Text(
            L10n.text("ios_disclaimer")
          )
          .font(.footnote)
          .foregroundStyle(.secondary)
        }
        .frame(maxWidth: 640, alignment: .leading)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
      }
      .scrollDismissesKeyboard(.interactively)

      if session.operationRunning {
        Color.black.opacity(0.45).ignoresSafeArea()
        VStack(spacing: 12) {
          ProgressView()
          Text(session.operationTitle ?? L10n.text("ios_working"))
            .fontWeight(.semibold)
        }
        .padding(24)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
      }
    }
    .confirmationDialog(
      L10n.text("ios_clear_confirmation_title"),
      isPresented: $confirmClear,
      titleVisibility: .visible
    ) {
      Button(L10n.text("dtc_clear_button"), role: .destructive) { session.clearDTCs() }
      Button(L10n.text("action_cancel"), role: .cancel) {}
    } message: {
      Text(clearConfirmationText)
    }
    .confirmationDialog(
      L10n.text("ios_reset_confirmation_title"),
      isPresented: $confirmReset,
      titleVisibility: .visible
    ) {
      Button(L10n.text("service_reset_button")) { performReset() }
      Button(L10n.text("action_cancel"), role: .cancel) {}
    } message: {
      Text(resetConfirmationText)
    }
    .toolbar {
      ToolbarItemGroup(placement: .keyboard) {
        Spacer()
        Button(L10n.text("action_done")) {
          validateDistanceInput(distance)
          distanceFieldFocused = false
        }
      }
    }
  }

  private var connectionCard: some View {
    Card {
      Text(session.state.title)
        .font(.title3.weight(.semibold))
      if session.state == .disconnected {
        Picker(
          L10n.text("motorcycle_profile_select"),
          selection: Binding(
            get: { session.selectedMotorcycle.id },
            set: { session.selectMotorcycle($0) })
        ) {
          ForEach(session.availableMotorcycles) { motorcycle in
            Text(motorcycle.displayName).tag(motorcycle.id)
          }
        }
        .pickerStyle(.menu)
      } else {
        Text(session.selectedMotorcycle.displayName)
          .font(.subheadline)
          .foregroundStyle(.secondary)
      }
      if session.motorcycleExperimental {
        Text(L10n.text("motorcycle_profile_experimental_short"))
          .font(.caption2.bold())
          .padding(.horizontal, 7)
          .padding(.vertical, 3)
          .background(Color.orange.opacity(0.2), in: Capsule())
          .foregroundStyle(.orange)
      }
      if let identity = session.adapterIdentity {
        Text(identity).font(.subheadline.monospaced()).foregroundStyle(.secondary)
        if session.adapterExperimental {
          Text(L10n.text("ios_adapter_experimental"))
            .font(.caption2.bold())
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(Color.orange.opacity(0.2), in: Capsule())
            .foregroundStyle(.orange)
        }
      } else if session.state == .disconnected {
        Text(L10n.text("ios_connection_hint"))
          .foregroundStyle(.secondary)
      }
      if session.state.isBusy { ProgressView() }
      Button {
        session.state.isReady || session.state.isBusy ? session.disconnect() : session.connect()
      } label: {
        ActionButtonLabel(key: connectionButtonKey)
      }
      .buttonStyle(.borderedProminent)
      .controlSize(.large)
      .disabled(session.operationRunning)
    }
  }

  private var dashboardCard: some View {
    Card {
      Text(L10n.text("ios_motorcycle_title")).font(.headline)
      Text(session.dashboardStatus).foregroundStyle(.secondary)
      if let dashboard = session.dashboard {
        LabeledContent(
          L10n.text("ios_odometer_label"), value: "\(dashboard.odometerKilometres) km")
        LabeledContent(
          L10n.text("ios_dashboard_label"), value: L10n.text("ios_dashboard_supported"))
        Text(
          L10n.format("ios_fingerprint_format", dashboard.statusASCII)
        )
        .font(.footnote)
        .foregroundStyle(.secondary)
      }
      Button {
        session.readDashboard()
      } label: {
        ActionButtonLabel(key: "instrument_read_button")
      }
      .buttonStyle(.borderedProminent)
      .controlSize(.large)
    }
  }

  private var dtcCard: some View {
    Card {
      Text(L10n.text("dtc_read_title")).font(.headline)
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
        ActionButtonLabel(key: "dtc_read_button")
      }
      .buttonStyle(.borderedProminent)
      .controlSize(.large)
    }
  }

  private var clearCard: some View {
    Card {
      HStack {
        Text(L10n.text("dtc_clear_button")).font(.headline)
        Text(L10n.text("ios_beta"))
          .font(.caption2.bold())
          .padding(.horizontal, 7)
          .padding(.vertical, 3)
          .background(Color.orange.opacity(0.2), in: Capsule())
          .foregroundStyle(.orange)
      }
      Text(
        L10n.text("ios_clear_detail")
      )
      .font(.subheadline)
      .foregroundStyle(.secondary)
      if !session.hasReadDTCs {
        Text(L10n.text("ios_read_codes_first"))
          .font(.footnote)
          .foregroundStyle(.orange)
      }
      Button(role: .destructive) {
        confirmClear = true
      } label: {
        ActionButtonLabel(key: "dtc_clear_button")
      }
      .buttonStyle(.bordered)
      .controlSize(.large)
      .disabled(
        !DTCActionPolicy.canClear(hasCurrentRead: session.hasReadDTCs, count: session.dtcs.count))
    }
  }

  private var serviceCard: some View {
    Card {
      Text(L10n.text("service_reset_title")).font(.headline)
      Text(session.serviceStatus).foregroundStyle(.secondary)
      Picker(L10n.text("ios_dashboard_unit"), selection: $distanceUnit) {
        Text(L10n.text("ios_unit_kilometres")).tag(DistanceUnit.kilometres)
        Text(L10n.text("ios_unit_miles")).tag(DistanceUnit.miles)
      }
      .pickerStyle(.segmented)
      TextField(L10n.text("ios_interval"), text: $distance)
        .keyboardType(.numberPad)
        .textFieldStyle(.roundedBorder)
        .focused($distanceFieldFocused)
        .onChange(of: distance) { validateDistanceInput($0) }
      DatePicker(
        L10n.text("service_reset_date_label"),
        selection: $nextServiceDate,
        in: Date()...(Calendar.current.date(byAdding: .year, value: 2, to: Date()) ?? Date()),
        displayedComponents: .date
      )
      Text(L10n.text("service_reset_date_time_reminder"))
        .font(.footnote)
        .foregroundStyle(.secondary)
      if session.serviceResetRequiresDashboardRead && session.dashboard == nil {
        Text(L10n.text("ios_read_motorcycle_first"))
          .font(.footnote)
          .foregroundStyle(.orange)
      }
      if let inputMessage { Text(inputMessage).font(.footnote).foregroundStyle(.red) }
      Button {
        guard validDistance != nil else {
          inputMessage = intervalErrorMessage
          return
        }
        distanceFieldFocused = false
        inputMessage = nil
        confirmReset = true
      } label: {
        ActionButtonLabel(key: "service_reset_button")
      }
      .buttonStyle(.borderedProminent)
      .controlSize(.large)
      .disabled(
        (session.serviceResetRequiresDashboardRead && session.dashboard == nil)
          || validDistance == nil)
    }
  }

  private var validDistance: Int? {
    guard let value = Int(distance),
      session.serviceIntervalConstraints(for: distanceUnit)?.accepts(value) == true
    else {
      return nil
    }
    return value
  }

  private var intervalErrorMessage: String {
    guard let constraints = session.serviceIntervalConstraints(for: distanceUnit) else {
      return L10n.text("ios_interval_error")
    }
    return L10n.format(
      "service_reset_interval_error_range",
      constraints.step, unitLabel, constraints.minimum, constraints.maximum)
  }

  private var unitLabel: String {
    L10n.text(distanceUnit == .kilometres ? "distance_unit_km" : "distance_unit_miles")
  }

  private var connectionButtonKey: String {
    if session.state.isBusy { return "action_cancel" }
    return session.state.isReady ? "button_disconnect" : "button_connect"
  }

  private var clearConfirmationText: String {
    let codes = session.dtcs.map(\.code).joined(separator: ", ")
    let warning =
      session.motorcycleExperimental
      ? " " + L10n.text("experimental_write_warning") : ""
    return L10n.format("ios_clear_confirmation_format", codes) + warning
  }

  private var resetConfirmationText: String {
    let odometer =
      session.dashboard.map {
        L10n.format("ios_current_odometer_format", $0.odometerKilometres) + " "
      } ?? ""
    let warning =
      session.motorcycleExperimental
      ? " " + L10n.text("experimental_write_warning") : ""
    return odometer
      + L10n.format(
        "ios_reset_confirmation_format", distance, unitLabel,
        nextServiceDate.formatted(date: .abbreviated, time: .omitted)) + warning
  }

  private func validateDistanceInput(_ input: String) {
    let digits = input.filter(\.isNumber)
    if digits != input {
      distance = digits
      return
    }
    guard !digits.isEmpty else {
      inputMessage = nil
      return
    }
    inputMessage =
      validDistance == nil ? intervalErrorMessage : nil
  }

  private func performReset() {
    guard let value = validDistance else { return }
    session.resetService(distance: value, unit: distanceUnit, date: nextServiceDate)
  }
}

private struct ActionButtonLabel: View {
  let key: String

  var body: some View {
    Text(L10n.text(key))
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
