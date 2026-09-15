package com.pawtrail.policy.infrastructure.persistence.jpa;

import com.pawtrail.policy.domain.model.PetPolicy;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 스프링 데이터가 구현체를 만들어 주는 인터페이스입니다.
 * 이 파일은 도메인이 보지 않습니다.
 *
 * PK 가 place_id 라 findById 가 곧 장소 조회입니다.
 * 도메인 쪽에서 findByPlaceId 로 이름을 바꿔 부르는 것은 읽는 사람이
 * "이 표의 식별자가 장소" 라는 것을 알아야 하기 때문입니다.
 */
public interface PetPolicyJpaRepository extends JpaRepository<PetPolicy, UUID> {

    List<PetPolicy> findByPlaceIdIn(List<UUID> placeIds);
}
