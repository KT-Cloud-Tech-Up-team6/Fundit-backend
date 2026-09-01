-- categories 마스터는 아직 V2 시드 마이그레이션이 없어 테스트에서만 필요한 조합을 넣는다.
-- 실제 시드가 들어오면 이 파일은 지우고 마이그레이션 데이터를 그대로 쓴다.
INSERT INTO categories (category_major, category_minor)
VALUES ('홈·리빙', '인테리어'),
       ('테크·가전', '음향기기')
ON CONFLICT DO NOTHING;
