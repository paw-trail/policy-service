package com.pawtrail.policy.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.policy.domain.enums.ExtractionMethod;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PolicyEvidence;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * batch 가 내보내는 근거를 고르는 규칙과 그 지문을 고정합니다.
 *
 * batch 조회와 재병합이 이 규칙 하나를 씁니다.
 * 고르는 규칙이 틀리면 진 소스의 문구가 출처로 뜨고,
 * 지문이 흔들리면 아무것도 안 바뀐 재병합에서 판이 올라 이벤트가 나갑니다.
 */
class AdoptedEvidenceTest {

    private static final UUID PLACE = UUID.fromString("01a09015-b6bc-7812-8e7e-d0c59c46b007");

    @Test
    @DisplayName("칸마다 이긴 소스의 근거만 고른다")
    void 이긴_소스의_근거만_고른다() {
        List<PolicyEvidence> evidences = List.of(
                evidence(SourceType.PET_TOUR, "scope", "acmpyTypeCd", null, "일부구역 동반가능"),
                evidence(SourceType.GOCAMPING, "scope", "animalCmgCl", null, "불가능"),
                evidence(SourceType.GOCAMPING, "sizeRule", "animalCmgCl", null, "소형견만"));
        Function<String, List<SourceType>> sourcesOf = winners(Map.of(
                "scope", List.of(SourceType.PET_TOUR),
                "sizeRule", List.of(SourceType.GOCAMPING)));

        List<PolicyEvidence> adopted = AdoptedEvidence.select(evidences, sourcesOf);

        assertThat(adopted).extracting(PolicyEvidence::getSegmentText)
                .containsExactly("일부구역 동반가능", "소형견만");
    }

    @Test
    @DisplayName("승자가 적혀 있지 않은 칸의 근거는 담지 않는다")
    void 승자가_없는_칸은_뺀다() {
        // V22 이전 행이나 아무 소스도 말하지 않은 칸 — 틀린 근거보다 빠진 근거가 낫다
        List<PolicyEvidence> evidences = List.of(
                evidence(SourceType.PET_TOUR, "scope", "acmpyTypeCd", null, "일부구역 동반가능"));

        assertThat(AdoptedEvidence.select(evidences, winners(Map.of()))).isEmpty();
    }

    @Test
    @DisplayName("조건 순서 → 소스 순서 → 조각 번호 순으로 늘어놓는다")
    void 조건_소스_조각_순서() {
        List<PolicyEvidence> evidences = List.of(
                evidence(SourceType.CULTURE_CSV, "excludedZones", "제한사항", 1, "문화정보원 구역"),
                evidence(SourceType.PET_TOUR, "excludedZones", "etcAcmpyInfo", 2, "공사 구역 둘째"),
                evidence(SourceType.PET_TOUR, "scope", "acmpyTypeCd", null, "공사 범위"),
                evidence(SourceType.PET_TOUR, "excludedZones", "etcAcmpyInfo", 1, "공사 구역 첫째"));
        Function<String, List<SourceType>> sourcesOf = winners(Map.of(
                "scope", List.of(SourceType.PET_TOUR),
                "excludedZones", List.of(SourceType.PET_TOUR, SourceType.CULTURE_CSV)));

        assertThat(AdoptedEvidence.select(evidences, sourcesOf))
                .extracting(PolicyEvidence::getSegmentText)
                .containsExactly("공사 범위", "공사 구역 첫째", "공사 구역 둘째", "문화정보원 구역");
    }

    @Test
    @DisplayName("앞의 셋이 같은 근거는 원문 필드와 문구로 순서를 못 박는다")
    void 끝까지_순서를_못_박는다() {
        // 조건 · 소스 · 조각 번호가 같은 근거가 둘이면 DB 가 돌려준 순서가 남는데 그 순서는 보장되지 않음
        PolicyEvidence first = evidence(SourceType.GOCAMPING, "requiredItems", "caravAcmpnyAt", null, "배변봉투");
        PolicyEvidence second = evidence(SourceType.GOCAMPING, "requiredItems", "intro", null, "목줄");
        Function<String, List<SourceType>> sourcesOf =
                winners(Map.of("requiredItems", List.of(SourceType.GOCAMPING)));

        assertThat(AdoptedEvidence.select(List.of(second, first), sourcesOf)).containsExactly(first, second);
        assertThat(AdoptedEvidence.select(List.of(first, second), sourcesOf)).containsExactly(first, second);
    }

    @Test
    @DisplayName("같은 근거는 들어온 순서가 달라도 지문이 같다")
    void 들어온_순서가_달라도_지문이_같다() {
        PolicyEvidence scope = evidence(SourceType.PET_TOUR, "scope", "acmpyTypeCd", null, "일부구역 동반가능");
        PolicyEvidence size = evidence(SourceType.GOCAMPING, "sizeRule", "animalCmgCl", null, "소형견만");
        Function<String, List<SourceType>> sourcesOf = winners(Map.of(
                "scope", List.of(SourceType.PET_TOUR),
                "sizeRule", List.of(SourceType.GOCAMPING)));

        String forward = AdoptedEvidence.digest(AdoptedEvidence.select(List.of(scope, size), sourcesOf));
        String backward = AdoptedEvidence.digest(AdoptedEvidence.select(List.of(size, scope), sourcesOf));

        assertThat(forward).isEqualTo(backward);
    }

    @Test
    @DisplayName("다시 넣은 근거는 식별자가 달라도 지문이 같다")
    void 다시_넣어도_지문이_같다() {
        // 적재는 근거를 지우고 다시 넣음 — 같은 내용이면 판이 오르면 안 됨
        PolicyEvidence before = evidence(SourceType.PET_TOUR, "scope", "acmpyTypeCd", null, "일부구역 동반가능");
        PolicyEvidence after = evidence(SourceType.PET_TOUR, "scope", "acmpyTypeCd", null, "일부구역 동반가능");

        assertThat(AdoptedEvidence.digest(List.of(before))).isEqualTo(AdoptedEvidence.digest(List.of(after)));
    }

    @Test
    @DisplayName("문구가 바뀌면 지문이 바뀐다")
    void 문구가_바뀌면_지문이_바뀐다() {
        String before = AdoptedEvidence.digest(List.of(
                evidence(SourceType.PET_TOUR, "scope", "acmpyTypeCd", null, "일부구역 동반가능")));
        String after = AdoptedEvidence.digest(List.of(
                evidence(SourceType.PET_TOUR, "scope", "acmpyTypeCd", null, "야외 테라스만 동반 가능")));

        assertThat(before).isNotEqualTo(after);
    }

    @Test
    @DisplayName("조각 번호가 없는 것과 0 번 조각은 지문이 다르다")
    void 조각_번호_없음과_0_은_다르다() {
        String none = AdoptedEvidence.digest(List.of(
                evidence(SourceType.PET_TOUR, "scope", "acmpyTypeCd", null, "일부구역 동반가능")));
        String zero = AdoptedEvidence.digest(List.of(
                evidence(SourceType.PET_TOUR, "scope", "acmpyTypeCd", 0, "일부구역 동반가능")));

        assertThat(none).isNotEqualTo(zero);
    }

    @Test
    @DisplayName("값의 경계가 옮겨진 두 근거는 이어 붙인 글자가 같아도 지문이 다르다")
    void 값의_경계가_달라도_가린다() {
        // 구분 문자로만 이으면 "ab" + "c" 와 "a" + "bc" 가 같은 문자열이 됨
        String left = AdoptedEvidence.digest(List.of(
                evidence(SourceType.PET_TOUR, "scope", "ab", null, "c")));
        String right = AdoptedEvidence.digest(List.of(
                evidence(SourceType.PET_TOUR, "scope", "a", null, "bc")));

        assertThat(left).isNotEqualTo(right);
    }

    @Test
    @DisplayName("근거가 하나도 없어도 64자 지문이 나오고 근거가 있을 때와 다르다")
    void 빈_목록도_지문이_있다() {
        String empty = AdoptedEvidence.digest(List.of());
        String one = AdoptedEvidence.digest(List.of(
                evidence(SourceType.PET_TOUR, "scope", "acmpyTypeCd", null, "일부구역 동반가능")));

        assertThat(empty).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(empty).isNotEqualTo(one);
    }

    @Test
    @DisplayName("문구가 같고 추출 방식만 다른 두 줄은 방식 순서로 늘어놓는다")
    void 방식까지_순서를_못_박는다() {
        // 규칙과 모델이 같은 원문 칸의 같은 문구를 근거로 대면 앞의 다섯이 모두 같음
        PolicyEvidence rule = evidence(SourceType.PET_TOUR, "scope", "acmpyPsblCpam", null, "불가",
                ExtractionMethod.RULE);
        PolicyEvidence model = evidence(SourceType.PET_TOUR, "scope", "acmpyPsblCpam", null, "불가",
                ExtractionMethod.LLM);
        Function<String, List<SourceType>> sourcesOf = winners(Map.of("scope", List.of(SourceType.PET_TOUR)));

        assertThat(AdoptedEvidence.select(List.of(model, rule), sourcesOf)).containsExactly(rule, model);
        assertThat(AdoptedEvidence.select(List.of(rule, model), sourcesOf)).containsExactly(rule, model);
    }

    @Test
    @DisplayName("추출 방식이 바뀌면 지문이 바뀐다")
    void 방식이_바뀌면_지문이_바뀐다() {
        // 판정 화면의 출처 표시가 달라지므로 판이 올라야 함
        String rule = AdoptedEvidence.digest(List.of(
                evidence(SourceType.PET_TOUR, "scope", "acmpyPsblCpam", null, "불가", ExtractionMethod.RULE)));
        String model = AdoptedEvidence.digest(List.of(
                evidence(SourceType.PET_TOUR, "scope", "acmpyPsblCpam", null, "불가", ExtractionMethod.LLM)));

        assertThat(rule).isNotEqualTo(model);
    }

    private static PolicyEvidence evidence(SourceType source, String fieldName, String originField,
                                           Integer segmentIndex, String segmentText) {
        return evidence(source, fieldName, originField, segmentIndex, segmentText, ExtractionMethod.RULE);
    }

    private static PolicyEvidence evidence(SourceType source, String fieldName, String originField,
                                           Integer segmentIndex, String segmentText,
                                           ExtractionMethod extractionMethod) {
        return PolicyEvidence.of(PLACE, source, fieldName, originField, segmentIndex, segmentText,
                extractionMethod);
    }

    private static Function<String, List<SourceType>> winners(Map<String, List<SourceType>> table) {
        return fieldName -> table.getOrDefault(fieldName, List.of());
    }
}
