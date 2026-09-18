package com.pawtrail.policy.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.policy.domain.enums.ExtractionMethod;
import com.pawtrail.policy.domain.enums.Scope;
import com.pawtrail.policy.domain.enums.SizeRule;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PetPolicySource;
import com.pawtrail.policy.domain.model.PolicyFields;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 실데이터에서 나온 조합으로 병합을 확인합니다.
 *
 * 규칙 자체를 고정하는 것은 PolicyMergerTest 이고 여기는 실측입니다.
 * 이 파일이 깨지면 규칙이 틀린 것이 아니라 실데이터에 대한 이해가 틀린 것입니다.
 *
 * <b>실측 기준 (2026.9.15)</b>
 * <pre>
 * 병합이 실제로 일할 장소      124곳
 *   CULTURE_CSV + PET_TOUR   109
 *   GOCAMPING + PET_TOUR       9
 *   CULTURE_CSV + GOCAMPING    5
 *   세 소스 전부                1
 *
 * 겹친 장소는 3,413곳이나 그중 3,268곳이 동물병원입니다.
 * 동물병원은 동반 조건이 없어 원재료 표에 행이 생기지 않으므로 병합 대상이 아닙니다.
 * 이것을 빼지 않으면 규모를 스무 배 넘게 잘못 봅니다.
 * </pre>
 *
 * <b>원문 값을 조건 필드로 옮기는 매핑은 extract 가 정합니다.</b>
 * 아래는 extract 가 정한 매핑이고(2026.9.18) 테스트가 그대로 따릅니다.
 * 병합이 맞게 도는지를 보는 것이 목적이라 매핑이 바뀌어도 규칙 검증은 그대로 유효합니다.
 * <pre>
 * 공사 "전구역 동반가능"         → scope = ALL_AREA            실내 · 실외로 넓히지 않음
 * 공사 "일부구역 동반가능"       → scope = PARTIAL
 * 문화정보원 실내 · 실외 Y/N     → indoorAllowed · outdoorAllowed
 * 고캠핑 "가능"                 → outdoorAllowed = true        실내는 원문이 말하지 않음
 * 고캠핑 "가능(소형견)"          → outdoorAllowed = true, sizeRule = SMALL_ONLY
 * 불가 — 공사 "불가" · 고캠핑 "불가능" · 문화정보원 동반 N
 *                              → scope = NONE, indoorAllowed = false, outdoorAllowed = false
 * </pre>
 * 불가를 범위 칸에도 적는 까닭 — 실내 · 실외에만 적으면 "일부 구역 가능" 을 말한 다른 출처와
 * 칸이 달라 정면으로 갈리는데도 충돌로 잡히지 않습니다. 이 파일의 두 테스트가 그 자리입니다.
 */
class PolicyMergerRealDataTest {

    private static final UUID PLACE_ID = UUID.randomUUID();

    @Test
    @DisplayName("가장 흔한 조합 — 공사가 범위를 말하고 문화정보원이 실내외를 말한다")
    void 공사_범위와_문화정보원_실내외() {
        // 일부구역 + 실내N/실외Y 가 57곳으로 가장 많음
        // 두 소스가 서로 다른 칸을 채우는 전형이며 이것이 필드 단위 병합의 근거임
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().scope(Scope.PARTIAL).build());
        PetPolicySource cultureCsv = extracted(SourceType.CULTURE_CSV,
                PolicyFields.builder().indoorAllowed(false).outdoorAllowed(true).build());

        MergeResult result = PolicyMerger.merge(List.of(petTour, cultureCsv));

        assertThat(result.fields().getScope()).isEqualTo(Scope.PARTIAL);
        assertThat(result.fields().getIndoorAllowed()).isFalse();
        assertThat(result.fields().getOutdoorAllowed()).isTrue();
        assertThat(result.hasConflict()).isFalse();
        assertThat(result.sourcePriority()).isEqualTo(SourceType.PET_TOUR);
    }

    @Test
    @DisplayName("전구역 가능인데 실내가 안 되는 조합도 충돌이 아니다")
    void 전구역_가능과_실내_불가() {
        // 40곳. 얼핏 어긋나 보이나 칸이 달라 우리 규칙으로는 충돌이 아님
        // 범위와 실내외를 사람이 다시 읽어야 하는 자리이며 판정은 RuleEngine 이 함
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().scope(Scope.ALL_AREA).build());
        PetPolicySource cultureCsv = extracted(SourceType.CULTURE_CSV,
                PolicyFields.builder().indoorAllowed(false).outdoorAllowed(true).build());

        MergeResult result = PolicyMerger.merge(List.of(petTour, cultureCsv));

        assertThat(result.fields().getScope()).isEqualTo(Scope.ALL_AREA);
        assertThat(result.fields().getIndoorAllowed()).isFalse();
        assertThat(result.hasConflict()).isFalse();
    }

    @Test
    @DisplayName("공사는 일부 가능인데 문화정보원은 동반 불가라고 한다")
    void 공사와_문화정보원이_정면으로_갈린다() {
        // 2곳. 문화정보원은 동반 N 이면 실내 · 실외도 늘 N 이라 셋이 함께 불가로 옴
        // 불가를 실내 · 실외에만 적던 때는 공사의 범위와 칸이 달라 충돌이 아니었음
        // 지금은 범위 칸에서 부딪혀 배지가 붙음
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().scope(Scope.PARTIAL).build());
        PetPolicySource cultureCsv = extracted(SourceType.CULTURE_CSV,
                PolicyFields.builder().scope(Scope.NONE).indoorAllowed(false).outdoorAllowed(false).build());

        MergeResult result = PolicyMerger.merge(List.of(petTour, cultureCsv));

        assertThat(result.hasConflict()).isTrue();
        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().getFirst().fieldName()).isEqualTo("scope");

        // 공사가 앞서므로 범위는 그 값이 남음
        assertThat(result.fields().getScope()).isEqualTo(Scope.PARTIAL);
        // 문화정보원만 말한 실내 · 실외는 그대로 들어옴
        assertThat(result.fields().getIndoorAllowed()).isFalse();
        assertThat(result.fields().getOutdoorAllowed()).isFalse();
    }

    @Test
    @DisplayName("공사는 일부 가능인데 고캠핑은 불가능이라고 한다")
    void 공사와_고캠핑이_정면으로_갈린다() {
        // 공사 일부구역 대 고캠핑 불가능 5곳 (2026.9.13 원문 덤프로 다시 셈)
        // 영월키즈캠핑장 · 금방아 민박캠핑장 · 명파해변오토캠핑장 · 여울소리 · 문암생태공원
        // 같은 기관(한국관광공사)의 두 데이터셋이 다른 말을 하는 자리임
        //
        // 이름을 대조해 거짓 병합이 아님을 확인했음 — 띄어쓰기만 다른 같은 곳임
        // 사용자가 한쪽만 보고 갔다가 못 들어가는 상황이 실재하며
        // 그것을 배지로 알리는 것이 이 서비스를 만든 이유임
        //
        // 공사의 동반 구분은 범위만 말하고 실내 · 실외는 채우지 않음
        // 그래서 두 출처가 부딪히는 칸은 범위 하나임
        // 예전 테스트는 불가를 실내 · 실외에만 적던 때라 공사에 실외 true 를 두어 충돌을 만들었음
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().scope(Scope.PARTIAL).build());
        PetPolicySource goCamping = extracted(SourceType.GOCAMPING,
                PolicyFields.builder().scope(Scope.NONE).indoorAllowed(false).outdoorAllowed(false).build());

        MergeResult result = PolicyMerger.merge(List.of(petTour, goCamping));

        assertThat(result.hasConflict()).isTrue();
        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().getFirst().fieldName()).isEqualTo("scope");

        // 공사가 앞서므로 범위는 그 값이 남음
        assertThat(result.fields().getScope()).isEqualTo(Scope.PARTIAL);
        // 고캠핑만 말한 칸은 그대로 들어옴
        assertThat(result.fields().getIndoorAllowed()).isFalse();
        assertThat(result.fields().getOutdoorAllowed()).isFalse();
    }

    @Test
    @DisplayName("고캠핑이 소형견만 가능이라고 하면 크기 제한으로 들어온다")
    void 고캠핑_소형견_제한() {
        // 2곳. 고캠핑이 조건을 정형 값으로 주는 유일한 칸임
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
    @DisplayName("문화정보원과 고캠핑만 있으면 고캠핑이 앞선다 — 젠틀펫 파라다이스")
    void 문화정보원과_고캠핑() {
        // 5곳. 공사가 없는 조합이며 둘 사이의 순서가 실제로 쓰이는 자리임
        // 고캠핑은 가능 셋 · 가능(소형견) 하나 · 불가능 하나 (2026.9.13 원문 덤프)
        // 실외에서 갈리는 곳이 둘 — 젠틀펫 파라다이스(가능 대 실외 N) · 소풍정원 캠핑장(아래 테스트)
        PetPolicySource goCamping = extracted(SourceType.GOCAMPING,
                PolicyFields.builder().outdoorAllowed(true).build());
        PetPolicySource cultureCsv = extracted(SourceType.CULTURE_CSV,
                PolicyFields.builder().indoorAllowed(true).outdoorAllowed(false).build());

        MergeResult result = PolicyMerger.merge(List.of(cultureCsv, goCamping));

        assertThat(result.sourcePriority()).isEqualTo(SourceType.GOCAMPING);
        assertThat(result.fields().getOutdoorAllowed()).isTrue();
        // 고캠핑은 실내를 말하지 않아 문화정보원 값이 그대로 들어옴
        assertThat(result.fields().getIndoorAllowed()).isTrue();
        assertThat(result.hasConflict()).isTrue();
        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().getFirst().fieldName()).isEqualTo("outdoorAllowed");
    }

    @Test
    @DisplayName("고캠핑은 불가능인데 문화정보원은 동반 가능이면 실외에서 갈린다 — 소풍정원 캠핑장")
    void 고캠핑_불가능과_문화정보원_가능() {
        // 문화정보원 동반 Y 는 범위를 채우지 않으므로 범위는 고캠핑만 말함 — 충돌 아님
        // 실외는 고캠핑 false 대 문화정보원 Y 로 부딪힘
        PetPolicySource goCamping = extracted(SourceType.GOCAMPING,
                PolicyFields.builder().scope(Scope.NONE).indoorAllowed(false).outdoorAllowed(false).build());
        PetPolicySource cultureCsv = extracted(SourceType.CULTURE_CSV,
                PolicyFields.builder().indoorAllowed(false).outdoorAllowed(true).build());

        MergeResult result = PolicyMerger.merge(List.of(cultureCsv, goCamping));

        assertThat(result.fields().getScope()).isEqualTo(Scope.NONE);
        assertThat(result.fields().getOutdoorAllowed()).isFalse();
        assertThat(result.hasConflict()).isTrue();
        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().getFirst().fieldName()).isEqualTo("outdoorAllowed");
    }

    @Test
    @DisplayName("세 소스가 모두 붙은 유일한 장소 — 문암생태공원")
    void 세_소스가_모두_붙은_곳() {
        // 01a09015-b6bc-7812-8e7e-d0c59c46b007
        // 3단 병합과 충돌을 한 번에 보는 유일한 실물임
        //
        // 공사        일부구역 동반가능
        // 문화정보원   실내 N · 실외 Y
        // 고캠핑      불가능
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().scope(Scope.PARTIAL).build());
        PetPolicySource goCamping = extracted(SourceType.GOCAMPING,
                PolicyFields.builder().scope(Scope.NONE).indoorAllowed(false).outdoorAllowed(false).build());
        PetPolicySource cultureCsv = extracted(SourceType.CULTURE_CSV,
                PolicyFields.builder().indoorAllowed(false).outdoorAllowed(true).build());

        MergeResult result = PolicyMerger.merge(List.of(cultureCsv, petTour, goCamping));

        assertThat(result.sourcePriority()).isEqualTo(SourceType.PET_TOUR);
        assertThat(result.fields().getScope()).isEqualTo(Scope.PARTIAL);

        // 셋 중 둘만 실내를 말했고 값이 같아 충돌이 아님
        assertThat(result.fields().getIndoorAllowed()).isFalse();

        // 실외는 고캠핑과 문화정보원이 갈림. 고캠핑이 앞서므로 그 값이 남음
        assertThat(result.fields().getOutdoorAllowed()).isFalse();
        assertThat(result.hasConflict()).isTrue();

        // 범위는 공사 일부 구역 대 고캠핑 동반 불가 · 실외는 고캠핑 대 문화정보원
        assertThat(result.conflicts()).extracting(MergeResult.FieldConflict::fieldName)
                .containsExactly("scope", "outdoorAllowed");
        assertThat(result.conflicts().get(0).sourceValues()).containsOnlyKeys("PET_TOUR", "GOCAMPING");
        assertThat(result.conflicts().get(1).sourceValues()).containsOnlyKeys("GOCAMPING", "CULTURE_CSV");
    }

    @Test
    @DisplayName("관리자가 정정하면 실데이터 충돌이 닫힌다")
    void 정정이_충돌을_닫는다() {
        // 위 문암생태공원을 관리자가 확인해 값을 정한 상태임
        // 정정이 통째로 이기므로 공공 소스끼리의 어긋남은 더 이상 세지 않음
        // 이것이 없으면 관리자가 고칠수록 배지가 안 사라짐
        PetPolicySource petTour = extracted(SourceType.PET_TOUR,
                PolicyFields.builder().scope(Scope.PARTIAL).build());
        PetPolicySource goCamping = extracted(SourceType.GOCAMPING,
                PolicyFields.builder().scope(Scope.NONE).indoorAllowed(false).outdoorAllowed(false).build());
        PetPolicySource cultureCsv = extracted(SourceType.CULTURE_CSV,
                PolicyFields.builder().indoorAllowed(false).outdoorAllowed(true).build());
        PetPolicySource manual = PetPolicySource.corrected(PLACE_ID, SourceType.MANUAL,
                PolicyFields.builder()
                        .scope(Scope.PARTIAL)
                        .indoorAllowed(false)
                        .outdoorAllowed(true)
                        .build(),
                "전화로 확인함. 야외 산책로만 동반 가능");

        MergeResult result = PolicyMerger.merge(
                List.of(cultureCsv, petTour, goCamping, manual));

        assertThat(result.sourcePriority()).isEqualTo(SourceType.MANUAL);
        assertThat(result.fields().getOutdoorAllowed()).isTrue();
        assertThat(result.hasConflict()).isFalse();
        assertThat(result.conflicts()).isEmpty();
    }

    private static PetPolicySource extracted(SourceType source, PolicyFields fields) {
        return PetPolicySource.extracted(PLACE_ID, source, fields,
                ExtractionMethod.RULE, null, "v1", LocalDateTime.now());
    }
}
