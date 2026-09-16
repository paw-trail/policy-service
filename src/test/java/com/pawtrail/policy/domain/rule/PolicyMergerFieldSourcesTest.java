package com.pawtrail.policy.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.policy.domain.enums.ExtractionMethod;
import com.pawtrail.policy.domain.enums.Scope;
import com.pawtrail.policy.domain.enums.SizeRule;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PetPolicySource;
import com.pawtrail.policy.domain.model.PolicyFields;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 병합이 칸마다 누가 이겼는지 적는 규칙을 고정합니다.
 *
 * 값을 고르는 규칙은 PolicyMergerTest 가 이미 고정하고 있습니다.
 * 여기서 보는 것은 그 값을 누가 만들었는지이며 batch 조회가 이것으로 근거를 거릅니다.
 * 이 기록이 틀리면 화면에 병합에서 진 소스의 문구가 출처로 뜹니다.
 */
class PolicyMergerFieldSourcesTest {

    private static final UUID PLACE_ID = UUID.randomUUID();

    @Test
    @DisplayName("소스가 없으면 적힌 승자도 없다")
    void 소스가_없으면_승자도_없다() {
        assertThat(PolicyMerger.merge(List.of()).fieldSources()).isEmpty();
    }

    @Test
    @DisplayName("값을 말한 칸만 그 소스가 이긴 것으로 적힌다")
    void 말한_칸만_적힌다() {
        MergeResult result = PolicyMerger.merge(List.of(
                extracted(SourceType.PET_TOUR, PolicyFields.builder().scope(Scope.PARTIAL).build())));

        assertThat(result.fieldSources()).containsOnlyKeys("scope");
        assertThat(result.fieldSources().get("scope")).containsExactly(SourceType.PET_TOUR);
    }

    @Test
    @DisplayName("값 칸은 우선순위가 앞선 소스가 이긴다")
    void 값_칸은_앞선_소스가_이긴다() {
        // 넣는 순서를 일부러 뒤집음
        // 승자는 넣은 순서가 아니라 우선순위로 정해져야 함
        MergeResult result = PolicyMerger.merge(List.of(
                extracted(SourceType.CULTURE_CSV,
                        PolicyFields.builder().scope(Scope.ALL_AREA).build()),
                extracted(SourceType.PET_TOUR,
                        PolicyFields.builder().scope(Scope.PARTIAL).build())));

        assertThat(result.fields().getScope()).isEqualTo(Scope.PARTIAL);
        assertThat(result.fieldSources().get("scope")).containsExactly(SourceType.PET_TOUR);
    }

    @Test
    @DisplayName("앞선 소스가 비운 칸은 뒤 소스가 이기고, 갈린 칸에서 진 소스는 적히지 않는다")
    void 비운_칸은_뒤_소스가_이긴다() {
        // 문암생태공원 조합임 (이슈 #5 실물 검증)
        // 공사는 scope 만, 고캠핑과 문화정보원은 실내외만 채움
        MergeResult result = PolicyMerger.merge(List.of(
                extracted(SourceType.PET_TOUR,
                        PolicyFields.builder().scope(Scope.PARTIAL).build()),
                extracted(SourceType.GOCAMPING, PolicyFields.builder()
                        .indoorAllowed(false).outdoorAllowed(false).build()),
                extracted(SourceType.CULTURE_CSV, PolicyFields.builder()
                        .indoorAllowed(false).outdoorAllowed(true).build())));

        assertThat(result.fieldSources().get("scope")).containsExactly(SourceType.PET_TOUR);
        assertThat(result.fieldSources().get("indoorAllowed")).containsExactly(SourceType.GOCAMPING);

        // 실외는 고캠핑과 문화정보원이 갈렸고 고캠핑 값이 이김
        // 문화정보원의 "실외 가능" 근거가 이 칸의 출처로 뜨면 안 됨
        assertThat(result.fields().getOutdoorAllowed()).isFalse();
        assertThat(result.fieldSources().get("outdoorAllowed")).containsExactly(SourceType.GOCAMPING);
    }

    @Test
    @DisplayName("목록 칸은 새 원소를 보탠 소스가 우선순위 순으로 이긴다")
    void 목록_칸은_보탠_소스가_전부_이긴다() {
        // 넣는 순서를 뒤집음 — 공사가 먼저 "실내" 를, 문화정보원이 뒤에서 "잔디" 를 보탬
        // 문화정보원은 "실내" 가 겹치지만 "잔디" 를 새로 보탰으므로 승자임
        MergeResult result = PolicyMerger.merge(List.of(
                extracted(SourceType.CULTURE_CSV, PolicyFields.builder()
                        .excludedZones(List.of("실내", "잔디")).build()),
                extracted(SourceType.PET_TOUR, PolicyFields.builder()
                        .excludedZones(List.of("실내")).build())));

        assertThat(result.fieldSources().get("excludedZones"))
                .containsExactly(SourceType.PET_TOUR, SourceType.CULTURE_CSV);
    }

    @Test
    @DisplayName("뒤 소스가 이미 나온 원소만 말했으면 승자가 아니다")
    void 이미_나온_원소만_말하면_승자가_아니다() {
        // 비어 있지 않은 목록을 말했다고 승자로 두면 고캠핑이 새로 보탠 것이 없는데도 승자가 되어
        // batch 가 고캠핑의 구역 근거까지 내보냄 (PR #8 리뷰)
        // 값 칸에서 같은 값을 뒤에서 한 번 더 말한 소스가 승자가 아닌 것과 같은 원리임
        MergeResult result = PolicyMerger.merge(List.of(
                extracted(SourceType.PET_TOUR, PolicyFields.builder()
                        .excludedZones(List.of("실내", "잔디")).build()),
                extracted(SourceType.GOCAMPING, PolicyFields.builder()
                        .excludedZones(List.of("실내")).build())));

        assertThat(result.fields().getExcludedZones()).containsExactly("실내", "잔디");
        assertThat(result.fieldSources().get("excludedZones")).containsExactly(SourceType.PET_TOUR);
    }

    @Test
    @DisplayName("다른 소스가 원소를 보탰으면 빈 목록을 말한 소스는 이기지 않는다")
    void 빈_목록은_보탠_것이_아니다() {
        // 빈 목록은 "해당 없음" 이라고 말한 것임
        // 최종 값이 ["실내"] 인데 "제한 구역 없음" 근거가 붙으면 최종 값과 반대 말을 하게 됨
        MergeResult result = PolicyMerger.merge(List.of(
                extracted(SourceType.PET_TOUR, PolicyFields.builder()
                        .excludedZones(List.of()).build()),
                extracted(SourceType.GOCAMPING, PolicyFields.builder()
                        .excludedZones(List.of("실내")).build())));

        assertThat(result.fields().getExcludedZones()).containsExactly("실내");
        assertThat(result.fieldSources().get("excludedZones")).containsExactly(SourceType.GOCAMPING);
    }

    @Test
    @DisplayName("모두 빈 목록을 말했으면 모두가 이긴다")
    void 모두_빈_목록이면_모두_이긴다() {
        // 최종 값인 빈 목록을 그 소스들이 함께 만든 것임
        MergeResult result = PolicyMerger.merge(List.of(
                extracted(SourceType.PET_TOUR, PolicyFields.builder()
                        .excludedZones(List.of()).build()),
                extracted(SourceType.GOCAMPING, PolicyFields.builder()
                        .excludedZones(List.of()).build())));

        assertThat(result.fields().getExcludedZones()).isEmpty();
        assertThat(result.fieldSources().get("excludedZones"))
                .containsExactly(SourceType.PET_TOUR, SourceType.GOCAMPING);
    }

    @Test
    @DisplayName("정정 행이 이기면 스무 칸 전부 그 소스가 이긴 것으로 적힌다")
    void 정정_행은_스무_칸_전부() {
        // 비어 있는 칸도 적음 — 정정 행의 빈 칸은 "정보 없음으로 판단한 칸" 임
        // 그렇게 적어야 batch 가 그 칸의 공공 근거를 내보내지 않음
        MergeResult result = PolicyMerger.merge(List.of(
                corrected(SourceType.MANUAL,
                        PolicyFields.builder().scope(Scope.ALL_AREA).build()),
                extracted(SourceType.PET_TOUR, PolicyFields.builder()
                        .scope(Scope.PARTIAL).sizeRule(SizeRule.SMALL_ONLY).build())));

        assertThat(result.fieldSources()).hasSize(FieldSpec.ALL.size());
        assertThat(result.fieldSources().values())
                .allSatisfy(sources -> assertThat(sources).containsExactly(SourceType.MANUAL));
    }

    @Test
    @DisplayName("OWNER 와 MANUAL 이 함께 있으면 OWNER 가 적힌다")
    void OWNER_가_적힌다() {
        MergeResult result = PolicyMerger.merge(List.of(
                corrected(SourceType.MANUAL,
                        PolicyFields.builder().scope(Scope.PARTIAL).build()),
                corrected(SourceType.OWNER,
                        PolicyFields.builder().scope(Scope.ALL_AREA).build())));

        assertThat(result.fieldSources().get("sizeRule")).containsExactly(SourceType.OWNER);
    }

    @Test
    @DisplayName("승자는 조건 순서대로 적힌다")
    void 조건_순서대로_적힌다() {
        // 빌더에 넣는 순서와 상관없이 FieldSpec 순서여야 함
        // 병합 결과를 바로 받는 쪽이 조건 순서로 읽음
        // * 저장한 뒤에는 보장되지 않음 — jsonb 가 키를 다시 정렬함
        MergeResult result = PolicyMerger.merge(List.of(
                extracted(SourceType.PET_TOUR, PolicyFields.builder()
                        .advanceInquiry(true)
                        .maxWeightKg(new BigDecimal("10"))
                        .scope(Scope.PARTIAL)
                        .build())));

        assertThat(result.fieldSources().keySet())
                .containsExactly("scope", "maxWeightKg", "advanceInquiry");
    }

    private static PetPolicySource extracted(SourceType source, PolicyFields fields) {
        return PetPolicySource.extracted(PLACE_ID, source, fields,
                ExtractionMethod.RULE, null, "v1", LocalDateTime.now());
    }

    private static PetPolicySource corrected(SourceType source, PolicyFields fields) {
        return PetPolicySource.corrected(PLACE_ID, source, fields, "검증 후 정정");
    }
}
