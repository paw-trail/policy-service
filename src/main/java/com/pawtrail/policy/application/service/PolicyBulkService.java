package com.pawtrail.policy.application.service;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.policy.application.dto.output.BulkUpsertResult;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.exception.PolicyErrorCode;
import com.pawtrail.policy.domain.model.PetPolicySource;
import com.pawtrail.policy.domain.model.PolicyConflict;
import com.pawtrail.policy.domain.model.PolicyEvidence;
import com.pawtrail.policy.domain.repository.PetPolicySourceRepository;
import com.pawtrail.policy.domain.repository.PolicyConflictRepository;
import com.pawtrail.policy.domain.repository.PolicyEvidenceRepository;
import com.pawtrail.policy.presentation.request.BulkItemRequest;
import com.pawtrail.policy.presentation.request.BulkUpsertRequest;
import com.pawtrail.policy.presentation.request.ConflictRequest;
import com.pawtrail.policy.presentation.request.EvidenceRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * extract 가 뽑은 조건을 받아 저장합니다.
 *
 * 청크 하나가 한 트랜잭션입니다.
 * 실패하면 그 청크만 롤백되고 extract 가 같은 청크를 다시 보냅니다.
 * 건별로 끊으면 실패 지점 앞쪽이 커밋된 채 남는데, 그 장소는 이미 병합까지 돌아
 * 판이 올라 있으므로 재시도에서 판이 또 오르고 알림이 중복으로 나갑니다.
 *
 * 재병합은 마지막에 한 번씩만 합니다.
 * 한 청크에 같은 장소가 여러 번 올 수 있고(소스 셋이 붙은 곳은 세 번),
 * 넣을 때마다 합치면 판이 그만큼 오릅니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyBulkService {

    private final PetPolicySourceRepository petPolicySourceRepository;
    private final PolicyEvidenceRepository policyEvidenceRepository;
    private final PolicyConflictRepository policyConflictRepository;
    private final PolicyMergeService policyMergeService;

    /**
     * 청크 하나를 저장하고 건드린 장소를 다시 합칩니다.
     */
    @Transactional
    public BulkUpsertResult upsert(BulkUpsertRequest request) {
        validateSources(request.items());

        Set<UUID> touched = new LinkedHashSet<>();
        for (BulkItemRequest item : request.items()) {
            save(item, request);
            touched.add(item.placeId());
        }

        // 장소를 정렬해 잠그는 순서를 고정함
        // 두 적재가 겹치는 장소를 서로 다른 순서로 잠그면 서로를 기다리며 멈출 수 있음
        int merged = 0;
        for (UUID placeId : touched.stream().sorted().toList()) {
            policyMergeService.remerge(placeId);
            merged++;
        }

        log.info("조건을 적재했습니다. 항목={} 장소={} 모델={} 프롬프트={}",
                request.items().size(), merged, request.extractedBy(), request.promptVersion());
        return new BulkUpsertResult(request.items().size(), merged);
    }

    /**
     * 적재로 넣을 수 없는 소스가 섞여 있는지 먼저 봅니다.
     *
     * 청크를 돌기 전에 훑는 이유는 재시도 때문입니다.
     * 100건 중 87번째에서 걸리면 앞 86건이 롤백되고 extract 가 통째로 다시 보내는데,
     * 원인이 요청 자체에 있으면 몇 번을 보내도 같습니다.
     *
     * 엔티티도 같은 것을 막고 있으나 그쪽은 계약 위반이라 500 이 나갑니다.
     * 요청이 잘못된 것이므로 400 으로 알려야 부르는 쪽이 원인을 봅니다.
     */
    private void validateSources(List<BulkItemRequest> items) {
        for (BulkItemRequest item : items) {
            if (item.source() == SourceType.MANUAL || item.source() == SourceType.OWNER) {
                throw new CustomException(PolicyErrorCode.POLICY_SOURCE_NOT_ALLOWED);
            }
        }
    }

    /**
     * 한 항목을 저장합니다.
     *
     * 그 소스의 행이 이미 있으면 갈아 끼우고 없으면 만듭니다.
     * 장소당 소스별로 한 행이라는 제약이 재추출의 멱등을 만듭니다.
     */
    private void save(BulkItemRequest item, BulkUpsertRequest batch) {
        Optional<PetPolicySource> existing =
                petPolicySourceRepository.findByPlaceIdAndSource(item.placeId(), item.source());

        if (existing.isPresent()) {
            existing.get().replaceExtraction(
                    item.fields().toPolicyFields(), item.extractionMethod(),
                    batch.extractedBy(), batch.promptVersion(), batch.extractedAt());
        } else {
            petPolicySourceRepository.save(PetPolicySource.extracted(
                    item.placeId(), item.source(), item.fields().toPolicyFields(),
                    item.extractionMethod(), batch.extractedBy(),
                    batch.promptVersion(), batch.extractedAt()));
        }

        replaceEvidence(item);
        replaceIntraSourceConflicts(item);
    }

    /**
     * 그 소스의 근거를 갈아 끼웁니다.
     *
     * 지우기와 넣기가 한 트랜잭션에 있어야 합니다.
     * 지우기만 하고 넣기가 실패하면 근거가 통째로 비어 그 장소의 판정에 이유가 사라집니다.
     */
    private void replaceEvidence(BulkItemRequest item) {
        policyEvidenceRepository.deleteByPlaceIdAndSource(item.placeId(), item.source());

        List<EvidenceRequest> requests = item.evidenceOrEmpty();
        if (requests.isEmpty()) {
            return;
        }
        List<PolicyEvidence> evidences = new ArrayList<>(requests.size());
        for (EvidenceRequest evidence : requests) {
            evidences.add(PolicyEvidence.of(
                    item.placeId(), item.source(), evidence.fieldName(),
                    evidence.originField(), evidence.segmentIndex(), evidence.segmentText()));
        }
        policyEvidenceRepository.saveAll(evidences);
    }

    /**
     * 그 소스의 소스 내 어긋남을 갈아 끼웁니다.
     *
     * 소스 간 어긋남은 여기서 건드리지 않습니다.
     * 그것은 재병합이 통째로 다시 만듭니다.
     */
    private void replaceIntraSourceConflicts(BulkItemRequest item) {
        policyConflictRepository.deleteIntraSource(item.placeId(), item.source());

        List<ConflictRequest> requests = item.conflictsOrEmpty();
        if (requests.isEmpty()) {
            return;
        }
        List<PolicyConflict> conflicts = new ArrayList<>(requests.size());
        for (ConflictRequest conflict : requests) {
            conflicts.add(PolicyConflict.intraSource(
                    item.placeId(), item.source(), conflict.fieldName(),
                    conflict.sourceValues()));
        }
        policyConflictRepository.saveAll(conflicts);
    }
}
