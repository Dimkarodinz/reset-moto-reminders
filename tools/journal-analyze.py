#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.10"
# dependencies = ["pyyaml"]
# ///
"""Offline analyzer for pulled read-only ECU capture journals.

Compares one app-private JSONL journal (pulled with adb as described in
android/README.md) against the adapter, ECU and DTC maps, then reports per
session:

  1. connection lifecycle and whether Adapter ready was reached
  2. adapter identity versus the adapter map
  3. an outbound-command audit against the derived read-only allowlist
  4. engine transport configuration acceptance
  5. identifier reads versus the observed map values
  6. DTC count/detail decoding, session used and dictionary lookup
  7. discrepancies as findings, each mapped to the required follow-up
     (promote observed -> validated, or write a failing transcript test)

The script only reads local files. It never talks to the phone, the adapter
or the motorcycle, and it never modifies a map: per AGENTS.md, maps are
updated by a human from a retained capture, and every discrepancy reported
here must become a minimized failing transcript test before code changes.

Usage:
  ./tools/journal-analyze.py ecu-capture.jsonl
  python3 tools/journal-analyze.py ecu-capture.jsonl   # needs PyYAML or yq

Exit codes: 0 = no critical findings, 1 = usage or input error,
2 = at least one critical finding.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
ADAPTER_MAP = REPO_ROOT / "adapter-maps" / "vlinker-mc-android.adaptermap.yaml"
ECU_MAP = REPO_ROOT / "ecu-maps" / "tiger-900-gt-pro-2021.ecumap.yaml"
DTC_MAP = REPO_ROOT / "dtc-maps" / "triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml"

NEGATIVE_RESPONSE_CODES = {
    "10": "generalReject",
    "11": "serviceNotSupported",
    "12": "subFunctionNotSupported",
    "13": "incorrectMessageLengthOrInvalidFormat",
    "22": "conditionsNotCorrect",
    "31": "requestOutOfRange",
    "33": "securityAccessDenied",
    "78": "requestCorrectlyReceivedResponsePending",
    "7E": "subFunctionNotSupportedInActiveSession",
    "7F": "serviceNotSupportedInActiveSession",
}

ELM_ERROR_MARKERS = (
    "NO DATA",
    "CAN ERROR",
    "BUS ERROR",
    "BUS BUSY",
    "UNABLE TO CONNECT",
    "STOPPED",
    "BUFFER FULL",
    "DATA ERROR",
    "FB ERROR",
)

VIN_PATTERN = re.compile(r"\b[A-HJ-NPR-Z0-9]{17}\b")
LONG_NUMBER_PATTERN = re.compile(r"\b[0-9]{10,}\b")


def load_yaml(path: Path) -> dict:
    """Parse a map file with PyYAML, falling back to yq -o=json."""
    text = path.read_text(encoding="utf-8")
    try:
        import yaml  # type: ignore

        return yaml.safe_load(text)
    except ModuleNotFoundError:
        pass
    try:
        completed = subprocess.run(
            ["yq", "-o=json", "."],
            input=text,
            capture_output=True,
            text=True,
            check=True,
        )
        return json.loads(completed.stdout)
    except (FileNotFoundError, subprocess.CalledProcessError) as failure:
        raise SystemExit(
            f"Cannot parse {path.name}: PyYAML is not installed and yq failed "
            f"({failure}). Run the script as ./tools/journal-analyze.py (uv "
            "installs PyYAML automatically) or install PyYAML/yq."
        )


def hex_only(value: str) -> str:
    return "".join(c for c in value.upper() if c in "0123456789ABCDEF")


def normalize_command(value: str) -> str:
    return value.replace(" ", "").upper()


@dataclass
class Finding:
    severity: str  # CRITICAL | WARN | INFO
    area: str
    message: str
    action: str


@dataclass
class Exchange:
    sequence: int
    command: str
    response_text: str | None = None
    response_hex: str | None = None


@dataclass
class SessionData:
    session_id: str
    events: list[dict] = field(default_factory=list)


@dataclass
class Profile:
    """Expectations derived from the three maps, mirroring EcuProfileLoader."""

    elm_identity: str
    stn_identity: str
    adapter_commands: list[str]
    config_commands: list[str]
    identifier_reads: list[dict]
    sensitive_requests: list[str]
    extended_session_request: str
    dtc_count_request: str
    dtc_detail_request: str
    dtc_count_pattern_prefix: str
    dtc_entries: dict
    dtc_reference_entries: dict
    dtc_generic_messages: dict

    @property
    def allowlist(self) -> set[str]:
        allowed = set(self.adapter_commands)
        allowed.update(self.config_commands)
        allowed.update(read["elm_request"] for read in self.identifier_reads)
        allowed.update(
            (
                self.extended_session_request,
                self.dtc_count_request,
                self.dtc_detail_request,
            )
        )
        return {normalize_command(command) for command in allowed}


def build_profile(adapter_map: dict, ecu_map: dict, dtc_map: dict) -> Profile:
    adapter = adapter_map["adapter"]
    identity = adapter["identity"]["adapter_protocol"]
    operations = adapter["operations"]
    adapter_commands = [operations["identify_adapter"]["command"]["text"]]
    adapter_commands += [
        step["command_text"] for step in operations["initialize_adapter"]["sequence"]
    ]

    engine = ecu_map["motorcycle"]["modules"]["engine_ecu"]
    commands = engine["commands"]
    config_commands = engine["transport"]["observed_elm_adapter_configuration"]

    identifier_reads = []
    sensitive_requests = []
    for read in commands["read_module_identifiers"]["observed_sequence"]:
        if read.get("sensitive_response"):
            sensitive_requests.append(read["elm_request"])
        else:
            identifier_reads.append(
                {
                    "name": read["name"],
                    "elm_request": read["elm_request"],
                    "did": read["uds_request"][2:],
                    "observed_response": read.get("observed_response"),
                }
            )

    extended = next(
        step
        for step in commands["connect"]["observed_sequence"]
        if step["name"] == "enter_extended_diagnostic_session"
    )
    dtc_requests = commands["read_diagnostic_trouble_codes"]["request_sequence"]
    count_pattern = dtc_requests[0]["observed_response_pattern"]
    count_prefix = hex_only(count_pattern.split("<")[0])

    dictionary = dtc_map["dictionary"]
    return Profile(
        elm_identity=identity["elm_compatibility_version"]["value"],
        stn_identity=identity["stn_chip_identity"]["value"],
        adapter_commands=adapter_commands,
        config_commands=config_commands,
        identifier_reads=identifier_reads,
        sensitive_requests=sensitive_requests,
        extended_session_request=extended["elm_request"],
        dtc_count_request=dtc_requests[0]["elm_request"],
        dtc_detail_request=dtc_requests[1]["elm_request"],
        dtc_count_pattern_prefix=count_prefix,
        dtc_entries=dtc_map.get("entries") or {},
        dtc_reference_entries=dtc_map.get("reference_entries") or {},
        dtc_generic_messages=dictionary["lookup"]["generic_subsystem_messages"],
    )


def parse_journal(path: Path) -> tuple[list[SessionData], list[Finding]]:
    findings: list[Finding] = []
    sessions: dict[str, SessionData] = {}
    for line_number, line in enumerate(
        path.read_text(encoding="utf-8").splitlines(), start=1
    ):
        if not line.strip():
            continue
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            findings.append(
                Finding(
                    "WARN",
                    "journal-structure",
                    f"Line {line_number} is not valid JSON and was skipped.",
                    "Inspect the raw journal; a truncated final line can mean "
                    "the app stopped while writing.",
                )
            )
            continue
        session_id = event.get("sessionId", "unknown")
        sessions.setdefault(session_id, SessionData(session_id)).events.append(event)
    ordered = list(sessions.values())
    for session in ordered:
        session.events.sort(key=lambda event: int(event.get("sequence", "0")))
    return ordered, findings


def pair_exchanges(events: list[dict]) -> list[Exchange]:
    exchanges: list[Exchange] = []
    pending: Exchange | None = None
    for event in events:
        if event.get("layer") != "elm":
            continue
        if event.get("name") == "outbound":
            pending = Exchange(
                sequence=int(event.get("sequence", "0")),
                command=normalize_command(event.get("text", "")),
            )
            exchanges.append(pending)
        elif event.get("name") == "inbound" and pending is not None:
            pending.response_text = event.get("text", "")
            pending.response_hex = event.get("rawHex", "")
            pending = None
    return exchanges


def dtc_display_code(code_hex: str) -> str:
    first = int(code_hex[0:2], 16)
    letter = "PCBU"[(first >> 6) & 0x3]
    return f"{letter}{(first >> 4) & 0x3}{first & 0xF:X}{code_hex[2:4]}-{code_hex[4:6]}"


def lookup_dtc(profile: Profile, display_code: str) -> str:
    entry = profile.dtc_entries.get(display_code)
    if entry:
        return f"{entry['message']} [{entry['message_status']}]"
    base_code = display_code.split("-")[0]
    for candidate, entry in profile.dtc_entries.items():
        if entry.get("base_code") == base_code:
            return f"{entry['message']} [{entry['message_status']}, base-code match]"
    reference = profile.dtc_reference_entries.get(base_code)
    if reference:
        return f"{reference} [reference_only_unvalidated]"
    generic = profile.dtc_generic_messages.get(display_code[0])
    if generic:
        return generic.replace("{code}", display_code)
    return "Unrecognized diagnostic trouble code format."


def negative_response(payload_hex: str, service: str) -> str | None:
    marker = payload_hex.find("7F" + service)
    if marker < 0 or len(payload_hex) < marker + 6:
        return None
    code = payload_hex[marker + 4 : marker + 6]
    meaning = NEGATIVE_RESPONSE_CODES.get(code, "unknown negative response code")
    return f"7F{service}{code} ({meaning})"


def elm_error(response_text: str | None) -> str | None:
    if response_text is None:
        return "no inbound response recorded"
    upper = response_text.upper()
    for marker in ELM_ERROR_MARKERS:
        if marker in upper:
            return marker
    if upper.strip() == "?":
        return "? (adapter did not understand the command)"
    return None


class SessionAnalyzer:
    def __init__(self, session: SessionData, profile: Profile):
        self.session = session
        self.profile = profile
        self.events = session.events
        self.exchanges = pair_exchanges(session.events)
        self.findings: list[Finding] = []
        self.lines: list[str] = []
        self.dtc_count: int | None = None
        self.extended_session_used = False

    def note(self, severity: str, area: str, message: str, action: str) -> None:
        self.findings.append(Finding(severity, area, message, action))

    def print_line(self, text: str = "") -> None:
        self.lines.append(text)

    def response_for(self, command: str) -> Exchange | None:
        normalized = normalize_command(command)
        for exchange in self.exchanges:
            if exchange.command == normalized:
                return exchange
        return None

    def analyze(self) -> list[str]:
        wall_times = [event.get("wallTime", "") for event in self.events]
        self.print_line(f"Session {self.session.session_id}")
        self.print_line(
            f"  {len(self.events)} events, {len(self.exchanges)} ELM exchanges, "
            f"{wall_times[0]} .. {wall_times[-1]}"
        )
        self.check_lifecycle()
        self.check_adapter_identity()
        self.audit_commands()
        self.check_configuration()
        self.check_identifier_reads()
        self.check_dtc_read()
        self.check_capture_outcome()
        self.check_redaction()
        self.summarize_milestone_questions()
        return self.lines

    def check_lifecycle(self) -> None:
        states = [
            event.get("text", "")
            for event in self.events
            if event.get("layer") == "operation"
            and event.get("name") == "connection_state"
        ]
        self.print_line()
        self.print_line("Connection lifecycle:")
        self.print_line(f"  {' -> '.join(states) if states else '(no state events)'}")
        if not states:
            self.note(
                "CRITICAL",
                "lifecycle",
                "The journal contains no connection_state events; this is an "
                "app-launch journal, not a connection capture.",
                "Pull the newest journal that contains connection_state and elm "
                "events (see android/README.md).",
            )
            return
        if "AdapterReady" not in states:
            self.note(
                "CRITICAL",
                "lifecycle",
                "AdapterReady was never reached, so adapter initialization is "
                "not validated by this journal.",
                "Diagnose the failure states below before re-testing; do not "
                "mark the MC-Android transport validated.",
            )
        for event in self.events:
            if event.get("name") == "connection_failed":
                self.note(
                    "WARN",
                    "lifecycle",
                    f"Bluetooth connection failure recorded: "
                    f"{event.get('outcome', 'unknown')}.",
                    "Convert the failing condition into a transcript test if the "
                    "app handled it incorrectly.",
                )

    def check_adapter_identity(self) -> None:
        self.print_line()
        self.print_line("Adapter identity:")
        for command, expected, label in (
            ("ATWS", self.profile.elm_identity, "ELM"),
            ("STI", self.profile.stn_identity, "STN"),
        ):
            exchange = self.response_for(command)
            if exchange is None or exchange.response_text is None:
                self.print_line(f"  {label}: {command} was not answered")
                self.note(
                    "WARN",
                    "adapter-identity",
                    f"No completed {command} exchange found.",
                    "Adapter identity cannot be promoted to validated from this "
                    "journal.",
                )
            elif expected in exchange.response_text:
                self.print_line(f"  {label}: matches map value '{expected}'")
            else:
                shown = exchange.response_text.replace("\n", " | ")
                self.print_line(f"  {label}: got '{shown}', map says '{expected}'")
                self.note(
                    "CRITICAL",
                    "adapter-identity",
                    f"{label} identity differs from the adapter map "
                    f"(got '{shown}', expected '{expected}').",
                    "Stop physical testing until the identity difference is "
                    "understood; a different adapter or firmware is not covered "
                    "by the captured profile.",
                )

    def audit_commands(self) -> None:
        self.print_line()
        self.print_line("Outbound command audit:")
        allowlist = self.profile.allowlist
        violations = 0
        for exchange in self.exchanges:
            command_hex = hex_only(exchange.command)
            if exchange.command in allowlist:
                continue
            violations += 1
            severity = "CRITICAL"
            detail = "outside the read-only allowlist"
            if "2701" in command_hex or "2702" in command_hex:
                detail = "a SecurityAccess request, which the capture must never send"
            elif "14FFFFFF" in command_hex:
                detail = "a DTC-clear request, which the capture must never send"
            elif any(
                command_hex == hex_only(sensitive)
                for sensitive in self.profile.sensitive_requests
            ):
                detail = "a sensitive identifier read (VIN/serial), which is excluded"
            self.print_line(f"  UNEXPECTED: {exchange.command} ({detail})")
            self.note(
                severity,
                "command-allowlist",
                f"Outbound command {exchange.command} is {detail}.",
                "Write a failing transcript test reproducing this command before "
                "any other change; the capture allowlist is violated.",
            )
        if violations == 0:
            self.print_line(
                f"  all {len(self.exchanges)} outbound commands are within the "
                "derived read-only allowlist"
            )
        for event in self.events:
            if event.get("rawHex") == "[REDACTED_SECURITY_ACCESS]":
                self.note(
                    "CRITICAL",
                    "command-allowlist",
                    "The journal contains redacted SecurityAccess traffic.",
                    "SecurityAccess must never occur in the read-only capture; "
                    "reproduce in a failing transcript test.",
                )

    def check_configuration(self) -> None:
        self.print_line()
        self.print_line("Engine transport configuration:")
        seen_any = False
        for command in self.profile.config_commands:
            exchange = self.response_for(command)
            if exchange is None:
                self.print_line(f"  {command}: not sent")
                continue
            seen_any = True
            error = elm_error(exchange.response_text)
            response = exchange.response_text or ""
            if command == "ATWS":
                accepted = self.profile.elm_identity.split(" ")[0] in response
            else:
                accepted = any(line.strip() == "OK" for line in response.splitlines())
            if accepted:
                self.print_line(f"  {command}: accepted")
            else:
                shown = response.replace("\n", " | ")
                self.print_line(f"  {command}: NOT accepted (got '{shown}')")
                self.note(
                    "WARN",
                    "engine-transport",
                    f"Adapter did not accept {command} (got '{shown}'"
                    + (f", ELM error {error}" if error else "")
                    + ").",
                    "The capture should have stopped as Blocked here; verify the "
                    "journal shows that, otherwise write a failing test.",
                )
        if not seen_any:
            self.note(
                "INFO",
                "engine-transport",
                "No engine transport configuration commands were sent; the "
                "capture was likely never started in this session.",
                "Run the single-attempt capture after Adapter ready.",
            )

    def check_identifier_reads(self) -> None:
        self.print_line()
        self.print_line("Identifier reads (non-sensitive allowlist):")
        for read in self.profile.identifier_reads:
            exchange = self.response_for(read["elm_request"])
            label = f"  {read['name']} ({read['elm_request']})"
            if exchange is None:
                self.print_line(f"{label}: not sent")
                continue
            response_hex = hex_only(exchange.response_text or "")
            error = elm_error(exchange.response_text)
            negative = negative_response(response_hex, "22")
            observed = read.get("observed_response")
            if observed and hex_only(observed) in response_hex:
                self.print_line(f"{label}: matches the observed map value")
            elif ("62" + read["did"]) in response_hex:
                self.print_line(
                    f"{label}: positive response with a NEW value "
                    f"(payload {response_hex})"
                )
                self.note(
                    "INFO",
                    "identifier-reads",
                    f"{read['name']} returned a value that differs from the "
                    f"map's single observation (journal payload {response_hex}).",
                    "Record the new value in the ECU map from this retained "
                    "capture and keep both observations labeled.",
                )
            elif negative:
                self.print_line(f"{label}: negative response {negative}")
                self.note(
                    "INFO",
                    "identifier-reads",
                    f"{read['name']} was rejected with {negative}.",
                    "Record the rejection in the map; it constrains the minimum "
                    "session for identifier reads.",
                )
            elif error:
                self.print_line(f"{label}: ELM error '{error}'")
                self.note(
                    "WARN",
                    "identifier-reads",
                    f"{read['name']} produced ELM error '{error}'.",
                    "If the engine route did not respond at all, the routing "
                    "settings need re-verification against a new capture.",
                )
            else:
                shown = (exchange.response_text or "").replace("\n", " | ")
                self.print_line(f"{label}: unrecognized response '{shown}'")
                self.note(
                    "WARN",
                    "identifier-reads",
                    f"{read['name']} response was not understood: '{shown}'. It "
                    "may span ISO-TP frames; inspect it manually.",
                    "Decode manually (CAN id | ISO-TP PCI | payload), then extend "
                    "the analyzer or the map from the retained capture.",
                )

    def check_dtc_read(self) -> None:
        self.print_line()
        self.print_line("DTC read:")
        count_request = normalize_command(self.profile.dtc_count_request)
        count_exchanges = [
            exchange
            for exchange in self.exchanges
            if exchange.command == count_request
        ]
        if not count_exchanges:
            self.print_line("  DTC count was never requested")
            return
        session_exchange = self.response_for(self.profile.extended_session_request)
        if session_exchange is not None:
            self.extended_session_used = True
            response_hex = hex_only(session_exchange.response_text or "")
            if "5003" in response_hex:
                self.print_line(
                    "  extended session (1003) was entered and accepted; the "
                    "default session was insufficient for the DTC read"
                )
            else:
                negative = negative_response(response_hex, "10")
                self.print_line(
                    f"  extended session was requested but NOT accepted "
                    f"({negative or 'no 5003 in response'})"
                )
                self.note(
                    "WARN",
                    "dtc-read",
                    "The observed extended-session transition was rejected.",
                    "The capture must have ended Blocked; record the rejection — "
                    "it changes the session-prerequisite answer.",
                )
        for index, exchange in enumerate(count_exchanges):
            if index == 0:
                which = "default session"
            elif self.extended_session_used and index == len(count_exchanges) - 1:
                which = "extended session"
            else:
                which = "retry"
            response_hex = hex_only(exchange.response_text or "")
            marker = response_hex.find("5901")
            if marker >= 0 and len(response_hex) >= marker + 12:
                mask = response_hex[marker + 4 : marker + 6]
                format_id = response_hex[marker + 6 : marker + 8]
                self.dtc_count = int(response_hex[marker + 8 : marker + 12], 16)
                self.print_line(
                    f"  count ({which}): {self.dtc_count} confirmed DTC(s), "
                    f"availability mask 0x{mask}, format 0x{format_id}"
                )
                prefix = self.profile.dtc_count_pattern_prefix
                if not response_hex[marker:].startswith(prefix):
                    self.note(
                        "INFO",
                        "dtc-read",
                        f"The count response header (mask 0x{mask}, format "
                        f"0x{format_id}) differs from the map pattern "
                        f"{prefix}<count>.",
                        "Update the observed pattern in the ECU map from this "
                        "retained capture.",
                    )
            else:
                negative = negative_response(response_hex, "19")
                error = elm_error(exchange.response_text)
                self.print_line(
                    f"  count ({which}): no 5901 response "
                    f"({negative or error or 'unrecognized payload'})"
                )
        detail_exchange = self.response_for(self.profile.dtc_detail_request)
        if detail_exchange is not None:
            self.decode_dtc_detail(detail_exchange)
        elif self.dtc_count and self.dtc_count > 0:
            self.note(
                "WARN",
                "dtc-read",
                f"The count reported {self.dtc_count} DTC(s) but no detail read "
                "was recorded.",
                "The capture should read details whenever the count is nonzero; "
                "check for an early failure event.",
            )

    def decode_dtc_detail(self, exchange: Exchange) -> None:
        response_hex = hex_only(exchange.response_text or "")
        marker = response_hex.find("5902")
        if marker < 0:
            negative = negative_response(response_hex, "19")
            self.print_line(
                f"  detail: no 5902 response ({negative or 'unrecognized payload'})"
            )
            return
        mask = response_hex[marker + 4 : marker + 6]
        records = response_hex[marker + 6 :]
        self.print_line(f"  detail: availability mask 0x{mask}")
        for start in range(0, len(records) - 7, 8):
            code_hex = records[start : start + 6]
            status = records[start + 6 : start + 8]
            display = dtc_display_code(code_hex)
            message = lookup_dtc(self.profile, display)
            self.print_line(
                f"    {display} (raw 0x{code_hex}, status 0x{status}): {message}"
            )

    def check_capture_outcome(self) -> None:
        self.print_line()
        self.print_line("Capture outcome:")
        started = finished = failed = None
        for event in self.events:
            name = event.get("name")
            if name == "read_only_engine_capture_started":
                started = event
            elif name == "read_only_engine_capture_finished":
                finished = event
            elif name == "read_only_engine_capture_failed":
                failed = event
        if started is None:
            self.print_line("  the read-only capture was not started")
            return
        if finished is not None:
            text = finished.get("text", "")
            self.print_line(f"  finished: {text}")
            match = re.search(r"dtc_count=(\d+)", text)
            if (
                match
                and self.dtc_count is not None
                and int(match.group(1)) != self.dtc_count
            ):
                self.note(
                    "CRITICAL",
                    "capture-outcome",
                    f"The app reported dtc_count={match.group(1)} but the ELM "
                    f"traffic decodes to {self.dtc_count}.",
                    "The DTC-count parser disagrees with the analyzer; write a "
                    "failing transcript test with this journal's responses.",
                )
        elif failed is not None:
            outcome = failed.get("outcome", "unknown")
            self.print_line(f"  FAILED: {outcome}")
            self.note(
                "WARN",
                "capture-outcome",
                f"The capture failed with {outcome}.",
                "Reproduce the failure as a transcript test; do not re-run the "
                "capture until it is understood.",
            )
        else:
            self.print_line("  started but no terminal event was recorded")
            self.note(
                "WARN",
                "capture-outcome",
                "The capture started but neither finished nor failed was logged.",
                "The app may have been killed mid-capture; check the phone before "
                "re-testing.",
            )

    def check_redaction(self) -> None:
        for event in self.events:
            for field_name in ("text", "rawHex"):
                value = event.get(field_name)
                if not value or value.startswith("[REDACTED"):
                    continue
                if VIN_PATTERN.search(value) and event.get("layer") == "elm":
                    self.note(
                        "WARN",
                        "redaction",
                        f"Event {event.get('sequence')} {field_name} contains a "
                        "17-character VIN-like token.",
                        "Review before sharing this journal anywhere; the "
                        "redactor may have a gap.",
                    )
                elif LONG_NUMBER_PATTERN.search(value) and field_name == "text":
                    self.note(
                        "INFO",
                        "redaction",
                        f"Event {event.get('sequence')} text contains a long "
                        "numeric token; confirm it is not a serial number.",
                        "Review before sharing this journal.",
                    )

    def summarize_milestone_questions(self) -> None:
        self.print_line()
        self.print_line("Milestone questions (AGENTS.md):")
        ready = any(
            event.get("text") == "AdapterReady"
            for event in self.events
            if event.get("name") == "connection_state"
        )
        engine_responded = any(
            "62" in hex_only(exchange.response_text or "")
            or "5901" in hex_only(exchange.response_text or "")
            for exchange in self.exchanges
            if exchange.command.startswith("03") or exchange.command.startswith("02")
        )
        answered = [
            ("expected adapter identity reached", ready),
            ("known engine route responded", engine_responded),
            (
                "DTC read required the extended session",
                self.extended_session_used,
            ),
            ("confirmed DTC count decoded", self.dtc_count is not None),
        ]
        for question, verdict in answered:
            self.print_line(f"  {question}: {'yes' if verdict else 'no'}")


def print_findings(findings: list[Finding]) -> None:
    if not findings:
        print()
        print("Findings: none — every observation matches the maps.")
        return
    order = {"CRITICAL": 0, "WARN": 1, "INFO": 2}
    print()
    print("Findings:")
    for finding in sorted(findings, key=lambda item: order[item.severity]):
        print(f"  [{finding.severity}] {finding.area}: {finding.message}")
        print(f"      next: {finding.action}")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Compare a pulled capture journal against the project maps."
    )
    parser.add_argument("journal", type=Path, help="JSONL journal pulled via adb")
    parser.add_argument("--adapter-map", type=Path, default=ADAPTER_MAP)
    parser.add_argument("--ecu-map", type=Path, default=ECU_MAP)
    parser.add_argument("--dtc-map", type=Path, default=DTC_MAP)
    arguments = parser.parse_args()

    for path in (
        arguments.journal,
        arguments.adapter_map,
        arguments.ecu_map,
        arguments.dtc_map,
    ):
        if not path.is_file():
            print(f"Not a file: {path}", file=sys.stderr)
            return 1

    profile = build_profile(
        load_yaml(arguments.adapter_map),
        load_yaml(arguments.ecu_map),
        load_yaml(arguments.dtc_map),
    )
    sessions, findings = parse_journal(arguments.journal)
    if not sessions:
        print("The journal contains no events.", file=sys.stderr)
        return 1

    for session in sessions:
        analyzer = SessionAnalyzer(session, profile)
        for line in analyzer.analyze():
            print(line)
        findings.extend(analyzer.findings)
        print()

    print_findings(findings)
    return 2 if any(finding.severity == "CRITICAL" for finding in findings) else 0


if __name__ == "__main__":
    sys.exit(main())
