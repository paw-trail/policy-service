package com.pawtrail.policy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.message.outbox.OutboxMessage;
import com.pawtrail.common.message.outbox.OutboxPublisher;
import com.pawtrail.common.message.outbox.OutboxRepository;
import com.pawtrail.common.response.PageResponse;
import com.pawtrail.policy.application.dto.output.OutboxMessageOutput;
import com.pawtrail.policy.domain.exception.PolicyErrorCode;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 멈춘 이벤트를 보고 다시 내보내는 규칙을 검사합니다.
 *
 * 스프링 컨텍스트도 데이터베이스도 카프카도 띄우지 않습니다.
 * 조회와 발행은 공통 모듈이 하고 이 서비스가 하는 일은
 * 무엇을 보여줄지 고르고 응답으로 바꾸는 것뿐이라 그 판단만 검사하면 됩니다.
 *
 * 특히 지키려는 것이 둘입니다.
 * payload 가 응답에 실리면 안 됩니다. 다른 서비스의 같은 화면과 모양이 갈립니다.
 * 발행이 실패했는데 성공으로 응답하면 관리자는 보냈다고 알고 넘어가는데 이벤트는 안 나갑니다.
 * 그 상태가 바로 이 기능이 막으려던 것입니다.
 */
@ExtendWith(MockitoExtension.class)
class AdminOutboxServiceTest {

    private static final UUID OUTBOX_ID = UUID.fromString("11111111-0000-7000-8000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString("22222222-0000-7000-8000-000000000002");
    private static final UUID PLACE_ID = UUID.fromString("01a09015-b6bc-7812-8e7e-d0c59c46b007");

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private OutboxPublisher outboxPublisher;

    @InjectMocks
    private AdminOutboxService adminOutboxService;

    @Nested
    @DisplayName("목록")
    class 목록 {

        @Test
        @DisplayName("포기한 항목을 응답 형태로 바꾼다")
        void 응답_변환() {
            when(outboxRepository.findGivenUpMessages(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(givenUp()), PageRequest.of(0, 20), 1));

            PageResponse<OutboxMessageOutput> result =
                    adminOutboxService.findGivenUp(PageRequest.of(0, 20));

            assertThat(result.content()).hasSize(1);
            OutboxMessageOutput row = result.content().get(0);
            assertThat(row.id()).isEqualTo(OUTBOX_ID);
            assertThat(row.eventId()).isEqualTo(EVENT_ID);
            assertThat(row.topic()).isEqualTo("policy.changed");
            assertThat(row.aggregateType()).isEqualTo("Policy");
            assertThat(row.aggregateId()).isEqualTo(PLACE_ID.toString());
        }

        @Test
        @DisplayName("판단 재료인 재시도 횟수와 마지막 오류를 담는다")
        void 판단_재료() {
            when(outboxRepository.findGivenUpMessages(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(givenUp()), PageRequest.of(0, 20), 1));

            // 카프카가 잠시 죽었던 것인지 코드를 고쳐야 하는 것인지를 여기서 가름
            OutboxMessageOutput row =
                    adminOutboxService.findGivenUp(PageRequest.of(0, 20)).content().get(0);

            assertThat(row.retryCount()).isEqualTo(10);
            assertThat(row.lastError()).isEqualTo("Broker not available");
        }

        @Test
        @DisplayName("응답에 payload 필드가 없다")
        void payload_없음() {
            // 이 서비스의 payload 는 민감하지 않으나 다섯 서비스의 아웃박스 화면이 한곳에 모이므로 모양을 맞춤
            assertThat(OutboxMessageOutput.class.getRecordComponents())
                    .extracting(RecordComponent::getName)
                    .doesNotContain("payload");
        }

        @Test
        @DisplayName("포기한 항목이 없으면 빈 목록이다")
        void 빈_목록() {
            when(outboxRepository.findGivenUpMessages(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            // 비어 있는 것이 정상임. 손댈 것이 없다는 뜻임
            assertThat(adminOutboxService.findGivenUp(PageRequest.of(0, 20)).content()).isEmpty();
        }
    }

    @Nested
    @DisplayName("재발행")
    class 재발행 {

        @Test
        @DisplayName("발행에 성공하면 조용히 끝난다")
        void 성공() {
            when(outboxPublisher.publish(OUTBOX_ID)).thenReturn(true);

            adminOutboxService.republish(OUTBOX_ID);

            verify(outboxPublisher).publish(OUTBOX_ID);
        }

        @Test
        @DisplayName("발행에 실패하면 OUTBOX_REPUBLISH_FAILED 다")
        void 실패() {
            when(outboxPublisher.publish(OUTBOX_ID)).thenReturn(false);

            // 성공으로 응답하면 관리자는 보냈다고 알고 넘어가는데 이벤트는 안 나감
            assertThatThrownBy(() -> adminOutboxService.republish(OUTBOX_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(PolicyErrorCode.OUTBOX_REPUBLISH_FAILED);
        }
    }

    /**
     * Relay 가 포기한 항목을 만듭니다.
     *
     * 팩터리를 쓰지 않고 빈 객체를 만들어 값을 넣습니다.
     *
     * 그 팩터리는 문자열 넷을 잇달아 받아 인자 순서를 눈으로 확인해야 하고,
     * 재시도 횟수와 마지막 오류는 애초에 받지 않아 어차피 리플렉션이 필요합니다.
     * recordFailure 를 열 번 부르는 방법도 있으나
     * 검사가 그 메서드의 내부 동작에 기대게 됩니다.
     *
     * 이 검사가 지키려는 것은 공통 모듈의 값이 응답으로 어떻게 옮겨지는가이지
     * 그 객체를 어떻게 만드는가가 아닙니다.
     */
    private static OutboxMessage givenUp() {
        OutboxMessage message = newInstance();
        setField(message, "id", OUTBOX_ID);
        setField(message, "eventId", EVENT_ID);
        setField(message, "aggregateType", "Policy");
        setField(message, "aggregateId", PLACE_ID.toString());
        setField(message, "topic", "policy.changed");
        setField(message, "payload", "{\"placeId\":\"" + PLACE_ID + "\",\"policyVersion\":2,"
                + "\"changedFields\":[\"scope\"],\"hasConflict\":false}");
        setField(message, "retryCount", 10);
        setField(message, "lastError", "Broker not available");
        return message;
    }

    /**
     * 빈 객체를 만듭니다.
     *
     * 기본 생성자가 공개되어 있지 않습니다. 하이버네이트만 쓰라고 막아 둔 것입니다.
     */
    private static OutboxMessage newInstance() {
        try {
            Constructor<OutboxMessage> constructor = OutboxMessage.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("OutboxMessage 를 만들지 못했습니다", e);
        }
    }

    private static void setField(Object target, String name, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(name + " 을 넣지 못했습니다", e);
            }
        }
        throw new IllegalStateException(name + " 필드를 찾지 못했습니다");
    }
}
