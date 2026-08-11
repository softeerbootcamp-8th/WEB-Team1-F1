package com.softeer.race.evaluation.presentation.request;

import com.softeer.race.evaluation.application.dto.command.EvaluationResultPatchCommand;
import com.softeer.race.vehicle.domain.VehicleKeyword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 이미 제출한 평가 결과에서 바꾸려는 항목만 보내는 요청. <b>보내지 않은 필드는 그대로 남는다.</b>
 * <p>
 * 제출 요청({@link EvaluationResultSubmitRequest})과 값의 범위는 같지만 필수 여부가 정반대라
 * 별도 타입으로 둔다. 저쪽의 {@code @NotEmpty} · {@code @NotBlank}를 그대로 쓰면 "보내지 않음"이
 * 전부 400이 되어 부분 수정 자체가 성립하지 않는다.
 * <p>
 * 대신 <b>범위 제약은 저쪽 상수를 그대로 가리킨다.</b> 상한을 여기 다시 적으면 계기판이 일곱
 * 자리가 되는 날 한쪽만 고쳐, 제출은 통과하는 값이 수정에서 막히거나 그 반대가 된다.
 * <p>
 * null을 허용하면서 잘못된 값은 막기 위해 제약을 골라 쓴다. {@code @Positive} · {@code @Max} ·
 * {@code @Size} · {@code @Pattern}은 모두 null을 통과시키고 값이 있을 때만 판정한다 —
 * {@code @NotNull} 계열만 null 자체를 잘못으로 본다.
 */
@Schema(description = "평가 결과 항목별 수정 요청. 보내지 않은 항목은 그대로 유지됩니다.")
public record EvaluationResultPatchRequest(

        @Schema(description = "바꿀 실측 주행거리(km). 생략하면 그대로 둡니다.", example = "46000")
        @Positive(message = "주행거리는 0보다 커야 합니다.")
        @Max(value = EvaluationResultSubmitRequest.MAX_MILEAGE_KM,
                message = "주행거리를 다시 확인해 주세요.")
        Integer mileage,

        @Schema(description = "바꿀 예상 시세(원). 생략하면 그대로 둡니다.", example = "21000000")
        @Positive(message = "예상 시세는 0보다 커야 합니다.")
        Long estimatedPrice,

        /*
         * 최소 한 장은 @Size(min = 1)로 강제한다. @NotEmpty는 null도 잘못으로 보아 "사진은 안
         * 건드림"을 표현할 수 없다.
         *
         * 0장을 막는 이유는 대표 사진 때문이다. 목록이 비면 Vehicle.mainPhotoUrl에 넣을 값이
         * 없어지고, 경매 목록 썸네일과 경매방이 그 값을 읽는다.
         */
        @Schema(description = """
                수정 뒤의 사진 목록 전부. 보낸 순서가 표시 순서이며 첫 번째가 대표 이미지가 됩니다.
                목록에서 빠진 주소는 삭제되고, 새 주소는 추가됩니다. 생략하면 사진을 건드리지 않습니다.
                """,
                example = "[\"https://www.f1race.site/images/2026/08/"
                        + "3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg\"]")
        @Size(min = 1, max = EvaluationResultSubmitRequest.MAX_IMAGE_COUNT,
                message = "사진은 1장 이상 "
                        + EvaluationResultSubmitRequest.MAX_IMAGE_COUNT + "장 이하여야 합니다.")
        List<@NotBlank(message = "사진 주소는 비어 있을 수 없습니다.") String> imageUrls,

        /*
         * @NotBlank를 쓸 수 없어 @Pattern으로 공백만 막는다. 공백 아닌 문자가 하나라도 있으면
         * 통과시키고, 그 주소가 우리가 발급한 문서인지는 저장소가 판정한다.
         */
        @Schema(description = "갈아 끼울 진단서 PDF 주소. 생략하면 그대로 둡니다.",
                example = "https://www.f1race.site/documents/2026/08/"
                        + "3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf")
        @Pattern(regexp = ".*\\S.*", message = "진단서 주소는 비어 있을 수 없습니다.")
        String diagnosticReportUrl,

        /*
         * 여기서는 빈 배열과 필드 없음이 갈린다. 제출에서 둘을 같게 볼 수 없어 @NotNull을 걸었던
         * 것과 달리, 이 요청에서 빈 배열은 "키워드를 전부 지운다"는 뜻이고 필드 없음은
         * "키워드는 건드리지 않는다"는 뜻이다.
         */
        @Schema(description = """
                수정 뒤의 키워드 전부. 빈 배열을 보내면 매겨 둔 키워드가 모두 지워집니다.
                생략하면 키워드를 건드리지 않습니다.
                """,
                example = "[\"ACCIDENT_FREE\", \"NO_LEAK\"]")
        @Size(max = EvaluationResultSubmitRequest.MAX_KEYWORD_COUNT,
                message = "키워드는 " + EvaluationResultSubmitRequest.MAX_KEYWORD_COUNT
                        + "개까지 매길 수 있습니다.")
        List<@NotNull(message = "키워드는 비어 있을 수 없습니다.") VehicleKeyword> keywords
) {

    /**
     * 아무것도 안 보낸 요청은 200 no-op이 아니라 400이다.
     * <p>
     * 조용히 성공시키면 <b>필드 이름을 틀린 요청이 성공으로 보인다.</b> 이 요청은 모르는 필드를
     * 무시하므로 {@code milage}라고 적어 보낸 평가사는 200을 받고 고쳤다고 믿는데, 실제로는
     * 아무것도 바뀌지 않은 채 잘못된 주행거리가 판매자에게 계속 보인다.
     */
    @AssertTrue(message = "수정할 항목을 하나 이상 보내야 합니다.")
    boolean isAnyFieldPresent() {
        return mileage != null || estimatedPrice != null || imageUrls != null
                || diagnosticReportUrl != null || keywords != null;
    }

    /**
     * 평가사 식별자를 인자로 받는다. 제출과 같은 이유다 — 본문으로 받으면 남의 이름을 대고
     * 남의 담당 건을 고칠 수 있다.
     */
    public EvaluationResultPatchCommand toCommand(long evaluationId, long evaluatorId) {
        return new EvaluationResultPatchCommand(
                evaluationId, evaluatorId, mileage, estimatedPrice, imageUrls, diagnosticReportUrl,
                keywords);
    }
}
