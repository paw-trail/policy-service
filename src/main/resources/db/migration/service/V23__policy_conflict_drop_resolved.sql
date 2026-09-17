-- policy_conflict.resolved 를 걷어냅니다.
--
-- 충돌을 닫는 길은 관리자 정정뿐입니다.
-- 정정 행이 병합에서 이기면 병합에 참여한 소스가 그 행 하나가 되어
-- 공공 소스의 어긋남은 has_conflict 에 세지 않고 배지가 닫힙니다.
-- 이 컬럼을 참으로 만드는 경로가 남지 않아 쓰는 곳도 읽는 곳도 없는 값이 됐습니다.
--
-- 남겨 두면 다음 사람이 "충돌만 닫는 기능이 있다" 로 읽습니다.
-- 어긋났던 기록은 policy_correction_log 의 전후 · 사유와
-- 정정이 이겨도 그대로 남는 pet_policy_source 의 공공 행이 맡습니다.
--
-- V20 의 주석 둘이 이것으로 뜻을 잃습니다.
-- resolved 설명("관리자가 정정했는지")과 deleted_at 설명("닫힌 충돌은 resolved = true 로만 표현")입니다.
-- 적용된 마이그레이션 파일은 고치면 체크섬이 달라져 기동이 막히므로 여기에 적어 둡니다.

ALTER TABLE policy_conflict
    DROP COLUMN resolved;
