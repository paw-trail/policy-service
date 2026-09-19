package com.pawtrail.policy.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pawtrail.common.message.outbox.OutboxRepository;
import com.pawtrail.policy.IntegrationTestSupport;
import com.pawtrail.policy.domain.enums.ExtractionMethod;
import com.pawtrail.policy.domain.enums.Scope;
import com.pawtrail.policy.domain.enums.SizeRule;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PetPolicy;
import com.pawtrail.policy.domain.model.PetPolicySource;
import com.pawtrail.policy.domain.model.PolicyFields;
import com.pawtrail.policy.domain.repository.PetPolicyRepository;
import com.pawtrail.policy.domain.repository.PetPolicySourceRepository;
import com.pawtrail.policy.presentation.request.BulkItemRequest;
import com.pawtrail.policy.presentation.request.BulkUpsertRequest;
import com.pawtrail.policy.presentation.request.EvidenceRequest;
import com.pawtrail.policy.presentation.request.PolicyFieldsRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 조건이 바뀌었을 때 policy.changed 가 언제 · 무엇을 싣고 기록되는지 실제 DB 로 고정합니다.
 *
 * 기록은 outbox 표에 남는 행으로 확인합니다.
 * 검사가 @Transactional 이라 끝나면 롤백되므로 커밋 직후의 실제 발행까지는 가지 않습니다.
 * 이 검사가 보는 것은 "판을 올린 트랜잭션에 이벤트 행이 함께 들어가는가" 와 payload 입니다.
 *
 * 판을 올리는 기준은 batch 가 내보내는 것입니다.
 * 조건 스무 칸 · 충돌 여부 · 최상위 티어 · batch 가 내보내는 근거 가운데 하나라도 바뀌면 기록되고,
 * 아무것도 안 바뀐 재병합에서는 기록되지 않아야 합니다.
 */
@Transactional
class PolicyChangedRecordTest extends IntegrationTestSupport {

    @Autowired
    private PolicyBulkService policyBulkService;

    @Autowired
    private PolicyMergeService policyMergeService;

    @Autowired
    private PetPolicyRepository petPolicyRepository;

    @Autowired
    private PetPolicySourceRepository petPolicySourceRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("첫 병합은 판 1 로 기록되고 값이 있는 칸이 조건 순서로 담긴다")
    void 첫_병합은_판_1_로_기록된다() {
        // 장소는 이미 있고 verdict 는 조건 행이 없는 장소를 UNKNOWN 으로 답함 — 첫 병합이 곧 그 답이 낡았다는 신호
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.GOCAMPING,
                        fieldsOf(PolicyFields.builder().sizeRule(SizeRule.SMALL_ONLY).build()), List.of()),
                item(placeId, SourceType.PET_TOUR,
                        fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build()), List.of())));

        List<RecordedEvent> events = eventsOf(placeId);

        assertThat(events).hasSize(1);
        RecordedEvent event = events.getFirst();
        assertThat(event.eventType()).isEqualTo("policy.changed");
        assertThat(event.aggregateType()).isEqualTo("Policy");
        assertThat(event.aggregateId()).isEqualTo(placeId.toString());
        assertThat(event.data().placeId()).isEqualTo(placeId);
        assertThat(event.data().policyVersion()).isEqualTo(1);
        assertThat(event.data().changedFields()).containsExactly("scope", "sizeRule");
        assertThat(event.data().hasConflict()).isFalse();
    }

    @Test
    @DisplayName("스무 칸이 모두 비어 있는 첫 병합도 기록되며 바뀐 칸은 비어 있다")
    void 빈_첫_병합도_기록되고_칸은_비어_있다() {
        // 사용자에게 달라진 값은 없으나 batch 에 행이 새로 담기므로 기록함
        // 알림이 필요한지는 받는 쪽이 비어 있는 changedFields 로 가림
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR, fieldsOf(PolicyFields.empty()), List.of())));

        List<RecordedEvent> events = eventsOf(placeId);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().data().policyVersion()).isEqualTo(1);
        assertThat(events.getFirst().data().changedFields()).isEmpty();
    }

    @Test
    @DisplayName("충돌이 있는 첫 병합은 충돌 여부를 참으로 싣는다")
    void 충돌_여부를_싣는다() {
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR,
                        fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build()), List.of()),
                item(placeId, SourceType.GOCAMPING,
                        fieldsOf(PolicyFields.builder().scope(Scope.ALL_AREA).build()), List.of())));

        assertThat(eventsOf(placeId).getFirst().data().hasConflict()).isTrue();
    }

    @Test
    @DisplayName("아무것도 안 바뀐 재병합은 기록하지 않는다")
    void 안_바뀐_재병합은_기록하지_않는다() {
        // 재병합 버튼은 소스가 그대로여도 누를 수 있음 — 누를 때마다 이벤트가 나가면 안 됨
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR,
                fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build()),
                List.of(evidence("scope", "acmpyTypeCd", "일부구역 동반가능")))));

        boolean changed = policyMergeService.remerge(placeId);

        assertThat(changed).isFalse();
        assertThat(eventsOf(placeId)).hasSize(1);
    }

    @Test
    @DisplayName("조건 값이 바뀌면 판이 오르고 바뀐 칸만 담긴다")
    void 바뀐_칸만_담긴다() {
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR,
                fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).sizeRule(SizeRule.SMALL_ONLY).build()),
                List.of())));
        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR,
                fieldsOf(PolicyFields.builder().scope(Scope.ALL_AREA).sizeRule(SizeRule.SMALL_ONLY).build()),
                List.of())));

        List<RecordedEvent> events = eventsOf(placeId);

        assertThat(events).hasSize(2);
        assertThat(events.getLast().data().policyVersion()).isEqualTo(2);
        assertThat(events.getLast().data().changedFields()).containsExactly("scope");
    }

    @Test
    @DisplayName("조건은 그대로인데 근거 문구만 바뀌어도 판이 오르고 바뀐 칸은 비어 있다")
    void 근거만_바뀌어도_판이_오른다() {
        // 근거 문구는 카드 한 줄 · 항목별 이유로 화면에 나감 — verdict 가 캐시한 옛 문구를 지워야 함
        UUID placeId = UUID.randomUUID();
        PolicyFieldsRequest fields = fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build());
        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR, fields,
                List.of(evidence("scope", "acmpyTypeCd", "일부구역 동반가능")))));
        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR, fields,
                List.of(evidence("scope", "acmpyTypeCd", "야외 테라스만 동반 가능")))));

        List<RecordedEvent> events = eventsOf(placeId);

        assertThat(events).hasSize(2);
        assertThat(events.getLast().data().policyVersion()).isEqualTo(2);
        assertThat(events.getLast().data().changedFields()).isEmpty();
    }

    @Test
    @DisplayName("같은 근거를 다시 보내면 판도 기록도 그대로다")
    void 같은_근거를_다시_보내면_기록하지_않는다() {
        // 적재는 근거를 지우고 다시 넣음 — 내용이 같으면 지문이 같아야 함
        UUID placeId = UUID.randomUUID();
        BulkUpsertRequest request = request(item(placeId, SourceType.PET_TOUR,
                fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build()),
                List.of(evidence("scope", "acmpyTypeCd", "일부구역 동반가능"))));

        policyBulkService.upsert(request);
        policyBulkService.upsert(request);
        clear();

        assertThat(eventsOf(placeId)).hasSize(1);
        assertThat(petPolicyRepository.findByPlaceId(placeId).orElseThrow().getPolicyVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("값은 같아도 칸의 승자가 바뀌어 보여 줄 근거가 달라지면 판이 오른다")
    void 보여_줄_근거가_바뀌면_승자만_바뀌어도_판이_오른다() {
        // 고캠핑이 채우던 실내 칸을 공사가 같은 값으로 새로 채움 — batch 가 내보낼 근거가 공사 것으로 바뀜
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR,
                        fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build()),
                        List.of(evidence("scope", "acmpyTypeCd", "공사 범위"))),
                item(placeId, SourceType.GOCAMPING,
                        fieldsOf(PolicyFields.builder().indoorAllowed(false).build()),
                        List.of(evidence("indoorAllowed", "animalCmgCl", "고캠핑 실내")))));

        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR,
                fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).indoorAllowed(false).build()),
                List.of(evidence("scope", "acmpyTypeCd", "공사 범위"),
                        evidence("indoorAllowed", "etcAcmpyInfo", "공사 실내")))));

        List<RecordedEvent> events = eventsOf(placeId);

        assertThat(events).hasSize(2);
        assertThat(events.getLast().data().policyVersion()).isEqualTo(2);
        assertThat(events.getLast().data().changedFields()).isEmpty();
    }

    @Test
    @DisplayName("정정 출처만 바뀌어도 기록되고 바뀐 칸은 비어 있다")
    void 정정_출처만_바뀌어도_기록된다() {
        // 공공 값과 같은 값으로 관리자가 정정함 — 값은 같으나 batch 의 correctionSource 가 붙음
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR,
                fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build()), List.of())));

        petPolicySourceRepository.save(PetPolicySource.corrected(placeId, SourceType.MANUAL,
                PolicyFields.builder().scope(Scope.PARTIAL).build(), "검증용 정정"));
        boolean changed = policyMergeService.remerge(placeId);

        List<RecordedEvent> events = eventsOf(placeId);

        assertThat(changed).isTrue();
        assertThat(events).hasSize(2);
        assertThat(events.getLast().data().policyVersion()).isEqualTo(2);
        assertThat(events.getLast().data().changedFields()).isEmpty();
    }

    @Test
    @DisplayName("지문이 비어 있는 옛 행은 지문만 채우고 판을 올리지 않는다")
    void 지문이_빈_행은_채우기만_한다() {
        // V24 이전에 만들어진 행 — 비교할 옛 지문이 없으므로 이번에는 기록하지 않음
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR,
                fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build()),
                List.of(evidence("scope", "acmpyTypeCd", "일부구역 동반가능")))));
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE pet_policy SET evidence_digest = NULL WHERE place_id = :placeId")
                .setParameter("placeId", placeId)
                .executeUpdate();
        entityManager.clear();

        boolean changed = policyMergeService.remerge(placeId);
        clear();

        PetPolicy policy = petPolicyRepository.findByPlaceId(placeId).orElseThrow();
        assertThat(changed).isFalse();
        assertThat(policy.getPolicyVersion()).isEqualTo(1);
        assertThat(policy.getEvidenceDigest()).hasSize(64);
        assertThat(eventsOf(placeId)).hasSize(1);
    }

    @Test
    @DisplayName("근거의 추출 방식만 바뀌어도 기록되고 바뀐 칸은 비어 있다")
    void 추출_방식만_바뀌어도_기록된다() {
        // 같은 문구를 이번에는 모델이 읽음 — 판정 화면의 출처 표시가 달라짐
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR,
                fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build()),
                List.of(evidence("scope", "acmpyPsblCpam", "일부 구역만 동반 가능", ExtractionMethod.RULE)))));

        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR,
                fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build()),
                List.of(evidence("scope", "acmpyPsblCpam", "일부 구역만 동반 가능", ExtractionMethod.LLM)))));

        List<RecordedEvent> events = eventsOf(placeId);

        assertThat(events).hasSize(2);
        assertThat(events.getLast().data().policyVersion()).isEqualTo(2);
        assertThat(events.getLast().data().changedFields()).isEmpty();
    }

    @Test
    @DisplayName("V25 이전 근거에 추출 방식이 새로 붙어도 지문을 비운 행은 채우기만 하고 판을 올리지 않는다")
    void 지문을_비운_행은_방식이_붙어도_채우기만_한다() {
        // V25 가 옛 지문을 비운 뒤 extract 가 방식을 싣고 전량을 다시 보내는 자리
        // 근거의 방식이 비어 있던 것까지 그대로 흉내 냄
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR,
                fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build()),
                List.of(evidence("scope", "acmpyTypeCd", "일부구역 동반가능")))));
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE policy_evidence SET extraction_method = NULL WHERE place_id = :placeId")
                .setParameter("placeId", placeId)
                .executeUpdate();
        entityManager.createNativeQuery("UPDATE pet_policy SET evidence_digest = NULL WHERE place_id = :placeId")
                .setParameter("placeId", placeId)
                .executeUpdate();
        entityManager.clear();

        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR,
                fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build()),
                List.of(evidence("scope", "acmpyTypeCd", "일부구역 동반가능")))));
        clear();

        PetPolicy policy = petPolicyRepository.findByPlaceId(placeId).orElseThrow();
        assertThat(policy.getPolicyVersion()).isEqualTo(1);
        assertThat(policy.getEvidenceDigest()).hasSize(64);
        assertThat(eventsOf(placeId)).hasSize(1);
    }

    private List<RecordedEvent> eventsOf(UUID placeId) {
        entityManager.flush();
        return outboxRepository.findAll().stream()
                .filter(message -> placeId.toString().equals(message.getAggregateId()))
                .map(message -> jsonMapper.readValue(message.getPayload(), RecordedEvent.class))
                .sorted(Comparator.comparingInt((RecordedEvent event) -> event.data().policyVersion()))
                .toList();
    }

    private void clear() {
        entityManager.flush();
        entityManager.clear();
    }

    // 모델명은 비움 — 항목이 전부 규칙 파싱(RULE)이라 태운 모델이 없음
    private static BulkUpsertRequest request(BulkItemRequest... items) {
        return new BulkUpsertRequest(null, "v1", LocalDateTime.now(), List.of(items));
    }

    private static BulkItemRequest item(UUID placeId, SourceType source, PolicyFieldsRequest fields,
                                        List<EvidenceRequest> evidence) {
        return new BulkItemRequest(placeId, source, fields, evidence, List.of(), ExtractionMethod.RULE);
    }

    private static EvidenceRequest evidence(String fieldName, String originField, String text) {
        return evidence(fieldName, originField, text, ExtractionMethod.RULE);
    }

    private static EvidenceRequest evidence(String fieldName, String originField, String text,
                                            ExtractionMethod extractionMethod) {
        return new EvidenceRequest(fieldName, originField, null, text, extractionMethod);
    }

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

    /**
     * outbox 행의 payload 에 담긴 봉투입니다.
     *
     * 발행되는 문자열을 그대로 읽어 받는 쪽이 보게 될 모양을 확인합니다.
     * 봉투의 eventId · occurredAt 은 이 검사가 보지 않아 담지 않습니다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RecordedEvent(String eventType, String aggregateType, String aggregateId, Payload data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(UUID placeId, int policyVersion, List<String> changedFields, boolean hasConflict) {
    }
}
