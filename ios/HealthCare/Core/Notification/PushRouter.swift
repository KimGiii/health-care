import Foundation

// MARK: - PushRouter
//
// 푸시 알림 탭 → 앱 내부 화면 라우팅의 단일 통로.
//
// 처리하는 race condition:
//  - Cold start: 앱 종료 상태에서 푸시 탭 → launch 직후 deliver되지만 MainTabView가
//    아직 onReceive 등록 전. NotificationCenter publisher는 놓침.
//    → pendingRoute에 저장해뒀다가 MainTabView가 .onAppear에서 consume.
//  - Foreground tap: 동작 중 푸시 탭 → @Published 변경으로 onChange 트리거.
//  - Main thread: 모든 라우트 변경을 @MainActor에서 처리해 SwiftUI 상태 안전.
//
// AppDelegate에서 deliver, MainTabView에서 consume.

@MainActor
final class PushRouter: ObservableObject {
    static let shared = PushRouter()

    /// 처리 대기 중인 라우트 type (백엔드의 NotificationType 문자열, 예: "WEEKLY_SUMMARY").
    /// 소비자는 consume()으로 가져가 nil 처리한다.
    @Published private(set) var pendingRoute: String?

    private init() {}

    /// AppDelegate의 푸시 핸들러에서 호출. 메인 스레드 보장.
    func deliver(type: String) {
        print("[PushRouter] deliver type=\(type)")
        // 동일 값을 두 번 set하면 onChange가 트리거되지 않을 수 있어, 일단 nil로 리셋 후 set.
        pendingRoute = nil
        pendingRoute = type
    }

    /// MainTabView가 .onAppear / .onChange에서 호출. 한 번만 처리되도록 nil로 리셋.
    func consume() -> String? {
        let route = pendingRoute
        pendingRoute = nil
        print("[PushRouter] consume route=\(route ?? "nil")")
        return route
    }
}
