-- 이 서비스의 첫 마이그레이션 스크립트입니다.
-- V1 부터 V19 는 공통 모듈이 사용하는 대역이므로 쓰지 않습니다.
--
-- 이미 적용된 스크립트는 수정하지 않습니다.
-- 내용이 바뀌면 체크섬이 달라져 다음 기동이 실패합니다.
-- 변경이 필요하면 다음 번호로 새 스크립트를 만듭니다.
--
-- policy_db 에는 이 다섯 테이블과 공통 대역의 outbox · inbox 만 있습니다.
-- 장소가 무엇인가는 place_db 의 소유이고, 여기는 "그 장소에 반려동물을 데려갈 수
-- 있는가" 만 답합니다. 두 표가 place_id 로 이어지나 외래키를 걸지 않습니다.
-- 서비스가 갈려 DB 가 다르기 때문입니다.
--
-- PostGIS 확장이 필요하지 않습니다.
-- 이 DB 에는 좌표 컬럼이 한 개도 없습니다.

-- =============================================================================
-- pet_policy_source
-- =============================================================================
-- 소스별 추출 결과입니다. 병합하기 전의 원재료이며 장소당 소스별로 한 행입니다.
--
-- 이 표를 따로 두는 이유는 병합 규칙을 나중에 바꿀 수 있게 하기 위해서입니다.
-- 최종본만 두면 규칙이 바뀔 때마다 LLM 을 다시 돌려야 하는데,
-- 원재료가 남아 있으면 재병합만으로 끝납니다.
-- POST /admin/policies/{placeId}/remerge 가 그 장치입니다.
--
-- 소스 간 충돌도 여기서 나옵니다.
-- 같은 장소의 여러 행을 나란히 놓고 값이 갈리는 필드를 찾습니다.
--
-- MANUAL 과 OWNER 도 소스입니다.
-- 관리자 정정을 별도 표로 두지 않고 소스 티어의 최상위로 넣었기 때문에,
-- 배치가 새 값을 뽑아도 병합에서 자동으로 밀립니다.

CREATE TABLE pet_policy_source
(
    -- PK 는 모든 테이블이 uuid 입니다.
    -- 애플리케이션이 Hibernate 의 @UuidGenerator(style = VERSION_7) 로 생성해 넣으므로
    -- 여기에 기본값을 지정하지 않습니다.
    id                 uuid          PRIMARY KEY,

    -- 어느 장소의 조건인지입니다.
    -- place_db 의 place.id 를 가리키나 외래키를 걸지 않습니다.
    place_id           uuid          NOT NULL,

    -- 이 조건을 어디서 뽑았는지입니다.
    -- PET_TOUR · GOCAMPING · CULTURE_CSV · MANUAL · OWNER
    --
    -- place 의 SourceType 과 값이 다릅니다.
    -- MOIS_VET 이 없는 것은 동물병원에 동반 조건이 없어 행이 생기지 않기 때문이고,
    -- MANUAL · OWNER 가 있는 것은 관리자 정정이 소스 티어로 들어오기 때문입니다.
    source             varchar(20)   NOT NULL,

    -- ── 조건 필드 20개 ────────────────────────────────────────────────────
    -- 아래 스무 컬럼은 pet_policy 와 정확히 같은 구성입니다.
    -- 엔티티에서는 PolicyFields 값 객체 하나로 묶어 양쪽이 공유합니다.
    --
    -- NULL 과 false 는 뜻이 다릅니다.
    -- NULL 은 "정보 없음" 이고 false 는 "요구하지 않음" 입니다.
    -- 판정이 이것으로 갈리므로 NOT NULL 을 걸지 않고 기본값도 주지 않습니다.

    -- 장소 전체가 되는지 일부 구역만 되는지입니다.
    -- ALL_AREA · PARTIAL · UNKNOWN
    -- 공사의 acmpyTypeCd 가 그대로 오며 일부구역이 40% 입니다. 판정의 첫 축입니다.
    scope              varchar(10),

    -- 안내견만 가능한 곳입니다.
    --
    -- 키워드 매칭이 못 잡는 함정입니다.
    -- "안내견 동반 가능" 이라고만 적혀 있고 불가라는 글자가 없어,
    -- 문장에서 낱말만 찾으면 동반 가능으로 읽힙니다.
    guide_dog_only     boolean,

    -- 반려견이 없으면 입장할 수 없는 곳입니다.
    -- 루파니애견캠핑장처럼 실재합니다.
    pet_only           boolean,

    indoor_allowed     boolean,
    outdoor_allowed    boolean,

    -- 체중 상한입니다.
    max_weight_kg      numeric(5,2),

    -- 상한값을 포함하는지입니다.
    -- "10kg 이하" 면 true, "10kg 미만" 이면 false 입니다.
    -- 정확히 10.0kg 인 아이의 판정이 이 값으로 뒤집힙니다.
    weight_inclusive   boolean,

    -- 동반 마릿수 상한입니다.
    max_count          smallint,

    -- 크기 제한입니다. SMALL_ONLY · SMALL_MEDIUM · ALL
    -- 고캠핑 447건 · 문화정보원 262건에 실제로 값이 있습니다.
    size_rule          varchar(20),

    -- 견종 제한입니다. NONE · DANGEROUS_MUZZLE · DANGEROUS_BANNED
    breed_rule         varchar(20),

    -- 목줄로는 안 되고 이동장이나 유모차가 있어야 하는 곳입니다.
    -- 산이정원처럼 실재하며, 준비물이 아니라 입장 조건입니다.
    carrier_required   boolean,

    -- 목줄 착용 요구입니다.
    -- 판정을 가르지 않고 준비물 안내로만 나갑니다.
    leash_required     boolean,

    -- 들어갈 수 없는 구역의 이름입니다.
    -- scope 가 PARTIAL 일 때 "어디가 안 되는지" 를 담습니다.
    -- 실내외 구분은 indoor_allowed · outdoor_allowed 가 이미 다루므로
    -- 여기에는 더 구체적인 구역 이름이 들어갑니다.
    excluded_zones     text[],

    -- 그 구역에서만 동반할 수 있는 경우의 구역 이름입니다.
    allowed_zones_only text[],

    -- 동반이 안 되는 날짜나 기간입니다.
    excluded_days      text[],

    -- 추가 요금입니다.
    extra_fee_amount   integer,

    -- 요금의 단위입니다. PER_DOG · PER_NIGHT · PER_VISIT
    -- 금액만 있고 단위가 없으면 사용자에게 보여줄 문장을 만들 수 없습니다.
    extra_fee_unit     varchar(12),

    -- 가져가야 하는 물건입니다.
    required_items     text[],

    -- 접종 증명서를 요구하는지입니다.
    vaccine_proof      boolean,

    -- 방문 전에 문의해야 하는지입니다.
    advance_inquiry    boolean,

    -- ── 추출 이력 ────────────────────────────────────────────────────────
    -- 아래 여섯은 pet_policy 에 없습니다.
    -- 병합 최종본은 "무엇이 결론인가" 만 담고 "어떻게 뽑았나" 는 원재료 쪽에 남깁니다.

    -- 어떻게 뽑았는지입니다. RULE · LLM · MIXED · MANUAL
    -- 정형 필드는 규칙으로 파싱하고 자유 텍스트만 LLM 을 태웁니다.
    extraction_method  varchar(10),

    -- LLM 을 태웠다면 어느 모델인지입니다.
    -- 모델별 정확도를 나눠 재는 데 씁니다.
    extracted_by       varchar(50),

    -- 프롬프트 판입니다.
    -- 프롬프트를 고친 뒤 결과가 달라지면 어느 판으로 뽑은 것인지 가릅니다.
    prompt_version     varchar(20),

    extracted_at       timestamp,

    -- 추출 진행 상태입니다. PENDING · DONE · FAILED
    -- 배치가 중단되면 PENDING 부터 이어서 실행합니다.
    status             varchar(12),

    -- 왜 이 값인지입니다.
    --
    -- 관리자 정정(MANUAL · OWNER)에서 필수입니다.
    -- 수집한 값은 비어 있고 사람이 고친 값에만 채워지므로,
    -- 이 컬럼이 비었는지로 두 성격이 갈립니다.
    reason             text,

    -- 공통 모듈의 BaseEntity 가 매핑하는 여섯 컬럼입니다.
    -- 빠뜨리면 ddl-auto: validate 가 기동을 막습니다.
    created_at         timestamp     NOT NULL,
    created_by         varchar(45)   NOT NULL,
    updated_at         timestamp     NOT NULL,
    updated_by         varchar(45)   NOT NULL,

    -- 재추출로 옛 결과를 무효화할 때 씁니다.
    -- 파이프라인이 자기 정리를 하는 자리이며 사람이 지우는 용도가 아닙니다.
    deleted_at         timestamp,
    deleted_by         varchar(45)
);

-- 장소당 소스별로 한 행입니다.
--
-- 이 제약이 재추출의 멱등을 만듭니다.
-- 같은 장소를 다시 뽑으면 새 행이 쌓이는 것이 아니라 그 소스의 행을 갱신합니다.
-- 관리자가 같은 장소를 두 번 정정하면 MANUAL 행을 덮어쓰므로
-- 앞선 값과 사유가 사라지는데, 그것을 policy_correction_log 가 받습니다.
CREATE UNIQUE INDEX uq_policy_source_place
    ON pet_policy_source (place_id, source);

COMMENT ON TABLE pet_policy_source IS '소스별 추출 결과. 병합 전 원재료이며 재병합의 입력입니다.';

-- =============================================================================
-- pet_policy
-- =============================================================================
-- 병합 최종본입니다. verdict 가 판정할 때 읽어 가는 대상입니다.
--
-- PK 가 place_id 입니다. 장소당 한 행뿐이라 별도 식별자를 두지 않았습니다.
-- 별도 id 를 두면 "한 장소에 조건이 둘일 수 있다" 로 읽히는데 그런 상태는 없습니다.
--
-- 병합은 세 단계입니다.
--   OWNER 행이 있으면            그 행이 통째로 이 표가 됨
--   없고 MANUAL 행이 있으면       그 행이 통째로 됨
--   둘 다 없으면                 공공 3종을 필드 단위로 합침
--                              PET_TOUR > GOCAMPING > CULTURE_CSV
--
-- 공공 3종을 필드 단위로 합치는 이유는 소스마다 채우는 칸이 다르기 때문입니다.
-- 공사는 scope 를 주고 고캠핑은 size_rule 을 주는데, 한 소스를 통째로 쓰면
-- 나머지가 채웠을 칸이 전부 NULL 이 되고 NULL 은 "정보 없음" 이라 판정이
-- UNKNOWN 으로 떨어집니다. 가진 근거를 버려서 모른다고 답하는 셈입니다.

CREATE TABLE pet_policy
(
    -- 장소 식별자가 곧 PK 입니다.
    place_id           uuid          PRIMARY KEY,

    -- ── 조건 필드 20개 ────────────────────────────────────────────────────
    -- pet_policy_source 와 정확히 같은 구성입니다.
    -- 컬럼별 설명은 그쪽에 있으며 여기서는 되풀이하지 않습니다.
    -- 엔티티에서는 PolicyFields 값 객체 하나를 양쪽이 공유합니다.

    scope              varchar(10),
    guide_dog_only     boolean,
    pet_only           boolean,
    indoor_allowed     boolean,
    outdoor_allowed    boolean,
    max_weight_kg      numeric(5,2),
    weight_inclusive   boolean,
    max_count          smallint,
    size_rule          varchar(20),
    breed_rule         varchar(20),
    carrier_required   boolean,
    leash_required     boolean,
    excluded_zones     text[],
    allowed_zones_only text[],
    excluded_days      text[],
    extra_fee_amount   integer,
    extra_fee_unit     varchar(12),
    required_items     text[],
    vaccine_proof      boolean,
    advance_inquiry    boolean,

    -- ── 병합 결과 ────────────────────────────────────────────────────────

    -- 소스끼리 값이 갈린 필드가 하나라도 있는지입니다.
    --
    -- 상세 내역은 policy_conflict 가 갖고 이 컬럼은 배지 표시용입니다.
    -- 장소 상세가 이 값이 참일 때만 GET /places/{id}/conflicts 를 부르므로,
    -- 충돌이 없는 장소에서 헛 호출이 나가지 않습니다.
    has_conflict       boolean       NOT NULL,

    -- 이 병합에서 최상위로 이긴 티어입니다.
    --
    -- 필드별 출처가 아닙니다. 그것은 policy_evidence 가 (place_id, source, field_name)
    -- 으로 갖고 있으며 화면의 "출처: 고캠핑" 한 줄도 그쪽에서 나옵니다.
    -- 이 컬럼은 사람이 손댄 값인지를 관리자 목록에서 한눈에 거르는 용도입니다.
    source_priority    varchar(20),

    merged_at          timestamp     NOT NULL,

    -- 갱신될 때마다 오릅니다.
    --
    -- policy.changed 발행 기준이며, 받는 쪽이 자기가 아는 판과 비교합니다.
    -- 재병합해도 결과가 같으면 오르지 않습니다.
    -- 그래야 값이 안 바뀐 재병합으로 알림이 나가지 않습니다.
    policy_version     integer       NOT NULL,

    created_at         timestamp     NOT NULL,
    created_by         varchar(45)   NOT NULL,
    updated_at         timestamp     NOT NULL,
    updated_by         varchar(45)   NOT NULL,

    -- 이 테이블에서는 사용하지 않고 항상 NULL 입니다.
    -- 소스가 전부 무효화되면 조건이 없는 상태가 되는데,
    -- 그것은 행을 지우는 것이 아니라 전 필드가 NULL 인 병합 결과로 나타납니다.
    deleted_at         timestamp,
    deleted_by         varchar(45)
);

COMMENT ON TABLE pet_policy IS '병합 최종본. verdict 가 읽어 가는 대상입니다.';

-- =============================================================================
-- policy_evidence
-- =============================================================================
-- 근거 문구입니다. 각 조건이 원문의 어느 문장에서 나왔는지를 담습니다.
--
-- 근거를 제시하는 것이 이 서비스의 정체성이고, 그 실체가 이 표입니다.
-- 화면 세 자리가 전부 여기서 나옵니다.
--   검색 카드의 한 줄 근거
--   장소 상세 「확인 사항」의 항목별 이유와 출처
--   「근거 원문 전체 보기」
--
-- 발췌만 보여주면 LLM 이 고른 것을 LLM 근거로 확인하는 순환이 되므로
-- 원문 전체는 ingest 의 raw_document 가 따로 내보냅니다.

CREATE TABLE policy_evidence
(
    id            uuid          PRIMARY KEY,

    place_id      uuid          NOT NULL,

    -- 이 근거가 어느 소스의 원문에서 나왔는지입니다.
    -- OWNER 면 상세에 "장소에서 직접 알려준 정보" 배지를 붙일 수 있습니다.
    source        varchar(20)   NOT NULL,

    -- 어느 조건의 근거인지입니다. pet_policy 의 컬럼 이름이 들어갑니다.
    field_name    varchar(40)   NOT NULL,

    -- 원문의 어느 필드에서 뽑았는지입니다.
    -- acmpyPsblCpam · etcAcmpyInfo · intro 처럼 소스가 쓰는 이름 그대로입니다.
    origin_field  varchar(40)   NOT NULL,

    -- 그 필드 안에서 몇 번째 조각인지입니다.
    --
    -- etcAcmpyInfo 는 개행으로 쪼갭니다.
    -- 하이픈으로 쪼개는 방식은 본문에 하이픈이 섞여 있어 버렸습니다.
    -- 쪼갤 것이 없는 필드면 NULL 입니다.
    segment_index integer,

    -- 근거 문구 자체입니다.
    -- 사람이 읽고 "아 그래서 이런 판정이구나" 를 알 수 있어야 합니다.
    segment_text  text          NOT NULL,

    created_at    timestamp     NOT NULL,
    created_by    varchar(45)   NOT NULL,
    updated_at    timestamp     NOT NULL,
    updated_by    varchar(45)   NOT NULL,

    -- 재추출로 옛 근거를 무효화할 때 씁니다.
    deleted_at    timestamp,
    deleted_by    varchar(45)
);

-- 장소 상세가 조건별로 근거를 찾습니다.
-- field_name 까지 넣는 것은 「확인 사항」이 항목마다 따로 조회하기 때문입니다.
CREATE INDEX idx_policy_evidence_place
    ON policy_evidence (place_id, field_name);

COMMENT ON TABLE policy_evidence IS '조건별 근거 문구. 카드 한 줄과 상세 확인 사항이 여기서 나옵니다.';

-- =============================================================================
-- policy_conflict
-- =============================================================================
-- 소스끼리 또는 한 소스 안에서 값이 어긋난 자리입니다.
--
-- 어긋났다고 해서 한쪽을 버리지 않습니다.
-- 우선순위대로 병합하되 어긋났다는 사실을 남겨 관리자가 볼 수 있게 합니다.
-- 어느 쪽이 맞는지는 우리가 판단할 수 없고, 버리면 그 판단의 근거도 사라집니다.
--
-- 사용자에게는 배지로만 알립니다.
-- 문구가 원인을 단정하지 않게 씁니다. 소스가 틀렸는지 장소가 바뀐 것인지 모릅니다.

CREATE TABLE policy_conflict
(
    id            uuid          PRIMARY KEY,

    place_id      uuid          NOT NULL,

    -- 어느 조건에서 갈렸는지입니다.
    field_name    varchar(40)   NOT NULL,

    -- 소스별로 무엇이라고 했는지입니다.
    --
    -- 필드마다 타입이 달라(boolean · numeric · text[]) 컬럼으로는 담을 수 없습니다.
    -- 관리자 화면이 그대로 펼쳐 보여주기만 하고 조건으로 조회하지 않으므로
    -- jsonb 로 두어도 인덱스가 필요하지 않습니다.
    source_values jsonb         NOT NULL,

    -- 어긋남의 종류입니다. CROSS_SOURCE · INTRA_SOURCE
    --
    -- CROSS_SOURCE 는 policy 가 병합하면서 스스로 찾습니다.
    -- INTRA_SOURCE 는 extract 가 실어 보냅니다.
    -- 한 레코드 안에서 필드값과 본문이 어긋난 경우인데(고캠핑 28건),
    -- policy 는 한 소스의 조건 한 벌만 받으므로 원본의 자기모순을 알 방법이 없습니다.
    conflict_type varchar(16)   NOT NULL,

    detected_at   timestamp     NOT NULL,

    -- 관리자가 정정했는지입니다.
    --
    -- place_pending_update 의 status 와 달리 이 컬럼은 개명하지 않았습니다.
    -- 저쪽은 값이 셋(PENDING · APPROVED · REJECTED)이라 이름이 boolean 처럼
    -- 읽히는 것이 문제였고, 여기는 실제로 참 거짓 둘뿐입니다.
    resolved      boolean       NOT NULL,

    created_at    timestamp     NOT NULL,
    created_by    varchar(45)   NOT NULL,
    updated_at    timestamp     NOT NULL,
    updated_by    varchar(45)   NOT NULL,

    -- 재병합으로 옛 충돌을 무효화할 때 씁니다.
    deleted_at    timestamp,
    deleted_by    varchar(45)
);

-- 장소 상세의 충돌 조회와 관리자 목록이 이 인덱스를 씁니다.
CREATE INDEX idx_policy_conflict_place
    ON policy_conflict (place_id);

COMMENT ON TABLE policy_conflict IS '소스 간·소스 내 충돌. 병합하되 어긋난 사실을 남깁니다.';

-- =============================================================================
-- policy_correction_log
-- =============================================================================
-- 관리자 정정 이력입니다.
--
-- 이 표가 필요한 이유는 pet_policy_source 가 장소당 소스별로 한 행이기 때문입니다.
-- 같은 장소를 두 번 정정하면 MANUAL 행을 덮어써 앞선 값도 사유도 사라집니다.
-- source 의 reason 만으로는 "왜" 만 남고 "무엇을 어떻게" 가 안 남아
-- 문장이 공중에 뜹니다.
--
-- BaseEntity 를 상속하지 않습니다.
-- 한 번 쓰고 고치지 않는 표라 updated 와 deleted 계열 넷이 죽은 컬럼이 되고,
-- 특히 deleted_at 이 있으면 이력을 지울 수 있는 것으로 읽혀
-- 이 표의 존재 이유가 무너집니다.
-- place 의 place_source_detach 를 같은 이유로 미상속으로 두었습니다.
--
-- 재병합은 여기에 남기지 않습니다.
-- 값을 바꾸는 동작이 아니고, 결과가 실제로 달라지면 policy_version 이 올라
-- policy.changed 가 나가므로 그쪽에 이미 흔적이 남습니다.

CREATE TABLE policy_correction_log
(
    id           uuid          PRIMARY KEY,

    place_id     uuid          NOT NULL,

    -- 누가 알려준 값인지입니다. MANUAL · OWNER
    --
    -- 업주가 알려준 값과 관리자 추정은 신뢰도가 다릅니다.
    -- 나중에 갈라 볼 이유가 실제로 있어 컬럼으로 둡니다.
    source       varchar(20)   NOT NULL,

    -- 고치기 전과 후의 조건 한 벌입니다.
    --
    -- 전후를 함께 담는 것은 이력을 두는 이유가 "원래 뭐였지" 이기 때문입니다.
    -- 뒤만 남기면 그 질문에 답을 못 합니다.
    -- place_pending_update 가 current_value 와 new_value 를 둘 다 담는 것과 같습니다.
    --
    -- policy 가 덮어쓰기 전에 기존 행을 어차피 읽으므로 조회가 더 붙지 않습니다.
    before_value jsonb         NOT NULL,
    after_value  jsonb         NOT NULL,

    -- 왜 고쳤는지입니다. 정정에서는 필수입니다.
    reason       text          NOT NULL,

    -- 고친 관리자입니다.
    --
    -- BaseEntity 를 안 쓰므로 JPA Auditing 이 채우지 않습니다.
    -- AuditorProvider.current() 로 꺼내 서비스로 넘깁니다.
    --
    -- uuid 가 아니라 varchar(45) 인 것은 다른 모든 표의 "누가" 가 그 타입이기
    -- 때문입니다. 여기만 uuid 면 조인 타입이 갈립니다.
    --
    -- 배치는 여기에 쓰지 않습니다.
    -- 배치의 흔적은 pet_policy_source 의 추출 이력 넷에 따로 남습니다.
    corrected_by varchar(45)   NOT NULL,

    corrected_at timestamp     NOT NULL
);

-- 한 장소의 정정 이력을 최근 것부터 봅니다.
CREATE INDEX idx_policy_correction_place
    ON policy_correction_log (place_id, corrected_at DESC);

COMMENT ON TABLE policy_correction_log IS '관리자 정정 이력. 한 번 쓰고 고치지 않습니다.';
