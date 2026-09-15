package com.pawtrail.policy.infrastructure.persistence.jpa;

import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PetPolicySource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 스프링 데이터가 구현체를 만들어 주는 인터페이스입니다.
 * 이 파일은 도메인이 보지 않습니다.
 */
public interface PetPolicySourceJpaRepository extends JpaRepository<PetPolicySource, UUID> {

    Optional<PetPolicySource> findByPlaceIdAndSource(UUID placeId,
                                                                       SourceType source);

    List<PetPolicySource> findByPlaceId(UUID placeId);
}
