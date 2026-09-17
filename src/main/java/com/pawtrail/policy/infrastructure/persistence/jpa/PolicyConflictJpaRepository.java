package com.pawtrail.policy.infrastructure.persistence.jpa;

import com.pawtrail.policy.domain.enums.ConflictType;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PolicyConflict;
import java.util.Collection;
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
public interface PolicyConflictJpaRepository extends JpaRepository<PolicyConflict, UUID> {

    List<PolicyConflict> findByPlaceId(UUID placeId);

    boolean existsByPlaceIdAndConflictTypeAndSourceIn(UUID placeId, ConflictType conflictType,
                                                      Collection<SourceType> sources);

    /**
     * 한 소스가 남긴 소스 내 어긋남을 지웁니다.
     *
     * extract 가 실어 보내는 종류라 그 소스의 것만 갈아 끼웁니다.
     *
     * clearAutomatically 를 켜지 않는 이유는 근거 쪽과 같습니다.
     * 재병합이 고치고 있던 엔티티가 준영속이 되어 그 변경이 조용히 사라집니다.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from PolicyConflict c where c.placeId = :placeId "
            + "and c.conflictType = com.pawtrail.policy.domain.enums.ConflictType.INTRA_SOURCE "
            + "and c.source = :source")
    int deleteIntraSource(@Param("placeId") UUID placeId,
                          @Param("source") SourceType source);

    /**
     * 이 장소의 소스 간 어긋남을 지웁니다.
     *
     * 재병합이 통째로 다시 만듭니다.
     * PolicyMerger 가 소스들을 나란히 놓고 계산해 내는 것이라
     * 소스 하나만 고쳐도 결과가 통째로 바뀝니다.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from PolicyConflict c where c.placeId = :placeId "
            + "and c.conflictType = com.pawtrail.policy.domain.enums.ConflictType.CROSS_SOURCE")
    int deleteCrossSource(@Param("placeId") UUID placeId);
}
