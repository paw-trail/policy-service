package com.pawtrail.policy.application.service;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.policy.application.dto.output.CorrectionOutput;
import com.pawtrail.policy.application.dto.output.PolicyAdminOutput;
import com.pawtrail.policy.application.dto.output.PolicyFieldsOutput;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.exception.PolicyErrorCode;
import com.pawtrail.policy.domain.model.PetPolicy;
import com.pawtrail.policy.domain.model.PetPolicySource;
import com.pawtrail.policy.domain.model.PolicyCorrectionLog;
import com.pawtrail.policy.domain.model.PolicyFields;
import com.pawtrail.policy.domain.repository.PetPolicyRepository;
import com.pawtrail.policy.domain.repository.PetPolicySourceRepository;
import com.pawtrail.policy.domain.repository.PlaceLockRepository;
import com.pawtrail.policy.domain.repository.PolicyCorrectionLogRepository;
import com.pawtrail.policy.domain.rule.FieldSpec;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자가 조건을 확인하고 정정합니다.
 *
 * 정정은 별도 표가 아니라 소스 티어의 최상위 행으로 들어갑니다.
 * 그래서 재추출이 공공 행을 갈아 끼워도 사람이 정한 값이 밀려나지 않고,
 * 병합이 통째로 이겨 소스 간 어긋남도 만들어지지 않습니다.
 *
 * 충돌을 닫는 길은 이 정정뿐입니다. 정정 행이 이기면 병합에 참여한 소스가
 * 그 행 하나가 되어 공공 소스의 어긋남은 has_conflict 에 세지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class PolicyCorrectionService {

    private final PetPolicyRepository petPolicyRepository;
    private final PetPolicySourceRepository petPolicySourceRepository;
    private final PolicyCorrectionLogRepository policyCorrectionLogRepository;
    private final PolicyMergeService policyMergeService;
    private final PlaceLockRepository placeLockRepository;

    /**
     * 폼을 채울 현재 상태를 돌려줍니다.
     *
     * 조건 행이 없어도 404 가 아니라 빈 폼입니다.
     * 추출 전인 장소도 업주가 알려준 조건으로 바로 정정할 수 있어야 하기 때문입니다.
     */
    @Transactional(readOnly = true)
    public PolicyAdminOutput getCurrent(UUID placeId) {
        return currentOf(placeId);
    }

    /**
     * 조건을 정정합니다.
     *
     * 순서가 곧 규칙입니다.
     * <pre>
     * ① 출처가 MANUAL · OWNER 인지         아니면 400 POLICY_SOURCE_NOT_ALLOWED
     * ② 장소 잠금                          같은 장소의 적재 · 정정이 끝날 때까지 기다림
     * ③ MANUAL 인데 OWNER 행이 있는지      있으면 409 POLICY_OWNER_CORRECTION_EXISTS
     * ④ 저장 직전 병합 결과를 스냅샷으로 떠 둠   이력의 before
     * ⑤ 같은 출처의 정정 행을 갈아 끼우거나 새로 만듦
     * ⑥ 재병합                            같은 잠금을 다시 잡아도 막히지 않음
     * ⑦ 이력을 남김                        같은 값으로 다시 저장해도 남김
     * </pre>
     *
     * 잠금을 OWNER 확인보다 먼저 잡는 이유는 확인과 저장 사이가 비어 있으면 안 되기 때문입니다.
     * 잠그지 않으면 MANUAL 이 "OWNER 없음" 을 본 뒤 OWNER 가 먼저 커밋되고,
     * MANUAL 이 OWNER 를 못 본 병합 결과로 덮어 OWNER 행이 있는데 MANUAL 이 이긴 것으로 남습니다.
     * 조건 행이 없는 첫 정정에도 걸리도록 행이 아니라 장소 키로 잠급니다.
     *
     * before 를 같은 출처의 이전 정정 행이 아니라 병합 결과로 두는 것은
     * 첫 정정에서도 "원래 공공 값이 뭐였지" 가 남아야 하기 때문입니다.
     * 관리자가 폼에서 보던 값과도 정확히 같습니다.
     *
     * 모두 한 트랜잭션입니다. 조건만 바뀌고 이력이 안 남으면 앞선 값이 사라집니다.
     *
     * @param correctedBy 정정한 관리자의 계정 식별자
     */
    @Transactional
    public PolicyAdminOutput correct(UUID placeId, SourceType source, String reason,
                                     PolicyFields fields, String correctedBy) {
        if (source == null || !source.isCorrection()) {
            throw new CustomException(PolicyErrorCode.POLICY_SOURCE_NOT_ALLOWED);
        }

        placeLockRepository.lock(placeId);

        if (source == SourceType.MANUAL
                && petPolicySourceRepository.findByPlaceIdAndSource(placeId, SourceType.OWNER).isPresent()) {
            throw new CustomException(PolicyErrorCode.POLICY_OWNER_CORRECTION_EXISTS);
        }

        Map<String, Object> before = FieldSpec.snapshotOf(
                petPolicyRepository.findByPlaceId(placeId)
                        .map(PetPolicy::getFields)
                        .orElse(PolicyFields.empty()));

        Optional<PetPolicySource> existing = petPolicySourceRepository.findByPlaceIdAndSource(placeId, source);
        if (existing.isPresent()) {
            existing.get().replaceCorrection(fields, reason);
        } else {
            petPolicySourceRepository.save(PetPolicySource.corrected(placeId, source, fields, reason));
        }

        policyMergeService.remerge(placeId);

        policyCorrectionLogRepository.save(PolicyCorrectionLog.of(
                placeId, source, before, FieldSpec.snapshotOf(fields), reason, correctedBy));

        return currentOf(placeId);
    }

    /**
     * 값을 바꾸지 않고 병합만 다시 돌립니다.
     *
     * 이력을 남기지 않습니다. 값을 바꾸는 동작이 아니고,
     * 병합 결과가 달라지면 판에 흔적이 남습니다.
     *
     * 조건 소스가 하나도 없으면 404 입니다. 빈 조건 행을 만들지 않습니다.
     */
    @Transactional
    public void remerge(UUID placeId) {
        if (petPolicySourceRepository.findByPlaceId(placeId).isEmpty()) {
            throw new CustomException(PolicyErrorCode.POLICY_SOURCE_NOT_FOUND);
        }
        policyMergeService.remerge(placeId);
    }

    // 지금 이기고 있는 정정 행은 병합이 적어 둔 최상위 티어로 찾음
    // 읽을 때 티어 순서를 다시 계산하지 않음 — 저장된 결과와 결론이 둘이 되지 않게
    private PolicyAdminOutput currentOf(UUID placeId) {
        Optional<PetPolicy> policy = petPolicyRepository.findByPlaceId(placeId);

        PolicyFields fields = policy.map(PetPolicy::getFields).orElse(PolicyFields.empty());
        CorrectionOutput correction = policy
                .map(PetPolicy::getSourcePriority)
                .filter(SourceType::isCorrection)
                .flatMap(source -> petPolicySourceRepository.findByPlaceIdAndSource(placeId, source))
                .map(CorrectionOutput::from)
                .orElse(null);

        return new PolicyAdminOutput(placeId, PolicyFieldsOutput.from(fields), correction);
    }
}
