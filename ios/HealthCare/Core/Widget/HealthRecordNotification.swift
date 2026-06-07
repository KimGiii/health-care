//
//  HealthRecordNotification.swift
//  HealthCare
//
//  앱 내부에서 "건강 기록(식단/운동/체중)이 변경되었음"을 broadcast하는 이벤트.
//  홈 화면이 mount 상태일 때 받아서 dashboard 재로드 + 위젯 스냅샷 갱신.
//

import Foundation

public extension Notification.Name {
    /// 식단 기록(추가/수정/삭제) 변경. AddDietLogViewModel 저장 성공 직후 post.
    static let dietRecordChanged = Notification.Name("HealthCare.dietRecordChanged")

    /// 운동 기록 변경 (향후 운동 기록 화면에서 동일 패턴 사용).
    static let exerciseRecordChanged = Notification.Name("HealthCare.exerciseRecordChanged")

    /// 체중/체지방 기록 변경 (향후 목표 위젯 갱신에 사용).
    static let bodyMeasurementChanged = Notification.Name("HealthCare.bodyMeasurementChanged")
}
