package com.pawtrail.policy.domain.event.payload;

import com.pawtrail.common.message.DomainEvent;
import java.util.List;
import java.util.UUID;

/**
 * 한 장소의 조건이 바뀌었음을 알립니다. verdict 와 notification 이 받습니다.
 *
 * <b>이벤트가 온다는 것 자체가 "batch 가 내보내는 것이 바뀌었다" 는 뜻입니다.</b>
 * 조건 스무 칸 · 충돌 여부 · 최상위 티어 · batch 가 내보내는 근거 가운데 무엇이든 바뀌면 나갑니다.
 * 받는 쪽은 changedFields 가 비어 있어도 그 장소의 캐시를 지워야 합니다.
 * 조건 값은 그대로이고 충돌 여부 · 정정 출처 · 근거만 바뀐 경우 changedFields 가 비어 있기 때문입니다.
 * 사용자 알림은 changedFields 가 비어 있지 않을 때만 보내면 됩니다.
 *
 * 첫 병합(조건 행이 처음 생김)도 판 1 로 나갑니다.
 * 장소는 이미 있고 verdict 는 조건 행이 없는 장소를 UNKNOWN 으로 답하므로,
 * 첫 병합이 곧 그 답이 낡았다는 신호입니다.
 *
 * 값을 싣지 않고 이름만 싣습니다.
 * 받는 쪽이 새 값이 필요하면 batch 로 다시 읽습니다. 값을 실으면 조건 칸이 늘 때마다
 * 이벤트 모양이 함께 바뀌고, batch 와 이벤트가 같은 값을 두 길로 나르게 됩니다.
 *
 * @param placeId       장소 식별자. 파티션 키가 되어 같은 장소의 이벤트 순서를 지킴
 * @param policyVersion batch 가 내보내는 것의 판. 받는 쪽이 자기가 아는 판과 비교함
 * @param changedFields 값이 바뀐 조건 칸 이름(FieldSpec 이름). 조건 순서이며 비어 있을 수 있음.
 *                      첫 병합은 빈 조건과 비교하므로 값이 있는 칸이 담김
 * @param hasConflict   지금의 충돌 여부
 */
public record PolicyChangedEvent(
        UUID placeId,
        int policyVersion,
        List<String> changedFields,
        boolean hasConflict
) implements DomainEvent {

    public PolicyChangedEvent {
        if (placeId == null) {
            throw new IllegalArgumentException("placeId 는 필수입니다.");
        }
        changedFields = changedFields == null ? List.of() : List.copyOf(changedFields);
    }

    // 아래 셋은 봉투를 만들 때만 쓰이고 payload 에는 실리지 않음
    // DomainEvent 가 @JsonIgnore 를 선언해 두었으므로 구현체가 그대로 물려받음

    @Override
    public String getTopic() {
        // infra 의 create-topics.sh 에 같은 이름이 있어야 함
        // 토픽 자동 생성을 꺼 두었으므로 없으면 발행이 실패함
        return "policy.changed";
    }

    @Override
    public String getAggregateType() {
        return "Policy";
    }

    @Override
    public String getAggregateId() {
        // 파티션 키가 되어 같은 장소에 대한 이벤트의 순서를 보장함
        return placeId.toString();
    }
}
