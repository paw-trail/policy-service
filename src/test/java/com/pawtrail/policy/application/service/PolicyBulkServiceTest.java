package com.pawtrail.policy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.policy.application.dto.output.BulkUpsertResult;
import com.pawtrail.policy.domain.enums.ConflictType;
import com.pawtrail.policy.domain.enums.ExtractionMethod;
import com.pawtrail.policy.domain.enums.Scope;
import com.pawtrail.policy.domain.enums.SizeRule;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PetPolicy;
import com.pawtrail.policy.domain.model.PolicyConflict;
import com.pawtrail.policy.domain.model.PolicyEvidence;
import com.pawtrail.policy.domain.repository.PetPolicyRepository;
import com.pawtrail.policy.domain.repository.PetPolicySourceRepository;
import com.pawtrail.policy.domain.repository.PolicyConflictRepository;
import com.pawtrail.policy.domain.repository.PolicyEvidenceRepository;
import com.pawtrail.policy.presentation.request.BulkItemRequest;
import com.pawtrail.policy.presentation.request.BulkUpsertRequest;
import com.pawtrail.policy.presentation.request.ConflictRequest;
import com.pawtrail.policy.presentation.request.EvidenceRequest;
import com.pawtrail.policy.presentation.request.PolicyFieldsRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.pawtrail.policy.IntegrationTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조건 적재를 실제 DB 로 확인합니다.
 *
 * 병합 규칙 자체는 PolicyMergerTest 가 이미 고정하고 있습니다.
 * 여기서 보는 것은 배선입니다.
 * 요청이 엔티티로 옮겨지는지, 갈아 끼우기가 도는지, 같은 청크를 두 번 받아도 같은지입니다.
 *
 * 이 저장소에서 데이터베이스를 쓰는 첫 검사입니다.
 * place 는 서비스 검사를 순수 단위로 두고 실물 확인을 손으로 했으나,
 * 여기서 보려는 것이 갈아 끼우기와 판이 오르는 규칙이라 실제로 넣어 봐야 드러납니다.
 * 저장소를 흉내 내면 "내가 짠 대로 부른다" 만 확인하게 됩니다.
 */
@Transactional
class PolicyBulkServiceTest extends IntegrationTestSupport {

    @Autowired
    private PolicyBulkService policyBulkService;

    @Autowired
    private PetPolicySourceRepository petPolicySourceRepository;

    @Autowired
    private PetPolicyRepository petPolicyRepository;

    @Autowired
    private PolicyEvidenceRepository policyEvidenceRepository;

    @Autowired
    private PolicyConflictRepository policyConflictRepository;

    // 영속성 컨텍스트를 비울 때만 씀
    // 같은 트랜잭션 안에서 다시 읽으면 캐시된 객체가 돌아와 jsonb 를 거치지 않음
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("조건 스무 칸이 빠짐없이 저장된다")
    void 스무_칸이_다_저장된다() {
        // 요청 DTO 에서 값 객체로 옮기는 코드가 손으로 쓴 스무 줄이라
        // 한 줄을 빠뜨려도 컴파일이 통과하고 그 칸만 조용히 안 들어옴
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR, filledFields())));

        var saved = petPolicySourceRepository
                .findByPlaceIdAndSource(placeId, SourceType.PET_TOUR).orElseThrow();
        var fields = saved.getFields();

        assertThat(fields.getScope()).isEqualTo(Scope.PARTIAL);
        assertThat(fields.getGuideDogOnly()).isTrue();
        assertThat(fields.getPetOnly()).isFalse();
        assertThat(fields.getIndoorAllowed()).isFalse();
        assertThat(fields.getOutdoorAllowed()).isTrue();
        assertThat(fields.getMaxWeightKg()).isEqualByComparingTo("10.00");
        assertThat(fields.getWeightInclusive()).isTrue();
        assertThat(fields.getMaxCount()).isEqualTo((short) 2);
        assertThat(fields.getSizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
        assertThat(fields.getBreedRule()).isNotNull();
        assertThat(fields.getCarrierRequired()).isTrue();
        assertThat(fields.getLeashRequired()).isTrue();
        assertThat(fields.getExcludedZones()).containsExactly("실내");
        assertThat(fields.getAllowedZonesOnly()).containsExactly("산책로");
        assertThat(fields.getExcludedDays()).containsExactly("설날");
        assertThat(fields.getExtraFeeAmount()).isEqualTo(5000);
        assertThat(fields.getExtraFeeUnit()).isNotNull();
        assertThat(fields.getRequiredItems()).containsExactly("배변봉투");
        assertThat(fields.getVaccineProof()).isTrue();
        assertThat(fields.getAdvanceInquiry()).isFalse();
    }

    @Test
    @DisplayName("적재하면 병합 결과가 함께 만들어진다")
    void 적재하면_병합까지_된다() {
        UUID placeId = UUID.randomUUID();
        BulkUpsertResult result = policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR,
                        PolicyFieldsRequestFixture.scope(Scope.PARTIAL))));

        assertThat(result.accepted()).isEqualTo(1);
        assertThat(result.merged()).isEqualTo(1);

        PetPolicy policy = petPolicyRepository.findByPlaceId(placeId).orElseThrow();
        assertThat(policy.getFields().getScope()).isEqualTo(Scope.PARTIAL);
        assertThat(policy.getPolicyVersion()).isEqualTo(1);
        assertThat(policy.getSourcePriority()).isEqualTo(SourceType.PET_TOUR);
    }

    @Test
    @DisplayName("적재하면 칸별 승자가 함께 저장된다")
    void 칸별_승자가_저장된다() {
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR,
                        PolicyFieldsRequestFixture.scope(Scope.PARTIAL)),
                item(placeId, SourceType.GOCAMPING,
                        PolicyFieldsRequestFixture.sizeRule(SizeRule.SMALL_ONLY))));

        // 비우고 다시 읽어 jsonb 를 한 바퀴 돌림
        entityManager.flush();
        entityManager.clear();

        PetPolicy policy = petPolicyRepository.findByPlaceId(placeId).orElseThrow();
        assertThat(policy.sourcesOf("scope")).containsExactly(SourceType.PET_TOUR);
        assertThat(policy.sourcesOf("sizeRule")).containsExactly(SourceType.GOCAMPING);
        assertThat(policy.sourcesOf("indoorAllowed")).isEmpty();
    }

    @Test
    @DisplayName("승자만 바뀌고 보여 줄 근거가 같으면 판은 그대로지만 승자는 새로 적힌다")
    void 승자만_바뀌면_판은_그대로다() {
        // 공사가 이미 소스로 있고, 고캠핑이 채우던 칸을 같은 값으로 새로 채우는 경우임
        // 두 소스 모두 그 칸의 근거를 보내지 않아 batch 가 내보내는 것이 같음 — 판이 오르면 안 됨
        // 승자는 batch 가 근거를 고르는 기준이라 판과 상관없이 공사로 바뀌어야 함
        //
        // * 보여 줄 근거가 달라지는 경우는 판이 오름 — PolicyChangedRecordTest 에서 봄
        // * 공사가 새로 들어오는 경우도 이 검사가 아님
        //   그때는 최상위 티어(sourcePriority)가 바뀌어 판이 오름
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR,
                        PolicyFieldsRequestFixture.scope(Scope.PARTIAL)),
                item(placeId, SourceType.GOCAMPING,
                        PolicyFieldsRequestFixture.scopeAndIndoor(null, false))));

        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR,
                        PolicyFieldsRequestFixture.scopeAndIndoor(Scope.PARTIAL, false))));

        entityManager.flush();
        entityManager.clear();

        PetPolicy policy = petPolicyRepository.findByPlaceId(placeId).orElseThrow();
        assertThat(policy.getFields().getIndoorAllowed()).isFalse();
        assertThat(policy.getPolicyVersion()).isEqualTo(1);
        assertThat(policy.sourcesOf("indoorAllowed")).containsExactly(SourceType.PET_TOUR);
    }

    @Test
    @DisplayName("스무 칸이 모두 비어 있는 행을 다시 읽어도 병합이 멈추지 않는다")
    void 빈_행을_다시_읽어도_병합된다() {
        // 하이버네이트는 임베디드의 컬럼이 전부 NULL 이면 값 객체를 null 로 읽음
        // extract 가 조건을 못 찾은 원문을 빈 행으로 보내면 그 행이 그렇게 되고
        // 같은 장소에 다른 소스가 들어와 재병합할 때 그 행을 다시 읽음
        //
        // * 한 트랜잭션 안에서는 저장한 객체가 그대로 돌아와 이 문제가 안 보임
        //   그래서 사이에 영속성 컨텍스트를 비워 다른 트랜잭션에서 읽는 것과 같게 만듦
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR, PolicyFieldsRequestFixture.empty())));

        entityManager.flush();
        entityManager.clear();

        policyBulkService.upsert(request(
                item(placeId, SourceType.GOCAMPING,
                        PolicyFieldsRequestFixture.sizeRule(SizeRule.SMALL_ONLY))));

        entityManager.flush();
        entityManager.clear();

        PetPolicy policy = petPolicyRepository.findByPlaceId(placeId).orElseThrow();
        assertThat(policy.getFields().getSizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
        assertThat(policy.getPolicyVersion()).isEqualTo(2);
        assertThat(policy.sourcesOf("sizeRule")).containsExactly(SourceType.GOCAMPING);
    }

    @Test
    @DisplayName("같은 장소에 소스가 둘이면 병합은 한 번만 돈다")
    void 같은_장소는_한_번만_병합된다() {
        // 판이 오르는 횟수가 곧 policy.changed 가 나가는 횟수라 한 청크에서 여러 번 오르면 안 됨
        UUID placeId = UUID.randomUUID();
        BulkUpsertResult result = policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR,
                        PolicyFieldsRequestFixture.scope(Scope.PARTIAL)),
                item(placeId, SourceType.GOCAMPING,
                        PolicyFieldsRequestFixture.sizeRule(SizeRule.SMALL_ONLY))));

        assertThat(result.accepted()).isEqualTo(2);
        assertThat(result.merged()).isEqualTo(1);

        PetPolicy policy = petPolicyRepository.findByPlaceId(placeId).orElseThrow();
        assertThat(policy.getPolicyVersion()).isEqualTo(1);
        assertThat(policy.getFields().getScope()).isEqualTo(Scope.PARTIAL);
        assertThat(policy.getFields().getSizeRule()).isEqualTo(SizeRule.SMALL_ONLY);
    }

    @Test
    @DisplayName("같은 청크를 두 번 보내도 판이 오르지 않는다")
    void 두_번_보내도_판이_안_오른다() {
        // extract 가 실패하면 같은 청크를 다시 보냄
        // 판이 또 오르면 아무것도 안 바뀌었는데 알림이 나감
        UUID placeId = UUID.randomUUID();
        BulkUpsertRequest request = request(
                item(placeId, SourceType.PET_TOUR,
                        PolicyFieldsRequestFixture.scope(Scope.PARTIAL)));

        policyBulkService.upsert(request);
        policyBulkService.upsert(request);

        PetPolicy policy = petPolicyRepository.findByPlaceId(placeId).orElseThrow();
        assertThat(policy.getPolicyVersion()).isEqualTo(1);

        // 소스 행도 늘어나지 않음
        assertThat(petPolicySourceRepository.findByPlaceId(placeId)).hasSize(1);
    }

    @Test
    @DisplayName("값이 달라지면 판이 오른다")
    void 값이_달라지면_판이_오른다() {
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR,
                        PolicyFieldsRequestFixture.scope(Scope.PARTIAL))));
        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR,
                        PolicyFieldsRequestFixture.scope(Scope.ALL_AREA))));

        PetPolicy policy = petPolicyRepository.findByPlaceId(placeId).orElseThrow();
        assertThat(policy.getPolicyVersion()).isEqualTo(2);
        assertThat(policy.getFields().getScope()).isEqualTo(Scope.ALL_AREA);
    }

    @Test
    @DisplayName("근거를 다시 보내면 옛 것이 남지 않는다")
    void 근거가_갈아_끼워진다() {
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR,
                PolicyFieldsRequestFixture.scope(Scope.PARTIAL),
                List.of(new EvidenceRequest("scope", "acmpyTypeCd", null, "전구역 동반가능")),
                List.of())));

        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR,
                PolicyFieldsRequestFixture.scope(Scope.PARTIAL),
                List.of(new EvidenceRequest("scope", "acmpyTypeCd", null, "일부구역 동반가능")),
                List.of())));

        List<PolicyEvidence> evidences = policyEvidenceRepository.findByPlaceId(placeId);
        assertThat(evidences).hasSize(1);
        assertThat(evidences.getFirst().getSegmentText()).isEqualTo("일부구역 동반가능");
    }

    @Test
    @DisplayName("다른 소스의 근거는 안 지워진다")
    void 다른_소스의_근거는_남는다() {
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR,
                        PolicyFieldsRequestFixture.scope(Scope.PARTIAL),
                        List.of(new EvidenceRequest("scope", "acmpyTypeCd", null, "공사 근거")),
                        List.of()),
                item(placeId, SourceType.GOCAMPING,
                        PolicyFieldsRequestFixture.sizeRule(SizeRule.SMALL_ONLY),
                        List.of(new EvidenceRequest("sizeRule", "animalCmgCl", null, "고캠핑 근거")),
                        List.of())));

        // 공사만 다시 보냄
        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR,
                PolicyFieldsRequestFixture.scope(Scope.ALL_AREA),
                List.of(new EvidenceRequest("scope", "acmpyTypeCd", null, "공사 새 근거")),
                List.of())));

        List<PolicyEvidence> evidences = policyEvidenceRepository.findByPlaceId(placeId);
        assertThat(evidences).hasSize(2);
        assertThat(evidences).extracting(PolicyEvidence::getSegmentText)
                .containsExactlyInAnyOrder("공사 새 근거", "고캠핑 근거");
    }

    @Test
    @DisplayName("소스 내 어긋남은 그 소스 것만 갈아 끼워진다")
    void 소스_내_어긋남이_소스별로_갈린다() {
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.GOCAMPING,
                        PolicyFieldsRequestFixture.sizeRule(SizeRule.SMALL_ONLY),
                        List.of(),
                        List.of(new ConflictRequest("sizeRule",
                                Map.of("field", "가능", "text", "불가")))),
                item(placeId, SourceType.CULTURE_CSV,
                        PolicyFieldsRequestFixture.scope(Scope.PARTIAL),
                        List.of(),
                        List.of(new ConflictRequest("scope",
                                Map.of("field", "Y", "text", "동반 불가"))))));

        List<PolicyConflict> intra = policyConflictRepository.findByPlaceId(placeId).stream()
                .filter(c -> c.getConflictType() == ConflictType.INTRA_SOURCE)
                .toList();
        assertThat(intra).hasSize(2);
        assertThat(intra).extracting(PolicyConflict::getSource)
                .containsExactlyInAnyOrder(SourceType.GOCAMPING, SourceType.CULTURE_CSV);
    }

    @Test
    @DisplayName("소스가 갈리면 소스 간 어긋남이 만들어진다")
    void 소스_간_어긋남이_만들어진다() {
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR,
                        PolicyFieldsRequestFixture.scope(Scope.PARTIAL)),
                item(placeId, SourceType.GOCAMPING,
                        PolicyFieldsRequestFixture.scope(Scope.ALL_AREA))));

        PetPolicy policy = petPolicyRepository.findByPlaceId(placeId).orElseThrow();
        assertThat(policy.isHasConflict()).isTrue();

        List<PolicyConflict> cross = policyConflictRepository.findByPlaceId(placeId).stream()
                .filter(c -> c.getConflictType() == ConflictType.CROSS_SOURCE)
                .toList();
        assertThat(cross).hasSize(1);
        assertThat(cross.getFirst().getFieldName()).isEqualTo("scope");
        assertThat(cross.getFirst().getSource()).isNull();
    }

    @Test
    @DisplayName("정정 소스는 적재로 들어올 수 없다")
    void 정정_소스는_거부된다() {
        // 배치가 관리자 정정을 덮으면 사유가 지워지고
        // 배치가 뽑은 값이 병합 최상위 티어를 차지함
        UUID placeId = UUID.randomUUID();

        assertThatThrownBy(() -> policyBulkService.upsert(request(
                item(placeId, SourceType.MANUAL,
                        PolicyFieldsRequestFixture.scope(Scope.PARTIAL)))))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("POLICY_SOURCE_NOT_ALLOWED");
    }

    @Test
    @DisplayName("거부되면 앞쪽 항목이 네 표 어디에도 남지 않는다")
    void 거부되면_아무것도_안_들어간다() {
        // 지금은 청크를 돌기 전에 미리 훑으므로 앞 항목이 저장될 일이 없음
        //
        // 그 전제를 검사에 적어 두는 이유는 검증 위치가 옮겨질 수 있기 때문임
        // 항목별 저장 뒤로 옮기면 앞 항목이 부분 저장된 채 예외가 나는데
        // 병합 결과만 보면 그것을 못 잡음
        // 한 트랜잭션이 보장하는 것은 네 표에 아무것도 안 들어가는 것임
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThatThrownBy(() -> policyBulkService.upsert(request(
                item(first, SourceType.PET_TOUR,
                        PolicyFieldsRequestFixture.scope(Scope.PARTIAL),
                        List.of(new EvidenceRequest("scope", "acmpyTypeCd", null, "일부구역")),
                        List.of(new ConflictRequest("scope",
                                Map.of("field", "Y", "text", "불가")))),
                item(second, SourceType.OWNER,
                        PolicyFieldsRequestFixture.scope(Scope.ALL_AREA)))))
                .isInstanceOf(CustomException.class);

        assertThat(petPolicyRepository.findByPlaceId(first)).isEmpty();
        assertThat(petPolicySourceRepository.findByPlaceId(first)).isEmpty();
        assertThat(policyEvidenceRepository.findByPlaceId(first)).isEmpty();
        assertThat(policyConflictRepository.findByPlaceId(first)).isEmpty();
    }

    // 모델명은 비움 — 아래 항목이 전부 규칙 파싱(RULE)이라 태운 모델이 없음
    // extract 의 모델은 아직 정해진 적이 없어 이름을 지어 넣지 않음
    private static BulkUpsertRequest request(BulkItemRequest... items) {
        return new BulkUpsertRequest(null, "v1", LocalDateTime.now(), List.of(items));
    }

    private static BulkItemRequest item(UUID placeId, SourceType source,
                                        PolicyFieldsRequest fields) {
        return item(placeId, source, fields, List.of(), List.of());
    }

    private static BulkItemRequest item(UUID placeId, SourceType source,
                                        PolicyFieldsRequest fields,
                                        List<EvidenceRequest> evidence,
                                        List<ConflictRequest> conflicts) {
        return new BulkItemRequest(placeId, source, fields, evidence, conflicts,
                ExtractionMethod.RULE);
    }

    private static PolicyFieldsRequest filledFields() {
        return new PolicyFieldsRequest(
                Scope.PARTIAL, true, false, false, true,
                new BigDecimal("10.00"), true, (short) 2,
                SizeRule.SMALL_ONLY, com.pawtrail.policy.domain.enums.BreedRule.NONE,
                true, true,
                List.of("실내"), List.of("산책로"), List.of("설날"),
                5000, com.pawtrail.policy.domain.enums.ExtraFeeUnit.PER_DOG,
                List.of("배변봉투"), true, false);
    }

    /**
     * 한 칸만 채운 조건을 만듭니다.
     *
     * 테스트마다 스무 칸을 다 쓰면 정작 보려는 값이 묻힙니다.
     */
    private static final class PolicyFieldsRequestFixture {

        private static PolicyFieldsRequest empty() {
            return new PolicyFieldsRequest(null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null);
        }

        private static PolicyFieldsRequest scope(Scope scope) {
            return new PolicyFieldsRequest(scope, null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null);
        }

        private static PolicyFieldsRequest sizeRule(SizeRule sizeRule) {
            return new PolicyFieldsRequest(null, null, null, null, null,
                    null, null, null, sizeRule, null, null, null,
                    null, null, null, null, null, null, null, null);
        }

        private static PolicyFieldsRequest scopeAndIndoor(Scope scope, Boolean indoorAllowed) {
            return new PolicyFieldsRequest(scope, null, null, indoorAllowed, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null);
        }
    }
}
