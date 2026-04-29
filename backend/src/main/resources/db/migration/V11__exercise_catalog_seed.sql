-- 운동 카탈로그 시드 데이터 (한국어 지원)
-- MET 출처: Compendium of Physical Activities (Ainsworth et al., 2011)

INSERT INTO exercise_catalog (name, name_ko, muscle_group, exercise_type, met_value, is_custom, created_at, updated_at)
VALUES

-- ── 가슴 (CHEST) ──────────────────────────────────────────
('Bench Press', '벤치 프레스', 'CHEST', 'STRENGTH', 6.0, false, NOW(), NOW()),
('Incline Bench Press', '인클라인 벤치 프레스', 'CHEST', 'STRENGTH', 6.0, false, NOW(), NOW()),
('Decline Bench Press', '디클라인 벤치 프레스', 'CHEST', 'STRENGTH', 6.0, false, NOW(), NOW()),
('Dumbbell Fly', '덤벨 플라이', 'CHEST', 'STRENGTH', 5.0, false, NOW(), NOW()),
('Cable Crossover', '케이블 크로스오버', 'CHEST', 'STRENGTH', 5.0, false, NOW(), NOW()),
('Push-Up', '푸시업', 'CHEST', 'BODYWEIGHT', 8.0, false, NOW(), NOW()),
('Wide Push-Up', '와이드 푸시업', 'CHEST', 'BODYWEIGHT', 8.0, false, NOW(), NOW()),
('Diamond Push-Up', '다이아몬드 푸시업', 'CHEST', 'BODYWEIGHT', 8.0, false, NOW(), NOW()),
('Chest Dip', '체스트 딥', 'CHEST', 'BODYWEIGHT', 8.0, false, NOW(), NOW()),

-- ── 등 (BACK) ────────────────────────────────────────────
('Deadlift', '데드리프트', 'BACK', 'STRENGTH', 6.0, false, NOW(), NOW()),
('Barbell Row', '바벨 로우', 'BACK', 'STRENGTH', 6.0, false, NOW(), NOW()),
('Dumbbell Row', '덤벨 로우', 'BACK', 'STRENGTH', 5.5, false, NOW(), NOW()),
('Lat Pulldown', '랫 풀다운', 'BACK', 'STRENGTH', 5.0, false, NOW(), NOW()),
('Seated Cable Row', '시티드 케이블 로우', 'BACK', 'STRENGTH', 5.0, false, NOW(), NOW()),
('Pull-Up', '풀업', 'BACK', 'BODYWEIGHT', 8.0, false, NOW(), NOW()),
('Chin-Up', '친업', 'BACK', 'BODYWEIGHT', 8.0, false, NOW(), NOW()),
('T-Bar Row', 'T바 로우', 'BACK', 'STRENGTH', 6.0, false, NOW(), NOW()),
('Hyperextension', '백 익스텐션', 'BACK', 'BODYWEIGHT', 4.0, false, NOW(), NOW()),

-- ── 어깨 (SHOULDERS) ──────────────────────────────────────
('Overhead Press', '오버헤드 프레스', 'SHOULDERS', 'STRENGTH', 6.0, false, NOW(), NOW()),
('Dumbbell Shoulder Press', '덤벨 숄더 프레스', 'SHOULDERS', 'STRENGTH', 5.5, false, NOW(), NOW()),
('Lateral Raise', '레터럴 레이즈', 'SHOULDERS', 'STRENGTH', 4.0, false, NOW(), NOW()),
('Front Raise', '프론트 레이즈', 'SHOULDERS', 'STRENGTH', 4.0, false, NOW(), NOW()),
('Rear Delt Fly', '리어 델트 플라이', 'SHOULDERS', 'STRENGTH', 4.0, false, NOW(), NOW()),
('Arnold Press', '아놀드 프레스', 'SHOULDERS', 'STRENGTH', 5.5, false, NOW(), NOW()),
('Upright Row', '업라이트 로우', 'SHOULDERS', 'STRENGTH', 5.0, false, NOW(), NOW()),

-- ── 이두 (BICEPS) ────────────────────────────────────────
('Barbell Curl', '바벨 컬', 'BICEPS', 'STRENGTH', 4.0, false, NOW(), NOW()),
('Dumbbell Curl', '덤벨 컬', 'BICEPS', 'STRENGTH', 4.0, false, NOW(), NOW()),
('Hammer Curl', '해머 컬', 'BICEPS', 'STRENGTH', 4.0, false, NOW(), NOW()),
('Concentration Curl', '컨센트레이션 컬', 'BICEPS', 'STRENGTH', 4.0, false, NOW(), NOW()),
('Cable Curl', '케이블 컬', 'BICEPS', 'STRENGTH', 4.0, false, NOW(), NOW()),
('Preacher Curl', '프리처 컬', 'BICEPS', 'STRENGTH', 4.0, false, NOW(), NOW()),

-- ── 삼두 (TRICEPS) ──────────────────────────────────────
('Tricep Pushdown', '트라이셉 푸시다운', 'TRICEPS', 'STRENGTH', 4.0, false, NOW(), NOW()),
('Overhead Tricep Extension', '오버헤드 트라이셉 익스텐션', 'TRICEPS', 'STRENGTH', 4.0, false, NOW(), NOW()),
('Skull Crusher', '스컬 크러셔', 'TRICEPS', 'STRENGTH', 4.0, false, NOW(), NOW()),
('Close Grip Bench Press', '클로즈 그립 벤치 프레스', 'TRICEPS', 'STRENGTH', 6.0, false, NOW(), NOW()),
('Tricep Dip', '트라이셉 딥', 'TRICEPS', 'BODYWEIGHT', 7.0, false, NOW(), NOW()),
('Kickback', '킥백', 'TRICEPS', 'STRENGTH', 4.0, false, NOW(), NOW()),

-- ── 코어 (CORE) ──────────────────────────────────────────
('Plank', '플랭크', 'CORE', 'BODYWEIGHT', 4.0, false, NOW(), NOW()),
('Crunch', '크런치', 'CORE', 'BODYWEIGHT', 5.0, false, NOW(), NOW()),
('Sit-Up', '싯업', 'CORE', 'BODYWEIGHT', 5.5, false, NOW(), NOW()),
('Leg Raise', '레그 레이즈', 'CORE', 'BODYWEIGHT', 5.0, false, NOW(), NOW()),
('Russian Twist', '러시안 트위스트', 'CORE', 'BODYWEIGHT', 5.0, false, NOW(), NOW()),
('Bicycle Crunch', '바이시클 크런치', 'CORE', 'BODYWEIGHT', 8.0, false, NOW(), NOW()),
('Dead Bug', '데드 버그', 'CORE', 'BODYWEIGHT', 4.0, false, NOW(), NOW()),
('Ab Wheel Rollout', '복근 롤아웃', 'CORE', 'BODYWEIGHT', 7.0, false, NOW(), NOW()),
('Mountain Climber', '마운틴 클라이머', 'CORE', 'BODYWEIGHT', 8.0, false, NOW(), NOW()),

-- ── 대퇴사두 (QUADRICEPS) ────────────────────────────────
('Squat', '스쿼트', 'QUADRICEPS', 'STRENGTH', 6.0, false, NOW(), NOW()),
('Front Squat', '프론트 스쿼트', 'QUADRICEPS', 'STRENGTH', 6.0, false, NOW(), NOW()),
('Leg Press', '레그 프레스', 'QUADRICEPS', 'STRENGTH', 5.0, false, NOW(), NOW()),
('Leg Extension', '레그 익스텐션', 'QUADRICEPS', 'STRENGTH', 4.0, false, NOW(), NOW()),
('Lunge', '런지', 'QUADRICEPS', 'BODYWEIGHT', 5.5, false, NOW(), NOW()),
('Walking Lunge', '워킹 런지', 'QUADRICEPS', 'BODYWEIGHT', 6.0, false, NOW(), NOW()),
('Hack Squat', '핵 스쿼트', 'QUADRICEPS', 'STRENGTH', 5.5, false, NOW(), NOW()),
('Goblet Squat', '고블릿 스쿼트', 'QUADRICEPS', 'BODYWEIGHT', 5.5, false, NOW(), NOW()),
('Split Squat', '스플릿 스쿼트', 'QUADRICEPS', 'BODYWEIGHT', 5.5, false, NOW(), NOW()),

-- ── 햄스트링 (HAMSTRINGS) ────────────────────────────────
('Romanian Deadlift', '루마니안 데드리프트', 'HAMSTRINGS', 'STRENGTH', 6.0, false, NOW(), NOW()),
('Leg Curl', '레그 컬', 'HAMSTRINGS', 'STRENGTH', 4.0, false, NOW(), NOW()),
('Stiff Leg Deadlift', '스티프 레그 데드리프트', 'HAMSTRINGS', 'STRENGTH', 6.0, false, NOW(), NOW()),
('Good Morning', '굿모닝', 'HAMSTRINGS', 'STRENGTH', 5.0, false, NOW(), NOW()),
('Nordic Curl', '노르딕 컬', 'HAMSTRINGS', 'BODYWEIGHT', 7.0, false, NOW(), NOW()),

-- ── 둔근 (GLUTES) ────────────────────────────────────────
('Hip Thrust', '힙 쓰러스트', 'GLUTES', 'STRENGTH', 5.5, false, NOW(), NOW()),
('Glute Bridge', '글루트 브리지', 'GLUTES', 'BODYWEIGHT', 4.5, false, NOW(), NOW()),
('Cable Kickback', '케이블 킥백', 'GLUTES', 'STRENGTH', 4.0, false, NOW(), NOW()),
('Sumo Squat', '스모 스쿼트', 'GLUTES', 'STRENGTH', 5.5, false, NOW(), NOW()),
('Step-Up', '스텝업', 'GLUTES', 'BODYWEIGHT', 5.5, false, NOW(), NOW()),

-- ── 종아리 (CALVES) ──────────────────────────────────────
('Standing Calf Raise', '스탠딩 카프 레이즈', 'CALVES', 'STRENGTH', 4.0, false, NOW(), NOW()),
('Seated Calf Raise', '시티드 카프 레이즈', 'CALVES', 'STRENGTH', 4.0, false, NOW(), NOW()),
('Donkey Calf Raise', '동키 카프 레이즈', 'CALVES', 'STRENGTH', 4.0, false, NOW(), NOW()),

-- ── 전신 (FULL_BODY) ─────────────────────────────────────
('Clean and Jerk', '클린 앤 저크', 'FULL_BODY', 'STRENGTH', 8.0, false, NOW(), NOW()),
('Snatch', '스내치', 'FULL_BODY', 'STRENGTH', 8.0, false, NOW(), NOW()),
('Thruster', '쓰러스터', 'FULL_BODY', 'STRENGTH', 8.0, false, NOW(), NOW()),
('Burpee', '버피', 'FULL_BODY', 'BODYWEIGHT', 8.0, false, NOW(), NOW()),
('Kettlebell Swing', '케틀벨 스윙', 'FULL_BODY', 'STRENGTH', 8.0, false, NOW(), NOW()),
('Battle Rope', '배틀 로프', 'FULL_BODY', 'CARDIO', 9.0, false, NOW(), NOW()),
('Box Jump', '박스 점프', 'FULL_BODY', 'BODYWEIGHT', 8.0, false, NOW(), NOW()),
('Jump Squat', '점프 스쿼트', 'FULL_BODY', 'BODYWEIGHT', 7.0, false, NOW(), NOW()),
('Turkish Get-Up', '터키시 겟업', 'FULL_BODY', 'STRENGTH', 6.0, false, NOW(), NOW()),

-- ── 유산소 (CARDIO) ──────────────────────────────────────
('Running', '달리기', 'CARDIO', 'CARDIO', 9.8, false, NOW(), NOW()),
('Treadmill Walking', '러닝머신 걷기', 'CARDIO', 'CARDIO', 3.5, false, NOW(), NOW()),
('Treadmill Running', '러닝머신 달리기', 'CARDIO', 'CARDIO', 9.8, false, NOW(), NOW()),
('Cycling', '자전거 타기', 'CARDIO', 'CARDIO', 8.0, false, NOW(), NOW()),
('Stationary Bike', '실내 자전거', 'CARDIO', 'CARDIO', 6.8, false, NOW(), NOW()),
('Rowing Machine', '로잉머신', 'CARDIO', 'CARDIO', 7.0, false, NOW(), NOW()),
('Jump Rope', '줄넘기', 'CARDIO', 'CARDIO', 11.0, false, NOW(), NOW()),
('Elliptical', '일립티컬', 'CARDIO', 'CARDIO', 6.0, false, NOW(), NOW()),
('Swimming', '수영', 'CARDIO', 'CARDIO', 8.0, false, NOW(), NOW()),
('Walking', '걷기', 'CARDIO', 'CARDIO', 3.5, false, NOW(), NOW()),
('Hiking', '등산', 'CARDIO', 'CARDIO', 6.0, false, NOW(), NOW()),
('HIIT', '고강도 인터벌 트레이닝', 'CARDIO', 'CARDIO', 12.0, false, NOW(), NOW()),
('Stair Climbing', '계단 오르기', 'CARDIO', 'CARDIO', 8.0, false, NOW(), NOW()),

-- ── 유연성 / 기타 (FLEXIBILITY) ──────────────────────────
('Yoga', '요가', 'FULL_BODY', 'FLEXIBILITY', 3.0, false, NOW(), NOW()),
('Stretching', '스트레칭', 'FULL_BODY', 'FLEXIBILITY', 2.3, false, NOW(), NOW()),
('Pilates', '필라테스', 'FULL_BODY', 'FLEXIBILITY', 3.8, false, NOW(), NOW()),
('Foam Rolling', '폼롤링', 'FULL_BODY', 'FLEXIBILITY', 2.0, false, NOW(), NOW()),

-- ── 스포츠 (SPORTS) ──────────────────────────────────────
('Basketball', '농구', 'FULL_BODY', 'SPORTS', 8.0, false, NOW(), NOW()),
('Soccer', '축구', 'FULL_BODY', 'SPORTS', 10.0, false, NOW(), NOW()),
('Tennis', '테니스', 'FULL_BODY', 'SPORTS', 8.0, false, NOW(), NOW()),
('Badminton', '배드민턴', 'FULL_BODY', 'SPORTS', 7.0, false, NOW(), NOW()),
('Table Tennis', '탁구', 'FULL_BODY', 'SPORTS', 4.0, false, NOW(), NOW()),
('Golf', '골프', 'FULL_BODY', 'SPORTS', 4.5, false, NOW(), NOW()),
('Volleyball', '배구', 'FULL_BODY', 'SPORTS', 4.0, false, NOW(), NOW()),

-- ── 전완 (FOREARMS) ──────────────────────────────────────
('Wrist Curl', '손목 컬', 'FOREARMS', 'STRENGTH', 3.0, false, NOW(), NOW()),
('Reverse Wrist Curl', '리버스 손목 컬', 'FOREARMS', 'STRENGTH', 3.0, false, NOW(), NOW()),
('Farmer Walk', '파머 워크', 'FOREARMS', 'STRENGTH', 5.0, false, NOW(), NOW())

ON CONFLICT DO NOTHING;
