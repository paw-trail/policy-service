package com.pawtrail.policy.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.policy.domain.enums.Scope;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 칸 라벨과 값 문장을 고정합니다.
 *
 * 공개 충돌 목록이 이 문장을 그대로 내보냅니다.
 * 값은 jsonb 에서 읽은 모양으로 오므로, 메모리의 모양과 같은 문장이 되는지를 함께 봅니다.
 * 둘이 갈리면 병합 직후와 다시 읽은 뒤의 화면이 다른 말을 하게 됩니다.
 */
class FieldSpecTextTest {

    @Test
    @DisplayName("조건 스무 칸이 전부 비어 있지 않은 서로 다른 라벨을 가진다")
    void 스무_칸_모두_라벨이_있다() {
        assertThat(FieldSpec.ALL).allSatisfy(spec -> assertThat(spec.label()).isNotBlank());
        assertThat(FieldSpec.ALL).extracting(FieldSpec::label).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("불리언 칸은 칸마다 정한 말로 바뀐다")
    void 불리언_칸() {
        assertThat(FieldSpec.textOf("indoorAllowed", false)).isEqualTo("불가");
        assertThat(FieldSpec.textOf("carrierRequired", true)).isEqualTo("필요");
        assertThat(FieldSpec.textOf("weightInclusive", false)).isEqualTo("미만");
    }

    @Test
    @DisplayName("열거 칸은 jsonb 의 이름 문자열이든 메모리의 열거값이든 같은 말이 된다")
    void 열거_칸() {
        assertThat(FieldSpec.textOf("scope", "PARTIAL")).isEqualTo("일부 구역");
        assertThat(FieldSpec.textOf("scope", Scope.PARTIAL)).isEqualTo("일부 구역");
        assertThat(FieldSpec.textOf("sizeRule", "SMALL_ONLY")).isEqualTo("소형견만");
    }

    @Test
    @DisplayName("숫자 칸은 뒷자리 0 없이 단위를 붙인다")
    void 숫자_칸() {
        // jsonb 에서 읽으면 10.00 이 10.0 으로 오고 메모리에서는 BigDecimal 10.00 임
        assertThat(FieldSpec.textOf("maxWeightKg", 10.0)).isEqualTo("10kg");
        assertThat(FieldSpec.textOf("maxWeightKg", new BigDecimal("10.00"))).isEqualTo("10kg");
        assertThat(FieldSpec.textOf("maxWeightKg", new BigDecimal("7.50"))).isEqualTo("7.5kg");
        assertThat(FieldSpec.textOf("maxCount", 2)).isEqualTo("2마리까지");
        assertThat(FieldSpec.textOf("extraFeeAmount", 5000)).isEqualTo("5,000원");
    }

    @Test
    @DisplayName("목록 칸은 쉼표로 잇고 비어 있으면 없음이라 적는다")
    void 목록_칸() {
        assertThat(FieldSpec.textOf("excludedZones", List.of("실내", "잔디"))).isEqualTo("실내, 잔디");
        assertThat(FieldSpec.textOf("requiredItems", List.of())).isEqualTo("없음");
    }

    @Test
    @DisplayName("모르는 칸이면 라벨은 이름 그대로 · 값은 문자열 그대로 둔다")
    void 모르는_칸() {
        // bulk 가 막기 전에 들어간 행이 있어도 화면을 죽이지 않음
        assertThat(FieldSpec.labelOf("max_weight_kg")).isEqualTo("max_weight_kg");
        assertThat(FieldSpec.textOf("max_weight_kg", 10)).isEqualTo("10");
    }
}
