public enum DTCActionPolicy {
  public static func canClear(hasCurrentRead: Bool, count: Int) -> Bool {
    hasCurrentRead && count > 0
  }
}
