package com.pawtrail.policy.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.policy.application.dto.output.ConflictOutput;
import com.pawtrail.policy.application.service.PolicyQueryService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장소 상세가 부르는 조건 충돌 목록입니다.
 *
 * 경로가 /api/v1/places 아래라 place 의 것처럼 보이나 이 서비스가 답합니다.
 * 조건이 어긋났는지는 조건의 주인이 알기 때문이며,
 * 게이트웨이가 이 경로 하나만 policy 로 보냅니다.
 */
@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceConflictController {

    private final PolicyQueryService policyQueryService;

    /**
     * 그 장소의 열린 조건 충돌을 돌려줍니다.
     *
     * 장소 상세는 판정 응답의 hasConflict 가 참일 때만 이것을 부릅니다.
     * 목록은 그 플래그가 센 것과 같은 집합이라 배지와 목록이 어긋나지 않습니다.
     *
     * 충돌이 없거나 조건 행이 없으면 빈 목록입니다. 404 를 내지 않습니다.
     * 이 서비스는 장소가 있는지 모르기 때문입니다.
     * placeId 가 UUID 형식이 아니면 공통 핸들러가 400 VALIDATION_FAILED 로 돌려줍니다.
     */
    @GetMapping("/{placeId}/conflicts")
    public ResponseEntity<CommonApiResponse<List<ConflictOutput>>> getConflicts(
            @PathVariable UUID placeId) {
        return ResponseEntity.ok(CommonApiResponse.success(policyQueryService.findConflicts(placeId)));
    }
}
