package com.pawtrail.policy.infrastructure.persistence.jpa;

import com.pawtrail.policy.domain.model.PolicyCorrectionLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 스프링 데이터가 구현체를 만들어 주는 인터페이스입니다.
 * 이 파일은 도메인이 보지 않습니다.
 *
 * 소프트 딜리트 조건이 없습니다.
 * 이 엔티티는 BaseEntity 를 상속하지 않아 deleted_at 자체가 없습니다.
 */
public interface PolicyCorrectionLogJpaRepository
        extends JpaRepository<PolicyCorrectionLog, UUID> {

    List<PolicyCorrectionLog> findByPlaceIdOrderByCorrectedAtDesc(UUID placeId);
}
