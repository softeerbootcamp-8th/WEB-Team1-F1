package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.evaluation.presentation.request.EvaluationResultSubmitRequest;
import com.softeer.race.evaluation.presentation.response.EvaluationResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "EvaluationResult", description = "평가 결과 API")
public interface EvaluationResultApi {

    @Operation(summary = "평가 결과 제출",
            description = """
                    평가사가 방문해 확인한 결과를 제출합니다. 실측 주행거리 · 산정 시세 · 차량 사진 ·
                    진단서가 한 번에 반영되며, 하나라도 잘못되면 아무것도 반영되지 않습니다.

                    사진과 진단서는 이 API로 보내지 않습니다. 업로드 주소 발급 API
                    (POST /api/uploads/presigned)로 각각 주소를 받아 파일을 올린 뒤, 돌려받은
                    fileUrl 을 여기로 보냅니다. 사진은 이미지 형식으로, 진단서는 application/pdf 로
                    발급받아야 합니다.

                    사진 목록은 통째로 교체됩니다. 한 장을 더하려면 기존 목록에 그 한 장을 더해
                    전부 보내야 합니다.

                    POST가 아니라 PUT인 것은 재제출을 교체로 처리하기 때문입니다. 잘못 적은
                    주행거리를 고치려면 다시 제출하면 되고, 몇 번을 보내도 결과는 마지막에 보낸
                    내용 하나입니다.

                    **이 신청에 배정된 평가사만 제출할 수 있습니다.** 배정은 배정 대기 목록에서
                    수락할 때 받으며, 아직 아무도 수락하지 않은 신청에는 누구도 제출할 수 없습니다.

                    제출이 끝나면 신청 상태가 DIAGNOSED가 되고, 그때부터 차량에 주행거리와
                    예상 시세가 채워집니다.
                    """)
    @ApiResponse(responseCode = "200", description = "제출되었거나 갈아 끼워졌습니다.")
    @ApiResponse(responseCode = "400",
            description = "값이 허용 범위를 벗어났거나, 우리가 발급하지 않은 주소입니다. "
                    + "사진 자리에 문서 주소를, 진단서 자리에 이미지 주소를 보낸 경우도 포함합니다.")
    @ApiResponse(responseCode = "401", description = "세션이 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "403", description = "다른 평가사가 담당인 신청입니다.")
    @ApiResponse(responseCode = "404", description = "없는 평가입니다.")
    @ApiResponse(responseCode = "409",
            description = "이미 반려되어 끝났거나, 아직 담당 평가사가 정해지지 않은 신청입니다. "
                    + "뒤쪽은 배정 대기 목록에서 수락하면 풀립니다.")
    ResponseEntity<EvaluationResultResponse> submit(
            AuthenticatedUser authenticatedUser,
            long evaluationId,
            EvaluationResultSubmitRequest request);
}
