package com.pawtrail.policy.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.policy.application.dto.output.BulkUpsertResult;
import com.pawtrail.policy.application.service.PolicyBulkService;
import com.pawtrail.policy.presentation.request.BulkUpsertRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다른 서비스가 부르는 조건 API 입니다.
 *
 * 게이트웨이가 /internal 을 라우팅하지 않으므로 브라우저에서는 닿지 않습니다.
 * 토큰도 다루지 않습니다. 네트워크로 격리하는 것이 이 경로의 보호입니다.
 *
 * 지금은 적재 하나뿐이고 조회는 다음 이슈에서 붙습니다.
 */
@RestController
@RequestMapping("/internal/policies")
@RequiredArgsConstructor
public class InternalPolicyController {

    private final PolicyBulkService policyBulkService;

    /**
     * extract 가 뽑은 조건을 받습니다.
     *
     * 청크 하나가 한 트랜잭션이라 실패하면 그 청크만 롤백되고 extract 가 다시 보냅니다.
     * 같은 청크를 두 번 받아도 결과가 같습니다.
     * 장소당 소스별로 한 행이라는 제약이 그것을 만듭니다.
     *
     * 받아 넣은 항목 수와 다시 합친 장소 수를 함께 돌려줍니다.
     * 같은 장소에 소스가 여럿 들어오면 병합은 장소당 한 번이므로 뒤가 더 작은 것이 정상입니다.
     *
     * MANUAL 과 OWNER 가 섞여 있으면 400 POLICY_SOURCE_NOT_ALLOWED 입니다.
     * 사람이 확인해 정한 값이라 관리자 API 로만 들어와야 합니다.
     */
    @PostMapping("/bulk")
    public ResponseEntity<CommonApiResponse<BulkUpsertResult>> upsertBulk(
            @Valid @RequestBody BulkUpsertRequest request) {
        return ResponseEntity.ok(CommonApiResponse.success(policyBulkService.upsert(request)));
    }
}
