import Foundation

/// Persists the probe journal as one JSONL file per app launch in the
/// Documents directory, so it can be shared (AirDrop/Files) and pulled into
/// the repository's `logs/` for analysis.
struct JournalStore {
    private let url: URL

    init() {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let stamp = Int(Date().timeIntervalSince1970 * 1000)
        url = documents.appendingPathComponent("probe-\(stamp).jsonl")
    }

    /// Rewrites the full journal; probe journals are tiny, so atomic
    /// whole-file writes are simpler and safer than appending.
    func write(_ jsonl: String) -> URL? {
        do {
            try jsonl.write(to: url, atomically: true, encoding: .utf8)
            return url
        } catch {
            return nil
        }
    }
}
