package com.pawtrail.policy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.policy.IntegrationTestSupport;
import com.pawtrail.policy.application.dto.output.PolicyAdminOutput;
import com.pawtrail.policy.domain.enums.ExtractionMethod;
import com.pawtrail.policy.domain.enums.Scope;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PetPolicy;
import com.pawtrail.policy.domain.model.PolicyCorrectionLog;
import com.pawtrail.policy.domain.model.PolicyFields;
import com.pawtrail.policy.domain.repository.PetPolicyRepository;
import com.pawtrail.policy.domain.repository.PetPolicySourceRepository;
import com.pawtrail.policy.domain.repository.PolicyCorrectionLogRepository;
import com.pawtrail.policy.presentation.request.BulkItemRequest;
import com.pawtrail.policy.presentation.request.BulkUpsertRequest;
import com.pawtrail.policy.presentation.request.PolicyFieldsRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 정정을 실제 DB 로 확인합니다.
 *
 * 정정 행이 병합에서 이기는지, 이력에 무엇이 남는지, 막아야 할 저장을 막는지를 봅니다.
 * 요청 검증과 ADMIN 역할 확인은 컨트롤러 앞에서 일어나므로 실물 검증에서 봅니다.
 */
@Transactional
class PolicyCorrectionServiceTest extends IntegrationTestSupport {

    // 정정한 관리자 — 계정 식별자 문자열이 들어가는 칸이라 모양만 맞춤
    private final String admin = UUID.randomUUID().toString();

    @Autowired
    private PolicyCorrectionService policyCorrectionService;

    @Autowired
    private PolicyBulkService policyBulkService;

    @Autowired
    private PolicyQueryService policyQueryService;

    @Autowired
    private PetPolicyRepository petPolicyRepository;

    @Autowired
    private PetPolicySourceRepository petPolicySourceRepository;

    @Autowired
    private PolicyCorrectionLogRepository policyCorrectionLogRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("조건 행이 없는 장소는 빈 폼이다")
    void 행이_없으면_빈_폼() {
        // 추출 전인 장소도 업주가 알려준 조건으로 바로 정정할 수 있어야 함
        UUID placeId = UUID.randomUUID();

        PolicyAdminOutput output = policyCorrectionService.getCurrent(placeId);

        assertThat(output.placeId()).isEqualTo(placeId);
        assertThat(output.fields().scope()).isNull();
        assertThat(output.fields().vaccineProof()).isNull();
        assertThat(output.correction()).isNull();
    }

    @Test
    @DisplayName("정정 전에는 현재 병합 값을 채우고 정정 행은 비어 있다")
    void 정정_전에는_병합_값을_채운다() {
        // 폼이 비어 있으면 한 칸만 고쳐 저장해도 나머지가 전부 정보 없음으로 들어감
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(item(placeId, SourceType.PET_TOUR,
                fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build()))));
        clear();

        PolicyAdminOutput output = policyCorrectionService.getCurrent(placeId);

        assertThat(output.fields().scope()).isEqualTo(Scope.PARTIAL);
        assertThat(output.correction()).isNull();
    }

    @Test
    @DisplayName("정정하면 정정 행이 이기고 이력에 저장 직전 병합 값과 저장한 값이 남는다")
    void 정정하면_이기고_이력이_남는다() {
        // 문암생태공원 조합 — 실외가 고캠핑 false · 문화정보원 true 로 갈려 배지가 붙어 있음
        UUID placeId = UUID.randomUUID();
        policyBulkService.upsert(request(
                item(placeId, SourceType.PET_TOUR,
                        fieldsOf(PolicyFields.builder().scope(Scope.PARTIAL).build())),
                item(placeId, SourceType.GOCAMPING,
                        fieldsOf(PolicyFields.builder()
                                .indoorAllowed(false).outdoorAllowed(false).build())),
                item(placeId, SourceType.CULTURE_CSV,
                        fieldsOf(PolicyFields.builder()
                                .indoorAllowed(false).outdoorAllowed(true).build()))));

        PolicyAdminOutput output = policyCorrectionService.correct(placeId, SourceType.MANUAL,
                "현장 확인 결과 실외 동반 가능",
                PolicyFields.builder()
                        .scope(Scope.PARTIAL).indoorAllowed(false).outdoorAllowed(true).build(),
                admin);

        // 저장 직후 응답이 조회와 같은 모양이라 화면이 다시 부르지 않아도 됨
        assertThat(output.fields().outdoorAllowed()).isTrue();
        assertThat(output.correction().source()).isEqualTo(SourceType.MANUAL);
        assertThat(output.correction().reason()).isEqualTo("현장 확인 결과 실외 동반 가능");
        clear();

        PetPolicy policy = petPolicyRepository.findByPlaceId(placeId).orElseThrow();
        assertThat(policy.getSourcePriority()).isEqualTo(SourceType.MANUAL);

        // 정정이 이기면 참여한 소스가 정정 행 하나라 배지가 닫히고 충돌 목록도 빔
        assertThat(policy.isHasConflict()).isFalse();
        assertThat(policyQueryService.findConflicts(placeId)).isEmpty();

        List<PolicyCorrectionLog> logs = policyCorrectionLogRepository.findByPlaceId(placeId);
        assertThat(logs).hasSize(1);
        PolicyCorrectionLog log = logs.getFirst();
        assertThat(log.getSource()).isEqualTo(SourceType.MANUAL);
        assertThat(log.getCorrectedBy()).isEqualTo(admin);

        // before 는 같은 출처의 이전 행이 아니라 저장 직전 병합 결과임
        // 첫 정정에서도 원래 공공 값이 남아야 함 · 비어 있는 칸도 키가 남아 스무 칸 전부
        assertThat(log.getBeforeValue())
                .hasSize(20)
                .containsEntry("scope", "PARTIAL")
                .containsEntry("outdoorAllowed", false)
                .containsEntry("vaccineProof", null);
        assertThat(log.getAfterValue())
                .hasSize(20)
                .containsEntry("outdoorAllowed", true);
    }

    @Test
    @DisplayName("같은 출처로 다시 정정하면 행을 갈아 끼우고 이력은 저장할 때마다 남는다")
    void 다시_정정해도_이력이_남는다() {
        // 공공 소스가 하나도 없는 장소를 직접 정정하는 경우이기도 함
        UUID placeId = UUID.randomUUID();
        PolicyFields fields = PolicyFields.builder().scope(Scope.ALL_AREA).build();

        policyCorrectionService.correct(placeId, SourceType.MANUAL, "첫 확인", fields, admin);
        policyCorrectionService.correct(placeId, SourceType.MANUAL, "다시 확인", fields, admin);
        clear();

        // 값이 같아도 사유가 붙은 판단 한 번이라 기록이 됨
        assertThat(policyCorrectionLogRepository.findByPlaceId(placeId)).hasSize(2);
        assertThat(petPolicySourceRepository.findByPlaceId(placeId)).hasSize(1);
        assertThat(policyCorrectionService.getCurrent(placeId).correction().reason())
                .isEqualTo("다시 확인");
    }

    @Test
    @DisplayName("OWNER 가 있는데 MANUAL 로 저장하면 409 이고 아무것도 바뀌지 않는다")
    void OWNER_가_있으면_MANUAL_은_409() {
        // OWNER 가 MANUAL 보다 위라 그대로 저장하면 200 인데 조건이 안 바뀜
        UUID placeId = UUID.randomUUID();
        policyCorrectionService.correct(placeId, SourceType.OWNER, "업주가 알려 줌",
                PolicyFields.builder().scope(Scope.ALL_AREA).build(), admin);

        assertThatThrownBy(() -> policyCorrectionService.correct(placeId, SourceType.MANUAL,
                "관리자 추정", PolicyFields.builder().scope(Scope.PARTIAL).build(), admin))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("POLICY_OWNER_CORRECTION_EXISTS");

        // 막는 검사가 쓰기보다 앞이라 정정 행 · 병합 결과 · 이력 어디에도 흔적이 없음
        assertThat(petPolicySourceRepository.findByPlaceId(placeId)).hasSize(1);
        assertThat(petPolicyRepository.findByPlaceId(placeId).orElseThrow().getFields().getScope())
                .isEqualTo(Scope.ALL_AREA);
        assertThat(policyCorrectionLogRepository.findByPlaceId(placeId)).hasSize(1);
    }

    @Test
    @DisplayName("공공 소스 이름으로 정정하면 400 으로 막는다")
    void 공공_소스로_정정하면_400() {
        // 사람이 정한 값이 다음 추출에 덮이는 자리임
        assertThatThrownBy(() -> policyCorrectionService.correct(UUID.randomUUID(),
                SourceType.PET_TOUR, "잘못된 출처",
                PolicyFields.builder().scope(Scope.PARTIAL).build(), admin))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("POLICY_SOURCE_NOT_ALLOWED");
    }

    @Test
    @DisplayName("조건 소스가 없는 장소를 재병합하면 404 이고 행을 만들지 않는다")
    void 소스가_없으면_재병합은_404() {
        // 동물병원에서 재병합을 누르면 batch 에서 빠지던 장소가 빈 조건으로 담기기 시작함
        UUID placeId = UUID.randomUUID();

        assertThatThrownBy(() -> policyCorrectionService.remerge(placeId))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("POLICY_SOURCE_NOT_FOUND");

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
                                        PolicyFieldsRequest fields) {
        return new BulkItemRequest(placeId, source, fields, List.of(), List.of(),
                ExtractionMethod.RULE);
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
