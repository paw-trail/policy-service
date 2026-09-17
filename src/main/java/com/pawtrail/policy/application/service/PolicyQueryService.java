package com.pawtrail.policy.application.service;

import com.pawtrail.policy.application.dto.output.ConflictOutput;
import com.pawtrail.policy.application.dto.output.EvidenceOutput;
import com.pawtrail.policy.application.dto.output.PolicyBatchOutput;
import com.pawtrail.policy.domain.enums.ConflictType;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PetPolicy;
import com.pawtrail.policy.domain.model.PolicyConflict;
import com.pawtrail.policy.domain.model.PolicyEvidence;
import com.pawtrail.policy.domain.repository.PetPolicyRepository;
import com.pawtrail.policy.domain.repository.PolicyConflictRepository;
import com.pawtrail.policy.domain.repository.PolicyEvidenceRepository;
import com.pawtrail.policy.domain.rule.FieldSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조건을 읽어 갑니다.
 *
 * 적재 · 병합과 나눈 것은 place 의 PlaceQueryService 와 같은 까닭입니다.
 * 읽기는 트랜잭션을 읽기 전용으로 열고, 쓰기 쪽 규칙(판 · 충돌 · 갈아 끼우기)을 몰라도 됩니다.
 */
@Service
@RequiredArgsConstructor
public class PolicyQueryService {

    /**
     * 근거를 늘어놓는 순서입니다.
     *
     * 조건 순서 → 소스 순서 → 조각 번호입니다.
     * 소스 열거값의 순서가 공공 우선순위와 같아 목록 칸에서는 앞선 소스의 근거가 먼저 옵니다.
     * 조각 번호가 없는 근거(쪼갤 것이 없는 필드)는 같은 소스 안에서 앞에 둡니다.
     * 순서를 못 박아야 같은 장소를 두 번 물었을 때 응답이 같습니다.
     */
    private static final Comparator<PolicyEvidence> EVIDENCE_ORDER =
            Comparator.comparingInt((PolicyEvidence evidence) -> FieldSpec.orderOf(evidence.getFieldName()))
                    .thenComparing(PolicyEvidence::getSource)
                    .thenComparing(PolicyEvidence::getSegmentIndex,
                            Comparator.nullsFirst(Comparator.<Integer>naturalOrder()));

    /**
     * 충돌을 늘어놓는 순서입니다.
     *
     * 조건 순서 → 소스 간 어긋남 먼저 → 소스 순서입니다.
     * 소스 간 어긋남은 소스 칸이 비어 있어 같은 조건 안에서 앞에 옵니다.
     */
    private static final Comparator<PolicyConflict> CONFLICT_ORDER =
            Comparator.comparingInt((PolicyConflict conflict) -> FieldSpec.orderOf(conflict.getFieldName()))
                    .thenComparing(PolicyConflict::getConflictType)
                    .thenComparing(PolicyConflict::getSource,
                            Comparator.nullsFirst(Comparator.<SourceType>naturalOrder()));

    private final PetPolicyRepository petPolicyRepository;
    private final PolicyEvidenceRepository policyEvidenceRepository;
    private final PolicyConflictRepository policyConflictRepository;

    /**
     * 여러 장소의 조건을 한 번에 돌려줍니다.
     *
     * <b>조건 행이 없는 장소는 결과에서 빠집니다.</b>
     * 빈 조건을 만들어 담지 않습니다.
     * 동물병원 · 아직 추출 전 · 존재하지 않는 식별자가 여기 해당하며 policy 는 셋을 가릴 수 없습니다.
     * 스무 칸이 모두 비어 있는 행은 담깁니다. "추출은 했으나 원문에 조건이 없음" 이라 뜻이 다릅니다.
     *
     * 빠진 장소는 "불러오지 못함" 이 아니라 "조건 정보 없음" 으로 읽어야 합니다.
     * user 의 카드가 UNKNOWN 을 조건 정보 없음으로, 비어 있는 판정을 불러오지 못함으로 안내하므로
     * verdict 는 빠진 장소를 UNKNOWN 으로 채웁니다.
     *
     * 누락을 경고로 남기지 않습니다.
     * place 의 같은 조회는 없는 식별자가 섞이면 경고를 남기지만 거기서는 누락이 이상 신호입니다.
     * 여기서는 동물병원만 전체의 3분의 1 이라 누락이 정상이고, 남기면 검색마다 경고가 쌓입니다.
     *
     * 중복과 null 은 걸러 내고 결과는 요청한 순서를 따릅니다.
     *
     * <b>근거는 칸마다 그 값을 만든 소스의 것만 담습니다.</b>
     * 근거 표는 소스마다 제 근거를 가지므로 병합에서 진 소스의 근거도 남아 있습니다.
     * 승자는 병합이 pet_policy.field_sources 에 적어 둔 것을 따릅니다.
     */
    @Transactional(readOnly = true)
    public List<PolicyBatchOutput> findByPlaceIds(Collection<UUID> placeIds) {
        List<UUID> ids = placeIds == null ? List.of() : placeIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return List.of();
        }

        Map<UUID, PetPolicy> policies = petPolicyRepository.findByPlaceIds(ids).stream()
                .collect(Collectors.toMap(PetPolicy::getPlaceId, Function.identity()));

        if (policies.isEmpty()) {
            return List.of();
        }

        // 행이 있는 장소의 근거만 읽음 — 빠질 장소의 근거까지 읽을 이유가 없음
        Map<UUID, List<PolicyEvidence>> evidences = policyEvidenceRepository
                .findByPlaceIds(List.copyOf(policies.keySet())).stream()
                .collect(Collectors.groupingBy(PolicyEvidence::getPlaceId));

        List<PolicyBatchOutput> result = new ArrayList<>(policies.size());
        for (UUID id : ids) {
            PetPolicy policy = policies.get(id);
            if (policy == null) {
                continue;
            }
            result.add(PolicyBatchOutput.of(policy,
                    adoptedEvidence(policy, evidences.getOrDefault(id, List.of()))));
        }
        return result;
    }

    /**
     * 그 장소의 근거 중 칸마다 이긴 소스의 것만 골라 늘어놓습니다.
     *
     * 승자가 적혀 있지 않은 칸의 근거는 담지 않습니다.
     * V22 이전에 만들어져 아직 다시 병합되지 않은 행이 그렇습니다.
     * 틀린 근거를 내보내느니 빠진 근거가 되는 쪽을 택합니다.
     */
    private List<EvidenceOutput> adoptedEvidence(PetPolicy policy, List<PolicyEvidence> evidences) {
        return evidences.stream()
                .filter(evidence -> policy.sourcesOf(evidence.getFieldName())
                        .contains(evidence.getSource()))
                .sorted(EVIDENCE_ORDER)
                .map(EvidenceOutput::from)
                .toList();
    }

    /**
     * 한 장소의 열린 조건 충돌을 돌려줍니다.
     *
     * <b>has_conflict 가 센 것과 같은 집합입니다.</b>
     * 장소 상세는 hasConflict 가 참일 때만 이 목록을 부르므로, 둘이 어긋나면
     * 배지가 붙었는데 목록이 비거나 목록이 있는데 배지가 안 붙습니다.
     * 플래그가 거짓이면 행이 있어도 빈 목록입니다.
     * 정정 행이 이긴 장소는 병합에 참여한 소스가 그 행 하나라 공공 소스의 소스 내 어긋남을 담지 않습니다.
     * 그 경우 플래그도 거짓이라 앞에서 이미 빈 목록이 되나, 규칙이 드러나게 거르는 자리를 남깁니다.
     *
     * 조건 행이 없어도 404 가 아니라 빈 목록입니다.
     * 이 서비스는 장소가 있는지 모르며, batch 가 행 없는 장소를 빼는 것과 같은 까닭입니다.
     */
    @Transactional(readOnly = true)
    public List<ConflictOutput> findConflicts(UUID placeId) {
        Optional<PetPolicy> policy = petPolicyRepository.findByPlaceId(placeId);
        if (policy.isEmpty() || !policy.get().isHasConflict()) {
            return List.of();
        }

        SourceType sourcePriority = policy.get().getSourcePriority();
        boolean correctionWins = sourcePriority != null && sourcePriority.isCorrection();

        return policyConflictRepository.findByPlaceId(placeId).stream()
                .filter(conflict -> conflict.getConflictType() == ConflictType.CROSS_SOURCE
                        || !correctionWins)
                .sorted(CONFLICT_ORDER)
                .map(ConflictOutput::from)
                .toList();
    }
}
