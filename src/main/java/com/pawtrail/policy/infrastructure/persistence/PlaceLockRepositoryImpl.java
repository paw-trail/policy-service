package com.pawtrail.policy.infrastructure.persistence;

import com.pawtrail.policy.domain.repository.PlaceLockRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL 의 트랜잭션 단위 advisory 잠금으로 장소를 잠급니다.
 *
 * 행 잠금(SELECT ... FOR UPDATE)을 쓰지 않는 이유는 잠글 행이 늘 있지 않기 때문입니다.
 * 추출 전인 장소를 처음 정정할 때는 pet_policy 행이 없고, 그 순간에도 경쟁을 막아야 합니다.
 * advisory 잠금은 행과 상관없이 키 하나로 잡히며 트랜잭션이 끝나면 저절로 풀립니다.
 *
 * 키는 장소 식별자 문자열의 64비트 해시입니다.
 * 서로 다른 장소가 같은 키가 되더라도 두 작업이 한 줄로 설 뿐 결과가 틀어지지는 않습니다.
 *
 * 잠금 함수가 void 를 돌려주므로 FROM 절에 두고 상수를 고릅니다.
 * void 열을 그대로 읽으면 타입 매핑에 걸릴 수 있기 때문입니다.
 */
@Repository
public class PlaceLockRepositoryImpl implements PlaceLockRepository {

    private static final String LOCK_SQL =
            "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(CAST(:placeId AS text), 0))";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void lock(UUID placeId) {
        entityManager.createNativeQuery(LOCK_SQL)
                .setParameter("placeId", placeId.toString())
                .getSingleResult();
    }
}
