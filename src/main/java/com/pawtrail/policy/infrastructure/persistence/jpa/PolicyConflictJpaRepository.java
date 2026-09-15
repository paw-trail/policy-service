package com.pawtrail.policy.infrastructure.persistence.jpa;

import com.pawtrail.policy.domain.model.PolicyConflict;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 스프링 데이터가 구현체를 만들어 주는 인터페이스입니다.
 * 이 파일은 도메인이 보지 않습니다.
 */
public interface PolicyConflictJpaRepository extends JpaRepository<PolicyConflict, UUID> {

    List<PolicyConflict> findByPlaceId(UUID placeId);
}
