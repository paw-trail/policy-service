package com.pawtrail.policy.domain.rule;

import com.pawtrail.policy.domain.enums.ExtractionMethod;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PolicyEvidence;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;

/**
 * batch 가 내보내는 근거를 고르고, 그 근거의 지문을 뜹니다.
 *
 * batch 조회와 재병합이 이 클래스 하나를 씁니다.
 * batch 는 고른 근거를 내보내고, 재병합은 고른 근거의 지문으로 판을 올릴지 정합니다.
 * 두 곳이 고르는 규칙이나 순서를 따로 가지면 같은 근거에 다른 지문이 나와,
 * 아무것도 안 바뀐 재병합에서 판이 오르거나 바뀐 근거를 놓칩니다.
 *
 * 순수 계산만 합니다. 리포지토리를 부르지 않으므로 엔티티만 만들어 단위 테스트로 확인할 수 있습니다.
 */
public final class AdoptedEvidence {

    /**
     * 근거를 늘어놓는 순서입니다.
     *
     * 조건 순서 → 소스 순서 → 조각 번호 → 원문 필드 → 문구 → 추출 방식입니다.
     * 소스 열거값의 순서가 공공 우선순위와 같아 목록 칸에서는 앞선 소스의 근거가 먼저 옵니다.
     * 조각 번호가 없는 근거(쪼갤 것이 없는 필드)는 같은 소스 안에서 앞에 둡니다.
     *
     * 원문 필드와 문구까지 비교하는 것은 순서를 끝까지 못 박기 위해서입니다.
     * 앞의 셋이 같은 근거가 둘이면 데이터베이스가 돌려준 순서가 그대로 남는데 그 순서는 보장되지 않습니다.
     * 그러면 같은 근거에서 지문이 달라지고, 같은 장소를 두 번 물었을 때 응답 순서도 달라집니다.
     *
     * 추출 방식이 마지막인 것도 같은 까닭입니다.
     * 규칙과 모델이 같은 원문 칸의 같은 문구를 근거로 대면 앞의 다섯이 모두 같은 두 줄이 생깁니다.
     * 비어 있는 방식(V25 이전 근거)은 앞에 둡니다.
     */
    public static final Comparator<PolicyEvidence> ORDER =
            Comparator.comparingInt((PolicyEvidence evidence) -> FieldSpec.orderOf(evidence.getFieldName()))
                    .thenComparing(PolicyEvidence::getSource)
                    .thenComparing(PolicyEvidence::getSegmentIndex,
                            Comparator.nullsFirst(Comparator.<Integer>naturalOrder()))
                    .thenComparing(PolicyEvidence::getOriginField,
                            Comparator.nullsFirst(Comparator.<String>naturalOrder()))
                    .thenComparing(PolicyEvidence::getSegmentText,
                            Comparator.nullsFirst(Comparator.<String>naturalOrder()))
                    .thenComparing(PolicyEvidence::getExtractionMethod,
                            Comparator.nullsFirst(Comparator.<ExtractionMethod>naturalOrder()));

    private AdoptedEvidence() {
    }

    /**
     * 그 장소의 근거 중 칸마다 이긴 소스의 것만 골라 늘어놓습니다.
     *
     * 근거 표는 소스마다 제 근거를 가지므로 병합에서 진 소스의 근거도 남아 있습니다.
     * 칸 이름으로만 고르면 정정한 값 옆에 옛 공공 문구가 출처로 뜨고,
     * 공공 소스끼리 갈린 칸에서는 최종 값과 반대 말을 하는 근거가 함께 붙습니다.
     *
     * 승자가 적혀 있지 않은 칸의 근거는 담지 않습니다.
     * 틀린 근거를 내보내느니 빠진 근거가 되는 쪽을 택합니다.
     *
     * @param evidences 그 장소의 근거 전부
     * @param sourcesOf 칸 이름을 받아 그 칸을 이긴 소스들을 돌려줌. 이긴 소스가 없으면 빈 목록
     * @return 고르고 늘어놓은 근거
     */
    public static List<PolicyEvidence> select(List<PolicyEvidence> evidences,
                                              Function<String, List<SourceType>> sourcesOf) {
        return evidences.stream()
                .filter(evidence -> sourcesOf.apply(evidence.getFieldName())
                        .contains(evidence.getSource()))
                .sorted(ORDER)
                .toList();
    }

    /**
     * 고른 근거의 지문을 뜹니다. SHA-256 을 소문자 16진수 64자로 돌려줍니다.
     *
     * batch 가 근거 한 줄에 싣는 여섯 값만 담습니다.
     * 추출 방식은 V25 에서 더했습니다. 판정 화면의 출처 표시가 달라지므로 방식만 바뀌어도 판이 올라야 합니다.
     * 공식이 바뀌면 옛 지문이 전부 새 것과 달라지므로, V25 가 옛 지문을 비워 판이 한꺼번에 오르지 않게 했습니다.
     * 식별자나 만든 시각은 넣지 않습니다. 적재는 근거를 지우고 다시 넣으므로 그 값들은
     * 같은 근거를 다시 보내도 달라지고, 그러면 아무것도 안 바뀐 재병합에서 판이 오릅니다.
     *
     * 값마다 길이를 앞에 붙여 이어 붙입니다.
     * 구분 문자로만 이으면 문구 안에 같은 문자가 들어 있을 때 서로 다른 근거가 같은 문자열이 됩니다.
     * null 은 길이 대신 표시를 남겨 빈 값과 가립니다.
     *
     * 근거가 하나도 없어도 지문이 나옵니다. 빈 목록도 batch 가 내보내는 모양 가운데 하나라
     * 근거가 있다가 없어지면 지문이 달라져야 합니다.
     *
     * @param adopted select 가 고르고 늘어놓은 근거
     * @return SHA-256 16진수 64자
     */
    public static String digest(List<PolicyEvidence> adopted) {
        StringBuilder canonical = new StringBuilder();
        for (PolicyEvidence evidence : adopted) {
            append(canonical, evidence.getFieldName());
            append(canonical, evidence.getSource() == null ? null : evidence.getSource().name());
            append(canonical, evidence.getOriginField());
            append(canonical, evidence.getSegmentIndex() == null
                    ? null : evidence.getSegmentIndex().toString());
            append(canonical, evidence.getSegmentText());
            append(canonical, evidence.getExtractionMethod() == null
                    ? null : evidence.getExtractionMethod().name());
            canonical.append('\n');
        }
        return sha256(canonical.toString());
    }

    private static void append(StringBuilder canonical, String value) {
        if (value == null) {
            canonical.append("-;");
            return;
        }
        canonical.append(value.length()).append(':').append(value).append(';');
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // 자바 표준 구현은 SHA-256 을 반드시 제공하므로 여기 올 일이 없음
            throw new IllegalStateException("SHA-256 을 쓸 수 없습니다.", e);
        }
    }
}
