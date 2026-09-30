-- 정률(RATE) 할인 100%가 통과돼 리워드 구매 가격이 0원이 되는 문제(#214).
-- PM 결정: 99%까지만 허용. V11의 제약을 RATE <= 99로 다시 만든다.

-- 기존 100% 행을 먼저 보정하지 않으면 아래 제약 추가가 실패한다.
UPDATE rewards
SET early_bird_discount_value = 99
WHERE early_bird_discount_type = 'RATE'
  AND early_bird_discount_value = 100;

ALTER TABLE rewards
    DROP CONSTRAINT chk_rewards_early_bird_discount;

ALTER TABLE rewards
    ADD CONSTRAINT chk_rewards_early_bird_discount CHECK (
        (is_early_bird = FALSE AND early_bird_discount_type IS NULL AND early_bird_discount_value IS NULL) OR
        (is_early_bird = TRUE AND early_bird_discount_type IS NOT NULL AND early_bird_discount_value IS NOT NULL AND
         ((early_bird_discount_type = 'AMOUNT' AND early_bird_discount_value > 0 AND early_bird_discount_value < price) OR
          (early_bird_discount_type = 'RATE' AND early_bird_discount_value >= 0 AND early_bird_discount_value <= 99))
            )
        );
