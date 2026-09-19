package com.pawtrail.policy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pawtrail.policy.IntegrationTestSupport;
import com.pawtrail.policy.application.dto.output.ConflictOutput;
import com.pawtrail.policy.application.dto.output.ConflictValueOutput;
import com.pawtrail.policy.application.dto.output.EvidenceOutput;
import com.pawtrail.policy.application.dto.output.PolicyBatchOutput;
import com.pawtrail.policy.application.dto.output.PolicyFieldsOutput;
import com.pawtrail.policy.domain.enums.BreedRule;
import com.pawtrail.policy.domain.enums.ConflictType;
import com.pawtrail.policy.domain.enums.ExtraFeeUnit;
import com.pawtrail.policy.domain.enums.ExtractionMethod;
import com.pawtrail.policy.domain.enums.Scope;
import com.pawtrail.policy.domain.enums.SizeRule;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PetPolicySource;
import com.pawtrail.policy.domain.model.PolicyFields;
import com.pawtrail.policy.domain.repository.PetPolicySourceRepository;
import com.pawtrail.policy.presentation.request.BulkItemRequest;
import com.pawtrail.policy.presentation.request.BulkUpsertRequest;
import com.pawtrail.policy.presentation.request.ConflictRequest;
import com.pawtrail.policy.presentation.request.EvidenceRequest;
import com.pawtrail.policy.presentation.request.PolicyFieldsRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * batch 조회를 실제 DB 로 확인합니다.
 *
 * 적재로 데이터를 만들고 영속성 컨텍스트를 비운 뒤 읽습니다.
 * 같은 트랜잭션 안에서 바로 읽으면 캐시된 객체가 돌아와 jsonb 와 임베디드를 거치지 않으므로
 * verdict 가 실제로 부를 때와 다른 길을 보게 됩니다.
 * 스무 칸이 모두 비어 있는 행이 null 로 읽히는 문제도 비워야만 드러났습니다.
 */
@Transactional
class PolicyQueryServiceTest extends IntegrationTestSupport {

    @Autowired
    private PolicyQueryService policyQueryService;

    @Autowired
    private PolicyBulkService policyBulkService;

    @Autowired
    private PolicyMergeService policyMergeService;

    @Autowired
    private PetPolicySourceRepository petPolicySourceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("조건 행이 있는 장소만 요청 순서대로 담고 없는 장소는 빠진다")
    void 행이_없는_장소는_빠진다() {
        // 빠진 장소를 verdict 가 UNKNOWN 으로 채움
        // 빈 조건을 지어 담으면 동물병원 · 추출 전 · 없는 식별자가 전부 "조건이 비어 있다" 가 됨
        UUID first = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(first, SourceType.PET_TOUR,
                        fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build())),
                item(second, SourceType.PET_TOUR,
                        fieldsOf(PolicyFields.builder().scope(Scope.ALL_AREA).build()))));
        clear();

        List<PolicyBatchOutput> result =
                policyQueryService.findByPlaceIds(List.of(second, missing, first));

        assertThat(result).extracting(PolicyBatchOutput::placeId).containsExactly(second, first);
    }

    @Test
    @DisplayName("스무 칸이 모두 비어 있는 행은 빠지지 않고 담긴다")
    void 스무_칸이_빈_행은_담긴다() {
        // "추출은 했으나 원문에 조건이 없음" 이라 행이 없는 것과 뜻이 다름
        // 하이버네이트가 이 행의 조건을 null 로 읽으므로 엔티티 게터가 막지 않으면 여기서 멈춤
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR, fieldsOf(PolicyFields.empty()))));
        clear();

        List<PolicyBatchOutput> result = policyQueryService.findByPlaceIds(List.of(placeId));

        assertThat(result).hasSize(1);
        PolicyFieldsOutput fields = result.getFirst().fields();
        assertThat(fields.scope()).isNull();
        assertThat(fields.vaccineProof()).isNull();
        assertThat(fields.excludedZones()).isNull();
        assertThat(result.getFirst().evidence()).isEmpty();
    }

    @Test
    @DisplayName("중복과 null 은 걸러 낸다")
    void 중복과_null_은_걸러_낸다() {
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR,
                        fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build()))));
        clear();

        List<PolicyBatchOutput> result =
                policyQueryService.findByPlaceIds(Arrays.asList(placeId, null, placeId));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("빈 목록이면 조회하지 않고 빈 결과를 준다")
    void 빈_목록이면_빈_결과() {
        assertThat(policyQueryService.findByPlaceIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("스무 칸이 다 찬 행은 응답에도 스무 칸이 그대로 나온다")
    void 스무_칸이_응답에_그대로_나온다() {
        // 출력 DTO 로 옮기는 코드가 손으로 쓴 스무 줄이라
        // 한 줄을 빠뜨려도 컴파일이 통과하고 그 칸만 응답에서 조용히 빠짐
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR, fieldsOf(filled()))));
        clear();

        PolicyBatchOutput output = policyQueryService.findByPlaceIds(List.of(placeId)).getFirst();
        PolicyFieldsOutput fields = output.fields();

        assertThat(fields.scope()).isEqualTo(Scope.PARTIAL);
        assertThat(fields.guideDogOnly()).isTrue();
        assertThat(fields.petOnly()).isFalse();
        assertThat(fields.indoorAllowed()).isFalse();
        assertThat(fields.outdoorAllowed()).isTrue();
        assertThat(fields.maxWeightKg()).isEqualByComparingTo("10.00");
        assertThat(fields.weightInclusive()).isTrue();
        assertThat(fields.maxCount()).isEqualTo((short) 2);
        assertThat(fields.sizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
        assertThat(fields.breedRule()).isEqualTo(BreedRule.NONE);
        assertThat(fields.carrierRequired()).isTrue();
        assertThat(fields.leashRequired()).isTrue();
        assertThat(fields.excludedZones()).containsExactly("실내");
        assertThat(fields.allowedZonesOnly()).containsExactly("산책로");
        assertThat(fields.excludedDays()).containsExactly("설날");
        assertThat(fields.extraFeeAmount()).isEqualTo(5000);
        assertThat(fields.extraFeeUnit()).isEqualTo(ExtraFeeUnit.PER_DOG);
        assertThat(fields.requiredItems()).containsExactly("배변봉투");
        assertThat(fields.vaccineProof()).isTrue();
        assertThat(fields.advanceInquiry()).isFalse();

        assertThat(output.hasConflict()).isFalse();
        assertThat(output.policyVersion()).isEqualTo(1);
        assertThat(output.correctionSource()).isNull();
    }

    @Test
    @DisplayName("근거는 칸마다 그 값을 만든 소스의 것만 담긴다")
    void 근거는_승자의_것만_담긴다() {
        // 문암생태공원 조합임 (이슈 #5 실물 검증)
        // 실외는 고캠핑 false 가 이기고 문화정보원 true 는 짐
        // 실내는 둘 다 false 라 안 갈렸으나 먼저 말한 고캠핑이 승자임
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR,
                        fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build()),
                        List.of(new EvidenceRequest("scope", "acmpyTypeCd", null, "공사 범위 근거", ExtractionMethod.RULE))),
                item(placeId, SourceType.GOCAMPING,
                        fieldsOf(PolicyFields.builder()
                                .indoorAllowed(false).outdoorAllowed(false).build()),
                        List.of(new EvidenceRequest("indoorAllowed", "animalCmgCl", null, "고캠핑 실내 근거",
                                        ExtractionMethod.RULE),
                                new EvidenceRequest("outdoorAllowed", "animalCmgCl", null, "고캠핑 실외 근거",
                                        ExtractionMethod.RULE))),
                item(placeId, SourceType.CULTURE_CSV,
                        fieldsOf(PolicyFields.builder()
                                .indoorAllowed(false).outdoorAllowed(true).build()),
                        List.of(new EvidenceRequest("indoorAllowed", "indoorColumn", null, "문화정보원 실내 근거",
                                        ExtractionMethod.RULE),
                                new EvidenceRequest("outdoorAllowed", "outdoorColumn", null, "문화정보원 실외 근거",
                                        ExtractionMethod.RULE)))));
        clear();

        PolicyBatchOutput output = policyQueryService.findByPlaceIds(List.of(placeId)).getFirst();

        assertThat(output.fields().outdoorAllowed()).isFalse();
        assertThat(output.hasConflict()).isTrue();
        assertThat(output.evidence()).extracting(EvidenceOutput::segmentText)
                .containsExactly("공사 범위 근거", "고캠핑 실내 근거", "고캠핑 실외 근거");
    }

    @Test
    @DisplayName("근거 줄마다 추출 방식이 그대로 실린다")
    void 근거_줄에_추출_방식이_실린다() {
        // 판정 화면이 이유마다 "공공데이터 항목" 과 "안내문을 AI 가 읽음" 을 가르는 재료임
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR,
                fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).excludedZones(List.of("실내")).build()),
                List.of(new EvidenceRequest("scope", "acmpyTypeCd", null, "일부구역 동반가능",
                                ExtractionMethod.RULE),
                        new EvidenceRequest("excludedZones", "etcAcmpyInfo", 1, "실내 동반 불가",
                                ExtractionMethod.LLM)))));
        clear();

        PolicyBatchOutput output = policyQueryService.findByPlaceIds(List.of(placeId)).getFirst();

        assertThat(output.evidence())
                .extracting(EvidenceOutput::fieldName, EvidenceOutput::extractionMethod)
                .containsExactly(
                        tuple("scope", ExtractionMethod.RULE),
                        tuple("excludedZones", ExtractionMethod.LLM));
    }

    @Test
    @DisplayName("목록 칸에서 새 원소를 보태지 않은 소스의 근거는 담기지 않는다")
    void 보태지_않은_소스의_근거는_없다() {
        // 고캠핑은 목록을 말했으나 공사가 이미 낸 "실내" 뿐이라 합집합에 보탠 것이 없음
        // 그 근거가 나가면 최종 값을 만들지 않은 소스가 출처로 뜸 (PR #8 리뷰)
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR,
                        fieldsOf(PolicyFields.builder()
                                .excludedZones(List.of("실내", "잔디")).build()),
                        List.of(new EvidenceRequest("excludedZones", "etcAcmpyInfo", null, "공사 구역 근거",
                                        ExtractionMethod.LLM))),
                item(placeId, SourceType.GOCAMPING,
                        fieldsOf(PolicyFields.builder()
                                .excludedZones(List.of("실내")).build()),
                        List.of(new EvidenceRequest("excludedZones", "animalCmgCl", null, "고캠핑 구역 근거",
                                        ExtractionMethod.RULE)))));
        clear();

        PolicyBatchOutput output = policyQueryService.findByPlaceIds(List.of(placeId)).getFirst();

        assertThat(output.fields().excludedZones()).containsExactly("실내", "잔디");
        assertThat(output.evidence()).extracting(EvidenceOutput::segmentText)
                .containsExactly("공사 구역 근거");
    }

    @Test
    @DisplayName("정정 행이 이긴 장소는 공공 소스의 근거를 담지 않는다")
    void 정정_행이_이기면_공공_근거가_없다() {
        // 칸 이름으로만 고르면 관리자가 고친 값 옆에 공사 문구가 출처로 뜸
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR,
                        fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build()),
                        List.of(new EvidenceRequest("scope", "acmpyTypeCd", null, "공사 범위 근거",
                                        ExtractionMethod.RULE)))));

        // 관리자 정정 API 는 이슈 ⑥ 이라 아직 없음
        // 정정 행을 직접 넣고 병합을 부름 — 병합은 부르는 쪽 트랜잭션에 참여함
        petPolicySourceRepository.save(PetPolicySource.corrected(placeId, SourceType.MANUAL,
                PolicyFields.builder().scope(Scope.ALL_AREA).build(), "검증용 정정"));
        policyMergeService.remerge(placeId);
        clear();

        PolicyBatchOutput output = policyQueryService.findByPlaceIds(List.of(placeId)).getFirst();

        assertThat(output.fields().scope()).isEqualTo(Scope.ALL_AREA);
        assertThat(output.evidence()).isEmpty();

        // 근거가 비어도 verdict 가 "사람이 확인해 정한 값" 으로 가를 수 있어야 함
        assertThat(output.correctionSource()).isEqualTo(SourceType.MANUAL);
    }

    @Test
    @DisplayName("근거는 조건 순서 · 소스 순서 · 조각 번호 순으로 늘어놓는다")
    void 근거의_순서가_정해져_있다() {
        // 넣는 순서를 일부러 섞음
        // 목록 칸은 원소를 보탠 소스가 둘 다 이기므로 두 소스의 근거가 함께 나옴
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.CULTURE_CSV,
                        fieldsOf(PolicyFields.builder().excludedZones(List.of("잔디")).build()),
                        List.of(new EvidenceRequest("excludedZones", "zoneColumn", null, "문화정보원 구역",
                                        ExtractionMethod.RULE))),
                item(placeId, SourceType.PET_TOUR,
                        fieldsOf(PolicyFields.builder()
                                .scope(Scope.PARTIAL).excludedZones(List.of("실내")).build()),
                        List.of(new EvidenceRequest("excludedZones", "etcAcmpyInfo", 1, "공사 구역 둘째",
                                        ExtractionMethod.LLM),
                                new EvidenceRequest("scope", "acmpyTypeCd", null, "공사 범위", ExtractionMethod.RULE),
                                new EvidenceRequest("excludedZones", "etcAcmpyInfo", 0, "공사 구역 첫째",
                                        ExtractionMethod.LLM)))));
        clear();

        PolicyBatchOutput output = policyQueryService.findByPlaceIds(List.of(placeId)).getFirst();

        assertThat(output.evidence()).extracting(EvidenceOutput::segmentText)
                .containsExactly("공사 범위", "공사 구역 첫째", "공사 구역 둘째", "문화정보원 구역");
    }

    @Test
    @DisplayName("충돌이 없거나 조건 행이 없는 장소는 빈 충돌 목록이다")
    void 충돌이_없으면_빈_목록() {
        // 조건 행이 없어도 404 가 아님 — 이 서비스는 장소가 있는지 모름
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR,
                        fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build()))));
        clear();

        assertThat(policyQueryService.findConflicts(placeId)).isEmpty();
        assertThat(policyQueryService.findConflicts(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("충돌은 조건 순서 · 소스 간 먼저 · 소스 순서로 라벨과 문장을 붙여 나온다")
    void 충돌_목록의_모양과_순서() {
        // 문암생태공원 조합에 고캠핑의 소스 내 어긋남을 하나 더함
        // 실외는 고캠핑 false · 문화정보원 true 로 소스끼리 갈림
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR,
                        fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build())),
                item(placeId, SourceType.GOCAMPING,
                        fieldsOf(PolicyFields.builder()
                                .indoorAllowed(false).outdoorAllowed(false).build()),
                        List.of(),
                        List.of(new ConflictRequest("scope", Map.of("field", "가능", "text", "불가")))),
                item(placeId, SourceType.CULTURE_CSV,
                        fieldsOf(PolicyFields.builder()
                                .indoorAllowed(false).outdoorAllowed(true).build()))));
        clear();

        List<ConflictOutput> conflicts = policyQueryService.findConflicts(placeId);

        // scope 가 outdoorAllowed 보다 조건 순서가 앞이라 소스 내 어긋남이어도 먼저 옴
        assertThat(conflicts)
                .extracting(ConflictOutput::fieldName, ConflictOutput::label, ConflictOutput::conflictType)
                .containsExactly(
                        tuple("scope", "동반 범위", ConflictType.INTRA_SOURCE),
                        tuple("outdoorAllowed", "실외 동반", ConflictType.CROSS_SOURCE));

        // jsonb 가 {"text", "field"} 로 뒤집어 저장해도 항목 값 → 본문 순이어야 함
        // 원문의 말을 옮긴 것이라 값은 문장으로 안 바꿈
        assertThat(conflicts.get(0).sourceValues()).containsExactly(
                new ConflictValueOutput(SourceType.GOCAMPING, "항목 값", "가능"),
                new ConflictValueOutput(SourceType.GOCAMPING, "본문", "불가"));

        // 소스 간 어긋남은 원값을 문장으로 바꾸고 소스 열거 순서로 둠
        assertThat(conflicts.get(1).sourceValues()).containsExactly(
                new ConflictValueOutput(SourceType.GOCAMPING, null, "불가"),
                new ConflictValueOutput(SourceType.CULTURE_CSV, null, "가능"));
    }

    @Test
    @DisplayName("정정 행이 이긴 장소는 소스 내 어긋남이 남아 있어도 빈 충돌 목록이다")
    void 정정이_이기면_충돌_목록이_빈다() {
        // 배지와 목록이 같은 집합이어야 함 — 정정이 이기면 배지가 닫히므로 목록도 비어야 함
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.GOCAMPING,
                        fieldsOf(PolicyFields.builder().indoorAllowed(false).build()),
                        List.of(),
                        List.of(new ConflictRequest("scope", Map.of("field", "가능", "text", "불가"))))));

        petPolicySourceRepository.save(PetPolicySource.corrected(placeId, SourceType.MANUAL,
                PolicyFields.builder().scope(Scope.PARTIAL).build(), "검증용 정정"));
        policyMergeService.remerge(placeId);
        clear();

        assertThat(policyQueryService.findConflicts(placeId)).isEmpty();
    }

    private void clear() {
        entityManager.flush();
        entityManager.clear();
    }

    // 모델명은 비움 — 항목이 전부 규칙 파싱(RULE)이라 태운 모델이 없음
    private static BulkUpsertRequest request(BulkItemRequest... items) {
        return new BulkUpsertRequest(null, "v1", LocalDateTime.now(), List.of(items));
    }

    private static BulkItemRequest item(UUID placeId, SourceType source,
                                        PolicyFieldsRequest fields) {
        return item(placeId, source, fields, List.of());
    }

    private static BulkItemRequest item(UUID placeId, SourceType source,
                                        PolicyFieldsRequest fields,
                                        List<EvidenceRequest> evidence) {
        return item(placeId, source, fields, evidence, List.of());
    }

    private static BulkItemRequest item(UUID placeId, SourceType source,
                                        PolicyFieldsRequest fields,
                                        List<EvidenceRequest> evidence,
                                        List<ConflictRequest> conflicts) {
        return new BulkItemRequest(placeId, source, fields, evidence, conflicts,
                ExtractionMethod.RULE);
    }

    /**
     * 빌더로 만든 조건을 요청 모양으로 옮깁니다.
     *
     * 검사마다 스무 칸짜리 생성자를 쓰면 정작 보려는 값이 묻힙니다.
     */
    private static PolicyFieldsRequest fieldsOf(PolicyFields fields) {
        return new PolicyFieldsRequest(
                fields.getScope(), fields.getGuideDogOnly(), fields.getPetOnly(),
                fields.getIndoorAllowed(), fields.getOutdoorAllowed(),
                fields.getMaxWeightKg(), fields.getWeightInclusive(), fields.getMaxCount(),
                fields.getSizeRule(), fields.getBreedRule(),
                fields.getCarrierRequired(), fields.getLeashRequired(),
                fields.getExcludedZones(), fields.getAllowedZonesOnly(), fields.getExcludedDays(),
                fields.getExtraFeeAmount(), fields.getExtraFeeUnit(), fields.getRequiredItems(),
                fields.getVaccineProof(), fields.getAdvanceInquiry());
    }

    private static PolicyFields filled() {
        return PolicyFields.builder()
                .scope(Scope.PARTIAL)
                .guideDogOnly(true)
                .petOnly(false)
                .indoorAllowed(false)
                .outdoorAllowed(true)
                .maxWeightKg(new BigDecimal("10.00"))
                .weightInclusive(true)
                .maxCount((short) 2)
                .sizeRule(SizeRule.SMALL_ONLY)
                .breedRule(BreedRule.NONE)
                .carrierRequired(true)
                .leashRequired(true)
                .excludedZones(List.of("실내"))
                .allowedZonesOnly(List.of("산책로"))
                .excludedDays(List.of("설날"))
                .extraFeeAmount(5000)
                .extraFeeUnit(ExtraFeeUnit.PER_DOG)
                .requiredItems(List.of("배변봉투"))
                .vaccineProof(true)
                .advanceInquiry(false)
                .build();
    }
}
