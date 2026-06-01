import SwiftUI

struct ProfileSetupView: View {
    @StateObject private var viewModel = ProfileSetupViewModel()
    @EnvironmentObject private var authState: AuthState
    @EnvironmentObject private var container: AppContainer
    @State private var step = 1

    var body: some View {
        ZStack {
            Color.backgroundPage.ignoresSafeArea()

            VStack(spacing: 0) {
                // Progress
                ProgressBar(current: step, total: 2)
                    .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
                    .padding(.top, Spacing.xxl) // design-lint:ignore — micro/hero spacing

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
                        .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
                        .padding(.bottom, Spacing.sm) // design-lint:ignore — micro/hero spacing
                }

                // CTA
                Group {
                    if step == 1 {
                        PrimaryButton(
                            "다음으로",
                            isEnabled: viewModel.canProceedStep1
                        ) {
                            withAnimation { step = 2 }
                        }
                    } else {
                        PrimaryButton(
                            "시작하기",
                            isEnabled: viewModel.canSubmit,
                            isLoading: viewModel.isLoading
                        ) {
                            Task { await viewModel.submit(apiClient: container.apiClient, authState: authState) }
                        }
                    }
                }
                .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
                .padding(.bottom, 48) // design-lint:ignore — micro/hero spacing
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
                        .font(.headingLarge)
                        .foregroundStyle(Color.brandPrimary)
                    Text("맞춤형 목표 설정에 사용됩니다")
                        .font(.bodyMedium)
                        .foregroundStyle(Color.textSecondary)
                }

                // Sex
                VStack(alignment: .leading, spacing: 12) {
                    Text("성별")
                        .font(.labelSmall)
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

                // Date of Birth
                VStack(alignment: .leading, spacing: 12) {
                    Text("생년월일")
                        .font(.labelSmall)
                        .foregroundStyle(Color.textSecondary)

                    DateOfBirthField(date: $viewModel.dateOfBirth)
                }

                // Height & Weight
                VStack(alignment: .leading, spacing: 12) {
                    Text("키 / 몸무게")
                        .font(.labelSmall)
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
            .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
            .padding(.top, 36) // design-lint:ignore — micro/hero spacing
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
                        .font(.headingLarge)
                        .foregroundStyle(Color.brandPrimary)
                    Text("칼로리 목표 계산에 반영됩니다")
                        .font(.bodyMedium)
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
            .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
            .padding(.top, 36) // design-lint:ignore — micro/hero spacing
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
                    .font(.system(size: 22)) // design-lint:ignore — SF Symbol size
                    .foregroundStyle(isSelected ? .white : Color.brandPrimary)
                Text(option.label)
                    .font(.labelSmall)
                    .foregroundStyle(isSelected ? .white : Color.brandPrimary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .background(isSelected ? Color.brandPrimary : Color.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.lg)
                    .stroke(Color.brandPrimary.opacity(isSelected ? 0 : 0.2), lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
        }
    }
}

// MARK: - Date of Birth Field

private struct DateOfBirthField: View {
    @Binding var date: Date
    @State private var showPicker = false

    private static let displayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = "yyyy년 M월 d일"
        return f
    }()

    var body: some View {
        Button { showPicker = true } label: {
            HStack {
                Image(systemName: "calendar")
                    .font(.bodyMedium)
                    .foregroundStyle(Color.brandPrimary)
                Text(Self.displayFormatter.string(from: date))
                    .font(.bodyLarge)
                    .fontWeight(.medium)
                    .foregroundStyle(Color.textPrimary)
                Spacer()
                Image(systemName: "chevron.down")
                    .font(.caption)
                    .foregroundStyle(Color.textSecondary)
            }
            .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
            .background(Color.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: Radius.md))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.md)
                    .stroke(Color.brandPrimary.opacity(0.2), lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
        }
        .sheet(isPresented: $showPicker) {
            CalendarPickerSheet(selectedDate: $date, isPresented: $showPicker)
                .presentationDetents([.height(500)])
                .presentationDragIndicator(.visible)
        }
    }
}

// MARK: - Calendar Picker Sheet

private struct CalendarPickerSheet: View {
    @Binding var selectedDate: Date
    @Binding var isPresented: Bool

    @State private var displayedMonth: Date
    @State private var showYearMonthPicker = false
    @State private var pickerYear: Int
    @State private var pickerMonth: Int

    private let cal = Calendar.current
    private let minDate: Date = Calendar.current.date(byAdding: .year, value: -120, to: Date()) ?? Date()
    private let maxDate: Date = Date()

    private var years: [Int] {
        let minYear = Calendar.current.component(.year, from: minDate)
        let maxYear = Calendar.current.component(.year, from: Date())
        return Array(minYear...maxYear)
    }

    init(selectedDate: Binding<Date>, isPresented: Binding<Bool>) {
        _selectedDate = selectedDate
        _isPresented = isPresented
        let cal = Calendar.current
        let comps = cal.dateComponents([.year, .month], from: selectedDate.wrappedValue)
        let firstOfMonth = cal.date(from: DateComponents(year: comps.year, month: comps.month, day: 1)) ?? selectedDate.wrappedValue
        _displayedMonth = State(initialValue: firstOfMonth)
        _pickerYear = State(initialValue: comps.year ?? cal.component(.year, from: Date()))
        _pickerMonth = State(initialValue: comps.month ?? cal.component(.month, from: Date()))
    }

    var body: some View {
        VStack(spacing: 0) {
            monthHeader
            Divider()
            weekdayRow
            calendarGrid
                .padding(.horizontal, Spacing.sm)
            Spacer(minLength: 0)
        }
        .overlay {
            if showYearMonthPicker {
                yearMonthPickerOverlay
            }
        }
    }

    // MARK: - Month Header

    private var monthHeader: some View {
        HStack {
            Button { navigateMonth(by: -1) } label: {
                Image(systemName: "chevron.left")
                    .font(.bodyMedium)
                    .foregroundStyle(Color.textPrimary)
                    .frame(width: 44, height: 44)
            }

            Spacer()

            Button {
                let comps = cal.dateComponents([.year, .month], from: displayedMonth)
                pickerYear = comps.year ?? pickerYear
                pickerMonth = comps.month ?? pickerMonth
                showYearMonthPicker = true
            } label: {
                HStack(spacing: 4) {
                    Text(monthTitle)
                        .font(.title3).fontWeight(.bold)
                        .foregroundStyle(Color.textPrimary)
                    Image(systemName: "chevron.down")
                        .font(.caption)
                        .foregroundStyle(Color.textSecondary)
                }
            }

            Spacer()

            Button { navigateMonth(by: 1) } label: {
                Image(systemName: "chevron.right")
                    .font(.bodyMedium)
                    .foregroundStyle(Color.textPrimary)
                    .frame(width: 44, height: 44)
            }
        }
        .padding(.horizontal, Spacing.sm)
        .padding(.vertical, Spacing.md)
    }

    // MARK: - Weekday Row

    private var weekdayRow: some View {
        HStack(spacing: 0) {
            ForEach(["일", "월", "화", "수", "목", "금", "토"], id: \.self) { label in
                Text(label)
                    .font(.caption).fontWeight(.semibold)
                    .foregroundStyle(Color.textSecondary)
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, Spacing.sm)
        .padding(.vertical, Spacing.sm)
    }

    // MARK: - Calendar Grid

    private var calendarGrid: some View {
        let days = calendarDays()
        let rows = stride(from: 0, to: days.count, by: 7).map {
            Array(days[$0..<min($0 + 7, days.count)])
        }

        return VStack(spacing: 2) {
            ForEach(rows.indices, id: \.self) { rowIdx in
                HStack(spacing: 0) {
                    ForEach(0..<7, id: \.self) { colIdx in
                        if let date = rows[rowIdx][colIdx] {
                            CalendarDayCell(
                                date: date,
                                isSelected: cal.isDate(date, inSameDayAs: selectedDate),
                                isToday: cal.isDateInToday(date),
                                isDisabled: date > maxDate || date < minDate,
                                weekday: cal.component(.weekday, from: date)
                            ) {
                                selectedDate = date
                                isPresented = false
                            }
                        } else {
                            Color.clear
                                .frame(maxWidth: .infinity)
                                .frame(height: 44)
                        }
                    }
                }
            }
        }
    }

    // MARK: - Year/Month Picker Overlay

    private var yearMonthPickerOverlay: some View {
        ZStack {
            Color.black.opacity(0.35)
                .ignoresSafeArea()
                .onTapGesture { showYearMonthPicker = false }

            VStack(spacing: 0) {
                HStack {
                    Text("연도 / 월 선택")
                        .font(.headline)
                        .foregroundStyle(Color.textPrimary)
                    Spacer()
                }
                .padding(.horizontal, Spacing.xl)
                .padding(.top, Spacing.xl)
                .padding(.bottom, Spacing.sm)

                HStack(spacing: 0) {
                    Picker("연도", selection: $pickerYear) {
                        ForEach(years, id: \.self) { year in
                            Text("\(year)년").tag(year)
                        }
                    }
                    .pickerStyle(.wheel)
                    .frame(maxWidth: .infinity)

                    Picker("월", selection: $pickerMonth) {
                        ForEach(1...12, id: \.self) { month in
                            Text("\(month)월").tag(month)
                        }
                    }
                    .pickerStyle(.wheel)
                    .frame(maxWidth: .infinity)
                }
                .frame(height: 160)

                Button {
                    if let newMonth = cal.date(from: DateComponents(year: pickerYear, month: pickerMonth, day: 1)) {
                        displayedMonth = newMonth
                    }
                    showYearMonthPicker = false
                } label: {
                    Text("선택")
                        .font(.bodyMedium).fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.md)
                        .background(Color.brandPrimary)
                        .foregroundStyle(.white)
                }
            }
            .background(Color.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            .padding(.horizontal, 32) // design-lint:ignore — modal overlay horizontal inset (28·40 모두 부적합)
        }
    }

    // MARK: - Helpers

    private var monthTitle: String {
        let comps = cal.dateComponents([.year, .month], from: displayedMonth)
        return "\(comps.year ?? 0)년 \(comps.month ?? 0)월"
    }

    private func navigateMonth(by value: Int) {
        if let next = cal.date(byAdding: .month, value: value, to: displayedMonth) {
            displayedMonth = next
        }
    }

    private func calendarDays() -> [Date?] {
        guard let interval = cal.dateInterval(of: .month, for: displayedMonth),
              let weekday = cal.dateComponents([.weekday], from: interval.start).weekday else {
            return []
        }
        // 일요일=1 기준 선행 빈칸
        let leading = (weekday - 1) % 7
        var days: [Date?] = Array(repeating: nil, count: leading)

        let count = cal.range(of: .day, in: .month, for: displayedMonth)?.count ?? 0
        for day in 0..<count {
            days.append(cal.date(byAdding: .day, value: day, to: interval.start))
        }
        while days.count % 7 != 0 { days.append(nil) }
        return days
    }
}

// MARK: - Calendar Day Cell

private struct CalendarDayCell: View {
    let date: Date
    let isSelected: Bool
    let isToday: Bool
    let isDisabled: Bool
    let weekday: Int
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            ZStack {
                if isSelected {
                    Circle()
                        .fill(Color.brandPrimary)
                        .frame(width: 36, height: 36)
                } else if isToday {
                    Circle()
                        .strokeBorder(Color.brandPrimary, lineWidth: 1.5)
                        .frame(width: 36, height: 36)
                }
                Text("\(Calendar.current.component(.day, from: date))")
                    .font(.bodyMedium)
                    .fontWeight(isSelected || isToday ? .bold : .regular)
                    .foregroundStyle(labelColor)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 44)
        }
        .disabled(isDisabled)
    }

    private var labelColor: Color {
        if isDisabled { return Color.textSecondary.opacity(0.3) }
        if isSelected { return .white }
        if weekday == 1 { return .red }
        if weekday == 7 { return Color(red: 0.2, green: 0.4, blue: 0.9) }
        return Color.textPrimary
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
                .font(.bodyLarge)
                .fontWeight(.medium)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
            Text(unit)
                .font(.bodySmall)
                .foregroundStyle(Color.textSecondary)
        }
        .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.md))
        .overlay(
            RoundedRectangle(cornerRadius: Radius.md)
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
                    .font(.system(size: 22)) // design-lint:ignore — SF Symbol size
                    .foregroundStyle(isSelected ? .white : Color.brandPrimary)
                    .frame(width: 28)

                VStack(alignment: .leading, spacing: 2) {
                    Text(option.title)
                        .font(.headingSmall)
                        .foregroundStyle(isSelected ? .white : Color.brandPrimary)
                    Text(option.description)
                        .font(.caption)
                        .foregroundStyle(isSelected ? .white.opacity(0.8) : Color.textSecondary)
                }

                Spacer()

                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(.white)
                }
            }
            .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .background(isSelected ? Color.brandPrimary : Color.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.lg)
                    .stroke(Color.brandPrimary.opacity(isSelected ? 0 : 0.2), lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
        }
    }
}
