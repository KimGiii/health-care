-- food_serving_options.sort_order를 SMALLINT → INTEGER로 변경.
-- JPA 엔티티(FoodServingOption.sortOrder: Integer)와 타입을 일치시켜
-- Hibernate 스키마 검증(ddl-auto: validate) 실패를 해소한다.
ALTER TABLE food_serving_options
    ALTER COLUMN sort_order TYPE INTEGER;
