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

    /// 운동 기록 변경 — 스트릭 위젯의 오늘 운동 체크 갱신용.
    static let exerciseRecordChanged = Notification.Name("HealthCare.exerciseRecordChanged")

    // 참고: 체중/체지방 변경은 BodyMeasurementViewModel이 이미 발행하는
    // `.bodyMeasurementDidChange` 알림을 그대로 활용한다 (중복 정의 방지).
}
