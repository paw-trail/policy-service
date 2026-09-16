-- policy_conflict 에 어느 소스의 것인지를 담습니다.
--
-- 소스 내 어긋남(INTRA_SOURCE)은 extract 가 소스별로 보내므로
-- 재추출할 때 그 소스의 것만 갈아 끼워야 합니다.
-- 담을 칸이 없으면 장소 전체를 지우게 되는데, 그러면 이번 요청에 없는
-- 다른 소스의 어긋남 기록까지 사라지고 되살릴 방법이 없습니다.
--
-- policy_evidence 가 이미 같은 컬럼을 갖고 있습니다.
-- 근거와 소스 내 어긋남은 둘 다 한 소스의 원문에서 나온 것이라 모양이 같아야 합니다.
--
-- 얻는 것이 하나 더 있습니다.
-- 지금까지는 어느 소스가 자기모순이었는지를 알 길이 없었습니다.
-- 고캠핑에서 필드값과 본문이 다른 말을 하는 경우가 28건 확인됐는데,
-- 관리자가 "이 소스 데이터가 원래 이렇다" 를 보려면 이 값이 필요합니다.

ALTER TABLE policy_conflict
    ADD COLUMN source varchar(20);

COMMENT ON COLUMN policy_conflict.source IS
    'INTRA_SOURCE 면 그 소스. CROSS_SOURCE 는 소스 여럿에 걸쳐 있어 NULL 입니다.';

-- 인덱스는 넓히지 않습니다.
--
-- 한 장소의 충돌은 조건 스무 칸에 종류 둘이 상한이라 행이 애초에 적습니다.
-- idx_policy_conflict_place(place_id) 로 그 장소를 찾고 나면
-- 종류와 소스로 거르는 것은 몇 행을 훑는 일입니다.
-- place_pending_update 에서 같은 이유로 인덱스를 두지 않았습니다.
