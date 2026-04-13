import SwiftUI

struct MainTabView: View {
    @State private var selectedTab: Tab = .home

    enum Tab: Int, CaseIterable {
        case home, diary, explore, myPage

        var title: String {
            switch self {
            case .home:    return "대시보드"
            case .diary:   return "다이어리"
            case .explore: return "탐색"
            case .myPage:  return "프로필"
            }
        }

        var systemImage: String {
            switch self {
            case .home:    return "square.grid.2x2.fill"
            case .diary:   return "calendar"
            case .explore: return "safari"
            case .myPage:  return "person.fill"
            }
        }
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView()
                .tabItem { Label(Tab.home.title,    systemImage: Tab.home.systemImage) }
                .tag(Tab.home)

            DiaryView()
                .tabItem { Label(Tab.diary.title,   systemImage: Tab.diary.systemImage) }
                .tag(Tab.diary)

            ExploreView()
                .tabItem { Label(Tab.explore.title, systemImage: Tab.explore.systemImage) }
                .tag(Tab.explore)

            MyPageView()
                .tabItem { Label(Tab.myPage.title,  systemImage: Tab.myPage.systemImage) }
                .tag(Tab.myPage)
        }
        .tint(Color.brandPrimary)
    }
}
