package com.pawtrail.policy.application.service;

import com.pawtrail.policy.domain.model.PetPolicy;
import com.pawtrail.policy.domain.model.PetPolicySource;
import com.pawtrail.policy.domain.model.PolicyConflict;
import com.pawtrail.policy.domain.repository.PetPolicyRepository;
import com.pawtrail.policy.domain.repository.PetPolicySourceRepository;
import com.pawtrail.policy.domain.repository.PolicyConflictRepository;
import com.pawtrail.policy.domain.model.PolicyFields;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 장소의 소스들을 다시 합칩니다.
 *
 * 규칙은 PolicyMerger 가 갖고 이 클래스는 읽기와 쓰기만 합니다.
 * 나눠 둔 덕에 병합 규칙을 엔티티 없이 단위 테스트로 검증할 수 있습니다.
 *
 * 부르는 곳이 둘입니다.
 * 적재가 청크를 다 넣고 나서 건드린 장소마다 한 번씩 부르고,
 * 관리자가 정정하거나 재병합을 요청할 때도 부릅니다.
 *
 * 스스로 트랜잭션을 열지 않습니다.
 * 부르는 쪽이 이미 열어 둔 것에 참여해야 하기 때문입니다.
 * 적재에서 병합만 따로 커밋되면, 소스 저장이 실패해 롤백됐는데
 * 병합 결과는 남는 상태가 생깁니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyMergeService {

    private final PetPolicySourceRepository petPolicySourceRepository;
    private final PetPolicyRepository petPolicyRepository;
    private final PolicyConflictRepository policyConflictRepository;

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
     * 칸별 승자는 판과 상관없이 늘 새로 적습니다.
     * batch 가 근거를 고르는 기준이라 값이 같아도 승자가 바뀌면 그대로 따라가야 합니다.
     *
     * @return 병합 결과가 이전과 달라졌는지
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public boolean remerge(UUID placeId) {
        List<PetPolicySource> sources = petPolicySourceRepository.findByPlaceId(placeId);
        MergeResult result = PolicyMerger.merge(sources);

        Optional<PetPolicy> existing = petPolicyRepository.findByPlaceId(placeId);
        boolean changed = existing.isEmpty() || isChanged(existing.get(), result);

        if (existing.isEmpty()) {
            petPolicyRepository.save(PetPolicy.merged(
                    placeId, result.fields(), result.hasConflict(), result.sourcePriority(),
                    result.fieldSources()));
        } else {
            existing.get().remerge(
                    result.fields(), result.hasConflict(), result.sourcePriority(),
                    result.fieldSources(), changed);
        }

        replaceCrossSourceConflicts(placeId, result);

        if (changed) {
            log.debug("조건을 다시 합쳤습니다. placeId={} 소스={} 충돌={}",
                    placeId, sources.size(), result.conflicts().size());
        }
        return changed;
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
     * 병합 결과가 이전과 달라졌는지 봅니다.
     *
     * 판이 오르는 기준이며 곧 알림이 나가는 기준입니다.
     * 재병합은 소스가 안 바뀌어도 부를 수 있는 동작이라, 부를 때마다 판을 올리면
     * 아무것도 안 바뀐 재병합으로 즐겨찾기한 사람 전부에게 알림이 갑니다.
     *
     * 조건 한 벌과 충돌 여부를 함께 봅니다.
     * 조건이 같아도 어긋남이 생기거나 사라졌으면 화면에 배지가 붙고 떨어지므로
     * 사용자에게는 달라진 것입니다.
     *
     * 칸별 승자는 보지 않습니다.
     * 승자만 바뀐 재병합은 사용자에게 보이는 값이 같아 알림 대상이 아닙니다.
     * 다만 batch 가 보여 줄 근거는 바뀌므로, 판을 올릴지는 policy.changed 이슈에서
     * 근거만 바뀐 경우와 함께 봅니다.
     */
    private boolean isChanged(PetPolicy existing, MergeResult result) {
        return hasDifferentFields(existing.getFields(), result.fields())
                || existing.isHasConflict() != result.hasConflict()
                || existing.getSourcePriority() != result.sourcePriority();
    }

    /**
     * 조건 스무 칸 중 달라진 것이 있는지 봅니다.
     *
     * PolicyFields 에 equals 를 두지 않았으므로 칸마다 비교합니다.
     * 값 객체에 equals 를 붙이면 BigDecimal 이 10 과 10.00 을 다르게 보아
     * 아무것도 안 바뀐 재병합에서 판이 오르고 알림이 나갑니다.
     * 칸마다 같음의 뜻이 다를 수 있다는 판단을 FieldSpec 이 이미 갖고 있어 그것을 씁니다.
     *
     * 다음 이슈에서 만들 changedFields 도 같은 순회를 씁니다.
     * 무엇이 달라졌는지를 알려면 어차피 칸마다 봐야 하므로,
     * 그때는 이 메서드가 이름 목록을 돌려주는 형태로 바뀝니다.
     */
    private boolean hasDifferentFields(PolicyFields before, PolicyFields after) {
        for (FieldSpec<?> spec : FieldSpec.ALL) {
            if (isDifferent(spec, before, after)) {
                return true;
            }
        }
        return false;
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
