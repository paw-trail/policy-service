package com.pawtrail.policy.presentation.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.policy.domain.enums.ExtractionMethod;
import com.pawtrail.policy.domain.enums.SourceType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * bulk 요청이 조건 이름을 막는지 확인합니다.
 *
 * 스프링을 띄우지 않고 검증기만 씁니다.
 * 컨트롤러의 @Valid 가 부르는 것과 같은 검증기이며, 여기서 보려는 것은
 * 레코드에 붙인 @AssertTrue 가 실제로 돌고 오류 경로가 어느 항목의 어느 근거인지를
 * 가리키는지입니다.
 *
 * 레코드에 @AssertTrue 를 붙인 첫 자리입니다.
 * 다른 서비스의 선례는 전부 클래스라, 레코드에서도 게터로 잡히는지를 여기서 고정합니다.
 */
class BulkUpsertRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("조건 스무 칸의 이름이면 통과한다")
    void 알려진_이름은_통과한다() {
        BulkUpsertRequest request = request(
                List.of(new EvidenceRequest("maxWeightKg", "etcAcmpyInfo", 0, "10kg 이하")),
                List.of(new ConflictRequest("sizeRule", Map.of("field", "가능", "text", "불가"))));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("근거의 조건 이름이 모르는 이름이면 그 근거를 가리켜 막는다")
    void 모르는_근거_이름은_막힌다() {
        // 한 청크에 근거가 여럿이어도 어느 것이 틀렸는지 경로로 찾을 수 있어야 함
        BulkUpsertRequest request = request(
                List.of(new EvidenceRequest("scope", "acmpyTypeCd", null, "일부구역 동반가능"),
                        new EvidenceRequest("maxWeight", "etcAcmpyInfo", 0, "10kg 이하")),
                List.of());

        Set<ConstraintViolation<BulkUpsertRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
                .isEqualTo("items[0].evidence[1].fieldNameKnown");
    }

    @Test
    @DisplayName("DB 컬럼 이름으로 보내도 막는다")
    void 컬럼_이름은_막힌다() {
        // 주석이 "pet_policy 의 컬럼 이름" 이라고 적혀 있던 자리임
        // 그 말대로 보내면 조건과 근거가 이름으로 안 이어져 근거가 빠짐
        BulkUpsertRequest request = request(
                List.of(new EvidenceRequest("max_weight_kg", "etcAcmpyInfo", 0, "10kg 이하")),
                List.of());

        assertThat(validator.validate(request)).hasSize(1);
    }

    @Test
    @DisplayName("소스 내 충돌의 조건 이름도 같은 규칙으로 막는다")
    void 모르는_충돌_이름은_막힌다() {
        BulkUpsertRequest request = request(
                List.of(),
                List.of(new ConflictRequest("size_rule", Map.of("field", "가능", "text", "불가"))));

        Set<ConstraintViolation<BulkUpsertRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
                .isEqualTo("items[0].conflicts[0].fieldNameKnown");
    }

    @Test
    @DisplayName("이름이 비어 있으면 필수 오류 하나만 뜬다")
    void 비어_있으면_오류가_하나다() {
        // 비어 있는 이름을 이름 검사까지 막으면 같은 자리에 오류가 둘 뜸
        BulkUpsertRequest request = request(
                List.of(new EvidenceRequest(" ", "etcAcmpyInfo", 0, "10kg 이하")),
                List.of());

        Set<ConstraintViolation<BulkUpsertRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
                .isEqualTo("items[0].evidence[0].fieldName");
    }

    private static BulkUpsertRequest request(List<EvidenceRequest> evidence,
                                             List<ConflictRequest> conflicts) {
        BulkItemRequest item = new BulkItemRequest(UUID.randomUUID(), SourceType.PET_TOUR,
                emptyFields(), evidence, conflicts, ExtractionMethod.RULE);
        return new BulkUpsertRequest(null, "v1", LocalDateTime.now(), List.of(item));
    }

    private static PolicyFieldsRequest emptyFields() {
        return new PolicyFieldsRequest(null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }
}
