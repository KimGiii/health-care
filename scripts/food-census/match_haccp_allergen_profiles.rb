#!/usr/bin/env ruby
# frozen_string_literal: true

require "csv"
require "fileutils"

DATE = ENV.fetch("CURATION_DATE", "2026-06-30")
CANDIDATE_CSV = ARGV[0] || "docs/references/food_catalog_brand_allergen_gap_engine_ready_#{DATE}.csv"
HACCP_CSV = ARGV[1] || "docs/references/HACCP_ALLERGEN_PRODUCTS_2026-06-20.csv"
OUT_DIR = ARGV[2] || "docs/references"
BATCH_LIMIT = Integer(ENV.fetch("BATCH_LIMIT", "300"))
BATCH_FOCUS = ENV.fetch("BATCH_FOCUS", "ENGINE_RESCUE")

MATCH_CSV = File.join(OUT_DIR, "brand_allergen_profile_haccp_full_match_candidates_#{DATE}.csv")
CURATION_CSV = File.join(OUT_DIR, "brand_allergen_profile_curated_batch_full_#{DATE}.csv")
SUMMARY_MD = File.join(OUT_DIR, "BRAND_ALLERGEN_PROFILE_HACCP_FULL_MATCH_#{DATE}.md")

ALLERGEN_ORDER = [
  "난류",
  "우유",
  "메밀",
  "땅콩",
  "대두",
  "밀",
  "고등어",
  "게",
  "새우",
  "잣",
  "돼지고기",
  "복숭아",
  "토마토",
  "아황산류",
  "호두",
  "닭고기",
  "쇠고기",
  "오징어",
  "조개류"
].freeze

ALLERGEN_PATTERNS = {
  "난류" => /난류|계란|달걀|알류/,
  "우유" => /우유/,
  "메밀" => /메밀/,
  "땅콩" => /땅콩/,
  "대두" => /대두|콩/,
  "밀" => /(?:^|[,\/\s])밀(?:\s*함유|[,\/\s]|$)/,
  "고등어" => /고등어/,
  "게" => /(?:^|[,\/\s])게(?:\s*함유|[,\/\s]|$)/,
  "새우" => /새우/,
  "잣" => /잣/,
  "돼지고기" => /돼지고기/,
  "복숭아" => /복숭아/,
  "토마토" => /토마토/,
  "아황산류" => /아황산류|아황산/,
  "호두" => /호두/,
  "닭고기" => /닭고기/,
  "쇠고기" => /쇠고기|소고기/,
  "오징어" => /오징어/,
  "조개류" => /조개류|굴|전복|홍합/
}.freeze

ADDRESS_MARKER = /
  \s+
  (서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주|충청|전라|경상)
  .*
/x

COMPANY_STOPWORDS = [
  "알수없음",
  "해당없음",
  "해당무",
  "없음",
  "무",
  "기타"
].freeze

HIGH_PROTEIN_NAME_PATTERN = /닭가슴|참치|튜나|연어|계란|달걀|흰자|프로틴|단백|두부|콩|소고기|쇠고기|돼지|햄|육포/
ENGINE_RESCUE_BLOCK_TAGS = %w[우유 새우 밀 메밀 땅콩 게 조개류].freeze

def nfkc(value)
  text = value.to_s
  text.respond_to?(:unicode_normalize) ? text.unicode_normalize(:nfkc) : text
end

def normalize_name(value)
  nfkc(value)
    .downcase
    .gsub("\uFEFF", "")
    .gsub(/[[:space:][:punct:]·ㆍ・]/, "")
end

def clean_company_token(value)
  token = nfkc(value).downcase.strip
  token = token.sub(ADDRESS_MARKER, "")
  token = token.gsub(/\([^)]*\)/, "")
  token = token.gsub(/㈜|\(주\)|주식회사|유한회사|농업회사법인|영농조합법인|영어조합법인|재단법인|사단법인|학교법인|의료법인|사회복지법인|합자회사|합명회사|법인|회사/, "")
  token = token.gsub(/제?\d+\s*공장|공장|본점|지점/, "")
  token = token.gsub(/[[:space:][:punct:]·ㆍ・"“”‘’]/, "")
  return nil if token.empty?
  return nil if COMPANY_STOPWORDS.include?(token)
  return nil if token.length < 2

  token
end

def company_tokens(*values)
  values
    .compact
    .flat_map { |value| value.to_s.split(/[\/,;|]/) }
    .map { |part| clean_company_token(part) }
    .compact
    .uniq
end

def company_agreement?(candidate_tokens, haccp_tokens)
  candidate_tokens.any? do |candidate|
    haccp_tokens.any? do |haccp|
      candidate == haccp ||
        (candidate.length >= 4 && haccp.length >= 4 && (candidate.include?(haccp) || haccp.include?(candidate)))
    end
  end
end

def allergen_tags(value)
  text = nfkc(value).gsub("메밀", "메밀 ")
  tags = ALLERGEN_ORDER.select do |tag|
    ALLERGEN_PATTERNS.fetch(tag).match?(text)
  end
  tags.uniq
end

def row_value(row, *keys)
  keys.each do |key|
    value = row[key]
    return value.to_s.strip if value && !value.to_s.strip.empty?
  end
  ""
end

def row_number(row, *keys)
  value = row_value(row, *keys)
  value.empty? ? nil : value.to_f
end

def caution_reason(row)
  serving_g = row_number(row, "primary_serving_g", "serving_size_g").to_f
  multiplier = serving_g / 100.0
  cautions = []
  sodium = row_number(row, "sodium_per_100g_mg")&.*(multiplier)
  sugars = row_number(row, "sugars_per_100g")&.*(multiplier)
  saturated = row_number(row, "saturated_fat_per_100g")&.*(multiplier)
  cautions << "나트륨" if sodium && sodium >= 600.0
  cautions << "당류" if sugars && sugars >= 15.0
  cautions << "포화지방" if saturated && saturated >= 4.5
  cautions.empty? ? nil : "#{cautions.join("/")} 주의"
end

def recommendation_status(row)
  caution = caution_reason(row)
  [caution ? "RECOMMENDABLE_WITH_CAUTION" : "RECOMMENDABLE", caution.to_s]
end

def protein_g_per_100kcal(row)
  calories = row_number(row, "calories_per_100g")
  protein = row_number(row, "protein_per_100g")
  return 0.0 if calories.nil? || calories <= 0 || protein.nil?

  protein / calories * 100.0
end

def high_protein_candidate?(row)
  protein = row_number(row, "protein_per_100g").to_f
  density = protein_g_per_100kcal(row)
  category = row_value(row, "category")
  name = row_value(row, "name_ko", "name")
  category == "PROTEIN_SOURCE" || protein >= 15.0 || density >= 8.0 || HIGH_PROTEIN_NAME_PATTERN.match?(name)
end

def engine_rescue_score(entry)
  row = entry.fetch(:candidate)
  tags = entry.fetch(:haccp).fetch(:tags)
  protein = row_number(row, "protein_per_100g").to_f
  density = protein_g_per_100kcal(row)
  score = 0.0
  score += 100_000.0 if high_protein_candidate?(row)
  score += 20_000.0 if HIGH_PROTEIN_NAME_PATTERN.match?(row_value(row, "name_ko", "name"))
  score += 12_000.0 if row_value(row, "category") == "PROTEIN_SOURCE"
  score += protein * 250.0
  score += density * 350.0
  score += 3_000.0 if row_value(row, "recommendation_status") == "RECOMMENDABLE"
  score -= (tags & ENGINE_RESCUE_BLOCK_TAGS).size * 8_000.0
  score -= row_value(row, "category") == "GRAIN" ? 20_000.0 : 0.0
  score
end

def curation_sort_key(entry)
  legacy_status_rank = row_value(entry[:candidate], "recommendation_status") == "RECOMMENDABLE" ? 0 : 1
  food_id = row_value(entry[:candidate], "food_catalog_id").to_i
  return [legacy_status_rank, food_id] if BATCH_FOCUS == "ID"

  [-engine_rescue_score(entry), legacy_status_rank, food_id]
end

haccp_by_name = Hash.new { |hash, key| hash[key] = [] }
CSV.foreach(HACCP_CSV, headers: true) do |row|
  name = row["prdlstNm"].to_s.strip
  tags = allergen_tags(row["allergy"])
  next if name.empty? || tags.empty?

  haccp_by_name[normalize_name(name)] << {
    report_no: row["prdlstReportNo"],
    product_name: name,
    manufacture: row["manufacture"].to_s.strip,
    seller: row["seller"].to_s.strip,
    allergy: row["allergy"].to_s.strip,
    tags: tags,
    tag_key: tags.join("|"),
    imgurl1: row["imgurl1"].to_s.strip,
    imgurl2: row["imgurl2"].to_s.strip,
    company_tokens: company_tokens(row["manufacture"], row["seller"])
  }
end

candidate_count = 0
processed_count = 0
name_match_count = 0
company_match_count = 0
apply_rows = []
match_rows = []

CSV.foreach(CANDIDATE_CSV, headers: true) do |candidate|
  candidate_count += 1
  source = row_value(candidate, "source")
  food_catalog_id = row_value(candidate, "food_catalog_id")
  food_code = row_value(candidate, "food_code")
  brand_name = row_value(candidate, "brand_name", "brand_or_maker")
  maker = row_value(candidate, "maker", "brand_or_maker")
  name_ko = row_value(candidate, "name_ko", "name")
  category = row_value(candidate, "category")
  current_recommendation_status = row_value(candidate, "recommendation_status", "current_recommendation_status")
  primary_serving_g = row_value(candidate, "primary_serving_g", "serving_size_g")

  next unless source == "MFDS_STANDARD_PROCESSED"

  processed_count += 1
  matches = haccp_by_name[normalize_name(name_ko)]
  next if matches.empty?

  name_match_count += 1
  candidate_tokens = company_tokens(brand_name, maker)
  company_matches = matches.select { |match| company_agreement?(candidate_tokens, match[:company_tokens]) }
  company_match_count += 1 unless company_matches.empty?

  selected_matches = company_matches.empty? ? matches : company_matches
  distinct_tag_sets = selected_matches.map { |match| match[:tag_key] }.uniq
  decision =
    if company_matches.empty?
      "REVIEW_NAME_ONLY"
    elsif distinct_tag_sets.size > 1
      "REVIEW_CONFLICTING_ALLERGENS"
    else
      "APPLY"
    end
  reason =
    case decision
    when "APPLY"
      "제품명과 제조사/판매사 맥락이 일치하고 알러젠 세트가 하나로 수렴한다."
    when "REVIEW_CONFLICTING_ALLERGENS"
      "제조사/판매사 맥락은 맞지만 HACCP 매칭 간 알러젠 세트가 다르다."
    else
      "제품명은 일치하지만 제조사/판매사 맥락이 확인되지 않았다."
    end

  selected = selected_matches.first
  status, recommendation_reason = recommendation_status(candidate)

  match_rows << [
    decision,
    reason,
    food_catalog_id,
    source,
    food_code,
    brand_name,
    maker,
    name_ko,
    category,
    current_recommendation_status,
    primary_serving_g,
    row_value(candidate, "calories_per_100g"),
    row_value(candidate, "protein_per_100g"),
    row_value(candidate, "carbs_per_100g"),
    row_value(candidate, "fat_per_100g"),
    format("%.1f", protein_g_per_100kcal(candidate)),
    high_protein_candidate?(candidate) ? "true" : "false",
    format("%.1f", engine_rescue_score(candidate: candidate, haccp: selected)),
    candidate_tokens.join("|"),
    selected[:report_no],
    selected[:product_name],
    selected[:manufacture],
    selected[:seller],
    selected[:allergy],
    selected[:tags].join(","),
    selected[:imgurl1],
    matches.size,
    company_matches.size,
    distinct_tag_sets.size
  ]

  next unless decision == "APPLY"

  apply_rows << {
    candidate: candidate,
    haccp: selected,
    status: status,
    recommendation_reason: recommendation_reason
  }
end

FileUtils.mkdir_p(OUT_DIR)

CSV.open(MATCH_CSV, "w", write_headers: true, headers: [
  "decision",
  "reason",
  "food_catalog_id",
  "source",
  "food_code",
  "brand_name",
  "maker",
  "name_ko",
  "category",
  "current_recommendation_status",
  "primary_serving_g",
  "calories_per_100g",
  "protein_per_100g",
  "carbs_per_100g",
  "fat_per_100g",
  "protein_g_per_100kcal",
  "high_protein_candidate",
  "engine_rescue_score",
  "candidate_company_tokens",
  "haccp_report_no",
  "haccp_product_name",
  "haccp_manufacture",
  "haccp_seller",
  "haccp_allergy",
  "allergen_tags",
  "haccp_imgurl1",
  "name_match_count",
  "company_match_count",
  "distinct_tag_set_count"
]) do |csv|
  match_rows.each { |row| csv << row }
end

curation_rows = apply_rows
  .sort_by { |entry| curation_sort_key(entry) }
  .first(BATCH_LIMIT)

CSV.open(CURATION_CSV, "w", write_headers: true, headers: [
  "source",
  "food_code",
  "recommendation_status",
  "recommendation_reason",
  "last_verified_at",
  "review_source_url",
  "allergen_tags",
  "allergen_profile_verified",
  "allergen_data_source",
  "allergen_confidence_level"
]) do |csv|
  curation_rows.each do |entry|
    csv << [
      row_value(entry[:candidate], "source"),
      row_value(entry[:candidate], "food_code"),
      entry[:status],
      entry[:recommendation_reason],
      DATE,
      entry[:haccp][:imgurl1],
      entry[:haccp][:tags].join(","),
      "true",
      "FOODQR",
      "LABEL_DERIVED"
    ]
  end
end

decision_counts = match_rows.each_with_object(Hash.new(0)) { |row, counts| counts[row[0]] += 1 }
category_counts = curation_rows.each_with_object(Hash.new(0)) do |entry, counts|
  counts[row_value(entry[:candidate], "category")] += 1
end
status_counts = curation_rows.each_with_object(Hash.new(0)) { |entry, counts| counts[entry[:status]] += 1 }
high_protein_count = curation_rows.count { |entry| high_protein_candidate?(entry[:candidate]) }
engine_rescue_restriction_free_count = curation_rows.count do |entry|
  (entry[:haccp][:tags] & ENGINE_RESCUE_BLOCK_TAGS).empty?
end

File.write(SUMMARY_MD, <<~MD)
  # HACCP 브랜드 알러젠 전체 매칭 결과 #{DATE}

  ## 입력

  - 후보 CSV: `#{CANDIDATE_CSV}`
  - HACCP CSV: `#{HACCP_CSV}`
  - 적용 배치 제한: #{BATCH_LIMIT}건
  - 배치 포커스: `#{BATCH_FOCUS}`

  ## 전체 결과

  | 지표 | 건수 |
  | --- | ---: |
  | engine-ready 알러젠 gap 후보 | #{candidate_count} |
  | `MFDS_STANDARD_PROCESSED` 후보 | #{processed_count} |
  | HACCP 제품명 매칭 후보 | #{name_match_count} |
  | 제품명+제조사/판매사 매칭 후보 | #{company_match_count} |
  | 적용 가능 후보 | #{apply_rows.size} |
  | 이번 curated batch row | #{curation_rows.size} |
  | 이번 batch 고단백 후보 | #{high_protein_count} |
  | 이번 batch 우유/새우/밀 등 공통 제한 비포함 후보 | #{engine_rescue_restriction_free_count} |

  ## 결정 분포

  | decision | 건수 |
  | --- | ---: |
  #{decision_counts.sort.map { |decision, count| "| `#{decision}` | #{count} |" }.join("\n")}

  ## 이번 batch 상태 분포

  | recommendation_status | 건수 |
  | --- | ---: |
  #{status_counts.sort.map { |status, count| "| `#{status}` | #{count} |" }.join("\n")}

  ## 이번 batch 카테고리 분포

  | category | 건수 |
  | --- | ---: |
  #{category_counts.sort.map { |category, count| "| `#{category}` | #{count} |" }.join("\n")}

  ## 운영 메모

  - `APPLY`는 제품명 정규화 일치, 제조사/판매사 맥락 일치, 단일 알러젠 세트 수렴을 모두 만족한 row다.
  - `REVIEW_NAME_ONLY`는 제품명만 같고 제조사/판매사 맥락이 확인되지 않아 자동 승격하지 않는다.
  - `REVIEW_CONFLICTING_ALLERGENS`는 회사 맥락은 맞지만 매칭 라벨 간 알러젠 세트가 달라 수동 확인이 필요하다.
  - 이번 batch CSV는 `RecommendationCurationCsvImporter`에 바로 넣을 수 있는 형식이다.
  - `BATCH_FOCUS=ENGINE_RESCUE`는 단백질량·단백질 밀도·고단백 키워드를 우선하고, 우유/새우/밀 등 흔한 제한 알러젠을 가진 row에 페널티를 준다.
  - 기존 ID 순 배치가 필요하면 `BATCH_FOCUS=ID`로 실행한다.
MD

puts "candidate_count=#{candidate_count}"
puts "processed_count=#{processed_count}"
puts "name_match_count=#{name_match_count}"
puts "company_match_count=#{company_match_count}"
puts "apply_rows=#{apply_rows.size}"
puts "curation_rows=#{curation_rows.size}"
puts "batch_focus=#{BATCH_FOCUS}"
puts "high_protein_rows=#{high_protein_count}"
puts "engine_rescue_restriction_free_rows=#{engine_rescue_restriction_free_count}"
puts "wrote=#{MATCH_CSV}"
puts "wrote=#{CURATION_CSV}"
puts "wrote=#{SUMMARY_MD}"
