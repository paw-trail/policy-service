package com.pawtrail.policy.application.service;

import com.pawtrail.common.message.outbox.OutboxEventRecorder;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.event.payload.PolicyChangedEvent;
import com.pawtrail.policy.domain.model.PetPolicy;
import com.pawtrail.policy.domain.model.PetPolicySource;
import com.pawtrail.policy.domain.model.PolicyConflict;
import com.pawtrail.policy.domain.model.PolicyFields;
import com.pawtrail.policy.domain.repository.PetPolicyRepository;
import com.pawtrail.policy.domain.repository.PetPolicySourceRepository;
import com.pawtrail.policy.domain.repository.PlaceLockRepository;
import com.pawtrail.policy.domain.repository.PolicyConflictRepository;
import com.pawtrail.policy.domain.repository.PolicyEvidenceRepository;
import com.pawtrail.policy.domain.rule.AdoptedEvidence;
import com.pawtrail.policy.domain.rule.FieldSpec;
import com.pawtrail.policy.domain.rule.MergeResult;
import com.pawtrail.policy.domain.rule.PolicyMerger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 장소의 소스들을 다시 합치고, 결과가 달라졌으면 policy.changed 를 기록합니다.
 *
 * 규칙은 PolicyMerger 가 갖고 이 클래스는 읽기와 쓰기만 합니다.
 * 나눠 둔 덕에 병합 규칙을 엔티티 없이 단위 테스트로 검증할 수 있습니다.
 *
 * 부르는 곳이 셋입니다.
 * 적재가 청크를 다 넣고 나서 건드린 장소마다 한 번씩 부르고,
 * 관리자가 정정하거나 재병합 버튼을 누를 때도 부릅니다.
 *
 * <b>이벤트를 기록하는 곳이 여기 한 곳입니다.</b>
 * 판을 올리는 조건과 이벤트를 내는 조건이 같은 메서드에 있어야 한쪽만 고치는 일이 생기지 않습니다.
 * 부르는 경로마다 기록하면 한 경로를 빠뜨려도 컴파일도 테스트도 통과하고
 * 판만 오른 채 이벤트가 조용히 안 나갑니다.
 *
 * 스스로 트랜잭션을 열지 않습니다.
 * 부르는 쪽이 이미 열어 둔 것에 참여해야 하기 때문입니다.
 * 적재에서 병합만 따로 커밋되면, 소스 저장이 실패해 롤백됐는데
 * 병합 결과와 이벤트는 남는 상태가 생깁니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyMergeService {

    private final PetPolicySourceRepository petPolicySourceRepository;
    private final PetPolicyRepository petPolicyRepository;
    private final PolicyConflictRepository policyConflictRepository;
    private final PolicyEvidenceRepository policyEvidenceRepository;
    private final PlaceLockRepository placeLockRepository;
    private final OutboxEventRecorder outboxEventRecorder;

    /**
     * 이 장소의 조건을 다시 합칩니다.
     *
     * 소스 간 어긋남은 통째로 다시 만듭니다.
     * 병합이 소스들을 나란히 놓고 계산해 내는 것이라 소스 하나만 고쳐도
     * 결과가 통째로 바뀌기 때문입니다.
     *
     * 소스 내 어긋남은 건드리지 않습니다.
     * 그것은 extract 가 실어 보내는 것이라 적재가 갈아 끼웁니다.
     *
     * 칸별 승자와 근거 지문은 판과 상관없이 늘 새로 적습니다.
     * batch 가 근거를 고르는 기준이라 값이 같아도 승자가 바뀌면 그대로 따라가야 합니다.
     *
     * <b>has_conflict 는 병합이 찾은 소스 간 어긋남에 소스 내 어긋남을 더합니다.</b>
     * 소스 내 어긋남은 병합에 참여한 소스의 것만 셉니다.
     * 소스가 하나뿐인 장소에도 자기모순은 생기는데, 이 플래그가 거짓이면
     * 장소 상세가 충돌 목록을 부르지 않아 볼 길이 없어집니다.
     * 반대로 정정 행이 이기면 참여한 소스가 그 행 하나라 공공 소스의 자기모순은 세지 않습니다.
     * 사람이 확인해 정한 장소에 "확인하세요" 가 계속 뜨지 않게 하려는 것이며,
     * 소스 간 어긋남을 참여한 티어끼리만 비교하는 것과 같은 기준입니다.
     *
     * <b>batch 가 내보내는 것이 달라졌으면 판을 올리고 policy.changed 를 기록합니다.</b>
     * 조건 스무 칸 · 충돌 여부 · 최상위 티어 · batch 가 내보내는 근거 가운데 하나라도 달라지면 해당합니다.
     * 조건 행이 처음 생기는 첫 병합도 판 1 로 기록합니다.
     * 장소는 이미 있고 verdict 는 조건 행이 없는 장소를 UNKNOWN 으로 답하므로 그 답이 낡기 때문입니다.
     * 이벤트 행은 판을 올린 이 트랜잭션에 함께 들어가므로 "판은 올랐는데 이벤트가 없는" 상태가 생기지 않습니다.
     *
     * 소스가 하나도 없고 행도 없으면 아무것도 만들지 않습니다.
     * 빈 행을 만들면 "조건 행이 없음"(동물병원 · 추출 전)이
     * "추출했으나 조건이 없음" 으로 바뀌어 batch 에서 빠지던 장소가 빈 조건으로 담깁니다.
     *
     * <b>장소 잠금을 가장 먼저 잡습니다.</b>
     * 소스를 읽기 전에 잡아야 다른 트랜잭션이 넣은 행을 본 뒤에 계산합니다.
     * 적재 · 관리자 정정 · 재병합 버튼이 모두 이 메서드를 거치므로 한 장소의 병합이 한 줄로 섭니다.
     * 먼저 읽은 쪽이 나중에 커밋하며 상대가 넣은 정정 행을 못 본 결과로 덮는 일을 막고,
     * 같은 장소의 이벤트마다 판이 겹치지 않고 커집니다.
     *
     * @return batch 가 내보내는 것이 이전과 달라졌는지. 참이면 이벤트가 기록됨
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean remerge(UUID placeId) {
        placeLockRepository.lock(placeId);

        List<PetPolicySource> sources = petPolicySourceRepository.findByPlaceId(placeId);
        Optional<PetPolicy> existing = petPolicyRepository.findByPlaceId(placeId);

        if (sources.isEmpty() && existing.isEmpty()) {
            return false;
        }

        MergeResult result = PolicyMerger.merge(sources);
        boolean hasConflict = result.hasConflict()
                || policyConflictRepository.existsIntraSource(placeId, result.participants());
        String evidenceDigest = evidenceDigestOf(placeId, result);

        // 첫 병합은 빈 조건과 비교함 — 값이 있는 칸이 바뀐 칸이 되고 스무 칸이 비어 있으면 빈 목록임
        PolicyFields before = existing.map(PetPolicy::getFields).orElseGet(PolicyFields::empty);
        List<String> changedFields = changedFields(before, result.fields());

        boolean changed = existing.isEmpty() || isChanged(
                existing.get(), changedFields, hasConflict, result.sourcePriority(), evidenceDigest);

        PetPolicy policy;
        if (existing.isEmpty()) {
            policy = PetPolicy.merged(placeId, result.fields(), hasConflict,
                    result.sourcePriority(), result.fieldSources(), evidenceDigest);
            petPolicyRepository.save(policy);
        } else {
            policy = existing.get();
            policy.remerge(result.fields(), hasConflict, result.sourcePriority(),
                    result.fieldSources(), evidenceDigest, changed);
        }

        replaceCrossSourceConflicts(placeId, result);

        if (changed) {
            outboxEventRecorder.record(new PolicyChangedEvent(
                    placeId, policy.getPolicyVersion(), changedFields, hasConflict));
            log.debug("조건을 다시 합쳤습니다. placeId={} 판={} 바뀐 칸={} 소스={} 충돌={}",
                    placeId, policy.getPolicyVersion(), changedFields,
                    sources.size(), result.conflicts().size());
        }
        return changed;
    }

    /**
     * 이번 병합 결과로 batch 가 내보낼 근거의 지문을 뜹니다.
     *
     * 근거는 이 자리에서 다시 읽습니다.
     * 적재는 소스의 근거를 지운 뒤 다시 넣고 나서 이 메서드를 부르므로, 이미 새 근거가 들어 있습니다.
     *
     * 고르는 기준은 저장된 승자가 아니라 이번 병합이 계산한 승자입니다.
     * 엔티티에 승자를 적기 전에 부르기 때문이며, batch 가 다음에 읽을 승자와 같습니다.
     */
    private String evidenceDigestOf(UUID placeId, MergeResult result) {
        return AdoptedEvidence.digest(AdoptedEvidence.select(
                policyEvidenceRepository.findByPlaceId(placeId),
                fieldName -> result.fieldSources().getOrDefault(fieldName, List.of())));
    }

    /**
     * 소스 간 어긋남을 지우고 새 결과로 채웁니다.
     *
     * 지우고 다시 넣는 것은 이 표에 소프트 딜리트를 두지 않았기 때문입니다.
     * 무효화한 행을 남기면 같은 자리에 새 충돌이 생길 때 어느 것이 지금 것인지
     * 가릴 수 없습니다.
     */
    private void replaceCrossSourceConflicts(UUID placeId, MergeResult result) {
        policyConflictRepository.deleteCrossSource(placeId);

        if (result.conflicts().isEmpty()) {
            return;
        }
        List<PolicyConflict> conflicts = new ArrayList<>(result.conflicts().size());
        for (MergeResult.FieldConflict conflict : result.conflicts()) {
            conflicts.add(PolicyConflict.crossSource(
                    placeId, conflict.fieldName(), conflict.sourceValues()));
        }
        policyConflictRepository.saveAll(conflicts);
    }

    /**
     * batch 가 내보내는 것이 이전과 달라졌는지 봅니다.
     *
     * 판이 오르는 기준이며 곧 policy.changed 가 나가는 기준입니다.
     * 재병합은 소스가 안 바뀌어도 부를 수 있는 동작이라, 부를 때마다 판을 올리면
     * 아무것도 안 바뀐 재병합으로 이벤트가 나갑니다.
     *
     * 넷을 봅니다.
     *   조건 스무 칸      사용자에게 보이는 값
     *   충돌 여부         배지가 붙고 떨어짐
     *   최상위 티어       정정 출처 줄이 붙고 떨어짐 (batch 의 correctionSource)
     *   근거 지문         카드 한 줄 · 항목별 이유로 나가는 근거 문구
     *
     * 칸별 승자는 따로 보지 않습니다.
     * batch 가 내보내는 것은 승자가 아니라 승자의 근거이고, 지문이 그것을 이미 담습니다.
     */
    private boolean isChanged(PetPolicy existing, List<String> changedFields, boolean hasConflict,
                              SourceType sourcePriority, String evidenceDigest) {
        return !changedFields.isEmpty()
                || existing.isHasConflict() != hasConflict
                || existing.getSourcePriority() != sourcePriority
                || isEvidenceChanged(existing.getEvidenceDigest(), evidenceDigest);
    }

    /**
     * 근거 지문이 달라졌는지 봅니다.
     *
     * 저장된 지문이 없으면 달라지지 않은 것으로 봅니다.
     * V24 이전에 만들어졌거나 V25 가 지문 공식을 바꾸며 비운 행이며, 비교할 옛 지문이 없으므로
     * 이번에는 지문만 채우고 판은 올리지 않습니다.
     * 그 행들을 판 없이 전부 한 번씩 올리면 근거가 그대로인 장소에도 이벤트가 나갑니다.
     */
    private boolean isEvidenceChanged(String before, String after) {
        return before != null && !before.equals(after);
    }

    /**
     * 조건 스무 칸 중 값이 달라진 칸의 이름을 조건 순서로 돌려줍니다.
     *
     * PolicyFields 에 equals 를 두지 않았으므로 칸마다 비교합니다.
     * 값 객체에 equals 를 붙이면 BigDecimal 이 10 과 10.00 을 다르게 보아
     * 아무것도 안 바뀐 재병합에서 판이 오르고 이벤트가 나갑니다.
     * 칸마다 같음의 뜻이 다를 수 있다는 판단을 FieldSpec 이 이미 갖고 있어 그것을 씁니다.
     *
     * 이 목록이 이벤트의 changedFields 로 그대로 나갑니다.
     * 판을 올릴지 보는 비교와 이벤트에 싣는 목록이 한 번의 순회에서 나오므로 둘이 어긋나지 않습니다.
     */
    private List<String> changedFields(PolicyFields before, PolicyFields after) {
        List<String> names = new ArrayList<>();
        for (FieldSpec<?> spec : FieldSpec.ALL) {
            if (isDifferent(spec, before, after)) {
                names.add(spec.name());
            }
        }
        return names;
    }

    private <T> boolean isDifferent(FieldSpec<T> spec, PolicyFields before, PolicyFields after) {
        T left = spec.getter().apply(before);
        T right = spec.getter().apply(after);
        if (left == null || right == null) {
            return left != right;
        }
        return !spec.sameAs().test(left, right);
    }
}
