import CoreBluetooth
import Foundation
import OSLog
import ResetMotoCore

enum AdapterConnectionState: Equatable {
  case disconnected
  case waitingForBluetooth
  case scanning
  case connecting
  case discovering
  case identifying
  case ready
  case disconnecting
  case failed(String)

  var title: String {
    switch self {
    case .disconnected: return L10n.text("status_disconnected_title")
    case .waitingForBluetooth: return L10n.text("ios_status_waiting_bluetooth")
    case .scanning: return L10n.text("ios_status_scanning")
    case .connecting: return L10n.text("status_connecting_title")
    case .discovering: return L10n.text("ios_status_discovering")
    case .identifying: return L10n.text("status_identifying_title")
    case .ready: return L10n.text("ios_status_motorcycle_connected")
    case .disconnecting: return L10n.text("status_disconnecting_title")
    case .failed(let reason): return reason
    }
  }

  var isReady: Bool { self == .ready }
  var isBusy: Bool {
    switch self {
    case .waitingForBluetooth, .scanning, .connecting, .discovering, .identifying, .disconnecting:
      return true
    default: return false
    }
  }
}

enum BLECommandError: LocalizedError, Equatable {
  case notReady
  case unsupportedLayout
  case invalidIdentity
  case timeout(String)
  case ambiguousWrite(String)
  case disconnected
  case bluetooth(String)

  var errorDescription: String? {
    switch self {
    case .notReady: return L10n.text("ios_error_connect_first")
    case .unsupportedLayout: return L10n.text("ios_error_unsupported_layout")
    case .invalidIdentity: return L10n.text("ios_error_invalid_identity")
    case .timeout: return L10n.text("ios_error_timeout")
    case .ambiguousWrite: return L10n.text("ios_error_ambiguous_write")
    case .disconnected: return L10n.text("ios_error_disconnected")
    case .bluetooth(let message): return message
    }
  }
}

@MainActor
final class AdapterSession: NSObject, ObservableObject, @unchecked Sendable {
  @Published private(set) var state: AdapterConnectionState = .disconnected
  @Published private(set) var adapterIdentity: String?
  @Published private(set) var adapterExperimental = false
  @Published private(set) var dashboard: DashboardResult?
  @Published private(set) var dashboardStatus = L10n.text("instrument_read_body_idle")
  @Published private var dtcReadState = DTCReadState()
  @Published private(set) var dtcStatus = L10n.text("dtc_read_body_idle")
  @Published private(set) var serviceStatus = L10n.text("service_reset_body_idle")
  @Published private(set) var operationRunning = false
  @Published private(set) var operationTitle: String?

  let profile: ResetMotoProfile

  private var central: CBCentralManager?
  private var peripheral: CBPeripheral?
  private var commandCharacteristic: CBCharacteristic?
  private var responseCharacteristic: CBCharacteristic?
  private var selectedAdapter: ResetMotoCore.AdapterProfile?
  private var assembler: ElmResponseAssembler
  private var pending: PendingCommand?
  private var timeoutTask: Task<Void, Never>?
  private let operationGate = OperationGate()
  private let logger = Logger(subsystem: "dev.resetlight.ios", category: "DiagnosticSession")
  private var operationSentStateChangingWrite = false
  private var backgroundInterruptedAfterWrite = false

  var dtcs: [DiagnosticTroubleCode] { dtcReadState.codes }
  var hasReadDTCs: Bool { dtcReadState.hasCurrentRead }

  override init() {
    do {
      profile = try ResetMotoProfile.bundledTiger900()
    } catch {
      fatalError("Bundled motorcycle profile is invalid: \(error)")
    }
    assembler = ElmResponseAssembler(promptByte: profile.adapters[0].promptByte)
    super.init()
  }

  func connect() {
    guard state == .disconnected || isFailed else { return }
    resetFeatureState()
    logger.info("Starting MC-IOS connection")
    state = .waitingForBluetooth
    central = CBCentralManager(delegate: self, queue: .main)
  }

  func disconnect() {
    guard !operationRunning, state != .disconnected else { return }
    state = .disconnecting
    logger.info("Disconnect requested")
    timeoutTask?.cancel()
    if pending != nil {
      finishPending(.failure(BLECommandError.disconnected))
    }
    central?.stopScan()
    if let peripheral { central?.cancelPeripheralConnection(peripheral) }
    finishDisconnected()
  }

  func applicationDidEnterBackground() {
    guard state != .disconnected, !isFailed else { return }
    backgroundInterruptedAfterWrite = OperationInterruptionPolicy.isAmbiguous(
      operationRunning: operationRunning,
      stateChangingWriteSent: operationSentStateChangingWrite
    )
    let failure: BLECommandError =
      backgroundInterruptedAfterWrite
      ? .ambiguousWrite(operationTitle ?? L10n.text("ios_operation_interrupted"))
      : .disconnected
    let reason =
      backgroundInterruptedAfterWrite
      ? failure.localizedDescription
      : L10n.text("ios_background_closed")
    logger.notice(
      "Closing connection in background; ambiguous=\(self.backgroundInterruptedAfterWrite)")
    failAndDisconnect(reason, pendingFailure: failure)
  }

  func readDashboard() {
    guard canStartOperation else { return }
    dashboard = nil
    dashboardStatus = L10n.text("instrument_read_body_running")
    runOperation(.dashboard) { channel in
      let result = try await DashboardUseCase(profile: self.profile.instrument).read(using: channel)
      self.dashboard = result
      self.dashboardStatus = L10n.text("ios_dashboard_responded")
    }
  }

  func readDTCs() {
    guard canStartOperation else { return }
    dtcReadState.beginRead()
    dtcStatus = L10n.text("dtc_read_body_running")
    runOperation(.dtcRead) { channel in
      let result = try await DTCUseCase(
        profile: self.profile.engine,
        descriptions: self.localizedDTCDescriptions
      ).read(using: channel)
      self.dtcReadState.completeRead(result)
      self.dtcStatus =
        result.isEmpty
        ? L10n.text("dtc_read_body_none") : L10n.format("dtc_read_body_count", result.count)
    }
  }

  func clearDTCs() {
    guard canStartOperation, dtcReadState.canClear else { return }
    dtcReadState.beginClearAttempt()
    runOperation(.dtcClear) { channel in
      let result = try await DTCUseCase(
        profile: self.profile.engine,
        descriptions: self.localizedDTCDescriptions
      ).clear(using: channel)
      switch result {
      case .cleared:
        self.dtcReadState.completeRead([])
        self.dtcStatus = L10n.text("ios_dtc_cleared")
      case .blocked:
        self.dtcStatus = L10n.text("ios_dtc_clear_blocked")
      case .needsVerification:
        self.dtcStatus = L10n.text("ios_dtc_clear_unverified")
      }
    }
  }

  func resetService(distance: Int, unit: DistanceUnit, date: Date) {
    runOperation(.serviceReset) { channel in
      let outcome = try await ServiceReminderUseCase(profile: self.profile.instrument)
        .reset(distance: distance, unit: unit, nextServiceDate: date, using: channel)
      switch outcome {
      case .committed(let odometer):
        self.serviceStatus = L10n.format("ios_service_committed_format", odometer)
      case .blocked:
        self.serviceStatus = L10n.text("ios_service_blocked")
      case .partiallyApplied:
        self.serviceStatus = L10n.text("ios_service_partial")
      }
    }
  }

  fileprivate func execute(_ command: String, intent: CommandIntent) async throws -> String {
    guard state == .ready else { throw BLECommandError.notReady }
    return try await executeRaw(command, intent: intent, allowBeforeReady: false)
  }

  private var isFailed: Bool {
    if case .failed = state { return true }
    return false
  }

  private var canStartOperation: Bool { state == .ready && !operationRunning }

  private var localizedDTCDescriptions: [String: String] {
    profile.dtcDescriptions(forLanguage: Locale.preferredLanguages.first)
  }

  private func resetFeatureState() {
    adapterIdentity = nil
    adapterExperimental = false
    dashboard = nil
    dashboardStatus = L10n.text("instrument_read_body_idle")
    dtcReadState = DTCReadState()
    dtcStatus = L10n.text("dtc_read_body_idle")
    serviceStatus = L10n.text("service_reset_body_idle")
  }

  private func runOperation(
    _ operation: OperationKind,
    action: @escaping @MainActor (SessionCommandChannel) async throws -> Void
  ) {
    guard canStartOperation else { return }
    let title = L10n.text(operation.titleKey)
    operationRunning = true
    operationTitle = title
    operationSentStateChangingWrite = false
    backgroundInterruptedAfterWrite = false
    logger.info("Operation started: \(title, privacy: .public)")
    Task { @MainActor in
      guard let lease = await operationGate.tryAcquire() else {
        operationTitle = nil
        operationRunning = false
        return
      }
      do {
        try await action(SessionCommandChannel(session: self))
        logger.info("Operation completed: \(title, privacy: .public)")
      } catch {
        let reportedError: Error =
          backgroundInterruptedAfterWrite
          ? BLECommandError.ambiguousWrite(title)
          : error
        let message = L10n.message(for: reportedError)
        logger.error(
          "Operation failed: \(title, privacy: .public); \(message, privacy: .public)")
        switch operation {
        case .dashboard: dashboardStatus = message
        case .dtcRead, .dtcClear: dtcStatus = message
        case .serviceReset: serviceStatus = message
        }
        if reportedError is BLECommandError { failAndDisconnect(message) }
      }
      operationTitle = nil
      operationRunning = false
      operationSentStateChangingWrite = false
      backgroundInterruptedAfterWrite = false
      await lease.release()
    }
  }

  private func executeRaw(
    _ command: String,
    intent: CommandIntent,
    allowBeforeReady: Bool
  ) async throws -> String {
    guard allowBeforeReady || state == .ready else { throw BLECommandError.notReady }
    guard pending == nil,
      let peripheral,
      let characteristic = commandCharacteristic
    else { throw BLECommandError.notReady }
    guard let adapter = selectedAdapter else { throw BLECommandError.notReady }
    let payload = Data((command + "\r").utf8)
    let reportedMaximum = peripheral.maximumWriteValueLength(for: .withoutResponse)
    let maximum = BLEPacketSizer.safePayloadBytes(reportedMaximum: reportedMaximum)
    let chunks = BLEPacketSizer.chunks(payload, maximum: maximum)
    guard let firstChunk = chunks.first else { throw BLECommandError.unsupportedLayout }
    assembler = ElmResponseAssembler(promptByte: adapter.promptByte)
    let commandTag = logTag(for: command)
    logger.debug(
      "Sending command \(commandTag, privacy: .public); write=\(intent == .write)")
    return try await withCheckedThrowingContinuation { continuation in
      pending = PendingCommand(
        command: command, intent: intent, continuation: continuation, sent: true,
        completion: CommandCompletionGate(), chunks: chunks, nextChunkIndex: 1)
      if intent == .write { operationSentStateChangingWrite = true }
      peripheral.writeValue(firstChunk, for: characteristic, type: .withResponse)
      startTimeout(command: command, intent: intent)
    }
  }

  private func startTimeout(command: String, intent: CommandIntent) {
    timeoutTask?.cancel()
    timeoutTask = Task { @MainActor [weak self] in
      try? await Task.sleep(nanoseconds: 5_000_000_000)
      guard !Task.isCancelled, let self, self.pending != nil else { return }
      self.logger.error("Command timed out: \(self.logTag(for: command), privacy: .public)")
      let error: BLECommandError = intent == .write ? .ambiguousWrite(command) : .timeout(command)
      self.finishPending(.failure(error))
    }
  }

  private func finishPending(_ result: Result<String, Error>) {
    timeoutTask?.cancel()
    timeoutTask = nil
    guard let pending else { return }
    self.pending = nil
    pending.continuation.resume(with: result)
  }

  private func finishPendingIfComplete() {
    guard let pending, let response = pending.completion.completedResponse else { return }
    logger.debug(
      "Command completed: \(self.logTag(for: pending.command), privacy: .public); responseCharacters=\(response.count)"
    )
    finishPending(.success(response))
  }

  private func logTag(for command: String) -> String {
    command.uppercased().hasPrefix("AT")
      ? command.uppercased() : String(command.prefix(8)).uppercased()
  }

  private func beginIdentification() {
    state = .identifying
    Task { @MainActor in
      do {
        guard let adapter = selectedAdapter else { throw BLECommandError.unsupportedLayout }
        let identity = try await executeRaw(
          adapter.identifyCommand, intent: .read, allowBeforeReady: true)
        guard AdapterValidation.acceptsIdentity(identity) else {
          throw BLECommandError.invalidIdentity
        }
        adapterIdentity = identity
        adapterExperimental = adapter.experimental
        state = .ready
        logger.info("Adapter identity accepted; diagnostic session ready")
      } catch {
        failAndDisconnect(
          (error as? LocalizedError)?.errorDescription ?? error.localizedDescription)
      }
    }
  }

  private func failAndDisconnect(
    _ reason: String,
    pendingFailure: BLECommandError? = nil
  ) {
    logger.error("Connection failed: \(reason, privacy: .public)")
    let activePeripheral = peripheral
    if let pending {
      let failure =
        pendingFailure
        ?? (pending.intent == .write && pending.sent
          ? BLECommandError.ambiguousWrite(pending.command)
          : BLECommandError.bluetooth(reason))
      finishPending(.failure(failure))
    }
    state = .failed(reason)
    central?.stopScan()
    if let activePeripheral { central?.cancelPeripheralConnection(activePeripheral) }
    self.peripheral = nil
    selectedAdapter = nil
    commandCharacteristic = nil
    responseCharacteristic = nil
    central = nil
  }

  private func finishDisconnected() {
    peripheral = nil
    selectedAdapter = nil
    commandCharacteristic = nil
    responseCharacteristic = nil
    central = nil
    state = .disconnected
  }
}

private enum OperationKind {
  case dashboard
  case dtcRead
  case dtcClear
  case serviceReset

  var titleKey: String {
    switch self {
    case .dashboard: return "ios_operation_reading_motorcycle"
    case .dtcRead: return "ios_operation_reading_dtc"
    case .dtcClear: return "ios_operation_clearing_dtc"
    case .serviceReset: return "ios_operation_resetting_service"
    }
  }
}

private struct PendingCommand {
  let command: String
  let intent: CommandIntent
  let continuation: CheckedContinuation<String, Error>
  let sent: Bool
  var completion: CommandCompletionGate
  let chunks: [Data]
  var nextChunkIndex: Int
}

private final class SessionCommandChannel: DiagnosticCommanding, @unchecked Sendable {
  private weak var session: AdapterSession?

  init(session: AdapterSession) { self.session = session }

  func execute(_ command: String, intent: CommandIntent) async throws -> String {
    guard let session else { throw BLECommandError.disconnected }
    return try await session.execute(command, intent: intent)
  }
}

extension AdapterSession: CBCentralManagerDelegate {
  nonisolated func centralManagerDidUpdateState(_ central: CBCentralManager) {
    Task { @MainActor in
      guard self.central === central else { return }
      switch central.state {
      case .poweredOn:
        self.state = .scanning
        central.scanForPeripherals(
          withServices: self.profile.adapters.map { CBUUID(string: $0.serviceUUID) })
      case .unauthorized:
        self.failAndDisconnect(L10n.text("ios_error_bluetooth_permission"))
      case .poweredOff:
        self.failAndDisconnect(L10n.text("ios_error_bluetooth_off"))
      case .unsupported:
        self.failAndDisconnect(L10n.text("ios_error_ble_unsupported"))
      default:
        break
      }
    }
  }

  nonisolated func centralManager(
    _ central: CBCentralManager,
    didDiscover peripheral: CBPeripheral,
    advertisementData: [String: Any],
    rssi: NSNumber
  ) {
    Task { @MainActor in
      let name = advertisementData[CBAdvertisementDataLocalNameKey] as? String ?? peripheral.name
      let advertisedServices = Set(
        (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] ?? []).map(
          \.uuidString))
      guard self.central === central, self.state == .scanning,
        let adapter = AdapterSelection.match(
          name: name, serviceUUIDs: advertisedServices, adapters: self.profile.adapters)
      else { return }
      central.stopScan()
      self.selectedAdapter = adapter
      self.peripheral = peripheral
      peripheral.delegate = self
      self.state = .connecting
      central.connect(peripheral)
    }
  }

  nonisolated func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral)
  {
    Task { @MainActor in
      guard self.central === central, self.peripheral === peripheral else { return }
      guard let adapter = self.selectedAdapter else {
        return self.failAndDisconnect(BLECommandError.unsupportedLayout.localizedDescription)
      }
      self.state = .discovering
      peripheral.discoverServices([CBUUID(string: adapter.serviceUUID)])
    }
  }

  nonisolated func centralManager(
    _ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?
  ) {
    Task { @MainActor in
      guard self.central === central, self.peripheral === peripheral else { return }
      self.failAndDisconnect(error?.localizedDescription ?? L10n.text("ios_error_connect_failed"))
    }
  }

  nonisolated func centralManager(
    _ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?
  ) {
    Task { @MainActor in
      guard self.central === central, self.peripheral === peripheral else { return }
      let failure: BLECommandError =
        self.pending?.intent == .write && self.pending?.sent == true
        ? .ambiguousWrite(self.pending?.command ?? "write")
        : .disconnected
      let reason =
        failure.errorDescription ?? error?.localizedDescription ?? "Adapter disconnected."
      self.failAndDisconnect(reason, pendingFailure: failure)
    }
  }
}

extension AdapterSession: CBPeripheralDelegate {
  nonisolated func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
    Task { @MainActor in
      guard self.peripheral === peripheral else { return }
      guard let adapter = self.selectedAdapter else {
        return self.failAndDisconnect(BLECommandError.unsupportedLayout.localizedDescription)
      }
      guard error == nil,
        let service = peripheral.services?.first(where: {
          $0.uuid == CBUUID(string: adapter.serviceUUID)
        })
      else {
        return self.failAndDisconnect(BLECommandError.unsupportedLayout.localizedDescription)
      }
      peripheral.discoverCharacteristics(
        [
          CBUUID(string: adapter.commandCharacteristicUUID),
          CBUUID(string: adapter.responseCharacteristicUUID),
        ],
        for: service
      )
    }
  }

  nonisolated func peripheral(
    _ peripheral: CBPeripheral,
    didDiscoverCharacteristicsFor service: CBService,
    error: Error?
  ) {
    Task { @MainActor in
      guard self.peripheral === peripheral else { return }
      guard let adapter = self.selectedAdapter else {
        return self.failAndDisconnect(BLECommandError.unsupportedLayout.localizedDescription)
      }
      guard error == nil else {
        return self.failAndDisconnect(BLECommandError.unsupportedLayout.localizedDescription)
      }
      self.commandCharacteristic = service.characteristics?.first {
        $0.uuid == CBUUID(string: adapter.commandCharacteristicUUID)
      }
      self.responseCharacteristic = service.characteristics?.first {
        $0.uuid == CBUUID(string: adapter.responseCharacteristicUUID)
      }
      guard let command = self.commandCharacteristic,
        let response = self.responseCharacteristic,
        AdapterValidation.acceptsGattLayout(
          responseCanNotify: response.properties.contains(.notify)
            || response.properties.contains(.indicate),
          commandCanWriteWithResponse: command.properties.contains(.write)
        )
      else {
        return self.failAndDisconnect(BLECommandError.unsupportedLayout.localizedDescription)
      }
      peripheral.setNotifyValue(true, for: response)
    }
  }

  nonisolated func peripheral(
    _ peripheral: CBPeripheral,
    didUpdateNotificationStateFor characteristic: CBCharacteristic,
    error: Error?
  ) {
    Task { @MainActor in
      guard self.peripheral === peripheral else { return }
      guard error == nil, characteristic === self.responseCharacteristic, characteristic.isNotifying
      else {
        return self.failAndDisconnect(BLECommandError.unsupportedLayout.localizedDescription)
      }
      self.beginIdentification()
    }
  }

  nonisolated func peripheral(
    _ peripheral: CBPeripheral,
    didWriteValueFor characteristic: CBCharacteristic,
    error: Error?
  ) {
    Task { @MainActor in
      guard self.peripheral === peripheral, characteristic === self.commandCharacteristic,
        var pending = self.pending
      else { return }
      if let error {
        let failure: BLECommandError =
          pending.intent == .write && pending.sent
          ? .ambiguousWrite(pending.command)
          : .bluetooth(error.localizedDescription)
        return self.finishPending(.failure(failure))
      }
      if pending.nextChunkIndex < pending.chunks.count {
        let chunk = pending.chunks[pending.nextChunkIndex]
        pending.nextChunkIndex += 1
        self.pending = pending
        peripheral.writeValue(chunk, for: characteristic, type: .withResponse)
        return
      }
      pending.completion.acknowledgeWrite()
      self.pending = pending
      self.finishPendingIfComplete()
    }
  }

  nonisolated func peripheral(
    _ peripheral: CBPeripheral,
    didUpdateValueFor characteristic: CBCharacteristic,
    error: Error?
  ) {
    Task { @MainActor in
      guard self.peripheral === peripheral, characteristic === self.responseCharacteristic,
        self.pending != nil
      else { return }
      if let error {
        return self.finishPending(.failure(BLECommandError.bluetooth(error.localizedDescription)))
      }
      guard let value = characteristic.value else { return }
      do {
        if let frame = try self.assembler.append(value) {
          let response = frame.normalized(removingEcho: self.pending?.command)
          self.pending?.completion.receive(response: response)
          self.finishPendingIfComplete()
        }
      } catch {
        self.finishPending(.failure(error))
      }
    }
  }
}
