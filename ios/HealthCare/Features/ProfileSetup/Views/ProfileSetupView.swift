import SwiftUI

struct ProfileSetupView: View {
    @StateObject private var viewModel = ProfileSetupViewModel()
    @EnvironmentObject private var authState: AuthState
    @EnvironmentObject private var container: AppContainer
    @State private var step = 1

    var body: some View {
        ZStack {
            Color(hex: "#F5F4EC").ignoresSafeArea()

            VStack(spacing: 0) {
                // Progress
                ProgressBar(current: step, total: 2)
                    .padding(.horizontal, 28)
                    .padding(.top, 24)

                // Step Content
                if step == 1 {
                    StepOneView(viewModel: viewModel)
                        .transition(.asymmetric(
                            insertion: .move(edge: .trailing),
                            removal: .move(edge: .leading)
                        ))
                } else {
                    StepTwoView(viewModel: viewModel)
                        .transition(.asymmetric(
                            insertion: .move(edge: .trailing),
                            removal: .move(edge: .leading)
                        ))
                }

                Spacer()

                // Error
                if let error = viewModel.errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(Color.brandDanger)
                        .padding(.horizontal, 28)
                        .padding(.bottom, 8)
                }

                // CTA
                VStack(spacing: 0) {
                    if step == 1 {
                        Button {
                            withAnimation { step = 2 }
                        } label: {
                            Text("다음")
                                .font(.system(size: 17, weight: .semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 16)
                                .background(viewModel.canProceedStep1 ? Color.brandPrimary : Color.brandPrimary.opacity(0.3))
                                .foregroundStyle(.white)
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                        }
                        .disabled(!viewModel.canProceedStep1)
                    } else {
                        Button {
                            Task { await viewModel.submit(apiClient: container.apiClient, authState: authState) }
                        } label: {
                            Group {
                                if viewModel.isLoading {
                                    ProgressView().tint(.white)
                                } else {
                                    Text("시작하기")
                                        .font(.system(size: 17, weight: .semibold))
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .background(viewModel.canSubmit ? Color.brandPrimary : Color.brandPrimary.opacity(0.3))
                            .foregroundStyle(.white)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            .shadow(color: Color.brandPrimary.opacity(0.3), radius: 10, x: 0, y: 5)
                        }
                        .disabled(!viewModel.canSubmit || viewModel.isLoading)
                    }
                }
                .padding(.horizontal, 28)
                .padding(.bottom, 48)
            }
        }
        .navigationBarBackButtonHidden(true)
        .toolbar {
            if step == 2 {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        withAnimation { step = 1 }
                    } label: {
                        Image(systemName: "chevron.left")
                            .foregroundStyle(Color.brandPrimary)
                    }
                }
            }
        }
    }
}

// MARK: - Progress Bar

private struct ProgressBar: View {
    let current: Int
    let total: Int

    var body: some View {
        HStack(spacing: 6) {
            ForEach(1...total, id: \.self) { index in
                Capsule()
                    .fill(index <= current ? Color.brandPrimary : Color.brandPrimary.opacity(0.15))
                    .frame(height: 4)
            }
        }
    }
}

// MARK: - Step 1: 신체 정보

private struct StepOneView: View {
    @ObservedObject var viewModel: ProfileSetupViewModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 32) {
                // Title
                VStack(alignment: .leading, spacing: 6) {
                    Text("신체 정보를 알려주세요")
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.brandPrimary)
                    Text("맞춤형 목표 설정에 사용됩니다")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.textSecondary)
                }

                // Sex
                VStack(alignment: .leading, spacing: 12) {
                    Text("성별")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.textSecondary)

                    HStack(spacing: 10) {
                        ForEach(SexOption.allCases) { option in
                            SexCard(
                                option: option,
                                isSelected: viewModel.sex == option.rawValue
                            ) {
                                viewModel.sex = option.rawValue
                            }
                        }
                    }
                }

                // Height & Weight
                VStack(alignment: .leading, spacing: 12) {
                    Text("키 / 몸무게")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.textSecondary)

                    HStack(spacing: 12) {
                        MeasurementField(
                            placeholder: "키",
                            unit: "cm",
                            text: $viewModel.heightText
                        )
                        MeasurementField(
                            placeholder: "몸무게",
                            unit: "kg",
                            text: $viewModel.weightText
                        )
                    }
                }
            }
            .padding(.horizontal, 28)
            .padding(.top, 36)
        }
    }
}

// MARK: - Step 2: 활동 수준

private struct StepTwoView: View {
    @ObservedObject var viewModel: ProfileSetupViewModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 32) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("평소 활동 수준은?")
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.brandPrimary)
                    Text("칼로리 목표 계산에 반영됩니다")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.textSecondary)
                }

                VStack(spacing: 10) {
                    ForEach(ActivityOption.allCases) { option in
                        ActivityCard(
                            option: option,
                            isSelected: viewModel.activityLevel == option.rawValue
                        ) {
                            viewModel.activityLevel = option.rawValue
                        }
                    }
                }
            }
            .padding(.horizontal, 28)
            .padding(.top, 36)
        }
    }
}

// MARK: - Sex Card

private enum SexOption: String, CaseIterable, Identifiable {
    case male   = "MALE"
    case female = "FEMALE"
    case other  = "OTHER"

    var id: String { rawValue }
    var label: String {
        switch self {
        case .male:   return "남성"
        case .female: return "여성"
        case .other:  return "기타"
        }
    }
    var icon: String {
        switch self {
        case .male:   return "person.fill"
        case .female: return "person.fill"
        case .other:  return "person.2.fill"
        }
    }
}

private struct SexCard: View {
    let option: SexOption
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: option.icon)
                    .font(.system(size: 22))
                    .foregroundStyle(isSelected ? .white : Color.brandPrimary)
                Text(option.label)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(isSelected ? .white : Color.brandPrimary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 18)
            .background(isSelected ? Color.brandPrimary : Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color.brandPrimary.opacity(isSelected ? 0 : 0.2), lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
        }
    }
}

// MARK: - Measurement Field

private struct MeasurementField: View {
    let placeholder: String
    let unit: String
    @Binding var text: String

    var body: some View {
        HStack(spacing: 4) {
            TextField(placeholder, text: $text)
                .font(.system(size: 17, weight: .medium))
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
            Text(unit)
                .font(.system(size: 13))
                .foregroundStyle(Color.textSecondary)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.brandPrimary.opacity(0.2), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
    }
}

// MARK: - Activity Card

private enum ActivityOption: String, CaseIterable, Identifiable {
    case sedentary        = "SEDENTARY"
    case lightlyActive    = "LIGHTLY_ACTIVE"
    case moderatelyActive = "MODERATELY_ACTIVE"
    case veryActive       = "VERY_ACTIVE"
    case extraActive      = "EXTRA_ACTIVE"

    var id: String { rawValue }
    var icon: String {
        switch self {
        case .sedentary:        return "figure.stand"
        case .lightlyActive:    return "figure.walk"
        case .moderatelyActive: return "figure.run"
        case .veryActive:       return "figure.strengthtraining.traditional"
        case .extraActive:      return "bolt.heart.fill"
        }
    }
    var title: String {
        switch self {
        case .sedentary:        return "비활동적"
        case .lightlyActive:    return "가볍게 활동"
        case .moderatelyActive: return "보통 활동"
        case .veryActive:       return "활발히 활동"
        case .extraActive:      return "매우 활발"
        }
    }
    var description: String {
        switch self {
        case .sedentary:        return "주로 앉아서 생활해요"
        case .lightlyActive:    return "가끔 걷거나 스트레칭해요"
        case .moderatelyActive: return "주 3~4회 운동해요"
        case .veryActive:       return "매일 강도 있게 운동해요"
        case .extraActive:      return "하루 두 번 이상 운동해요"
        }
    }
}

private struct ActivityCard: View {
    let option: ActivityOption
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: option.icon)
                    .font(.system(size: 22))
                    .foregroundStyle(isSelected ? .white : Color.brandPrimary)
                    .frame(width: 28)

                VStack(alignment: .leading, spacing: 2) {
                    Text(option.title)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(isSelected ? .white : Color.brandPrimary)
                    Text(option.description)
                        .font(.system(size: 12))
                        .foregroundStyle(isSelected ? .white.opacity(0.8) : Color.textSecondary)
                }

                Spacer()

                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(.white)
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 16)
            .background(isSelected ? Color.brandPrimary : Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color.brandPrimary.opacity(isSelected ? 0 : 0.2), lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
        }
    }
}
