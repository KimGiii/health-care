import SwiftUI

struct BodyMeasurementView: View {
    @StateObject private var viewModel = BodyMeasurementViewModel()

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Text("신체 변화 기록")
                    .font(.title2.bold())
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding()
        }
        .navigationTitle("신체 변화 기록")
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.load() }
    }
}
