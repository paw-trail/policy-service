package com.pawtrail.policy.domain.exception;

import com.pawtrail.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 이 서비스의 도메인 에러 코드입니다.
 *
 * 공통 코드는 CommonErrorCode 에 있고 도메인 개념은 여기에 둡니다.
 * 공통에 두면 코드 하나를 더할 때마다 공통 모듈 재배포와 전 서비스 버전업이 필요해집니다.
 *
 * getCode 는 반드시 name 을 그대로 반환합니다.
 * 상수 이름이 곧 응답의 code 값이자 API 계약인데, 규칙을 어겨도 컴파일러가 잡지 못합니다.
 *
 * 메시지는 고정 문자열입니다. 동적인 값이 필요하면 응답 data 에 담습니다.
 *
 * 이 파일은 조건 적재에서 처음 생겼습니다.
 */
public enum PolicyErrorCode implements ErrorCode {

    // 적재로 넣을 수 없는 소스임
    //
    // 이 경로로는 공공 3종만 들어옵니다.
    // MANUAL 과 OWNER 는 사람이 확인해 정한 값이라 관리자 API 로만 들어와야 합니다.
    // 배치가 그 행을 덮으면 소스는 그대로인 채 사유가 지워지고,
    // 그러면 배치가 뽑은 값이 병합 최상위 티어를 차지합니다.
    // 정정을 소스 티어로 넣은 설계가 그 자리에서 뒤집힙니다.
    //
    // * 엔티티도 같은 것을 막고 있으나 그쪽은 IllegalStateException 이라 500 이 나감
    //   요청 자체가 잘못된 것이므로 400 으로 알려야 부르는 쪽이 원인을 봄
    //
    // * 청크를 돌기 전에 미리 훑어서 냄
    //   100건 중 87번째에서 걸리면 앞 86건이 롤백되고 재시도가 도는데,
    //   원인이 요청에 있으면 몇 번을 다시 보내도 같음
    POLICY_SOURCE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "적재할 수 없는 소스입니다.");

    private final HttpStatus status;
    private final String message;

    PolicyErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public HttpStatus getHttpStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
