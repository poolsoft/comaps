import Foundation

extension Date {
  func formatTimeAgo() -> String {
    return Self.relativeFormatter.localizedString(for: self, relativeTo: Date())
  }
  
  private static let relativeFormatter: RelativeDateTimeFormatter = {
    let formatter = RelativeDateTimeFormatter()
    formatter.unitsStyle = .full
    return formatter
  }()
}