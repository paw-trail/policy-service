package com.pawtrail.policy.presentation.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * verdict 가 여러 장소의 조건을 한 번에 묻는 요청입니다.
 *
 * 이름은 verdict 명세의 {placeIds[], petIds[]} 와 같게 placeIds 로 둡니다.
 * verdict 가 받은 목록을 그대로 넘기므로 이름이 같아야 헷갈리지 않습니다.
 *
 * 500곳이 상한입니다.
 * verdict 가 한 번에 받는 장소가 500곳이라, 그대로 넘겨받으면 verdict 가 나눠 부를 일이 없습니다.
 * 이 저장소의 bulk 요청도 같은 500 입니다.
 *
 * 목록이 비어 있어도 막지 않습니다. 조회하지 않고 빈 결과를 돌려줍니다.
 * 목록 자체가 없으면 요청이 잘못된 것이라 400 입니다.
 *
 * @param placeIds 조건을 물을 장소들. 중복과 null 은 서비스가 걸러 냄
 */
public record BatchQueryRequest(

        @NotNull(message = "장소 식별자 목록은 필수입니다.")
        @Size(max = 500, message = "한 번에 조회할 수 있는 장소는 500곳입니다.")
        List<UUID> placeIds
) {
}
