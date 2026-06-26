//
//  HealthCareWidgetBundle.swift
//  HealthCareWidget
//
//  Created by kingloo on 6/5/26.
//

import WidgetKit
import SwiftUI

@main
struct HealthCareWidgetBundle: WidgetBundle {
    var body: some Widget {
        CalorieWidget()
        GoalWidget()
        StreakWidget()
    }
}
