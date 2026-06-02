#!/usr/bin/env bash
set -euo pipefail

# Mac의 현재 LAN IP를 감지해서 Debug.xcconfig의 LOCAL_IP를 갱신한다.
# 사용처: 실기기 빌드 전 Wi-Fi가 바뀌었을 때 수동 실행
#   ./ios/Scripts/update-local-ip.sh
#
# 대역 우선순위: 172.30 (KT) → 192.168 → 10.x

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
XCCONFIG="$SCRIPT_DIR/../Configs/Debug.xcconfig"

if [ ! -f "$XCCONFIG" ]; then
  echo "ERROR: $XCCONFIG not found" >&2
  exit 1
fi

# 활성 인터페이스 중 LAN 대역 IP 추출
for prefix in "172.30" "192.168" "10."; do
  IP=$(ifconfig | awk -v p="$prefix" '/^[a-z]/{iface=$1} /inet /{if($2 ~ "^"p) {print $2; exit}}')
  [ -n "${IP:-}" ] && break
done

if [ -z "${IP:-}" ]; then
  echo "ERROR: LAN IP not found (Wi-Fi 연결 확인)" >&2
  exit 1
fi

CURRENT=$(grep -E "^LOCAL_IP" "$XCCONFIG" | awk -F'= *' '{print $2}' | tr -d ' ')
if [ "$CURRENT" = "$IP" ]; then
  echo "LOCAL_IP already $IP — no change"
  exit 0
fi

# 백업 + 치환
sed -i.bak -E "s/^LOCAL_IP = .*/LOCAL_IP = $IP/" "$XCCONFIG"
rm -f "$XCCONFIG.bak"
echo "LOCAL_IP: $CURRENT → $IP"
