import SwiftUI

struct DiaryView: View {
    @StateObject private var viewModel = DiaryViewModel()
    @EnvironmentObject private var container: AppContainer
    @State private var activeAddSheet: DiaryAddSheet?

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 24) {
                    // 월 선택 헤더
                    MonthPickerHeader(viewModel: viewModel)

                    // 달력 그리드
                    CalendarGrid(viewModel: viewModel)

                    QuickAddSection(
                        selectedDate: viewModel.selectedDate,
                        hasExercise: viewModel.hasExerciseRecord(on: viewModel.selectedDate),
                        hasDiet: viewModel.hasDietRecord(on: viewModel.selectedDate),
                        hasMeasurement: viewModel.hasMeasurementRecord(on: viewModel.selectedDate),
                        onSelect: { activeAddSheet = $0 }
                    )

                    // 선택된 날짜의 운동 기록
                    if !viewModel.exerciseSessions(on: viewModel.selectedDate).isEmpty {
                        ExerciseRecordsSection(
                            date: viewModel.selectedDate,
                            sessions: viewModel.exerciseSessions(on: viewModel.selectedDate)
                        )
                    }

                    // 선택된 날짜의 식단 기록
                    if !viewModel.dietLogs(on: viewModel.selectedDate).isEmpty {
                        DietRecordsSection(
                            date: viewModel.selectedDate,
                            logs: viewModel.dietLogs(on: viewModel.selectedDate)
                        )
                    }

                    // 선택된 날짜의 신체 측정 기록
                    if !viewModel.measurements(on: viewModel.selectedDate).isEmpty {
                        MeasurementRecordsSection(
                            date: viewModel.selectedDate,
                            measurements: viewModel.measurements(on: viewModel.selectedDate)
                        )
                    }
                }
                .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
                .padding(.vertical, Spacing.xl) // design-lint:ignore — micro/hero spacing
            }
            .background(Color.backgroundPage)
            .navigationTitle(Text("diary.title"))
            .navigationBarTitleDisplayMode(.large)
            .overlay(alignment: .center) {
                if viewModel.isLoading {
                    ProgressView()
                        .padding(Spacing.xl) // design-lint:ignore — micro/hero spacing
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: Radius.lg))
                }
            }
            .refreshable {
                await viewModel.load(apiClient: container.apiClient)
                viewModel.errorMessage = nil
            }
            .alert(Text("오류"), isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            )) {
                Button(String(localized: "common.ok"), role: .cancel) { viewModel.errorMessage = nil }
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
            .sheet(item: $activeAddSheet) { sheet in
                switch sheet {
                case .exercise:
                    AddExerciseSessionView(initialDate: viewModel.selectedDate) { _ in
                        finishQuickAdd()
                    }
                    .environmentObject(container)
                case .diet:
                    AddDietLogView(initialDate: viewModel.selectedDate) {
                        finishQuickAdd()
                    }
                    .environmentObject(container)
                case .measurement:
                    AddMeasurementView(initialDate: viewModel.selectedDate) {
                        finishQuickAdd()
                    }
                    .environmentObject(container)
                }
            }
        }
        .task { await viewModel.load(apiClient: container.apiClient) }
        .onChange(of: viewModel.selectedDate) { _ in
            Task { await viewModel.load(apiClient: container.apiClient) }
        }
    }

    private func finishQuickAdd() {
        activeAddSheet = nil
        Task { await viewModel.load(apiClient: container.apiClient) }
    }
}

private enum DiaryAddSheet: String, Identifiable {
    case exercise
    case diet
    case measurement

    var id: String { rawValue }
}

// MARK: - Month Picker Header

private struct MonthPickerHeader: View {
    @ObservedObject var viewModel: DiaryViewModel

    var body: some View {
        HStack {
            Button {
                viewModel.previousMonth()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.bodyLarge).fontWeight(.semibold)
                    .foregroundStyle(Color.brandAccent)
                    .frame(width: 36, height: 36)
                    .background(Color.brandAccent.opacity(0.15))
                    .clipShape(Circle())
            }

            Spacer()

            Text(viewModel.monthYearText)
                .font(.headingMedium).fontWeight(.bold)
                .foregroundStyle(Color.textPrimary)

            Spacer()

            Button {
                viewModel.nextMonth()
            } label: {
                Image(systemName: "chevron.right")
                    .font(.bodyLarge).fontWeight(.semibold)
                    .foregroundStyle(Color.brandAccent)
                    .frame(width: 36, height: 36)
                    .background(Color.brandAccent.opacity(0.15))
                    .clipShape(Circle())
            }
        }
        .padding(.horizontal, Spacing.xs) // design-lint:ignore — micro/hero spacing
    }
}

// MARK: - Quick Add

private struct QuickAddSection: View {
    let selectedDate: Date
    let hasExercise: Bool
    let hasDiet: Bool
    let hasMeasurement: Bool
    let onSelect: (DiaryAddSheet) -> Void

    private var dateText: String {
        let formatter = DateFormatter()
        formatter.locale = LocaleManager.shared.effectiveLocale
        formatter.dateFormat = String(localized: "diary.dayHeader.format")
        return formatter.string(from: selectedDate)
    }

    private var hasAnyQuickAction: Bool {
        !hasExercise || !hasDiet || !hasMeasurement
    }

    var body: some View {
        if hasAnyQuickAction {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(String(localized: "diary.quickAdd"))
                            .font(.bodyLarge).fontWeight(.bold)
                            .foregroundStyle(Color.textPrimary)
                        Text(dateText)
                            .font(.caption)
                            .foregroundStyle(Color.textSecondary)
                    }
                    Spacer()
                }

                HStack(spacing: 10) {
                    if !hasExercise {
                        QuickAddButton(
                            icon: "figure.strengthtraining.traditional",
                            title: String(localized: "diary.quickAdd.exercise"),
                            tint: .green
                        ) {
                            onSelect(.exercise)
                        }
                    }

                    if !hasDiet {
                        QuickAddButton(
                            icon: "fork.knife",
                            title: String(localized: "diary.quickAdd.diet"),
                            tint: .orange
                        ) {
                            onSelect(.diet)
                        }
                    }

                    if !hasMeasurement {
                        QuickAddButton(
                            icon: "scalemass.fill",
                            title: String(localized: "diary.quickAdd.body"),
                            tint: Color.brandAccent
                        ) {
                            onSelect(.measurement)
                        }
                    }
                }
            }
            .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
            .background(Color.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 2)
        }
    }
}

private struct QuickAddButton: View {
    let icon: String
    let title: String
    let tint: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.cta)
                    .foregroundStyle(tint)
                    .frame(width: 34, height: 34)
                    .background(tint.opacity(0.12))
                    .clipShape(Circle())

                Text(title)
                    .font(.captionBold)
                    .foregroundStyle(Color.textPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
            .background(Color.backgroundPage)
            .clipShape(RoundedRectangle(cornerRadius: Radius.md))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Calendar Grid

private struct CalendarGrid: View {
    @ObservedObject var viewModel: DiaryViewModel

    private let weekdaySymbols = [
        String(localized: "common.weekday.sun"),
        String(localized: "common.weekday.mon"),
        String(localized: "common.weekday.tue"),
        String(localized: "common.weekday.wed"),
        String(localized: "common.weekday.thu"),
        String(localized: "common.weekday.fri"),
        String(localized: "common.weekday.sat"),
    ]
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 7)

    var body: some View {
        VStack(spacing: 12) {
            // 요일 헤더
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(weekdaySymbols.indices, id: \.self) { index in
                    Text(weekdaySymbols[index])
                        .font(.captionBold)
                        .foregroundStyle(
                            index == 0 ? Color.brandDanger :
                            index == 6 ? Color.brandAccent :
                            Color.textSecondary
                        )
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(.bottom, Spacing.xs) // design-lint:ignore — micro/hero spacing

            // 날짜 그리드
            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(viewModel.calendarDays.indices, id: \.self) { index in
                    if let date = viewModel.calendarDays[index] {
                        CalendarDayCell(
                            date: date,
                            hasExercise: viewModel.hasExerciseRecord(on: date),
                            hasDiet: viewModel.hasDietRecord(on: date),
                            hasMeasurement: viewModel.hasMeasurementRecord(on: date),
                            isSelected: Calendar.current.isDate(date, inSameDayAs: viewModel.selectedDate),
                            isToday: Calendar.current.isDateInToday(date)
                        ) {
                            viewModel.selectedDate = date
                        }
                    } else {
                        Color.clear
                            .frame(height: 44)
                    }
                }
            }
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 2)
    }
}

// MARK: - Calendar Day Cell

private struct CalendarDayCell: View {
    let date: Date
    let hasExercise: Bool
    let hasDiet: Bool
    let hasMeasurement: Bool
    let isSelected: Bool
    let isToday: Bool
    let onTap: () -> Void

    private var dayNumber: Int {
        Calendar.current.component(.day, from: date)
    }

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 4) {
                ZStack {
                    // 선택된 날짜 배경
                    if isSelected {
                        Circle()
                            .fill(Color.brandAccent)
                            .frame(width: 36, height: 36)
                    } else if isToday {
                        Circle()
                            .stroke(Color.brandAccent, lineWidth: 1.5)
                            .frame(width: 36, height: 36)
                    }

                    Text("\(dayNumber)")
                        .font(.bodyMedium)
                        .fontWeight(isToday || isSelected ? .semibold : .regular)
                        .foregroundStyle(
                            isSelected ? Color.textHeadline :
                            isToday ? Color.brandAccent :
                            Color.textPrimary
                        )
                }

                // 운동·식단·신체 측정 기록 표시
                if hasExercise || hasDiet || hasMeasurement {
                    HStack(spacing: 2) {
                        if hasExercise {
                            Circle()
                                .fill(Color.green)
                                .frame(width: 4, height: 4)
                        }
                        if hasDiet {
                            Circle()
                                .fill(Color.orange)
                                .frame(width: 4, height: 4)
                        }
                        if hasMeasurement {
                            Circle()
                                .fill(Color.brandAccent)
                                .frame(width: 4, height: 4)
                        }
                    }
                } else {
                    Spacer()
                        .frame(height: 4)
                }
            }
            .frame(height: 50)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Exercise Records Section

private struct ExerciseRecordsSection: View {
    let date: Date
    let sessions: [SessionSummary]

    private var dateText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = String(localized: "diary.section.format")
        formatter.locale = LocaleManager.resolvedLocale
        formatter.locale = LocaleManager.shared.effectiveLocale
        return formatter.string(from: date)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // 헤더
            HStack {
                Image(systemName: "checkmark.circle.fill")
                    .font(.bodyLarge)
                    .foregroundStyle(Color.green)

                Text(String(localized: "diary.section.exercise"))
                    .font(.bodyLarge).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)

                Spacer()

                Text(dateText)
                    .font(.bodySmall)
                    .foregroundStyle(Color.textSecondary)
            }
            .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .padding(.top, Spacing.lg) // design-lint:ignore — micro/hero spacing

            // 세션 리스트
            VStack(spacing: 8) {
                ForEach(sessions) { session in
                    NavigationLink {
                        ExerciseSessionDetailView(sessionId: session.sessionId)
                    } label: {
                        ExerciseSessionSummaryCard(session: session)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .padding(.bottom, Spacing.lg) // design-lint:ignore — micro/hero spacing
        }
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 2)
    }
}

// MARK: - Exercise Session Summary Card

private struct ExerciseSessionSummaryCard: View {
    let session: SessionSummary

    var body: some View {
        HStack(spacing: 12) {
            // 아이콘
            Image(systemName: "figure.strengthtraining.traditional")
                .font(.headingMedium).fontWeight(.regular)
                .foregroundStyle(Color.brandAccent)
                .frame(width: 40, height: 40)
                .background(Color.brandAccent.opacity(0.15))
                .clipShape(Circle())

            // 정보
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    if let vol = session.totalVolumeKg {
                        statChip(
                            icon: "figure.strengthtraining.traditional",
                            value: String(format: "%.0fkg", vol)
                        )
                    }
                    if let cal = session.caloriesBurned {
                        statChip(
                            icon: "flame.fill",
                            value: String(format: "%.0fkcal", cal)
                        )
                    }
                    if let dur = session.durationMinutes {
                        statChip(
                            icon: "clock",
                            value: String(format: String(localized: "diary.session.duration"), dur)
                        )
                    }
                }

                if let notes = session.notes, !notes.isEmpty {
                    Text(notes)
                        .font(.caption)
                        .foregroundStyle(Color.textSecondary)
                        .lineLimit(1)
                }
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.bodySmall).fontWeight(.medium)
                .foregroundStyle(Color.textSecondary.opacity(0.5))
        }
        .padding(Spacing.md) // design-lint:ignore — micro/hero spacing
        .background(Color.backgroundPage)
        .clipShape(RoundedRectangle(cornerRadius: Radius.md))
    }

    private func statChip(icon: String, value: String) -> some View {
        HStack(spacing: 3) {
            Image(systemName: icon)
                .font(.captionXSmall)
                .foregroundStyle(Color.brandAccent)
            Text(value)
                .font(.captionXSmall)
                .foregroundStyle(Color.textSecondary)
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
        }
        .padding(.horizontal, Spacing.sm) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, 3) // design-lint:ignore — micro/hero spacing
        .background(Color.brandAccent.opacity(0.12))
        .clipShape(Capsule())
    }
}

// MARK: - Diet Records Section

private struct DietRecordsSection: View {
    let date: Date
    let logs: [DietLogSummary]

    private var dateText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = String(localized: "diary.section.format")
        formatter.locale = LocaleManager.resolvedLocale
        formatter.locale = LocaleManager.shared.effectiveLocale
        return formatter.string(from: date)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // 헤더
            HStack {
                Image(systemName: "checkmark.circle.fill")
                    .font(.bodyLarge)
                    .foregroundStyle(Color.orange)

                Text(String(localized: "diary.section.diet"))
                    .font(.bodyLarge).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)

                Spacer()

                Text(dateText)
                    .font(.bodySmall)
                    .foregroundStyle(Color.textSecondary)
            }
            .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .padding(.top, Spacing.lg) // design-lint:ignore — micro/hero spacing

            // 식단 로그 리스트
            VStack(spacing: 8) {
                ForEach(logs) { log in
                    NavigationLink {
                        DietLogDetailView(logId: log.dietLogId, mealType: log.mealType, logDate: log.logDate)
                    } label: {
                        DietLogSummaryCard(log: log)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .padding(.bottom, Spacing.lg) // design-lint:ignore — micro/hero spacing
        }
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 2)
    }
}

// MARK: - Measurement Records Section

private struct MeasurementRecordsSection: View {
    let date: Date
    let measurements: [MeasurementResponse]

    private var dateText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = String(localized: "diary.section.format")
        formatter.locale = LocaleManager.resolvedLocale
        formatter.locale = LocaleManager.shared.effectiveLocale
        return formatter.string(from: date)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "scalemass.fill")
                    .font(.bodyLarge)
                    .foregroundStyle(Color.brandAccent)

                Text(String(localized: "diary.section.body"))
                    .font(.bodyLarge).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)

                Spacer()

                Text(dateText)
                    .font(.bodySmall)
                    .foregroundStyle(Color.textSecondary)
            }
            .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .padding(.top, Spacing.lg) // design-lint:ignore — micro/hero spacing

            VStack(spacing: 8) {
                ForEach(measurements) { measurement in
                    MeasurementSummaryCard(measurement: measurement)
                }
            }
            .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .padding(.bottom, Spacing.lg) // design-lint:ignore — micro/hero spacing
        }
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 2)
    }
}

private struct MeasurementSummaryCard: View {
    let measurement: MeasurementResponse

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "scalemass.fill")
                .font(.headingMedium).fontWeight(.regular)
                .foregroundStyle(Color.brandAccent)
                .frame(width: 40, height: 40)
                .background(Color.brandAccent.opacity(0.15))
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    if let w = measurement.weightKg {
                        statChip(icon: "scalemass", value: String(format: "%.1fkg", w), color: Color.brandAccent)
                    }
                    if let bf = measurement.bodyFatPct {
                        statChip(icon: "percent", value: String(format: "%.1f%%", bf), color: Color(hex: "#7C3AED"))
                    }
                    if let mm = measurement.muscleMassKg {
                        statChip(icon: "figure.arms.open", value: String(format: "%.1fkg", mm), color: Color(hex: "#10B981"))
                    }
                }
                if let notes = measurement.notes, !notes.isEmpty {
                    Text(notes)
                        .font(.caption)
                        .foregroundStyle(Color.textSecondary)
                        .lineLimit(1)
                }
            }

            Spacer()
        }
        .padding(Spacing.md) // design-lint:ignore — micro/hero spacing
        .background(Color.backgroundPage)
        .clipShape(RoundedRectangle(cornerRadius: Radius.md))
    }

    private func statChip(icon: String, value: String, color: Color) -> some View {
        HStack(spacing: 3) {
            Image(systemName: icon)
                .font(.captionXSmall)
                .foregroundStyle(color)
            Text(value)
                .font(.captionXSmall)
                .foregroundStyle(Color.textSecondary)
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
        }
        .padding(.horizontal, Spacing.sm) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, 3) // design-lint:ignore — micro/hero spacing
        .background(color.opacity(0.12))
        .clipShape(Capsule())
    }
}

// MARK: - Diet Log Summary Card

private struct DietLogSummaryCard: View {
    let log: DietLogSummary

    var body: some View {
        HStack(spacing: 12) {
            // 식사 유형 아이콘
            VStack(spacing: 2) {
                Image(systemName: log.mealType.sfSymbol)
                    .font(.system(size: 18)) // design-lint:ignore — SF Symbol icon sizing
                Text(log.mealType.displayName)
                    .font(.system(size: 9, weight: .semibold)) // design-lint:ignore — SF Symbol or special
                    .foregroundStyle(Color.brandAccent)
            }
            .frame(width: 40, height: 40)
            .background(Color.brandAccent.opacity(0.15))
            .clipShape(Circle())

            // 정보
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    if let cal = log.totalCalories {
                        statChip(
                            icon: "flame.fill",
                            value: String(format: "%.0fkcal", cal)
                        )
                    }
                    if let p = log.totalProteinG {
                        statChip(
                            icon: "p.circle.fill",
                            value: String(format: "%.0fg", p)
                        )
                    }
                    if let c = log.totalCarbsG {
                        statChip(
                            icon: "c.circle.fill",
                            value: String(format: "%.0fg", c)
                        )
                    }
                }
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.bodySmall).fontWeight(.medium)
                .foregroundStyle(Color.textSecondary.opacity(0.5))
        }
        .padding(Spacing.md) // design-lint:ignore — micro/hero spacing
        .background(Color.backgroundPage)
        .clipShape(RoundedRectangle(cornerRadius: Radius.md))
    }

    private func statChip(icon: String, value: String) -> some View {
        HStack(spacing: 3) {
            Image(systemName: icon)
                .font(.captionXSmall)
                .foregroundStyle(Color.orange)
            Text(value)
                .font(.captionXSmall)
                .foregroundStyle(Color.textSecondary)
        }
        .padding(.horizontal, Spacing.sm) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, 3) // design-lint:ignore — micro/hero spacing
        .background(Color.orange.opacity(0.1))
        .clipShape(Capsule())
    }
}
