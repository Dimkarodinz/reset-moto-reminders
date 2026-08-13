import CoreBluetooth
import Foundation
import ProbeKit

/// Runs the one-shot adapter-only `ATI` probe from
/// `ios/VLINK_CONNECTION.md`: scan for the advertised `18F0` service,
/// connect, discover the requested channel, enable notifications, write
/// `ATI\r` once and reassemble the notification fragments until the `>`
/// prompt or a timeout. Every step is journaled. Each channel can be probed
/// at most once per app launch — the safety policy forbids automatically
/// walking candidate channels.
final class ProbeSession: NSObject, ObservableObject {
    enum Phase: Equatable {
        case idle
        case bluetoothUnavailable
        case scanning
        case connecting
        case discovering
        case enablingNotifications
        case awaitingResponse
        case complete
        case noResponse
        case failed(String)

        var isTerminal: Bool {
            switch self {
            case .complete, .noResponse, .failed: return true
            default: return false
            }
        }
    }

    @Published private(set) var phase: Phase = .idle
    @Published private(set) var journalLines: [String] = []
    @Published private(set) var responseText: String?
    @Published private(set) var probedChannels: Set<String> = []
    @Published private(set) var journalURL: URL?

    private let profile = ProbeProfile.vlinkerMcIos
    private let journal = ProbeJournal()
    private let store = JournalStore()

    private var central: CBCentralManager?
    /// Strong reference — CoreBluetooth drops the connection if the
    /// peripheral is deallocated.
    private var peripheral: CBPeripheral?
    private var channel: ProbeChannel?
    private var commandCharacteristic: CBCharacteristic?
    private var assembler = ElmResponseAssembler()
    private var timeoutTask: Task<Void, Never>?
    private var running = false

    var primaryChannelName: String { profile.primaryChannel.name }
    var alternateChannelName: String { profile.alternateChannel.name }

    func probePrimaryChannel() { start(profile.primaryChannel) }

    func probeAlternateChannel() { start(profile.alternateChannel) }

    private func start(_ channel: ProbeChannel) {
        guard !running, !probedChannels.contains(channel.name) else { return }
        running = true
        probedChannels.insert(channel.name)
        self.channel = channel
        assembler = ElmResponseAssembler(promptByte: profile.promptByte)
        responseText = nil
        record(layer: "probe", name: "probe_started", text: "channel=\(channel.name)")
        phase = .scanning
        // Creating the manager triggers the Bluetooth permission prompt on
        // first use; scanning starts once the state callback reports poweredOn.
        central = CBCentralManager(delegate: self, queue: .main)
    }

    private func finish(_ terminal: Phase, outcome: String) {
        timeoutTask?.cancel()
        timeoutTask = nil
        record(layer: "probe", name: "probe_finished", text: responseText, outcome: outcome)
        phase = terminal
        running = false
        if let peripheral, let central, peripheral.state != .disconnected {
            central.cancelPeripheralConnection(peripheral)
        }
        central?.stopScan()
    }

    private func record(
        layer: String,
        name: String,
        text: String? = nil,
        rawHex: String? = nil,
        outcome: String? = nil
    ) {
        let event = journal.record(layer: layer, name: name, text: text, rawHex: rawHex, outcome: outcome)
        journalLines.append("\(event.sequence) \(event.name) \(text ?? outcome ?? "")")
        journalURL = store.write(journal.jsonl())
    }

    private func fail(_ reason: String) {
        finish(.failed(reason), outcome: reason)
    }
}

extension ProbeSession: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        record(layer: "ble", name: "central_state", text: String(describing: central.state.rawValue))
        switch central.state {
        case .poweredOn:
            guard phase == .scanning else { return }
            record(layer: "ble", name: "scan_started", text: "service=\(profile.advertisedServiceUuid)")
            central.scanForPeripherals(withServices: [CBUUID(string: profile.advertisedServiceUuid)])
        case .unauthorized, .unsupported, .poweredOff:
            phase = .bluetoothUnavailable
            if running { fail("bluetooth_unavailable") }
        default:
            break
        }
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        let name = (advertisementData[CBAdvertisementDataLocalNameKey] as? String) ?? peripheral.name ?? "?"
        record(layer: "ble", name: "peripheral_discovered", text: "name=\(name) rssi=\(RSSI)")
        // The advertised service is the identifier; the name is an
        // additional check only (VLINK_CONNECTION.md).
        guard name == profile.advertisedName else { return }
        central.stopScan()
        self.peripheral = peripheral
        peripheral.delegate = self
        phase = .connecting
        record(layer: "ble", name: "connect_requested")
        central.connect(peripheral)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        record(layer: "ble", name: "connected")
        phase = .discovering
        guard let channel else { return }
        let services = Set([channel.command.serviceUuid, channel.response.serviceUuid])
        peripheral.discoverServices(services.map(CBUUID.init(string:)))
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        record(layer: "ble", name: "connect_failed", outcome: error?.localizedDescription)
        fail("connect_failed")
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        record(layer: "ble", name: "disconnected", outcome: error?.localizedDescription)
        if !phase.isTerminal { fail("unexpected_disconnect") }
    }
}

extension ProbeSession: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error {
            record(layer: "ble", name: "service_discovery_failed", outcome: error.localizedDescription)
            return fail("service_discovery_failed")
        }
        guard let channel else { return }
        let services = peripheral.services ?? []
        record(
            layer: "ble",
            name: "services_discovered",
            text: services.map { $0.uuid.uuidString }.joined(separator: ",")
        )
        guard let target = services.first(where: { $0.uuid == CBUUID(string: channel.command.serviceUuid) }) else {
            return fail("service_missing")
        }
        let characteristics = Set([channel.command.characteristicUuid, channel.response.characteristicUuid])
        peripheral.discoverCharacteristics(characteristics.map(CBUUID.init(string:)), for: target)
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didDiscoverCharacteristicsFor service: CBService,
        error: Error?
    ) {
        if let error {
            record(layer: "ble", name: "characteristic_discovery_failed", outcome: error.localizedDescription)
            return fail("characteristic_discovery_failed")
        }
        guard let channel else { return }
        let characteristics = service.characteristics ?? []
        record(
            layer: "ble",
            name: "characteristics_discovered",
            text: characteristics.map { "\($0.uuid.uuidString):\($0.properties.rawValue)" }.joined(separator: ",")
        )
        let command = characteristics.first { $0.uuid == CBUUID(string: channel.command.characteristicUuid) }
        let response = characteristics.first { $0.uuid == CBUUID(string: channel.response.characteristicUuid) }
        guard let command, let response else { return fail("characteristic_missing") }
        // Fail closed on an unexpected layout instead of writing anyway.
        guard command.properties.contains(.write) || command.properties.contains(.writeWithoutResponse) else {
            return fail("command_characteristic_not_writable")
        }
        guard response.properties.contains(.notify) || response.properties.contains(.indicate) else {
            return fail("response_characteristic_not_notifiable")
        }
        commandCharacteristic = command
        phase = .enablingNotifications
        record(layer: "ble", name: "enabling_notifications", text: response.uuid.uuidString)
        peripheral.setNotifyValue(true, for: response)
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateNotificationStateFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        if let error {
            record(layer: "ble", name: "notification_state_failed", outcome: error.localizedDescription)
            return fail("notification_enable_failed")
        }
        guard characteristic.isNotifying, let commandCharacteristic else { return }
        record(layer: "ble", name: "notifications_enabled", text: characteristic.uuid.uuidString)

        // Exactly one ATI write, withResponse when the characteristic
        // supports it (VLINK_CONNECTION.md pending-proof spec).
        let writeType: CBCharacteristicWriteType =
            commandCharacteristic.properties.contains(.write) ? .withResponse : .withoutResponse
        let payload = Data(profile.identifyCommand)
        phase = .awaitingResponse
        record(
            layer: "elm",
            name: "outbound",
            text: "ATI",
            rawHex: payload.map { String(format: "%02X", $0) }.joined(),
            outcome: writeType == .withResponse ? "write_request" : "write_without_response"
        )
        peripheral.writeValue(payload, for: commandCharacteristic, type: writeType)
        startResponseTimeout()
    }

    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        record(layer: "ble", name: "write_confirmed", outcome: error?.localizedDescription ?? "ok")
        if error != nil { fail("write_failed") }
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateValueFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        if let error {
            record(layer: "ble", name: "notification_error", outcome: error.localizedDescription)
            return
        }
        guard let fragment = characteristic.value else { return }
        let complete = assembler.append(fragment)
        record(
            layer: "elm",
            name: "inbound_fragment",
            rawHex: fragment.map { String(format: "%02X", $0) }.joined(),
            outcome: complete ? "prompt_complete" : "partial"
        )
        if complete {
            responseText = assembler.text
            finish(.complete, outcome: "prompt_complete")
        }
    }

    private func startResponseTimeout() {
        timeoutTask = Task { [weak self] in
            guard let self else { return }
            let nanoseconds = UInt64(profile.responseTimeoutSeconds * 1_000_000_000)
            try? await Task.sleep(nanoseconds: nanoseconds)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                guard self.phase == .awaitingResponse else { return }
                if self.assembler.fragmentCount > 0 {
                    self.responseText = self.assembler.text
                    self.finish(.noResponse, outcome: "incomplete_response")
                } else {
                    self.finish(.noResponse, outcome: "no_response")
                }
            }
        }
    }
}
