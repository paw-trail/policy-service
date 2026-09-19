-- policy_evidence 에 근거 한 줄마다 규칙이 읽었는지 모델이 읽었는지를 담습니다.
--
-- 판정 서비스가 판정 이유마다 "공공데이터 항목" 과 "안내문을 AI 가 읽음" 을 가르는 데 씁니다.
-- 조건의 상당수를 모델이 안내문에서 읽어, 사용자가 믿을 무게를 스스로 정하게 하려는 것입니다.
--
-- 이미 있는 값으로는 가를 수 없습니다.
-- 출처 행의 extraction_method 는 행 단위라 한 원문 안에서 칸마다 갈리면 MIXED 가 됩니다.
-- 원문 키로도 못 가립니다. 규칙과 모델이 함께 읽는 원문 키가 있습니다.
-- 조각 번호로도 못 가립니다. 모델 근거도 통째로 읽는 칸이면 비어 있습니다.
-- 그래서 방식을 아는 extract 가 근거마다 싣고 이 표가 받습니다.

ALTER TABLE policy_evidence
    ADD COLUMN extraction_method varchar(10);

-- null 을 허용하는 것은 이미 있는 근거를 위한 것입니다.
--
-- 그 소스를 다시 뽑아 넣으면 근거가 통째로 갈리며 채워집니다.
-- 새로 들어오는 근거는 bulk 요청 검증이 RULE · LLM 둘만 받습니다.

COMMENT ON COLUMN policy_evidence.extraction_method IS
    '이 근거를 규칙이 읽었는지(RULE) 모델이 읽었는지(LLM). null 이면 V25 이전에 들어와 아직 다시 뽑지 않은 근거입니다.';

-- 근거 지문에 추출 방식을 넣으면서 지문을 뜨는 공식이 바뀌었습니다.
--
-- batch 가 근거 줄마다 방식을 내보내므로 방식만 바뀌어도 판이 올라야 합니다.
-- 그런데 옛 공식으로 뜬 지문은 새 공식과 늘 달라, 그대로 두면 다음 재병합에서
-- 근거가 그대로인 장소까지 판이 한꺼번에 오르고 policy.changed 가 나갑니다.
-- 그래서 옛 지문을 비웁니다. 지문이 빈 행은 다음 재병합 때 채우기만 하고 판을 올리지 않습니다 (V24 규칙).

UPDATE pet_policy
   SET evidence_digest = NULL
 WHERE evidence_digest IS NOT NULL;

COMMENT ON COLUMN pet_policy.evidence_digest IS
    'batch 가 내보내는 근거의 지문(SHA-256 16진수 64자). 재병합이 근거가 바뀌었는지 알아내 판을 올리는 데 씁니다. null 이면 V24 이전에 만들어졌거나 V25 가 지문 공식을 바꾸며 비운 행으로, 다음 재병합 때 채우기만 하고 판을 올리지 않습니다.';
