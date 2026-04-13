import SwiftUI

struct GoalSettingView: View {
    @StateObject private var viewModel = GoalSettingViewModel()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    Text("목표를 설정해주세요")
                        .font(.title2.bold())
                }
                .padding()
            }
            .navigationTitle("목표 설정")
            .navigationBarTitleDisplayMode(.large)
        }
        .task { await viewModel.load() }
    }
}
