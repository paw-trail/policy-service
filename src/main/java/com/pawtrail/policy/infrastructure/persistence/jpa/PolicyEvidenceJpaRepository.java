package com.pawtrail.policy.infrastructure.persistence.jpa;

import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PolicyEvidence;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 스프링 데이터가 구현체를 만들어 주는 인터페이스입니다.
 * 이 파일은 도메인이 보지 않습니다.
 */
public interface PolicyEvidenceJpaRepository extends JpaRepository<PolicyEvidence, UUID> {

    List<PolicyEvidence> findByPlaceId(UUID placeId);

    /**
     * 한 소스의 근거를 한 번에 지웁니다.
     *
     * flushAutomatically 를 켭니다.
     * 앞에서 저장한 것이 아직 안 나간 채로 삭제가 먼저 돌면 순서가 뒤집힙니다.
     *
     * clearAutomatically 는 켜지 않습니다.
     * 켜면 영속성 컨텍스트가 통째로 비워져 재병합이 고치고 있던 pet_policy 가
     * 준영속이 됩니다. 그 변경이 오류 없이 사라지므로
     * "병합했다고 로그는 찍혔는데 값은 그대로" 가 됩니다.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from PolicyEvidence e where e.placeId = :placeId and e.source = :source")
    int deleteByPlaceIdAndSource(@Param("placeId") UUID placeId,
                                 @Param("source") SourceType source);
}
