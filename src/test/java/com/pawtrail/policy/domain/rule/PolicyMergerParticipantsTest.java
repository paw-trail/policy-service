package com.pawtrail.policy.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.policy.domain.enums.ExtractionMethod;
import com.pawtrail.policy.domain.enums.Scope;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PetPolicySource;
import com.pawtrail.policy.domain.model.PolicyFields;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 병합에 어느 소스가 참여했는지를 고정합니다.
 *
 * has_conflict 가 소스 내 어긋남을 셀 때 이 목록의 소스만 봅니다.
 * 이 목록이 틀리면 정정한 장소에 배지가 남거나, 자기모순이 있는 장소에서 배지가 사라집니다.
 */
class PolicyMergerParticipantsTest {

    private static final UUID PLACE_ID = UUID.randomUUID();

    @Test
    @DisplayName("공공 3종이 병합하면 있는 소스 전부가 우선순위 순으로 참여한다")
    void 공공_병합은_소스_전부() {
        // 값을 하나도 안 채운 소스도 참여한 것으로 셈
        // 조건을 못 찾은 원문을 빈 행으로 보낸 소스의 자기모순도 배지에 들어가야 하기 때문임
        MergeResult result = PolicyMerger.merge(List.of(
                extracted(SourceType.CULTURE_CSV,
                        PolicyFields.builder().indoorAllowed(false).build()),
                extracted(SourceType.GOCAMPING, PolicyFields.empty()),
                extracted(SourceType.PET_TOUR,
                        PolicyFields.builder().scope(Scope.PARTIAL).build())));

        assertThat(result.participants())
                .containsExactly(SourceType.PET_TOUR, SourceType.GOCAMPING, SourceType.CULTURE_CSV);
    }

    @Test
    @DisplayName("정정 행이 이기면 그 행 하나만 참여한다")
    void 정정이_이기면_그_행_하나() {
        MergeResult result = PolicyMerger.merge(List.of(
                extracted(SourceType.GOCAMPING,
                        PolicyFields.builder().indoorAllowed(false).build()),
                corrected(SourceType.MANUAL,
                        PolicyFields.builder().scope(Scope.ALL_AREA).build())));

        assertThat(result.participants()).containsExactly(SourceType.MANUAL);
    }

    @Test
    @DisplayName("OWNER 와 MANUAL 이 함께 있으면 OWNER 하나만 참여한다")
    void OWNER_하나만_참여한다() {
        MergeResult result = PolicyMerger.merge(List.of(
                corrected(SourceType.MANUAL,
                        PolicyFields.builder().scope(Scope.PARTIAL).build()),
                corrected(SourceType.OWNER,
                        PolicyFields.builder().scope(Scope.ALL_AREA).build())));

        assertThat(result.participants()).containsExactly(SourceType.OWNER);
    }

    @Test
    @DisplayName("소스가 없으면 참여한 소스도 없다")
    void 소스가_없으면_없다() {
        assertThat(PolicyMerger.merge(List.of()).participants()).isEmpty();
    }

    private static PetPolicySource extracted(SourceType source, PolicyFields fields) {
        return PetPolicySource.extracted(PLACE_ID, source, fields,
                ExtractionMethod.RULE, null, "v1", LocalDateTime.now());
    }

    private static PetPolicySource corrected(SourceType source, PolicyFields fields) {
        return PetPolicySource.corrected(PLACE_ID, source, fields, "검증 후 정정");
    }
}
