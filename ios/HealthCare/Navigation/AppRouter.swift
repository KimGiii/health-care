import SwiftUI

/// NavigationPath-based router for push navigation within each tab.
@MainActor
final class AppRouter: ObservableObject {
    @Published var path = NavigationPath()

    func push<V: Hashable>(_ value: V) {
        path.append(value)
    }

    func pop() {
        guard !path.isEmpty else { return }
        path.removeLast()
    }

    func popToRoot() {
        path.removeLast(path.count)
    }
}
