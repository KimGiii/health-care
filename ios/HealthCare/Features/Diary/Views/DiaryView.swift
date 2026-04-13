import SwiftUI

struct DiaryView: View {
    @StateObject private var viewModel = DiaryViewModel()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    Text("기록 다이어리")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(Color.brandPrimary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(20)
            }
            .navigationTitle("다이어리")
            .navigationBarTitleDisplayMode(.large)
        }
        .task { await viewModel.load() }
    }
}
