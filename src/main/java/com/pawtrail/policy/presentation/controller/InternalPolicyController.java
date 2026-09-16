package com.pawtrail.policy.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.policy.application.dto.output.BulkUpsertResult;
import com.pawtrail.policy.application.dto.output.PolicyBatchOutput;
import com.pawtrail.policy.application.service.PolicyBulkService;
import com.pawtrail.policy.application.service.PolicyQueryService;
import com.pawtrail.policy.presentation.request.BatchQueryRequest;
import com.pawtrail.policy.presentation.request.BulkUpsertRequest;
import jakarta.validation.Valid;
import java.util.List;
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
 * 적재(bulk)와 조회(batch) 둘입니다.
 * 이름이 한 글자 차이라 헷갈리기 쉬운데, bulk 는 extract 가 쓰고 batch 는 verdict 가 읽습니다.
 */
@RestController
@RequestMapping("/internal/policies")
@RequiredArgsConstructor
public class InternalPolicyController {

    private final PolicyBulkService policyBulkService;
    private final PolicyQueryService policyQueryService;

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

    /**
     * 여러 장소의 조건을 한 번에 돌려줍니다.
     *
     * verdict 가 검색 결과와 즐겨찾기 카드를 판정할 때 부릅니다.
     * 장소 상세의 판정도 장소 하나로 이것을 부르면 됩니다.
     *
     * POST 인 것은 장소 목록이 주소에 안 들어가기 때문입니다.
     * 식별자 하나가 41바이트라 500곳이면 20KB 인데 Tomcat 은 요청 줄과 헤더를 8KB 까지만 받습니다.
     * 하는 일은 place · pet 의 ids 조회와 같습니다.
     *
     * <pre>
     * 조건 행이 없는 장소     빠짐. "불러오지 못함" 이 아니라 "조건 정보 없음" 으로 읽어야 함
     * 스무 칸이 빈 행        담김
     * 순서 · 중복 · null     요청 순서대로 · 중복과 null 은 걸러 냄 · 빈 목록이면 빈 결과
     * 근거                  칸마다 그 값을 만든 소스의 것만
     * 400                  500곳을 넘을 때 · placeIds 가 없을 때
     * </pre>
     */
    @PostMapping("/batch")
    public ResponseEntity<CommonApiResponse<List<PolicyBatchOutput>>> findBatch(
            @Valid @RequestBody BatchQueryRequest request) {
        return ResponseEntity.ok(CommonApiResponse.success(
                policyQueryService.findByPlaceIds(request.placeIds())));
    }
}
