import Foundation

@MainActor
final class AdsManager: ObservableObject {
    static let shared = AdsManager()

    private var lastInterstitialShown: Date?
    private let cooldown: TimeInterval = 1800 // 30분

    private(set) lazy var interstitialCoordinator = InterstitialAdCoordinator(
        adUnitID: interstitialAdUnitID
    )

    #if DEBUG
    let bannerAdUnitID = "ca-app-pub-3940256099942544/2934735716"
    let interstitialAdUnitID = "ca-app-pub-3940256099942544/4411468910"
    #else
    let bannerAdUnitID = "ca-app-pub-6600084915621974/5715944843"
    let interstitialAdUnitID = "ca-app-pub-6600084915621974/7771262548"
    #endif

    func showInterstitialIfReady() {
        guard canShowInterstitial() else { return }
        interstitialCoordinator.showIfReady()
        lastInterstitialShown = Date()
    }

    private func canShowInterstitial() -> Bool {
        guard let last = lastInterstitialShown else { return true }
        return Date().timeIntervalSince(last) > cooldown
    }
}
