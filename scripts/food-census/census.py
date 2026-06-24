#!/usr/bin/env python3
"""식품 공공 API 3종 전수 중복 프로파일링 하니스 (read-only).

food_catalog 에 적재하지 않고, 3개 API를 전수 페이징하며 행별 핑거프린트만
append-only TSV 로 남긴 뒤, 프로덕션 dedup 의미(normalize.py)를 그대로 적용해
6개 중복 지표를 산출한다.

  1) 출처별 food_code 자체 중복
  2) 출처 간 동일 food_code
  3) 동일 품목제조보고번호(itemMnftrRptNo)
  4) 이름+제조사(production dup_key) 일치
  5) 같은 코드인데 이름·영양값이 다른 충돌
  6) 합산 후 최종 고유 식품 예상 수

페이징은 체크포인트(.ckpt)로 재개 가능하다. 중단 후 같은 명령을 다시 실행하면
마지막 완료 페이지 다음부터 이어 받는다.

사용:
  PUBLIC_FOOD_API_KEY=... python3 census.py fetch --mode sample --sample-pages 3
  PUBLIC_FOOD_API_KEY=... python3 census.py fetch --mode full --delay 0.15
  python3 census.py profile --out ../../docs/references/FOOD_API_CENSUS_DEDUP_PROFILE.md
  python3 census.py run --mode sample            # fetch(sample)+profile 한 번에
"""

from __future__ import annotations

import argparse
import json
import os
import ssl
import sys
import time
import urllib.parse
import urllib.request
from collections import Counter
from datetime import datetime, timezone, timedelta

import normalize as N

HERE = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(HERE, "data")

# 출처 비트마스크
BIT = {"processed": 1, "dish": 2, "nutrient_db": 4}
SRC_LABEL = {
    "processed": "가공식품 표준데이터",
    "dish": "음식 표준데이터",
    "nutrient_db": "식품영양성분 DB",
}

# 전 출처 page_size=100. 표준데이터 API 는 numOfRows=1000 이면 페이지당 ~61s(타임아웃 직전,
# 행당 ~61ms)로 비정상적으로 느리다. 100 이면 ~1.2s(행당 ~12ms)로 처리량·안정성이 모두 우수.
# nutrient_db 는 numOfRows>100 이면 빈 응답이라 어차피 100 고정.
SOURCES = {
    "processed": {
        "url": "https://api.data.go.kr/openapi/tn_pubr_public_nutri_process_info_api",
        "envelope": "response.body",
        "page_size": 100,
    },
    "dish": {
        "url": "https://api.data.go.kr/openapi/tn_pubr_public_nutri_food_info_api",
        "envelope": "response.body",
        "page_size": 100,
    },
    "nutrient_db": {
        "url": "https://apis.data.go.kr/1471000/FoodNtrCpntDbInfo02/getFoodNtrCpntDbInq02",
        "envelope": "body",
        "page_size": 100,
    },
}

TSV_HEADER = "source\tfood_code\treport_no\taccepted\tdisplay_name\tbrand_name\tname_key\tdup_key\tnutri_sig"


# --- 공통 파서 -----------------------------------------------------------

def parse_double(value):
    n = N.normalize(value)
    if n is None:
        return None
    try:
        return float(n.replace(",", ""))
    except ValueError:
        return None


def sig_part(value):
    d = parse_double(value)
    return "" if d is None else f"{round(d, 1):g}"


def clean_field(value):
    """TSV 안전화: None->'' , 탭/개행 제거."""
    if value is None:
        return ""
    return str(value).replace("\t", " ").replace("\r", " ").replace("\n", " ")


def fingerprint(source, item):
    """API item(dict) -> 핑거프린트 dict. 프로덕션 임포터 매핑을 그대로 따른다."""
    if source == "nutrient_db":
        raw_code = item.get("FOOD_CD")
        raw_name = item.get("FOOD_NM_KR")
        kcal = item.get("AMT_NUM1")
        brand_raw = item.get("SELLER_MANUFAC_NM")
        report_no = None
        sig = "|".join(sig_part(item.get(k)) for k in ("AMT_NUM1", "AMT_NUM3", "AMT_NUM4", "AMT_NUM6"))
    else:  # processed / dish (StandardFood*)
        raw_code = item.get("foodCd")
        raw_name = item.get("foodNm")
        kcal = item.get("enerc")
        if source == "processed":
            brand_raw = None  # StandardProcessedFoodImporter: brandName 미오버라이드 -> null
            report_no = item.get("itemMnftrRptNo")
        else:  # dish: brandName = restNm
            brand_raw = item.get("restNm")
            report_no = None
        sig = "|".join(sig_part(item.get(k)) for k in ("enerc", "prot", "fatce", "chocdf"))

    code_norm = N.normalize(raw_code)
    accepted = (
        code_norm is not None
        and len(code_norm) <= 60
        and N.normalize(raw_name) is not None
        and parse_double(kcal) is not None
    )
    display = N.normalize_to_max_length(N.display_name(raw_name), 150)
    brand = N.normalize_to_max_length(brand_raw, 150)
    name_key = N.duplicate_name_key(display)
    dup_key = N.duplicate_key(display, brand)
    return {
        "source": source,
        "food_code": code_norm if code_norm is not None else clean_field(raw_code),
        "report_no": N.normalize(report_no),
        "accepted": 1 if accepted else 0,
        "display_name": display,
        "brand_name": brand,
        "name_key": name_key,
        "dup_key": dup_key,
        "nutri_sig": sig,
    }


def to_tsv(fp):
    return "\t".join([
        fp["source"],
        clean_field(fp["food_code"]),
        clean_field(fp["report_no"]),
        str(fp["accepted"]),
        clean_field(fp["display_name"]),
        clean_field(fp["brand_name"]),
        clean_field(fp["name_key"]),
        clean_field(fp["dup_key"]),
        clean_field(fp["nutri_sig"]),
    ])


# --- HTTP / 페이징 -------------------------------------------------------

# TLS 는 항상 검증한다. macOS python.org 빌드는 OS 키체인을 직접 쓰지 않으므로,
# curl 이 신뢰하는 동일한 시스템 루트를 PEM(macos-roots.pem)으로 export 해 신뢰 앵커에 더한다.
#   security find-certificate -a -p /System/Library/Keychains/SystemRootCertificates.keychain > macos-roots.pem
#   security find-certificate -a -p /Library/Keychains/System.keychain                       >> macos-roots.pem
# 환경변수 FOOD_CENSUS_CAFILE 로 다른 번들을 지정할 수 있다.
def _build_ssl_context():
    ctx = ssl.create_default_context()
    cafile = os.environ.get("FOOD_CENSUS_CAFILE") or os.path.join(HERE, "macos-roots.pem")
    if os.path.exists(cafile):
        ctx.load_verify_locations(cafile)
    return ctx


_SSL_CTX = _build_ssl_context()


def http_get_json(url, params, retries=8, timeout=40):
    """단발 GET. 일시적 DNS/네트워크 blip 을 견디도록 지수 백오프로 재시도(최대 ~2분)."""
    qs = urllib.parse.urlencode(params)
    full = f"{url}?{qs}"
    last_err = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(full, headers={"Accept": "application/json"})
            with urllib.request.urlopen(req, timeout=timeout, context=_SSL_CTX) as resp:
                raw = resp.read().decode("utf-8", errors="replace")
            return json.loads(raw)
        except Exception as e:  # noqa: BLE001 (네트워크/파싱 모두 재시도)
            last_err = e
            time.sleep(min(2 ** attempt, 30))
    raise RuntimeError(f"GET 실패({retries}회): {full[:120]}... :: {last_err}")


def extract_body(envelope, payload):
    node = payload or {}
    for key in envelope.split("."):
        node = (node or {}).get(key, {})
    return node or {}


def fetch_page(source, api_key, page_no):
    cfg = SOURCES[source]
    body = extract_body(cfg["envelope"], http_get_json(cfg["url"], {
        "serviceKey": api_key,
        "type": "json",
        "pageNo": page_no,
        "numOfRows": cfg["page_size"],
    }))
    items = body.get("items") or []
    try:
        total = int(str(body.get("totalCount") or "0").replace(",", ""))
    except ValueError:
        total = 0
    return items, total


def ckpt_path(source):
    return os.path.join(DATA_DIR, f"{source}.ckpt")


def tsv_path(source):
    return os.path.join(DATA_DIR, f"{source}.tsv")


def load_ckpt(source):
    try:
        with open(ckpt_path(source), encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return {"last_page": 0, "total_count": 0, "written": 0, "done": False}


def save_ckpt(source, ckpt):
    tmp = ckpt_path(source) + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(ckpt, f, ensure_ascii=False)
    os.replace(tmp, ckpt_path(source))


def fetch_source(source, api_key, mode, sample_pages, delay):
    os.makedirs(DATA_DIR, exist_ok=True)
    cfg = SOURCES[source]
    page_size = cfg["page_size"]
    ckpt = load_ckpt(source)
    if ckpt.get("done") and mode == "full":
        print(f"[{source}] 이미 완료(done) — {ckpt['written']}행, 건너뜀")
        return ckpt

    start_page = ckpt["last_page"] + 1
    # sample 모드는 깨끗한 표본을 위해 1페이지부터 새로 받는다.
    if mode == "sample":
        start_page = 1
        if os.path.exists(tsv_path(source)):
            os.remove(tsv_path(source))
        ckpt = {"last_page": 0, "total_count": 0, "written": 0, "done": False}

    written = ckpt["written"]
    fmode = "a" if (mode == "full" and start_page > 1) else "w"
    with open(tsv_path(source), fmode, encoding="utf-8") as out:
        if fmode == "w":
            out.write(TSV_HEADER + "\n")
        page = start_page
        pages_done = 0
        while True:
            items, total = fetch_page(source, api_key, page)
            if not items:
                ckpt["done"] = True
                break
            for it in items:
                out.write(to_tsv(fingerprint(source, it)) + "\n")
            written += len(items)
            out.flush()
            ckpt.update(last_page=page, total_count=total, written=written)
            save_ckpt(source, ckpt)
            pages_done += 1
            print(f"[{source}] page {page} (+{len(items)}) 누적 {written}/{total}", flush=True)

            if page * page_size >= total:
                ckpt["done"] = True
                break
            if mode == "sample" and pages_done >= sample_pages:
                break
            page += 1
            if delay:
                time.sleep(delay)
    save_ckpt(source, ckpt)
    state = "완료" if ckpt.get("done") else f"표본 {pages_done}p"
    print(f"[{source}] {state} — 총 {written}행 (totalCount={ckpt['total_count']})")
    return ckpt


# --- 프로파일링 ----------------------------------------------------------

def popcount(x):
    return bin(x).count("1")


def src_combo_label(mask):
    return " + ".join(SRC_LABEL[s] for s, b in BIT.items() if mask & b)


def profile(out_path):
    sources = [s for s in SOURCES if os.path.exists(tsv_path(s))]
    if not sources:
        print("프로파일링할 TSV가 없습니다. 먼저 fetch 를 실행하세요.")
        return

    raw_rows = Counter()
    accepted_rows = Counter()
    code_by_source = {s: Counter() for s in SOURCES}     # 출처별 code count (accepted)
    code_global = {}   # code -> [count, srcmask, name0, name_diff, sig0, sig_diff]
    report_global = {}  # report_no -> [count, srcmask]
    dupkey_global = {}  # dup_key -> [count, srcmask, set(codes)]

    for s in sources:
        bit = BIT[s]
        with open(tsv_path(s), encoding="utf-8") as f:
            next(f)  # header
            for line in f:
                parts = line.rstrip("\n").split("\t")
                if len(parts) < 9:
                    continue
                (src, code, report_no, accepted, _disp, _brand, name_key, dup_key, sig) = parts[:9]
                raw_rows[src] += 1
                if accepted != "1":
                    continue
                accepted_rows[src] += 1
                code_by_source[src][code] += 1

                ent = code_global.get(code)
                if ent is None:
                    code_global[code] = [1, bit, name_key, False, sig, False]
                else:
                    ent[0] += 1
                    ent[1] |= bit
                    if name_key and name_key != ent[2]:
                        ent[3] = True
                    if sig and sig != ent[4]:
                        ent[5] = True

                if report_no:
                    r = report_global.get(report_no)
                    if r is None:
                        report_global[report_no] = [1, bit]
                    else:
                        r[0] += 1
                        r[1] |= bit

                if dup_key:
                    d = dupkey_global.get(dup_key)
                    if d is None:
                        dupkey_global[dup_key] = [1, bit, {code}]
                    else:
                        d[0] += 1
                        d[1] |= bit
                        d[2].add(code)

    report = build_report(sources, raw_rows, accepted_rows, code_by_source,
                          code_global, report_global, dupkey_global)
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(report)
    print(report)
    print(f"\n리포트 저장: {out_path}")


def build_report(sources, raw_rows, accepted_rows, code_by_source,
                 code_global, report_global, dupkey_global):
    total_raw = sum(raw_rows.values())
    total_acc = sum(accepted_rows.values())
    is_sample = total_raw < 100_000  # 표본/전수 구분 표기용

    L = []
    kst = timezone(timedelta(hours=9))
    L.append("# 식품 API 3종 중복 프로파일 (read-only census)\n")
    L.append(f"- 생성: {datetime.now(kst).strftime('%Y-%m-%d %H:%M KST')}")
    L.append(f"- 모드: {'표본(SAMPLE)' if is_sample else '전수(FULL)'} — raw {total_raw:,}행, 수용 {total_acc:,}행")
    L.append(f"- 출처: {', '.join(SRC_LABEL[s] for s in sources)}")
    L.append("- 수용행 = food_code(≤60)·이름·칼로리 모두 존재(프로덕션 적재 조건). 지표는 수용행 기준.\n")

    # 적재 규모
    L.append("## 0. 적재 규모\n")
    L.append("| 출처 | raw 행 | 수용 행 | 스킵 행 | 스킵율 |")
    L.append("|---|---:|---:|---:|---:|")
    for s in sources:
        raw = raw_rows[s]
        acc = accepted_rows[s]
        skip = raw - acc
        L.append(f"| {SRC_LABEL[s]} | {raw:,} | {acc:,} | {skip:,} | {(skip / raw * 100 if raw else 0):.1f}% |")
    L.append(f"| **합계** | **{total_raw:,}** | **{total_acc:,}** | **{total_raw - total_acc:,}** | "
             f"**{((total_raw - total_acc) / total_raw * 100 if total_raw else 0):.1f}%** |\n")

    # 1. 출처별 자체 중복
    L.append("## 1. 출처별 food_code 자체 중복\n")
    L.append("| 출처 | 고유 코드 | 중복 코드(≥2) | 잉여 행 | 자체중복율 |")
    L.append("|---|---:|---:|---:|---:|")
    for s in sources:
        c = code_by_source[s]
        uniq = len(c)
        dup_codes = sum(1 for v in c.values() if v >= 2)
        redundant = sum(v - 1 for v in c.values() if v >= 2)
        acc = accepted_rows[s]
        L.append(f"| {SRC_LABEL[s]} | {uniq:,} | {dup_codes:,} | {redundant:,} | "
                 f"{(redundant / acc * 100 if acc else 0):.1f}% |")
    L.append("")

    # 2. 출처 간 동일 코드
    cross = [(code, ent) for code, ent in code_global.items() if popcount(ent[1]) >= 2]
    combo = Counter(src_combo_label(ent[1]) for _, ent in cross)
    L.append("## 2. 출처 간 동일 food_code\n")
    L.append(f"- 둘 이상 출처에 등장하는 food_code: **{len(cross):,}개**")
    L.append(f"- 전역 고유 food_code: **{len(code_global):,}개**\n")
    if combo:
        L.append("| 출처 조합 | 공유 코드 수 |")
        L.append("|---|---:|")
        for label, n in combo.most_common():
            L.append(f"| {label} | {n:,} |")
        L.append("")

    # 3. 품목제조보고번호
    rep_dup = {k: v for k, v in report_global.items() if v[0] >= 2}
    rep_redundant = sum(v[0] - 1 for v in rep_dup.values())
    L.append("## 3. 동일 품목제조보고번호 (itemMnftrRptNo)\n")
    L.append(f"- 보고번호 보유 행의 고유 번호: **{len(report_global):,}개**")
    L.append(f"- 중복 번호(≥2): **{len(rep_dup):,}개**, 잉여 행 **{rep_redundant:,}**")
    L.append("- (보고번호는 가공식품 표준데이터에만 존재)\n")

    # 4. 이름+제조사(production dup_key)
    dk_dup = {k: v for k, v in dupkey_global.items() if v[0] >= 2}
    dk_cross = sum(1 for v in dk_dup.values() if popcount(v[1]) >= 2)
    dk_within = len(dk_dup) - dk_cross
    dk_redundant = sum(v[0] - 1 for v in dk_dup.values())
    L.append("## 4. 이름+제조사 일치 (production dup_key)\n")
    L.append("- dup_key = `브랜드정규화:이름정규화` (가공=브랜드없음→이름만, 음식=식당명, 영양DB=판매원).")
    L.append("- **검토 후보**(자동 병합 아님): 동명이품 가능성 포함.\n")
    L.append(f"- 중복 그룹(≥2): **{len(dk_dup):,}개** (잉여 행 {dk_redundant:,})")
    L.append(f"  - 출처 간 교차 그룹: **{dk_cross:,}개**")
    L.append(f"  - 출처 내 그룹: **{dk_within:,}개**\n")

    # 5. 같은 코드 다른 내용 충돌
    coll = [(code, ent) for code, ent in code_global.items() if ent[0] >= 2 and (ent[3] or ent[5])]
    coll_name = sum(1 for _, e in coll if e[3])
    coll_sig = sum(1 for _, e in coll if e[5])
    coll_within = sum(1 for _, e in coll if popcount(e[1]) == 1)
    coll_cross = len(coll) - coll_within
    L.append("## 5. 같은 코드인데 이름·영양값이 다른 충돌\n")
    L.append(f"- 충돌 코드(이름 또는 영양값 불일치): **{len(coll):,}개**")
    L.append(f"  - 이름 불일치: {coll_name:,} / 영양값 불일치: {coll_sig:,}")
    L.append(f"  - 출처 내 충돌: {coll_within:,} (데이터 품질 이슈) / 출처 간 충돌: {coll_cross:,} (코드공간 중복)\n")

    # 6. 최종 고유 수
    R = total_acc
    D = len(code_global)
    exact_code_redundant = R - D
    name_merge_extra = sum(len(v[2]) - 1 for v in dupkey_global.values() if len(v[2]) >= 2)
    u_conservative = D
    u_aggressive = D - name_merge_extra
    L.append("## 6. 합산 후 최종 고유 식품 예상 수\n")
    L.append(f"- 수용 행(R): **{R:,}**")
    L.append(f"- exact food_code 병합 후(보수적, U₁): **{u_conservative:,}**  (잉여 {exact_code_redundant:,} 제거)")
    L.append(f"- + 이름·브랜드(dup_key) 병합 후(적극적, U₂): **{u_aggressive:,}**  (추가 {name_merge_extra:,} 검토 후 제거 가정)")
    L.append(f"- **추정 고유 수 범위: {u_aggressive:,} ~ {u_conservative:,}**")
    L.append(f"  - 단, 충돌 코드 {len(coll):,}개는 코드 병합 시 서로 다른 품목을 합칠 위험이 있어 수기 분리 필요"
             f" → 실제 고유 수는 U₁보다 다소 높을 수 있음.\n")

    if is_sample:
        L.append("> ⚠️ 표본 모드 결과입니다. 전수 확정치는 `fetch --mode full` 후 재프로파일하세요.\n")
    return "\n".join(L)


# --- canonical 적재 projection (전량 적재 검증 게이트) -------------------

# 출처 → resolver dedupPriority (FoodCatalogSource.dedupPriority). 높을수록 대표 채택.
# census 는 공공 3종만 다루므로 processed > nutrient_db > dish.
SRC_DEDUP_PRIORITY = {"processed": 300, "nutrient_db": 200, "dish": 100}


def project_canonical(out_path):
    """캡처된 census TSV에 프로덕션 dedup 의미(CanonicalDedupResolver)를 그대로 재생해
    전량 적재 시 기대 canonical/superseded/collision 수를 정확히 산출한다(설계 §8 통합 검증).

    profile() 의 '고유 수 범위'는 food_code 단독 병합(U₁)이라 resolver 의 실제 클러스터
    키((food_code, name_key))를 반영하지 못한다. 이 함수는 적재 경로를 2단계로 재현한다:

      1) (source, food_code) upsert 병합 — FoodCatalogIngestService.findBySourceAndFoodCode.
         가공식품 자체 2× 중복(census 지표 1)을 여기서 흡수해 출처당 코드 1행만 남긴다.
      2) (food_code, name_key) canonical 클러스터링 — CanonicalDedupResolver.
         클러스터당 대표 1개, 나머지는 superseded. 같은 코드·다른 name_key 는 COLLISION.

    canonical 수 = 적재 후 검색·추천에 노출되는 행 수 = ServingOption 생성 대상 행 수.
    이 수치가 전량 적재의 수용 기준(acceptance target)이다.
    """
    sources = [s for s in SOURCES if os.path.exists(tsv_path(s))]
    if not sources:
        print("projection 할 TSV가 없습니다. 먼저 fetch 를 실행하세요.")
        return

    # 1단계: (source, food_code) upsert 병합. value = name_key (last-wins, 사실 갱신 모사).
    source_code_namekey = {}
    accepted_raw = Counter()
    for s in sources:
        with open(tsv_path(s), encoding="utf-8") as f:
            next(f)  # header
            for line in f:
                parts = line.rstrip("\n").split("\t")
                if len(parts) < 9:
                    continue
                (src, code, _report, accepted, _disp, _brand, name_key, _dup, _sig) = parts[:9]
                if accepted != "1":
                    continue
                accepted_raw[src] += 1
                source_code_namekey[(src, code)] = name_key

    # 2단계: 출처-코드 행 위에서 (code, name_key) canonical 클러스터링.
    cluster_mask = {}        # (code, name_key) -> 출처 비트마스크
    code_namekeys = {}       # code -> {name_key} (비공백만) — 충돌 판정용
    rows_landed = Counter()  # 출처별 적재 행(=고유 source-code) 수
    clusterable = 0          # name_key 보유 source-code 행 수
    standalone = 0           # 코드 보유·name_key 공백 → 독립 대표(resolver 의 null name_key 분기)
    for (src, code), name_key in source_code_namekey.items():
        rows_landed[src] += 1
        if not name_key:
            standalone += 1
            continue
        clusterable += 1
        key = (code, name_key)
        cluster_mask[key] = cluster_mask.get(key, 0) | BIT[src]
        code_namekeys.setdefault(code, set()).add(name_key)

    distinct_clusters = len(cluster_mask)
    canonical = distinct_clusters + standalone
    superseded = clusterable - distinct_clusters
    total_landed = sum(rows_landed.values())

    # 클러스터별 대표 출처(최고 우선순위) 분포.
    winner_by_source = Counter()
    for mask in cluster_mask.values():
        winner = max((s for s in BIT if mask & BIT[s]), key=lambda s: SRC_DEDUP_PRIORITY[s])
        winner_by_source[winner] += 1

    # 충돌: 같은 코드에 name_key 가 2개 이상 → 각 클러스터가 각자 대표 + COLLISION 표시.
    # (resolver 의 COLLISION 은 name_key 차이 기준. 영양값만 다르고 이름 같은 건은 같은 클러스터로 병합되어
    #  COLLISION 이 아니다 — census 지표 5 의 3,961 중 '이름 불일치' 부분집합만 해당.)
    collision_codes = sum(1 for nks in code_namekeys.values() if len(nks) >= 2)
    collision_canon_rows = sum(len(nks) for nks in code_namekeys.values() if len(nks) >= 2)

    # 음식(dish) ⊂ 상위 출처 흡수: dish 를 포함한 클러스터 중 상위 출처(processed/nutrient_db)가 함께 있으면 흡수.
    dish_clusters = sum(1 for mask in cluster_mask.values() if mask & BIT["dish"])
    dish_absorbed = sum(
        1 for mask in cluster_mask.values()
        if (mask & BIT["dish"]) and (mask & (BIT["processed"] | BIT["nutrient_db"]))
    )
    dish_standalone = dish_clusters - dish_absorbed

    report = build_projection_report(
        sources, accepted_raw, rows_landed, total_landed,
        clusterable, standalone, distinct_clusters, canonical, superseded,
        winner_by_source, collision_codes, collision_canon_rows,
        dish_clusters, dish_absorbed, dish_standalone)
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(report)
    print(report)
    print(f"\n리포트 저장: {out_path}")


def build_projection_report(sources, accepted_raw, rows_landed, total_landed,
                            clusterable, standalone, distinct_clusters, canonical,
                            superseded, winner_by_source, collision_codes,
                            collision_canon_rows, dish_clusters, dish_absorbed,
                            dish_standalone):
    total_acc = sum(accepted_raw.values())
    is_sample = total_acc < 100_000
    dedup_pct = (superseded / total_landed * 100) if total_landed else 0
    kst = timezone(timedelta(hours=9))

    L = []
    L.append("# 식품 카탈로그 dedup 전량 적재 projection (acceptance target)\n")
    L.append(f"- 생성: {datetime.now(kst).strftime('%Y-%m-%d %H:%M KST')}")
    L.append(f"- 모드: {'표본(SAMPLE)' if is_sample else '전수(FULL)'} — 수용 raw {total_acc:,}행")
    L.append("- 산출: 캡처 census TSV에 `CanonicalDedupResolver` 의미를 재생(read-only, DB 미적재).")
    L.append("- 2단계 재현: ① (source, food_code) upsert 병합 → ② (food_code, name_key) canonical 클러스터링.\n")

    L.append("## 적재 후 기대 규모 (전량 적재 수용 기준)\n")
    L.append("| 지표 | 값 | 의미 |")
    L.append("|---|---:|---|")
    L.append(f"| 적재 행(food_catalog) | **{total_landed:,}** | (source, food_code) 병합 후 — 자체 2× 중복 흡수됨 |")
    L.append(f"| canonical(대표) | **{canonical:,}** | 검색·추천 노출 + ServingOption 생성 대상 |")
    L.append(f"| superseded(패자) | **{superseded:,}** | 숨김·옵션 미생성 (옵션 폭증 차단) |")
    L.append(f"| COLLISION 코드 | **{collision_codes:,}** | 같은 코드·다른 이름 → 검토 큐 |")
    L.append(f"| dedup 제거율 | **{dedup_pct:.1f}%** | superseded / 적재 행 |\n")

    L.append("## 1. (source, food_code) 병합 — 적재 행 수\n")
    L.append("가공식품 자체 2× 중복(census 지표 1)은 `findBySourceAndFoodCode` upsert로 흡수되어 출처당 코드 1행만 적재된다.\n")
    L.append("| 출처 | 수용 raw 행 | 적재 행(고유 코드) | 자체중복 흡수 |")
    L.append("|---|---:|---:|---:|")
    for s in sources:
        raw = accepted_raw[s]
        landed = rows_landed[s]
        L.append(f"| {SRC_LABEL[s]} | {raw:,} | {landed:,} | {raw - landed:,} |")
    L.append(f"| **합계** | **{sum(accepted_raw.values()):,}** | **{total_landed:,}** | **{sum(accepted_raw.values()) - total_landed:,}** |\n")

    L.append("## 2. canonical 클러스터링 — 출처 간 dedup\n")
    L.append(f"- 클러스터링 대상(name_key 보유): **{clusterable:,}**행")
    L.append(f"- 독립 대표(name_key 공백): **{standalone:,}**행")
    L.append(f"- 고유 (code, name_key) 클러스터: **{distinct_clusters:,}**")
    L.append(f"- → canonical 총계 = 클러스터 + 독립 = **{canonical:,}**")
    L.append(f"- → superseded = 클러스터링 대상 − 클러스터 = **{superseded:,}**\n")
    L.append("### 대표(canonical) 출처 분포\n")
    L.append("| 대표 출처 | 클러스터 수 |")
    L.append("|---|---:|")
    for s in sorted(winner_by_source, key=lambda x: -SRC_DEDUP_PRIORITY[x]):
        L.append(f"| {SRC_LABEL[s]} (우선순위 {SRC_DEDUP_PRIORITY[s]}) | {winner_by_source[s]:,} |")
    L.append("")

    L.append("## 3. 음식(dish) ⊂ 상위 출처 흡수\n")
    L.append(f"- dish 포함 클러스터: **{dish_clusters:,}**")
    L.append(f"  - 상위 출처(가공/영양DB)에 흡수(superseded): **{dish_absorbed:,}**")
    L.append(f"  - dish 가 대표로 남음(상위 출처 없음·name_key 불일치): **{dish_standalone:,}**\n")
    L.append("> census 결론 2(음식 코드 100% 가 영양DB와 겹침)는 코드 기준. 여기서는 (code, name_key) 기준이라"
             " 이름 불일치분은 dish 가 별도 대표(COLLISION)로 남을 수 있다.\n")

    L.append("## 4. COLLISION (검토 큐)\n")
    L.append(f"- 같은 코드·다른 name_key 코드: **{collision_codes:,}개**")
    L.append(f"- COLLISION 표시 canonical 행: **{collision_canon_rows:,}**")
    L.append("- resolver COLLISION 은 **이름 차이** 기준이다. census 지표 5의 충돌(3,961) 중 '영양값만 다르고 이름 같음'"
             " (1,360)은 같은 클러스터로 병합(우선순위 출처 영양값 채택)되어 COLLISION 이 아니다.\n")

    L.append("## 5. 전량 적재 게이트 체크리스트\n")
    L.append("- [x] 검색 `searchAll` 패자 제외: `isCustom OR canonicalGroupId IS NULL`")
    L.append("- [x] 추천 후보 `isCanonicalCandidate()`: `canonicalGroupId IS NULL`")
    L.append("- [x] 적재 시 옵션 게이트: 패자 행 옵션 삭제, 대표만 생성 (`syncServingOptions`)")
    L.append("- [x] 강등 구대표 옵션 정리: `deleteOptionsForSupersededRows()` (백필 최종 패스)")
    L.append("- [x] 적재 순서 무관 수렴: `CanonicalDedupResolver` 우선순위 승격/강등 (단위 테스트)")
    L.append(f"- [ ] 전량 적재 실행 → 결과가 위 기대 규모(canonical≈{canonical:,}, superseded≈{superseded:,})와 일치 확인")
    L.append("- [ ] 적재 후 ServingOption 행 수 ≤ canonical × 제공량 옵션 상한 확인 (옵션 폭증 0)\n")

    if is_sample:
        L.append("> ⚠️ 표본 모드 결과입니다. 전수 확정치는 `fetch --mode full` 후 `project` 재실행하세요.\n")
    return "\n".join(L)


# --- CLI -----------------------------------------------------------------

def resolve_api_key(cli_key):
    if cli_key:
        return cli_key
    env = os.environ.get("PUBLIC_FOOD_API_KEY")
    if env:
        return env
    # 최후: application-local.yml 에서 읽기
    yml = os.path.normpath(os.path.join(HERE, "..", "..", "backend", "src", "main", "resources", "application-local.yml"))
    try:
        with open(yml, encoding="utf-8") as f:
            for line in f:
                if "public-api-key:" in line:
                    return line.split(":", 1)[1].strip().strip('"').strip("'")
    except FileNotFoundError:
        pass
    return None


def cmd_fetch(args):
    api_key = resolve_api_key(args.api_key)
    if not api_key:
        sys.exit("API 키 없음: PUBLIC_FOOD_API_KEY 환경변수 또는 --api-key 지정")
    targets = list(SOURCES) if args.source == "all" else [args.source]
    for s in targets:
        fetch_source(s, api_key, args.mode, args.sample_pages, args.delay)


def cmd_profile(args):
    profile(args.out)


def cmd_run(args):
    cmd_fetch(args)
    cmd_profile(args)


def cmd_project(args):
    project_canonical(args.out)


def main():
    default_out = os.path.normpath(os.path.join(
        HERE, "..", "..", "docs", "references", "FOOD_API_CENSUS_DEDUP_PROFILE.md"))
    default_projection_out = os.path.normpath(os.path.join(
        HERE, "..", "..", "docs", "references", "FOOD_CATALOG_DEDUP_LOAD_PROJECTION.md"))
    p = argparse.ArgumentParser(description="식품 API 3종 전수 중복 프로파일링")
    sub = p.add_subparsers(dest="cmd", required=True)

    def add_common(sp, out_default=default_out):
        sp.add_argument("--source", choices=list(SOURCES) + ["all"], default="all")
        sp.add_argument("--mode", choices=["sample", "full"], default="sample")
        sp.add_argument("--sample-pages", type=int, default=3)
        sp.add_argument("--delay", type=float, default=0.1)
        sp.add_argument("--api-key", default=None)
        sp.add_argument("--out", default=out_default)

    for name in ("fetch", "profile", "run"):
        sp = sub.add_parser(name)
        add_common(sp)

    # project: 캡처 TSV에 resolver 의미를 재생해 전량 적재 acceptance target 산출(read-only).
    add_common(sub.add_parser("project"), out_default=default_projection_out)

    args = p.parse_args()
    {"fetch": cmd_fetch, "profile": cmd_profile,
     "run": cmd_run, "project": cmd_project}[args.cmd](args)


if __name__ == "__main__":
    main()
