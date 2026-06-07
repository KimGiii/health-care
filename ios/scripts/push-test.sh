#!/usr/bin/env bash
#
# push-test.sh — 시뮬레이터에 테스트 푸시 알림 주입
#
# 시뮬레이터는 FCM 푸시를 받지 못하므로, 백엔드 흐름과 무관하게 iOS 클라이언트의
# 알림 탭 → 화면 라우팅을 검증하기 위한 도구.
#
# 사용:
#   ios/scripts/push-test.sh                       # 기본: WEEKLY_SUMMARY
#   ios/scripts/push-test.sh path/to/custom.apns   # 커스텀 페이로드
#
# 사전 준비:
#   1. 시뮬레이터 실행 + 앱 설치 후 한 번 실행 (APNs 등록 트리거)
#   2. 알림 권한 허용
#
# 동작:
#   - 부팅된 시뮬레이터에 .apns 파일 주입
#   - 시뮬레이터 화면 상단에 알림 표시 → 알림 탭 시 AppDelegate.didReceive 호출

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
DEFAULT_APNS="$SCRIPT_DIR/push-test-weekly.apns"
APNS_FILE="${1:-$DEFAULT_APNS}"
BUNDLE_ID="com.kingloo.gainsy.ios"

if [ ! -f "$APNS_FILE" ]; then
  echo "ERROR: .apns 파일을 찾을 수 없습니다: $APNS_FILE" >&2
  exit 1
fi

# 부팅된 시뮬레이터 확인
BOOTED=$(xcrun simctl list devices booted 2>/dev/null | grep -E "Booted" | head -1 || true)
if [ -z "$BOOTED" ]; then
  echo "ERROR: 부팅된 시뮬레이터가 없습니다. Xcode에서 앱을 한 번 실행하세요." >&2
  exit 1
fi

echo "→ 시뮬레이터에 푸시 주입"
echo "  대상: $BUNDLE_ID"
echo "  파일: $APNS_FILE"
echo "  부팅된 디바이스: $BOOTED"
echo ""

xcrun simctl push booted "$BUNDLE_ID" "$APNS_FILE"

echo ""
echo "✓ 푸시 전송 완료. 시뮬레이터 상단에 알림이 표시됩니다."
echo "  알림을 탭하면 Xcode 콘솔에 [AppDelegate] didReceive 로그가 찍혀야 합니다."
