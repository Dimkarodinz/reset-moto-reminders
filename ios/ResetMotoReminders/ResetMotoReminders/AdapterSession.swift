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
    case .disconnected: return "Not connected"
    case .waitingForBluetooth: return "Checking Bluetooth…"
    case .scanning: return "Looking for vLinker MC-IOS…"
    case .connecting: return "Connecting…"
    case .discovering: return "Checking adapter…"
    case .identifying: return "Identifying adapter…"
    case .ready: return "Motorcycle connected"
    case .disconnecting: return "Disconnecting…"
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
    case .notReady: return "Connect to the motorcycle first."
    case .unsupportedLayout: return "This adapter does not expose the expected MC-IOS connection."
    case .invalidIdentity: return "The adapter identification reply was not recognized."
    case .timeout: return "The motorcycle did not answer in time."
    case .ambiguousWrite:
      return
        "The connection ended after a write. Its result is unknown—inspect the motorcycle before trying again."
    case .disconnected: return "The adapter disconnected."
    case .bluetooth(let message): return message
    }
  }
}

@MainActor
final class AdapterSession: NSObject, ObservableObject, @unchecked Sendable {
  @Published private(set) var state: AdapterConnectionState = .disconnected
  @Published private(set) var adapterIdentity: String?
  @Published private(set) var dashboard: DashboardResult?
  @Published private(set) var dashboardStatus =
    "Read the dashboard to confirm the motorcycle is responding."
  @Published private var dtcReadState = DTCReadState()
  @Published private(set) var dtcStatus = "Read the motorcycle to check confirmed trouble codes."
  @Published private(set) var serviceStatus = "Set the next interval and date after connecting."
  @Published private(set) var operationRunning = false
  @Published private(set) var operationTitle: String?

  let profile: ResetMotoProfile

  private var central: CBCentralManager?
  private var peripheral: CBPeripheral?
  private var commandCharacteristic: CBCharacteristic?
  private var responseCharacteristic: CBCharacteristic?
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
    assembler = ElmResponseAssembler(promptByte: profile.adapter.promptByte)
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
      ? .ambiguousWrite(operationTitle ?? "operation interrupted")
      : .disconnected
    let reason =
      backgroundInterruptedAfterWrite
      ? failure.localizedDescription
      : "Connection closed when the app entered the background. Reconnect before continuing."
    logger.notice(
      "Closing connection in background; ambiguous=\(self.backgroundInterruptedAfterWrite)")
    failAndDisconnect(reason, pendingFailure: failure)
  }

  func readDashboard() {
    guard canStartOperation else { return }
    dashboard = nil
    dashboardStatus = "Reading dashboard…"
    runOperation("Reading motorcycle") { channel in
      let result = try await DashboardUseCase(profile: self.profile.instrument).read(using: channel)
      self.dashboard = result
      self.dashboardStatus = "Motorcycle responded."
    }
  }

  func readDTCs() {
    guard canStartOperation else { return }
    dtcReadState.beginRead()
    dtcStatus = "Reading confirmed trouble codes…"
    runOperation("Reading trouble codes") { channel in
      let result = try await DTCUseCase(
        profile: self.profile.engine,
        descriptions: self.profile.dtcDescriptions
      ).read(using: channel)
      self.dtcReadState.completeRead(result)
      self.dtcStatus =
        result.isEmpty
        ? "No confirmed trouble codes reported." : "\(result.count) confirmed trouble code(s)."
    }
  }

  func clearDTCs() {
    guard canStartOperation, dtcReadState.canClear else { return }
    dtcReadState.beginClearAttempt()
    runOperation("Clearing trouble codes") { channel in
      let result = try await DTCUseCase(
        profile: self.profile.engine,
        descriptions: self.profile.dtcDescriptions
      ).clear(using: channel)
      switch result {
      case .cleared:
        self.dtcReadState.completeRead([])
        self.dtcStatus = "Confirmed trouble-code memory cleared. This does not repair a fault."
      case .blocked:
        self.dtcStatus = "Clear was refused before it could be confirmed."
      case .needsVerification:
        self.dtcStatus = "Clear may have run, but verification did not confirm an empty memory."
      }
    }
  }

  func resetService(distance: Int, unit: DistanceUnit, date: Date) {
    runOperation("Resetting service reminder") { channel in
      let outcome = try await ServiceReminderUseCase(profile: self.profile.instrument)
        .reset(distance: distance, unit: unit, nextServiceDate: date, using: channel)
      switch outcome {
      case .committed(let odometer):
        self.serviceStatus =
          "Service reminder reset at \(odometer) km. Maintenance was not performed by the app."
      case .blocked:
        self.serviceStatus = "Reset was refused before a complete reminder update was confirmed."
      case .partiallyApplied:
        self.serviceStatus =
          "Distance may be updated, but the date was not confirmed. Inspect the dashboard before retrying."
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

  private func resetFeatureState() {
    adapterIdentity = nil
    dashboard = nil
    dashboardStatus = "Read the dashboard to confirm the motorcycle is responding."
    dtcReadState = DTCReadState()
    dtcStatus = "Read the motorcycle to check confirmed trouble codes."
    serviceStatus = "Set the next interval and date after connecting."
  }

  private func runOperation(
    _ title: String,
    action: @escaping @MainActor (SessionCommandChannel) async throws -> Void
  ) {
    guard canStartOperation else { return }
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
        let message =
          (reportedError as? LocalizedError)?.errorDescription ?? reportedError.localizedDescription
        logger.error(
          "Operation failed: \(title, privacy: .public); \(message, privacy: .public)")
        if title == "Reading motorcycle" { dashboardStatus = message }
        if title.contains("trouble") || title.contains("Clearing") { dtcStatus = message }
        if title.contains("service") { serviceStatus = message }
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
    let payload = Data((command + "\r").utf8)
    guard payload.count <= peripheral.maximumWriteValueLength(for: .withResponse) else {
      throw BLECommandError.unsupportedLayout
    }
    assembler = ElmResponseAssembler(promptByte: profile.adapter.promptByte)
    let commandTag = logTag(for: command)
    logger.debug(
      "Sending command \(commandTag, privacy: .public); write=\(intent == .write)")
    return try await withCheckedThrowingContinuation { continuation in
      pending = PendingCommand(
        command: command, intent: intent, continuation: continuation, sent: true,
        completion: CommandCompletionGate())
      if intent == .write { operationSentStateChangingWrite = true }
      peripheral.writeValue(payload, for: characteristic, type: .withResponse)
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
        let identity = try await executeRaw(
          profile.adapter.identifyCommand, intent: .read, allowBeforeReady: true)
        guard AdapterValidation.acceptsIdentity(identity) else {
          throw BLECommandError.invalidIdentity
        }
        adapterIdentity = identity
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
    commandCharacteristic = nil
    responseCharacteristic = nil
    central = nil
  }

  private func finishDisconnected() {
    peripheral = nil
    commandCharacteristic = nil
    responseCharacteristic = nil
    central = nil
    state = .disconnected
  }
}

private struct PendingCommand {
  let command: String
  let intent: CommandIntent
  let continuation: CheckedContinuation<String, Error>
  let sent: Bool
  var completion: CommandCompletionGate
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
        central.scanForPeripherals(withServices: [CBUUID(string: self.profile.adapter.serviceUUID)])
      case .unauthorized:
        self.failAndDisconnect("Bluetooth permission is required to connect to the adapter.")
      case .poweredOff:
        self.failAndDisconnect("Bluetooth is off.")
      case .unsupported:
        self.failAndDisconnect("Bluetooth Low Energy is not supported on this device.")
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
      guard self.central === central, self.state == .scanning,
        name == self.profile.adapter.advertisedName
      else { return }
      central.stopScan()
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
      self.state = .discovering
      peripheral.discoverServices([CBUUID(string: self.profile.adapter.serviceUUID)])
    }
  }

  nonisolated func centralManager(
    _ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?
  ) {
    Task { @MainActor in
      guard self.central === central, self.peripheral === peripheral else { return }
      self.failAndDisconnect(error?.localizedDescription ?? "Could not connect to the adapter.")
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
      guard error == nil,
        let service = peripheral.services?.first(where: {
          $0.uuid == CBUUID(string: self.profile.adapter.serviceUUID)
        })
      else {
        return self.failAndDisconnect(BLECommandError.unsupportedLayout.localizedDescription)
      }
      peripheral.discoverCharacteristics(
        [
          CBUUID(string: self.profile.adapter.commandCharacteristicUUID),
          CBUUID(string: self.profile.adapter.responseCharacteristicUUID),
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
      guard error == nil else {
        return self.failAndDisconnect(BLECommandError.unsupportedLayout.localizedDescription)
      }
      self.commandCharacteristic = service.characteristics?.first {
        $0.uuid == CBUUID(string: self.profile.adapter.commandCharacteristicUUID)
      }
      self.responseCharacteristic = service.characteristics?.first {
        $0.uuid == CBUUID(string: self.profile.adapter.responseCharacteristicUUID)
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
