#!/usr/bin/env bash
#
# design-lint.sh — DesignSystem 토큰 우회 차단
#
# 검사:
#   1. .padding(... , <num>)            → Spacing.{xs|sm|md|lg|xl|xxl|xxxl}
#   2. cornerRadius: <num>              → Radius.{sm|md|lg|xl}
#   3. .font(.system(size: <num>...))   → Font.{display*|heading*|numeral*|body*|caption|eyebrow}
#
# 예외: 줄 끝에 `// design-lint:ignore` 주석을 달면 해당 줄은 통과한다.
#
# 사용:
#   ios/scripts/design-lint.sh
#   ios/scripts/design-lint.sh --staged

set -o pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
IOS_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
FEATURES_DIR="$IOS_ROOT/HealthCare/Features"

FILES=()
if [ "${1:-}" = "--staged" ]; then
  while IFS= read -r line; do
    FILES+=("$line")
  done < <(git diff --cached --name-only --diff-filter=ACM \
    | grep -E '^ios/HealthCare/Features/.*\.swift$' || true)
else
  while IFS= read -r line; do
    FILES+=("$line")
  done < <(find "$FEATURES_DIR" -name "*.swift" -type f)
fi

if [ "${#FILES[@]}" -eq 0 ]; then
  echo "[design-lint] 대상 파일 없음."
  exit 0
fi

VIOLATIONS=0

check_pattern() {
  local label="$1" hint="$2" pattern="$3"
  for f in "${FILES[@]}"; do
    [ -f "$f" ] || continue
    while IFS=: read -r line content; do
      echo "[$label] $f:$line"
      echo "        → ${content## }"
      echo "        $hint"
      VIOLATIONS=$((VIOLATIONS + 1))
    done < <(grep -nE "$pattern" "$f" 2>/dev/null \
      | grep -vE 'design-lint:ignore')
  done
}

# 1. padding 매직 넘버 (Spacing.* 호출이면 통과)
for f in "${FILES[@]}"; do
  [ -f "$f" ] || continue
  while IFS=: read -r line content; do
    # Spacing 토큰을 쓰면 통과
    echo "$content" | grep -qE 'Spacing\.' && continue
    echo "[PADDING] $f:$line"
    echo "          → ${content## }"
    echo "          → Spacing.{xs|sm|md|lg|xl|xxl|xxxl} 사용"
    VIOLATIONS=$((VIOLATIONS + 1))
  done < <(grep -nE '\.padding\([^)]*[0-9]+[^)]*\)' "$f" 2>/dev/null \
    | grep -vE 'design-lint:ignore')
done

# 2. cornerRadius 매직 넘버
check_pattern "RADIUS" "→ Radius.{sm|md|lg|xl} 사용" \
  'cornerRadius: ?[0-9]+'

# 3. 인라인 .font(.system(size:))
check_pattern "FONT" "→ Font.{display*|heading*|numeral*|body*|caption|eyebrow} 토큰 사용" \
  '\.font\(\.system\(size: ?[0-9]+'

echo ""
if [ "$VIOLATIONS" -eq 0 ]; then
  echo "[design-lint] ✅ 통과 — 디자인 토큰 우회 없음."
  exit 0
else
  echo "[design-lint] ❌ 위반 $VIOLATIONS 건. 토큰을 사용하거나, 정당한 사유가 있으면 줄 끝에 // design-lint:ignore 추가."
  exit 1
fi
