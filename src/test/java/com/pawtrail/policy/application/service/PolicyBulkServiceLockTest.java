package com.pawtrail.policy.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.policy.IntegrationTestSupport;
import com.pawtrail.policy.domain.enums.ExtractionMethod;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PetPolicySource;
import com.pawtrail.policy.domain.model.PolicyFields;
import com.pawtrail.policy.domain.repository.PetPolicySourceRepository;
import com.pawtrail.policy.domain.repository.PlaceLockRepository;
import com.pawtrail.policy.presentation.request.BulkItemRequest;
import com.pawtrail.policy.presentation.request.BulkUpsertRequest;
import com.pawtrail.policy.presentation.request.PolicyFieldsRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 적재가 원재료 행을 쓰기 전에 장소 잠금을 잡는지 확인합니다.
 *
 * 잠금을 쓰기 뒤에 잡으면 두 적재가 같은 원재료 행들을 반대 순서로 쓸 때
 * 장소 잠금에 닿기 전에 행 잠금끼리 서로를 기다려 교착이 납니다.
 * 여기서는 그 모양을 한 장소로 줄여 결정적으로 만듭니다.
 * <pre>
 * A  장소 잠금을 쥔 채, 적재가 시작되고 1초 뒤 같은 원재료 행을 직접 고침
 * B  같은 장소 · 같은 소스를 적재
 * 잠금이 쓰기 뒤라면   B 가 행을 먼저 잠그고 장소 잠금에서 A 를 기다림 · A 는 그 행에서 B 를 기다림 → 교착
 * 잠금이 쓰기 앞이라면 B 는 행에 손대기 전에 기다림 → A 가 끝나고 B 가 이어서 끝남
 * </pre>
 *
 * 테스트 트랜잭션을 쓰지 않고 스레드마다 트랜잭션을 따로 엽니다. 그래서 넣은 행을 직접 치웁니다.
 */
class PolicyBulkServiceLockTest extends IntegrationTestSupport {

    private static final List<String> TABLES =
            List.of("policy_evidence", "policy_conflict", "pet_policy_source", "pet_policy");

    @Autowired
    private PolicyBulkService policyBulkService;

    @Autowired
    private PlaceLockRepository placeLockRepository;

    @Autowired
    private PetPolicySourceRepository petPolicySourceRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final UUID placeId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        inTransaction(() -> TABLES.forEach(table ->
                entityManager.createNativeQuery("DELETE FROM " + table + " WHERE place_id = :placeId")
                        .setParameter("placeId", placeId)
                        .executeUpdate()));
    }

    @Test
    @DisplayName("장소 잠금을 쥔 트랜잭션이 원재료 행을 고쳐도 적재와 교착이 나지 않는다")
    void 적재는_쓰기_전에_잠금을_기다린다() throws Exception {
        // 고칠 원재료 행을 먼저 커밋해 둠
        inTransaction(() -> policyBulkService.upsert(
                request(PolicyFields.builder().indoorAllowed(false).build())));

        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch bulkStarted = new CountDownLatch(1);

        Future<?> holder = pool.submit(() -> inTransaction(() -> {
            placeLockRepository.lock(placeId);
            holding.countDown();
            awaitQuietly(bulkStarted);

            // 적재가 쓰기부터 했다면 이 사이에 그 행을 이미 잠가 둠
            sleepQuietly(1000);

            PetPolicySource row = petPolicySourceRepository
                    .findByPlaceIdAndSource(placeId, SourceType.GOCAMPING)
                    .orElseThrow();
            row.replaceExtraction(PolicyFields.builder().indoorAllowed(true).build(),
                    ExtractionMethod.RULE, null, "lock-test", LocalDateTime.now());
            entityManager.flush();
        }));
        assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();

        Future<?> bulk = pool.submit(() -> {
            bulkStarted.countDown();
            inTransaction(() -> policyBulkService.upsert(
                    request(PolicyFields.builder().outdoorAllowed(false).build())));
        });

        // 교착이 나면 PostgreSQL 이 한쪽 트랜잭션을 끊어 여기서 예외가 올라옴
        holder.get(15, TimeUnit.SECONDS);
        bulk.get(15, TimeUnit.SECONDS);

        // 적재가 A 가 끝난 뒤에 썼으므로 최종 값은 적재의 것 — 조건은 통째로 갈아 끼움
        Boolean[] finalValues = inTransactionResult(() -> {
            PolicyFields fields = petPolicySourceRepository
                    .findByPlaceIdAndSource(placeId, SourceType.GOCAMPING)
                    .orElseThrow()
                    .getFields();
            return new Boolean[] {fields.getOutdoorAllowed(), fields.getIndoorAllowed()};
        });
        assertThat(finalValues[0]).isFalse();
        assertThat(finalValues[1]).isNull();
    }

    private BulkUpsertRequest request(PolicyFields fields) {
        return new BulkUpsertRequest(null, "v1", LocalDateTime.now(), List.of(
                new BulkItemRequest(placeId, SourceType.GOCAMPING, fieldsOf(fields),
                        List.of(), List.of(), ExtractionMethod.RULE)));
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private <T> T inTransactionResult(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static PolicyFieldsRequest fieldsOf(PolicyFields fields) {
        return new PolicyFieldsRequest(
                fields.getScope(), fields.getGuideDogOnly(), fields.getPetOnly(),
                fields.getIndoorAllowed(), fields.getOutdoorAllowed(),
                fields.getMaxWeightKg(), fields.getWeightInclusive(), fields.getMaxCount(),
                fields.getSizeRule(), fields.getBreedRule(),
                fields.getCarrierRequired(), fields.getLeashRequired(),
                fields.getExcludedZones(), fields.getAllowedZonesOnly(), fields.getExcludedDays(),
                fields.getExtraFeeAmount(), fields.getExtraFeeUnit(), fields.getRequiredItems(),
                fields.getVaccineProof(), fields.getAdvanceInquiry());
    }
}
