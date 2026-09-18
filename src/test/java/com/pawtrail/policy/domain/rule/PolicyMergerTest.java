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
 * 병합 규칙을 고정합니다.
 *
 * 실데이터에서 뽑은 조합은 PolicyMergerRealDataTest 에 있습니다.
 * 나눠 둔 것은 깨졌을 때 뜻이 다르기 때문입니다.
 * 이 파일이 깨지면 규칙을 잘못 짠 것이고, 저쪽이 깨지면 실데이터에 대한 이해가 틀린 것입니다.
 */
class PolicyMergerTest {

    private static final UUID PLACE_ID = UUID.randomUUID();

    @Test
    @DisplayName("소스가 없으면 아무것도 모르는 결과를 준다")
    void 소스가_없으면_빈_결과() {
        // 소스가 전부 무효화된 장소에서 나옴. 판정은 UNKNOWN 으로 떨어짐
        assertThat(PolicyMerger.merge(List.of()).fields().isEmpty()).isTrue();
        assertThat(PolicyMerger.merge(null).fields().isEmpty()).isTrue();
        assertThat(PolicyMerger.merge(List.of()).hasConflict()).isFalse();
    }

    @Test
    @DisplayName("소스가 하나면 그 값이 그대로 최종본이 된다")
    void 소스가_하나면_그대로() {
        PetPolicySource only = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().scope(Scope.PARTIAL).build());

        MergeResult result = PolicyMerger.merge(List.of(only));

        assertThat(result.fields().getScope()).isEqualTo(Scope.PARTIAL);
        assertThat(result.sourcePriority()).isEqualTo(SourceType.PET_TOUR);
        assertThat(result.hasConflict()).isFalse();
    }

    @Test
    @DisplayName("OWNER 가 있으면 그 행이 통째로 이긴다")
    void OWNER_가_통째로_이긴다() {
        // 장소가 직접 알려준 값이라 가장 높은 티어임
        PetPolicySource owner = corrected(SourceType.OWNER,
                PolicyFields.builder().scope(Scope.ALL_AREA).build());
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder()
                        .scope(Scope.PARTIAL)
                        .maxWeightKg(new BigDecimal("10.00"))
                        .build());

        MergeResult result = PolicyMerger.merge(List.of(petTour, owner));

        assertThat(result.fields().getScope()).isEqualTo(Scope.ALL_AREA);
        assertThat(result.sourcePriority()).isEqualTo(SourceType.OWNER);
    }

    @Test
    @DisplayName("OWNER 가 비워 둔 칸은 공공 값으로 채우지 않는다")
    void OWNER_의_빈_칸은_그대로_빈다() {
        // 관리자 화면이 스무 값을 전부 보내므로 비어 있는 칸도 "정보 없음으로 판단한 칸" 임
        // 여기서 공공 값을 끌어올리면 그 판단이 뒤집힘
        PetPolicySource owner = corrected(SourceType.OWNER,
                PolicyFields.builder().scope(Scope.ALL_AREA).build());
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().maxWeightKg(new BigDecimal("10.00")).build());

        MergeResult result = PolicyMerger.merge(List.of(petTour, owner));

        assertThat(result.fields().getMaxWeightKg()).isNull();
    }

    @Test
    @DisplayName("OWNER 가 없으면 MANUAL 이 통째로 이긴다")
    void MANUAL_이_통째로_이긴다() {
        PetPolicySource manual = corrected(SourceType.MANUAL,
                PolicyFields.builder().sizeRule(SizeRule.ALL).build());
        PetPolicySource goCamping = extracted(SourceType.GOCAMPING,
                PolicyFields.builder().sizeRule(SizeRule.SMALL_ONLY).build());

        MergeResult result = PolicyMerger.merge(List.of(goCamping, manual));

        assertThat(result.fields().getSizeRule()).isEqualTo(SizeRule.ALL);
        assertThat(result.sourcePriority()).isEqualTo(SourceType.MANUAL);
    }

    @Test
    @DisplayName("정정이 이길 때는 공공 값과 달라도 충돌로 세지 않는다")
    void 정정이_이기면_충돌이_없다() {
        // 사람이 이미 확인해 값을 정한 자리임
        // 충돌로 세면 관리자가 고칠수록 배지가 안 사라지고
        // 사용자에게 "출처에 따라 조건이 다르니 확인하세요" 가 계속 뜸
        PetPolicySource manual = corrected(SourceType.MANUAL,
                PolicyFields.builder().maxWeightKg(new BigDecimal("15.00")).build());
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().maxWeightKg(new BigDecimal("10.00")).build());

        MergeResult result = PolicyMerger.merge(List.of(petTour, manual));

        assertThat(result.fields().getMaxWeightKg()).isEqualByComparingTo("15.00");
        assertThat(result.hasConflict()).isFalse();
        assertThat(result.conflicts()).isEmpty();
    }

    @Test
    @DisplayName("공공 3종은 칸마다 순위가 앞선 값을 쓴다")
    void 공공은_칸마다_앞선_값() {
        // PET_TOUR 가 앞인 것은 셋 중 동반 조건을 본업으로 가진 유일한 데이터셋이기 때문임
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().scope(Scope.PARTIAL).build());
        PetPolicySource cultureCsv = extracted(SourceType.CULTURE_CSV,
                PolicyFields.builder().scope(Scope.ALL_AREA).build());

        MergeResult result = PolicyMerger.merge(List.of(cultureCsv, petTour));

        assertThat(result.fields().getScope()).isEqualTo(Scope.PARTIAL);
        assertThat(result.sourcePriority()).isEqualTo(SourceType.PET_TOUR);
    }

    @Test
    @DisplayName("소스마다 다른 칸을 채우면 양쪽을 다 가져온다")
    void 서로_다른_칸은_다_가져온다() {
        // 필드 단위로 합치는 이유임
        // 한 소스를 통째로 쓰면 나머지가 채웠을 칸이 비고, 빈 값은 정보 없음이라
        // 판정이 UNKNOWN 으로 떨어짐. 가진 근거를 버려서 모른다고 답하는 셈임
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().scope(Scope.PARTIAL).build());
        PetPolicySource goCamping = extracted(SourceType.GOCAMPING,
                PolicyFields.builder().sizeRule(SizeRule.SMALL_ONLY).build());

        MergeResult result = PolicyMerger.merge(List.of(petTour, goCamping));

        assertThat(result.fields().getScope()).isEqualTo(Scope.PARTIAL);
        assertThat(result.fields().getSizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
        assertThat(result.hasConflict()).isFalse();
    }

    @Test
    @DisplayName("한쪽만 값이 있으면 충돌이 아니다")
    void 한쪽만_있으면_충돌_아님() {
        // 소스별 정보량 차이이지 어긋남이 아님
        // 충돌로 치면 겹친 장소 대부분에 배지가 붙어 신호가 아니게 됨
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().maxWeightKg(new BigDecimal("10.00")).build());
        PetPolicySource cultureCsv = extracted(SourceType.CULTURE_CSV,
                PolicyFields.builder().build());

        MergeResult result = PolicyMerger.merge(List.of(petTour, cultureCsv));

        assertThat(result.hasConflict()).isFalse();
        assertThat(result.fields().getMaxWeightKg()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("값이 서로 다르면 충돌로 남기고 앞선 값을 쓴다")
    void 값이_다르면_충돌() {
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().maxWeightKg(new BigDecimal("10.00")).build());
        PetPolicySource goCamping = extracted(SourceType.GOCAMPING,
                PolicyFields.builder().maxWeightKg(new BigDecimal("15.00")).build());

        MergeResult result = PolicyMerger.merge(List.of(petTour, goCamping));

        assertThat(result.fields().getMaxWeightKg()).isEqualByComparingTo("10.00");
        assertThat(result.hasConflict()).isTrue();
        assertThat(result.conflicts()).hasSize(1);

        MergeResult.FieldConflict conflict = result.conflicts().getFirst();
        assertThat(conflict.fieldName()).isEqualTo("maxWeightKg");
        assertThat(conflict.sourceValues()).containsOnlyKeys("PET_TOUR", "GOCAMPING");
    }

    @Test
    @DisplayName("범위의 동반 불가도 다른 값과 같이 충돌로 잡고 앞선 값을 쓴다")
    void 동반_불가도_충돌() {
        // 한 출처는 일부 구역이 된다고 하고 다른 출처는 안 된다고 하는 자리
        // 불가를 실내 · 실외에만 적던 때는 칸이 달라 여기서 충돌이 안 잡혔음
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().scope(Scope.PARTIAL).build());
        PetPolicySource goCamping = extracted(SourceType.GOCAMPING,
                PolicyFields.builder().scope(Scope.NONE).build());

        MergeResult result = PolicyMerger.merge(List.of(petTour, goCamping));

        assertThat(result.fields().getScope()).isEqualTo(Scope.PARTIAL);
        assertThat(result.hasConflict()).isTrue();
        assertThat(result.conflicts()).hasSize(1);

        MergeResult.FieldConflict conflict = result.conflicts().getFirst();
        assertThat(conflict.fieldName()).isEqualTo("scope");
        assertThat(conflict.sourceValues()).containsOnlyKeys("PET_TOUR", "GOCAMPING");
    }

    @Test
    @DisplayName("두 출처가 모두 동반 불가라 하면 충돌이 아니고 동반 불가가 남는다")
    void 둘_다_동반_불가() {
        PetPolicySource goCamping = extracted(SourceType.GOCAMPING,
                PolicyFields.builder().scope(Scope.NONE).indoorAllowed(false).outdoorAllowed(false).build());
        PetPolicySource cultureCsv = extracted(SourceType.CULTURE_CSV,
                PolicyFields.builder().scope(Scope.NONE).indoorAllowed(false).outdoorAllowed(false).build());

        MergeResult result = PolicyMerger.merge(List.of(goCamping, cultureCsv));

        assertThat(result.fields().getScope()).isEqualTo(Scope.NONE);
        assertThat(result.hasConflict()).isFalse();
    }

    @Test
    @DisplayName("충돌한 칸만 기록하고 나머지는 남기지 않는다")
    void 충돌한_칸만_기록() {
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder()
                        .scope(Scope.PARTIAL)
                        .maxWeightKg(new BigDecimal("10.00"))
                        .build());
        PetPolicySource goCamping = extracted(SourceType.GOCAMPING,
                PolicyFields.builder()
                        .scope(Scope.PARTIAL)
                        .maxWeightKg(new BigDecimal("15.00"))
                        .build());

        MergeResult result = PolicyMerger.merge(List.of(petTour, goCamping));

        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().getFirst().fieldName()).isEqualTo("maxWeightKg");
    }

    @Test
    @DisplayName("목록이 포함 관계면 충돌이 아니고 합집합을 쓴다")
    void 목록이_포함이면_합집합() {
        // 구역 이름은 "여기는 안 된다" 를 나열하는 것이라
        // 한 소스가 둘을 적고 다른 소스가 하나만 적었다고 그 하나만 안 되는 것이 아님
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().excludedZones(List.of("실내")).build());
        PetPolicySource goCamping = extracted(SourceType.GOCAMPING,
                PolicyFields.builder().excludedZones(List.of("실내", "수영장")).build());

        MergeResult result = PolicyMerger.merge(List.of(petTour, goCamping));

        assertThat(result.fields().getExcludedZones()).containsExactly("실내", "수영장");
        assertThat(result.hasConflict()).isFalse();
    }

    @Test
    @DisplayName("목록이 서로 겹치지 않으면 충돌이다")
    void 목록이_어긋나면_충돌() {
        // 서로 다른 구역을 지목한 것이라 실제로 정보가 엇갈린 것임
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().excludedZones(List.of("실내")).build());
        PetPolicySource goCamping = extracted(SourceType.GOCAMPING,
                PolicyFields.builder().excludedZones(List.of("수영장")).build());

        MergeResult result = PolicyMerger.merge(List.of(petTour, goCamping));

        assertThat(result.hasConflict()).isTrue();
        assertThat(result.conflicts().getFirst().fieldName()).isEqualTo("excludedZones");
        // 충돌이어도 합집합은 그대로 씀 — 어느 쪽이 맞는지 모르므로 둘 다 안내함
        assertThat(result.fields().getExcludedZones()).containsExactly("실내", "수영장");
    }

    @Test
    @DisplayName("빈 목록은 정보 없음이 아니라 해당 없음이다")
    void 빈_목록은_값이다() {
        // "제한 구역이 없다" 고 말한 것이므로 값으로 셈
        // null 과 같게 다루면 그 사실이 사라짐
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().excludedZones(List.of()).build());
        PetPolicySource cultureCsv = extracted(SourceType.CULTURE_CSV,
                PolicyFields.builder().build());

        MergeResult result = PolicyMerger.merge(List.of(petTour, cultureCsv));

        assertThat(result.fields().getExcludedZones()).isEmpty();
    }

    @Test
    @DisplayName("false 는 정보 없음이 아니라 요구하지 않음이다")
    void false_는_값이다() {
        // 판정이 이것으로 갈림
        // null 이면 "접종 증명이 필요한지 모른다" 이고 false 면 "필요 없다" 임
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().vaccineProof(false).build());
        PetPolicySource goCamping = extracted(SourceType.GOCAMPING,
                PolicyFields.builder().vaccineProof(true).build());

        MergeResult result = PolicyMerger.merge(List.of(petTour, goCamping));

        assertThat(result.fields().getVaccineProof()).isFalse();
        assertThat(result.hasConflict()).isTrue();
    }

    @Test
    @DisplayName("소스 순서가 뒤바뀌어도 결과가 같다")
    void 입력_순서를_안_탄다() {
        // 적재 순서에 따라 결과가 달라지면 재병합할 때마다 판이 오름
        PolicyFields petTourFields = PolicyFields.builder().scope(Scope.PARTIAL).build();
        PolicyFields cultureFields = PolicyFields.builder().scope(Scope.ALL_AREA).build();

        MergeResult forward = PolicyMerger.merge(List.of(
                extracted(SourceType.PET_TOUR, petTourFields),
                extracted(SourceType.CULTURE_CSV, cultureFields)));
        MergeResult backward = PolicyMerger.merge(List.of(
                extracted(SourceType.CULTURE_CSV, cultureFields),
                extracted(SourceType.PET_TOUR, petTourFields)));

        assertThat(forward.fields().getScope()).isEqualTo(backward.fields().getScope());
        assertThat(forward.sourcePriority()).isEqualTo(backward.sourcePriority());
        assertThat(forward.hasConflict()).isEqualTo(backward.hasConflict());
    }

    @Test
    @DisplayName("소수 표기가 달라도 같은 값이면 충돌이 아니다")
    void 소수_표기가_달라도_같은_값() {
        // BigDecimal 은 10 과 10.00 을 equals 로 다르게 봄
        // 소스마다 표기가 갈리므로 없는 충돌이 잡히면 안 됨
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().maxWeightKg(new BigDecimal("10")).build());
        PetPolicySource goCamping = extracted(SourceType.GOCAMPING,
                PolicyFields.builder().maxWeightKg(new BigDecimal("10.00")).build());

        MergeResult result = PolicyMerger.merge(List.of(petTour, goCamping));

        assertThat(result.hasConflict()).isFalse();
    }

    private static PetPolicySource extracted(SourceType source, PolicyFields fields) {
        return PetPolicySource.extracted(PLACE_ID, source, fields,
                ExtractionMethod.RULE, null, "v1", LocalDateTime.now());
    }

    private static PetPolicySource corrected(SourceType source, PolicyFields fields) {
        return PetPolicySource.corrected(PLACE_ID, source, fields, "검증 후 정정");
    }
}
