-- 리워드 얼리버드 할인 금액/방식을 저장할 컬럼이 없었다(is_early_bird boolean만 존재).
-- 정액(AMOUNT, 원)과 정률(RATE, %) 두 방식을 지원한다.
ALTER TABLE rewards
    ADD COLUMN early_bird_discount_type  VARCHAR(10)
        CHECK (early_bird_discount_type IN ('AMOUNT', 'RATE')),
    ADD COLUMN early_bird_discount_value BIGINT;

ALTER TABLE rewards
    ADD CONSTRAINT chk_rewards_early_bird_discount CHECK (
        (is_early_bird = FALSE AND early_bird_discount_type IS NULL AND early_bird_discount_value IS NULL) OR
        (is_early_bird = TRUE AND early_bird_discount_type IS NOT NULL AND early_bird_discount_value IS NOT NULL AND
         ((early_bird_discount_type = 'AMOUNT' AND early_bird_discount_value > 0 AND early_bird_discount_value < price) OR
          (early_bird_discount_type = 'RATE' AND early_bird_discount_value >= 0 AND early_bird_discount_value <= 100))
            )
        );
