import SwiftUI

struct MyPageView: View {
    @StateObject private var viewModel = MyPageViewModel()
    @EnvironmentObject private var authState: AuthState

    var body: some View {
        NavigationStack {
            List {
                Section("계정") {
                    Text("프로필 수정")
                }
                Section {
                    Button("로그아웃", role: .destructive) {
                        authState.setUnauthenticated()
                    }
                }
            }
            .navigationTitle("마이")
            .navigationBarTitleDisplayMode(.large)
        }
        .task { await viewModel.load() }
    }
}
