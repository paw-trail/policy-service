package com.pawtrail.policy.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.common.response.PageResponse;
import com.pawtrail.common.security.annotation.CurrentUser;
import com.pawtrail.common.security.principal.CustomUserPrincipal;
import com.pawtrail.policy.application.dto.output.OutboxMessageOutput;
import com.pawtrail.policy.application.dto.output.PolicyAdminOutput;
import com.pawtrail.policy.application.service.AdminOutboxService;
import com.pawtrail.policy.application.service.PolicyCorrectionService;
import com.pawtrail.policy.presentation.request.ManualCorrectionRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 조건 정정 화면과 이벤트 재발행 화면이 부르는 API 입니다.
 *
 * ADMIN 역할이 필요합니다. 게이트웨이가 먼저 막고 공통 보안 체인이 한 번 더 막습니다.
 * 조건 정정 화면은 대상 장소를 GET /api/v1/places/{placeId} · verdict · conflicts 로 함께 불러 보여 줍니다.
 *
 * outbox 두 경로는 auth · place · pet 과 모양이 같습니다.
 * 이벤트 재발행 화면이 다섯 서비스의 outbox 를 각각 불러 한곳에 모으므로
 * 경로 규칙 · 응답 · 상태 코드가 서비스마다 갈리면 화면에 분기가 생깁니다.
 * 그래서 재발행은 이 컨트롤러의 재병합(204)과 달리 선례대로 200 입니다.
 */
@RestController
@RequestMapping("/api/v1/admin/policies")
@RequiredArgsConstructor
public class AdminPolicyController {

    private final PolicyCorrectionService policyCorrectionService;
    private final AdminOutboxService adminOutboxService;

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

    /**
     * 발행이 끝내 실패해 멈춰 있는 이벤트를 봅니다.
     *
     * 아직 재시도 중인 건은 나오지 않습니다.
     * 비어 있다면 손댈 것이 없다는 뜻입니다.
     *
     * 경로 깊이가 GET /{placeId} 와 같으나 겹치지 않습니다.
     * 스프링은 변수가 없는 경로를 더 구체적인 것으로 보아 먼저 고르므로
     * outbox 가 장소 식별자로 읽혀 UUID 변환에 실패하는 일이 없습니다.
     */
    @GetMapping("/outbox")
    public ResponseEntity<CommonApiResponse<PageResponse<OutboxMessageOutput>>> findGivenUpOutbox(
            @PageableDefault(size = 20) Pageable pageable) {

        PageResponse<OutboxMessageOutput> response = adminOutboxService.findGivenUp(pageable);
        return ResponseEntity.ok(CommonApiResponse.success(response));
    }

    /**
     * 한 건을 다시 발행합니다.
     *
     * 위 목록의 id 를 그대로 넘깁니다.
     * 실패하면 성공으로 응답하지 않습니다. 보냈다고 알고 넘어가는 것이
     * 이 기능이 막으려던 상황 그 자체이기 때문입니다.
     * <pre>
     * 500  OUTBOX_REPUBLISH_FAILED  발행에 실패함 · 재시도 횟수가 오르고 마지막 오류가 갱신됨
     * </pre>
     */
    @PostMapping("/outbox/{outboxId}/retry")
    public ResponseEntity<CommonApiResponse<Void>> republishOutbox(
            @PathVariable UUID outboxId) {

        adminOutboxService.republish(outboxId);
        return ResponseEntity.ok(CommonApiResponse.success(null));
    }
}
