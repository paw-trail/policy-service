package com.pawtrail.policy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawtrail.policy.IntegrationTestSupport;
import com.pawtrail.policy.domain.repository.PlaceLockRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 장소 잠금이 실제로 트랜잭션을 한 줄로 세우는지 확인합니다.
 *
 * 테스트 트랜잭션을 쓰지 않고 스레드마다 트랜잭션을 따로 엽니다.
 * 한 트랜잭션 안에서는 같은 잠금을 다시 잡아도 막히지 않아 경쟁을 볼 수 없기 때문입니다.
 * 잠금만 잡고 표에는 아무것도 쓰지 않으므로 치울 행이 없습니다.
 */
class PlaceLockRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private PlaceLockRepository placeLockRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    @Test
    @DisplayName("같은 장소는 앞선 트랜잭션이 끝날 때까지 기다린다")
    void 같은_장소는_기다린다() throws Exception {
        UUID placeId = UUID.randomUUID();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> first = pool.submit(() -> inTransaction(() -> {
            placeLockRepository.lock(placeId);
            holding.countDown();
            awaitQuietly(release);
        }));
        assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();

        Future<?> second = pool.submit(() -> inTransaction(() -> placeLockRepository.lock(placeId)));

        // 앞선 트랜잭션이 잡고 있는 동안에는 끝나지 않아야 함
        assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);

        release.countDown();
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("다른 장소는 기다리지 않는다")
    void 다른_장소는_기다리지_않는다() throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> first = pool.submit(() -> inTransaction(() -> {
            placeLockRepository.lock(UUID.randomUUID());
            holding.countDown();
            awaitQuietly(release);
        }));
        assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();

        Future<?> second = pool.submit(() -> inTransaction(() -> placeLockRepository.lock(UUID.randomUUID())));

        // 앞선 트랜잭션이 아직 잡고 있어도 바로 끝나야 함 — 장소마다 줄이 따로임
        second.get(5, TimeUnit.SECONDS);

        release.countDown();
        first.get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("같은 트랜잭션 안에서는 같은 장소를 다시 잡아도 막히지 않는다")
    void 같은_트랜잭션은_다시_잡아도_된다() throws Exception {
        // 정정이 잠금을 잡은 뒤 재병합이 같은 잠금을 한 번 더 잡는 흐름임
        UUID placeId = UUID.randomUUID();

        Future<?> task = pool.submit(() -> inTransaction(() -> {
            placeLockRepository.lock(placeId);
            placeLockRepository.lock(placeId);
        }));

        task.get(5, TimeUnit.SECONDS);
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
