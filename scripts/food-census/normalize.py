"""식품 카탈로그 정규화 로직의 Python 포팅.

프로덕션 자바 코드(`com.healthcare.domain.diet.*`)의 dedup 의미를 그대로 미러링한다.
이 하니스가 산출하는 중복 통계가 실제 적재 시 `FoodCatalogIngestService`/
`FoodCatalogDuplicateCandidateReporter`가 보게 될 중복과 일치하도록,
아래 3개 자바 클래스를 1:1로 옮긴다.

  - FoodCatalogImportText.normalize         (공백 정리)
  - FoodDisplayNameNormalizer.normalize     (MFDS 원본명 -> 자연어 표시명)
  - FoodCatalogIdentity.duplicateKey        (브랜드:이름 정규화 dedup 키)

파일 하단 self-test가 자바 javadoc에 명시된 예시를 그대로 검증한다.
실행: `python3 normalize.py`  (모든 케이스 통과 시 OK 출력)
"""

from __future__ import annotations

import re
import unicodedata

# --- FoodCatalogImportText.normalize -------------------------------------

# 자바 String.trim(): 코드포인트 <= U+0020 을 양끝에서 제거
_TRIM_CHARS = "".join(chr(c) for c in range(0x21))
# 자바 정규식 \s == [ \t\n\x0B\f\r] (유니코드 공백 아님). 명시적으로 동일 클래스 사용.
_JAVA_WS = r"[ \t\n\x0b\f\r]"
_WS_RUN = re.compile(_JAVA_WS + "+")


def normalize(value):
    """FoodCatalogImportText.normalize 와 동일: trim + 연속 공백 1칸 축약, 빈값은 None."""
    if value is None:
        return None
    stripped = value.strip(_TRIM_CHARS)
    collapsed = _WS_RUN.sub(" ", stripped)
    return None if collapsed.strip(_TRIM_CHARS) == "" else collapsed


def normalize_to_max_length(value, max_length):
    normalized = normalize(value)
    if normalized is None or len(normalized) <= max_length:
        return normalized
    return normalized[:max_length]


# --- FoodCatalogIdentity -------------------------------------------------

# 자바: "[\\s\\-_/()（）]" (전각 괄호 포함). \s 는 위 _JAVA_WS 로 고정.
_DEDUP_STRIP = re.compile(r"[ \t\n\x0b\f\r\-_/()（）]")


def normalize_display_name(value):
    """FoodCatalogIdentity.normalizeDisplayName: NFC + trim + 공백 축약."""
    if value is None:
        return None
    nfc = unicodedata.normalize("NFC", value)
    collapsed = _WS_RUN.sub(" ", nfc.strip(_TRIM_CHARS))
    return None if collapsed.strip(_TRIM_CHARS) == "" else collapsed


def duplicate_name_key(value):
    normalized = normalize_display_name(value)
    if normalized is None:
        return None
    return _DEDUP_STRIP.sub("", normalized.lower())


def duplicate_key(display_name, brand_name):
    """FoodCatalogIdentity.duplicateKey(food).

    display_name 은 적재 시 nameKo(= 정규화 표시명), brand_name 은 출처별 브랜드.
    """
    name_key = duplicate_name_key(display_name)
    brand_key = duplicate_name_key(brand_name)
    return name_key if brand_key is None else f"{brand_key}:{name_key}"


# --- FoodDisplayNameNormalizer ------------------------------------------

_SEGMENT_DELIMITER = "_"
_DROP_SEGMENTS = {"반"}
_CODE_SEGMENT = re.compile(r"^[A-Za-z0-9.&·\-]+$")
_SIZE_SEGMENT = re.compile(r"^(?:XS|S|M|L|XL|XXL)$", re.IGNORECASE)
_HO_SIZE_SEGMENT = re.compile(r"^[0-9]+호$")
_STORAGE_STATES = {"냉장", "냉동", "실온"}
_YEAR_WITH_STATE = re.compile(r"^[0-9]+\((냉장|냉동|실온)\)$")
_SET_MARKER = "세트"
_MODIFIER_MAX_LENGTH = 4


def display_name(raw_name):
    """FoodDisplayNameNormalizer.normalize(...).displayName().

    dup_key 계산에는 displayName 만 필요하므로 searchAlias 는 생략한다.
    """
    normalized = normalize(raw_name)
    if normalized is None:
        return raw_name
    if _SEGMENT_DELIMITER not in normalized:
        return normalized

    segments = _split_segments(normalized)
    content = []
    suffixes = []
    for seg in segments:
        state = _storage_state(seg)
        if state is not None:
            _add_unique(suffixes, state)
        elif _is_size(seg) or _is_ho_size(seg) or (_SET_MARKER in seg):
            _add_unique(suffixes, seg)
        elif _is_droppable(seg):
            pass  # 코드/포장단위는 표시명에서 제거
        else:
            content.append(seg)

    if len(segments) <= 1 and not suffixes:
        return _spaces_for_inner_delimiters(normalized)

    base = _build_base(content)
    display = _spaces_for_inner_delimiters(_append_suffixes(base, suffixes))
    if display.strip() == "":
        display = normalized.replace(_SEGMENT_DELIMITER, " ").strip()
    return display


def _split_segments(value):
    segments = []
    current = []
    depth = 0
    for c in value:
        if c == "(":
            depth += 1
        elif c == ")":
            depth = max(0, depth - 1)
        if c == _SEGMENT_DELIMITER and depth == 0:
            _add_trimmed(segments, "".join(current))
            current = []
        else:
            current.append(c)
    _add_trimmed(segments, "".join(current))
    return segments


def _add_trimmed(target, value):
    trimmed = value.strip()
    if trimmed:
        target.append(trimmed)


def _add_unique(target, value):
    if value not in target:
        target.append(value)


def _is_size(seg):
    return _SIZE_SEGMENT.match(seg) is not None


def _is_ho_size(seg):
    return _HO_SIZE_SEGMENT.match(seg) is not None


def _storage_state(seg):
    if seg in _STORAGE_STATES:
        return seg
    m = _YEAR_WITH_STATE.match(seg)
    return m.group(1) if m else None


def _is_droppable(seg):
    return seg in _DROP_SEGMENTS or _CODE_SEGMENT.match(seg) is not None


def _build_base(content):
    if not content:
        return ""
    if len(content) == 1:
        return content[0]

    category = content[0]
    rest = content[1:]

    repeated = next((seg for seg in rest if category in seg), None)
    if repeated is not None:
        return _prefix_modifiers(rest, repeated)

    product_name = next((seg for seg in rest if _is_product_name(seg)), None)
    if product_name is not None:
        return _prefix_modifiers(rest, product_name)

    if " " in category:
        ordered = list(content)
    else:
        ordered = list(rest)
        ordered.append(category)
    return _join_deduped(ordered)


def _prefix_modifiers(segments, base):
    ordered = [seg for seg in segments if seg != base]
    ordered.append(base)
    return _join_deduped(ordered)


def _is_product_name(seg):
    return len(seg) > _MODIFIER_MAX_LENGTH or " " in seg


def _join_deduped(pieces):
    spaced = any(" " in p for p in pieces)
    kept = []
    accumulated = ""
    for p in pieces:
        if p not in accumulated:
            kept.append(p)
            accumulated += p
    return (" " if spaced else "").join(kept)


def _append_suffixes(base, suffixes):
    return base + "".join(f"({s})" for s in suffixes)


def _spaces_for_inner_delimiters(value):
    if _SEGMENT_DELIMITER not in value:
        return value
    return value.replace(_SEGMENT_DELIMITER, " ").strip()


# --- self test -----------------------------------------------------------

def _self_test():
    cases = {
        "경단_깨": "깨경단",
        "국수_막국수": "막국수",
        "도넛_링도넛": "링도넛",
        "샌드위치_햄_치즈": "햄치즈샌드위치",
        "6곡단팥빵_밤": "밤6곡단팥빵",
        "닭튀김_마늘스태미나 치킨": "마늘스태미나 치킨",
        "냉동혼합야채(4종_볶음밥용)": "냉동혼합야채(4종 볶음밥용)",
        "블랙춘 초코 크레이프 케이크_춘식이": "블랙춘 초코 크레이프 케이크 춘식이",
    }
    failures = []
    for raw, expected in cases.items():
        got = display_name(raw)
        if got != expected:
            failures.append(f"  display_name({raw!r}) = {got!r}, expected {expected!r}")

    # duplicate_key: 브랜드 없으면 nameKey 만, 있으면 brandKey:nameKey
    dk_cases = [
        (("깨 경단", None), "깨경단"),
        (("깨경단", "  "), "깨경단"),
        (("아메리카노(L)", "스타벅스"), "스타벅스:아메리카노l"),
    ]
    for (disp, brand), expected in dk_cases:
        got = duplicate_key(disp, brand)
        if got != expected:
            failures.append(f"  duplicate_key({disp!r},{brand!r}) = {got!r}, expected {expected!r}")

    if failures:
        print("SELF-TEST FAILED:")
        print("\n".join(failures))
        raise SystemExit(1)
    print(f"normalize.py self-test OK ({len(cases) + len(dk_cases)} cases)")


if __name__ == "__main__":
    _self_test()
