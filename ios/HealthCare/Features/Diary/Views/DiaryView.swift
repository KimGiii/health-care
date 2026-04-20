import SwiftUI

struct DiaryView: View {
    @StateObject private var viewModel = DiaryViewModel()
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 24) {
                    // 월 선택 헤더
                    MonthPickerHeader(viewModel: viewModel)

                    // 달력 그리드
                    CalendarGrid(viewModel: viewModel)

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
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 20)
            }
            .background(Color.surfaceGrouped)
            .navigationTitle("다이어리")
            .navigationBarTitleDisplayMode(.large)
            .refreshable { await viewModel.load(apiClient: container.apiClient) }
            .alert("오류", isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            )) {
                Button("확인", role: .cancel) { viewModel.errorMessage = nil }
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
        }
        .task { await viewModel.load(apiClient: container.apiClient) }
        .onChange(of: viewModel.selectedDate) { _ in
            Task { await viewModel.load(apiClient: container.apiClient) }
        }
    }
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
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color.brandPrimary)
                    .frame(width: 36, height: 36)
                    .background(Color.brandLight)
                    .clipShape(Circle())
            }

            Spacer()

            Text(viewModel.monthYearText)
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Color.textPrimary)

            Spacer()

            Button {
                viewModel.nextMonth()
            } label: {
                Image(systemName: "chevron.right")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color.brandPrimary)
                    .frame(width: 36, height: 36)
                    .background(Color.brandLight)
                    .clipShape(Circle())
            }
        }
        .padding(.horizontal, 4)
    }
}

// MARK: - Calendar Grid

private struct CalendarGrid: View {
    @ObservedObject var viewModel: DiaryViewModel

    private let weekdaySymbols = ["일", "월", "화", "수", "목", "금", "토"]
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 7)

    var body: some View {
        VStack(spacing: 12) {
            // 요일 헤더
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(weekdaySymbols.indices, id: \.self) { index in
                    Text(weekdaySymbols[index])
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(
                            index == 0 ? Color.brandDanger :
                            index == 6 ? Color.brandPrimary :
                            Color.textSecondary
                        )
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(.bottom, 4)

            // 날짜 그리드
            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(viewModel.calendarDays.indices, id: \.self) { index in
                    if let date = viewModel.calendarDays[index] {
                        CalendarDayCell(
                            date: date,
                            hasExercise: viewModel.hasExerciseRecord(on: date),
                            hasDiet: viewModel.hasDietRecord(on: date),
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
        .padding(16)
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 2)
    }
}

// MARK: - Calendar Day Cell

private struct CalendarDayCell: View {
    let date: Date
    let hasExercise: Bool
    let hasDiet: Bool
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
                            .fill(Color.brandPrimary)
                            .frame(width: 36, height: 36)
                    } else if isToday {
                        Circle()
                            .stroke(Color.brandPrimary, lineWidth: 1.5)
                            .frame(width: 36, height: 36)
                    }

                    Text("\(dayNumber)")
                        .font(.system(size: 15, weight: isToday || isSelected ? .semibold : .regular))
                        .foregroundStyle(
                            isSelected ? .white :
                            isToday ? Color.brandPrimary :
                            Color.textPrimary
                        )
                }

                // 운동 및 식단 기록 표시
                if hasExercise || hasDiet {
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
        formatter.dateFormat = "M월 d일 (E)"
        formatter.locale = Locale(identifier: "ko_KR")
        return formatter.string(from: date)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // 헤더
            HStack {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 16))
                    .foregroundStyle(Color.green)

                Text("운동 완료")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Color.textPrimary)

                Spacer()

                Text(dateText)
                    .font(.system(size: 13))
                    .foregroundStyle(Color.textSecondary)
            }
            .padding(.horizontal, 16)
            .padding(.top, 16)

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
            .padding(.horizontal, 16)
            .padding(.bottom, 16)
        }
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 16))
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
                .font(.system(size: 18))
                .foregroundStyle(Color.brandPrimary)
                .frame(width: 40, height: 40)
                .background(Color.brandLight)
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
                            value: "\(dur)분"
                        )
                    }
                }

                if let notes = session.notes, !notes.isEmpty {
                    Text(notes)
                        .font(.system(size: 12))
                        .foregroundStyle(Color.textSecondary)
                        .lineLimit(1)
                }
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Color.textSecondary.opacity(0.5))
        }
        .padding(12)
        .background(Color.surfaceGrouped)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func statChip(icon: String, value: String) -> some View {
        HStack(spacing: 3) {
            Image(systemName: icon)
                .font(.system(size: 10))
                .foregroundStyle(Color.brandPrimary)
            Text(value)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Color.textSecondary)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 3)
        .background(Color.brandLight.opacity(0.5))
        .clipShape(Capsule())
    }
}

// MARK: - Diet Records Section

private struct DietRecordsSection: View {
    let date: Date
    let logs: [DietLogSummary]

    private var dateText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "M월 d일 (E)"
        formatter.locale = Locale(identifier: "ko_KR")
        return formatter.string(from: date)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // 헤더
            HStack {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 16))
                    .foregroundStyle(Color.orange)

                Text("식단 완료")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Color.textPrimary)

                Spacer()

                Text(dateText)
                    .font(.system(size: 13))
                    .foregroundStyle(Color.textSecondary)
            }
            .padding(.horizontal, 16)
            .padding(.top, 16)

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
            .padding(.horizontal, 16)
            .padding(.bottom, 16)
        }
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 2)
    }
}

// MARK: - Diet Log Summary Card

private struct DietLogSummaryCard: View {
    let log: DietLogSummary

    var body: some View {
        HStack(spacing: 12) {
            // 식사 유형 아이콘
            VStack(spacing: 2) {
                Text(log.mealType.emoji)
                    .font(.system(size: 20))
                Text(log.mealType.displayName)
                    .font(.system(size: 9, weight: .semibold))
                    .foregroundStyle(Color.brandAccent)
            }
            .frame(width: 40, height: 40)
            .background(Color.brandLight)
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
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Color.textSecondary.opacity(0.5))
        }
        .padding(12)
        .background(Color.surfaceGrouped)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func statChip(icon: String, value: String) -> some View {
        HStack(spacing: 3) {
            Image(systemName: icon)
                .font(.system(size: 10))
                .foregroundStyle(Color.orange)
            Text(value)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Color.textSecondary)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 3)
        .background(Color.orange.opacity(0.1))
        .clipShape(Capsule())
    }
}
