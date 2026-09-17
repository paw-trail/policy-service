package com.pawtrail.policy.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.policy.IntegrationTestSupport;
import com.pawtrail.policy.domain.enums.ExtractionMethod;
import com.pawtrail.policy.domain.enums.Scope;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PetPolicy;
import com.pawtrail.policy.domain.model.PetPolicySource;
import com.pawtrail.policy.domain.model.PolicyFields;
import com.pawtrail.policy.domain.repository.PetPolicyRepository;
import com.pawtrail.policy.domain.repository.PetPolicySourceRepository;
import com.pawtrail.policy.domain.repository.PolicyConflictRepository;
import com.pawtrail.policy.presentation.request.BulkItemRequest;
import com.pawtrail.policy.presentation.request.BulkUpsertRequest;
import com.pawtrail.policy.presentation.request.ConflictRequest;
import com.pawtrail.policy.presentation.request.PolicyFieldsRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 병합 서비스가 has_conflict 를 정하는 규칙을 실제 DB 로 확인합니다.
 *
 * 병합 규칙 자체는 PolicyMerger 검사가 고정합니다.
 * 여기서 보는 것은 병합기 혼자 못 만드는 부분입니다 — 저장된 소스 내 어긋남을
 * 참여한 소스로 걸러 플래그에 더하는 것과, 소스가 없는 장소에 행을 만들지 않는 것입니다.
 */
@Transactional
class PolicyMergeServiceTest extends IntegrationTestSupport {

    @Autowired
    private PolicyBulkService policyBulkService;

    @Autowired
    private PolicyMergeService policyMergeService;

    @Autowired
    private PetPolicyRepository petPolicyRepository;

    @Autowired
    private PetPolicySourceRepository petPolicySourceRepository;

    @Autowired
    private PolicyConflictRepository policyConflictRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("소스 내 어긋남만 있는 장소도 충돌 있음으로 표시된다")
    void 소스_내_어긋남만_있어도_참() {
        // 소스가 하나뿐이라 병합이 찾는 소스 간 어긋남은 없음
        // 이 플래그가 거짓이면 장소 상세가 충돌 목록을 안 불러 자기모순을 볼 길이 없음
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(item(placeId, SourceType.GOCAMPING,
                fieldsOf(PolicyFields.builder().indoorAllowed(false).build()),
                List.of(intra("scope")))));
        clear();

        PetPolicy policy = petPolicyRepository.findByPlaceId(placeId).orElseThrow();
        assertThat(policy.isHasConflict()).isTrue();
    }

    @Test
    @DisplayName("정정 행이 이기면 공공 소스의 소스 내 어긋남은 세지 않는다")
    void 정정이_이기면_소스_내_어긋남을_안_센다() {
        // 관리자가 확인해 정한 장소에 "확인하세요" 가 계속 뜨면 안 됨
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(item(placeId, SourceType.GOCAMPING,
                fieldsOf(PolicyFields.builder().indoorAllowed(false).build()),
                List.of(intra("scope")))));

        // 관리자 정정 API 는 이 이슈의 3차에서 붙음 — 정정 행을 직접 넣고 병합을 부름
        petPolicySourceRepository.save(PetPolicySource.corrected(placeId, SourceType.MANUAL,
                PolicyFields.builder().scope(Scope.PARTIAL).build(), "검증용 정정"));
        policyMergeService.remerge(placeId);
        clear();

        PetPolicy policy = petPolicyRepository.findByPlaceId(placeId).orElseThrow();
        assertThat(policy.isHasConflict()).isFalse();

        // 어긋남 행은 그대로 남음 — 지우는 것이 아니라 세지 않는 것임
        assertThat(policyConflictRepository.findByPlaceId(placeId)).hasSize(1);
    }

    @Test
    @DisplayName("소스 내 어긋남이 새로 생기면 조건이 같아도 판이 오른다")
    void 소스_내_어긋남이_생기면_판이_오른다() {
        // 배지가 새로 붙는 것이라 사용자에게는 달라진 것임
        UUID placeId = UUID.randomUUID();
        PolicyFieldsRequest fields = fieldsOf(PolicyFields.builder().indoorAllowed(false).build());
        policyBulkService.upsert(request(item(placeId, SourceType.GOCAMPING, fields, List.of())));
        policyBulkService.upsert(request(item(placeId, SourceType.GOCAMPING, fields,
                List.of(intra("scope")))));
        clear();

        PetPolicy policy = petPolicyRepository.findByPlaceId(placeId).orElseThrow();
        assertThat(policy.isHasConflict()).isTrue();
        assertThat(policy.getPolicyVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("소스가 하나도 없는 장소를 재병합해도 행을 만들지 않는다")
    void 소스가_없으면_행을_안_만든다() {
        // 빈 행이 생기면 batch 에서 빠지던 장소(동물병원 · 추출 전)가 빈 조건으로 담기기 시작함
        UUID placeId = UUID.randomUUID();

        boolean changed = policyMergeService.remerge(placeId);
        clear();

        assertThat(changed).isFalse();
        assertThat(petPolicyRepository.findByPlaceId(placeId)).isEmpty();
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
                                        PolicyFieldsRequest fields,
                                        List<ConflictRequest> conflicts) {
        return new BulkItemRequest(placeId, source, fields, List.of(), conflicts,
                ExtractionMethod.RULE);
    }

    private static ConflictRequest intra(String fieldName) {
        return new ConflictRequest(fieldName, Map.of("field", "가능", "text", "불가"));
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
}
