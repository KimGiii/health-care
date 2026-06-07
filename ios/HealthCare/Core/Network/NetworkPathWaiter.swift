import Network

/// Apple Sign In 등 인터넷 통신 후 LAN 경로가 복구될 때까지 기다리는 유틸리티.
/// NWPathMonitor로 실제 경로 상태를 감시하므로 고정 딜레이보다 안정적이다.
enum NetworkPathWaiter {

    /// LAN 경로(로컬 네트워크)가 satisfied 상태가 될 때까지 대기.
    /// - Parameters:
    ///   - timeout: 최대 대기 시간 (기본 5초)
    ///   - pollInterval: 폴링 간격 (기본 200ms)
    static func waitForLocalNetwork(
        timeout: Duration = .seconds(5),
        pollInterval: Duration = .milliseconds(200)
    ) async {
        let monitor = NWPathMonitor()
        let queue = DispatchQueue(label: "com.gainsy.network-path-waiter")
        monitor.start(queue: queue)

        let deadline = ContinuousClock.now + timeout

        while ContinuousClock.now < deadline {
            let path = monitor.currentPath
            // satisfied 이고 로컬 네트워크 접근이 허용된 상태
            if path.status == .satisfied && !path.isExpensive {
                break
            }
            try? await Task.sleep(for: pollInterval)
        }

        monitor.cancel()
        // 경로가 열렸더라도 스택이 완전히 초기화되는 시간을 한 번 더 확보
        try? await Task.sleep(for: .milliseconds(300))
    }
}
