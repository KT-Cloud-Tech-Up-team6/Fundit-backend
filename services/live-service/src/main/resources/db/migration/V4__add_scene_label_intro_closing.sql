-- AI팀 요청(2026-09-23): 하이라이트 장면 분류가 7종인데 scene_label CHECK는 5종만 허용했다.
-- INTRO/CLOSING이 오면 지금은 500이 난다(SceneLabel.java 주석 참고). 급하지 않은 작업이라
-- AI팀이 FALLBACK_SCENE_LABEL=1로 임시 대체(SPEC) 가능하다고 확인해줬지만, 타임라인이 방송
-- 전체를 빈틈없이 덮어야 하는 설계라 결국 이 두 값이 필요하다.
ALTER TABLE live_highlights DROP CONSTRAINT live_highlights_scene_label_check;
ALTER TABLE live_highlights ADD CONSTRAINT live_highlights_scene_label_check
    CHECK (scene_label IN ('DEMO', 'AUDIENCE_REACTION', 'SPEC', 'PRICE_BENEFIT', 'COMPARISON',
                            'INTRO', 'CLOSING'));
