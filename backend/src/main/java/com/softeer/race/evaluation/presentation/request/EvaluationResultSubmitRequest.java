package com.softeer.race.evaluation.presentation.request;

import com.softeer.race.evaluation.application.dto.command.EvaluationResultSubmitCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 평가 결과 제출 요청. 사진과 진단서는 업로드 주소 발급 API가 돌려준 {@code fileUrl}을 보낸다.
 * <p>
 * 주행거리와 시세를 함께 받는 이유는 둘 중 하나만 채워진 차량이 나오지 않게 하기 위해서다.
 * 나눠 받으면 주행거리는 있는데 시세가 빈 차가 경매로 넘어갈 수 있다.
 */
@Schema(description = "평가 결과 제출 요청")
public record EvaluationResultSubmitRequest(

        /*
         * 상한은 계기판이 여섯 자리라는 데서 온다. 그보다 큰 값은 실측이 아니라 오타다.
         * 하한을 0이 아니라 1로 두는 것은 주행거리 0km인 중고차가 없기 때문이다.
         */
        @Schema(description = "실측 주행거리(km)", example = "45000")
        @Positive(message = "주행거리는 0보다 커야 합니다.")
        @Max(value = EvaluationResultSubmitRequest.MAX_MILEAGE_KM,
                message = "주행거리를 다시 확인해 주세요.")
        int mileage,

        // 만원 단위를 강제하지 않는다. QuotePolicy가 만원 단위로 내리는 것은 계산식이 만드는
        // 근거 없는 정밀도를 감추려는 장치이고, 실물을 보고 사람이 부른 금액에는 그 문제가 없다
        @Schema(description = "산정한 예상 시세(원)", example = "21500000")
        @Positive(message = "예상 시세는 0보다 커야 합니다.")
        long estimatedPrice,

        @Schema(description = "차량 사진 주소 목록. 보낸 순서가 표시 순서이며 첫 번째가 대표 이미지가 됩니다.",
                example = "[\"https://www.f1race.site/images/2026/08/"
                        + "3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg\"]")
        @NotEmpty(message = "차량 사진이 최소 한 장은 필요합니다.")
        @Size(max = EvaluationResultSubmitRequest.MAX_IMAGE_COUNT,
                message = "사진은 " + EvaluationResultSubmitRequest.MAX_IMAGE_COUNT + "장까지 등록할 수 있습니다.")
        List<@NotBlank(message = "사진 주소는 비어 있을 수 없습니다.") String> imageUrls,

        @Schema(description = "진단서 PDF 주소. documents/ 아래로 발급된 주소여야 합니다.",
                example = "https://www.f1race.site/documents/2026/08/"
                        + "3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf")
        @NotBlank(message = "진단서 주소는 필수입니다.")
        String diagnosticReportUrl
) {

    static final int MAX_MILEAGE_KM = 999_999;

    /** 업로드 주소 발급 상한과 같게 둔다. 한 번에 발급받은 것을 그대로 보낼 수 있어야 한다 */
    static final int MAX_IMAGE_COUNT = 20;

    /**
     * 평가사 식별자를 인자로 받는다. 본문으로 받으면 남의 이름을 대고 제출할 수 있어,
     * 배정된 평가사만 제출한다는 규칙이 무의미해진다.
     */
    public EvaluationResultSubmitCommand toCommand(long evaluationId, long evaluatorId) {
        return new EvaluationResultSubmitCommand(
                evaluationId, evaluatorId, mileage, estimatedPrice, imageUrls, diagnosticReportUrl);
    }
}
