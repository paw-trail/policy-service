package com.pawtrail.policy.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.common.security.annotation.CurrentUser;
import com.pawtrail.common.security.principal.CustomUserPrincipal;
import com.pawtrail.policy.application.dto.output.PolicyAdminOutput;
import com.pawtrail.policy.application.service.PolicyCorrectionService;
import com.pawtrail.policy.presentation.request.ManualCorrectionRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 조건 정정 화면이 부르는 API 입니다.
 *
 * ADMIN 역할이 필요합니다. 게이트웨이가 먼저 막고 공통 보안 체인이 한 번 더 막습니다.
 * 화면은 대상 장소를 GET /api/v1/places/{placeId} · verdict · conflicts 로 함께 불러 보여 줍니다.
 */
@RestController
@RequestMapping("/api/v1/admin/policies")
@RequiredArgsConstructor
public class AdminPolicyController {

    private final PolicyCorrectionService policyCorrectionService;

    /**
     * 폼을 채울 현재 조건과 지금 이기고 있는 정정 행을 돌려줍니다.
     *
     * 조건 행이 없어도 200 이며 스무 칸이 전부 비어 있습니다.
     */
    @GetMapping("/{placeId}")
    public ResponseEntity<CommonApiResponse<PolicyAdminOutput>> getPolicy(@PathVariable UUID placeId) {
        return ResponseEntity.ok(CommonApiResponse.success(policyCorrectionService.getCurrent(placeId)));
    }

    /**
     * 조건을 정정합니다. 전체 교체입니다.
     *
     * 저장한 뒤의 현재 상태를 조회와 같은 모양으로 돌려줘 화면이 다시 부르지 않아도 됩니다.
     * <pre>
     * 400  VALIDATION_FAILED               필수값이 비었거나 조건 칸 검증에 걸림
     * 400  POLICY_SOURCE_NOT_ALLOWED       출처가 MANUAL · OWNER 가 아님
     * 409  POLICY_OWNER_CORRECTION_EXISTS  OWNER 행이 있는데 MANUAL 로 저장함
     * </pre>
     */
    @PutMapping("/{placeId}/manual")
    public ResponseEntity<CommonApiResponse<PolicyAdminOutput>> correct(
            @PathVariable UUID placeId,
            @Valid @RequestBody ManualCorrectionRequest request,
            @CurrentUser CustomUserPrincipal principal) {

        return ResponseEntity.ok(CommonApiResponse.success(policyCorrectionService.correct(
                placeId,
                request.source(),
                request.reason(),
                request.fields().toPolicyFields(),
                principal.accountId().toString())));
    }

    /**
     * 값을 바꾸지 않고 병합만 다시 돌립니다.
     *
     * 조건 소스가 하나도 없으면 404 POLICY_SOURCE_NOT_FOUND 입니다.
     */
    @PostMapping("/{placeId}/remerge")
    public ResponseEntity<Void> remerge(@PathVariable UUID placeId) {
        policyCorrectionService.remerge(placeId);
        return ResponseEntity.noContent().build();
    }
}
