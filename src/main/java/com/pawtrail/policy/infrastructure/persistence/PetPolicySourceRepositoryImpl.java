package com.pawtrail.policy.infrastructure.persistence;

import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PetPolicySource;
import com.pawtrail.policy.domain.repository.PetPolicySourceRepository;
import com.pawtrail.policy.infrastructure.persistence.jpa.PetPolicySourceJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 스프링 데이터로 구현합니다.
 *
 * 무효화된 행을 거르는 조건은 이 층에만 있습니다.
 * 도메인은 "이 장소의 소스" 를 물을 뿐이고 그 답에 죽은 행이 없는 것은 당연합니다.
 */
@Repository
@RequiredArgsConstructor
public class PetPolicySourceRepositoryImpl implements PetPolicySourceRepository {

    private final PetPolicySourceJpaRepository petPolicySourceJpaRepository;

    @Override
    public PetPolicySource save(PetPolicySource source) {
        return petPolicySourceJpaRepository.save(source);
    }

    @Override
    public Optional<PetPolicySource> findByPlaceIdAndSource(UUID placeId, SourceType source) {
        return petPolicySourceJpaRepository
                .findByPlaceIdAndSourceAndDeletedAtIsNull(placeId, source);
    }

    @Override
    public List<PetPolicySource> findByPlaceId(UUID placeId) {
        return petPolicySourceJpaRepository.findByPlaceIdAndDeletedAtIsNull(placeId);
    }
}
