package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.evaluation.presentation.request.ImageUploadRequest;
import com.softeer.race.evaluation.presentation.response.ImageUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "EvaluationImage", description = "평가 사진 업로드 API")
public interface EvaluationImageApi {

    @Operation(summary = "평가 사진 업로드 주소 발급",
            description = """
                    평가 사진을 저장소에 직접 올릴 수 있는 서명된 주소를 발급합니다. 파일은 이 API로
                    보내지 않습니다. 세 단계로 진행합니다.

                    1. 올릴 파일의 형식과 크기를 보내 uploadUrl 을 받습니다.
                    2. 받은 uploadUrl 로 파일을 PUT 합니다. 이때 Content-Type 헤더와 파일 크기가
                       1번에서 보낸 값과 정확히 같아야 하며, 다르면 업로드가 거부됩니다.
                    3. 함께 받은 fileUrl 을 평가 결과 등록 시 함께 보냅니다. 저장해야 하는 값은
                       uploadUrl 이 아니라 fileUrl 입니다.

                    uploadUrl 은 만료 시각이 지나면 쓸 수 없으므로 발급 후 바로 업로드해야 합니다.
                    세션 쿠키가 필요합니다.
                    """)
    @ApiResponse(responseCode = "200", description = "요청한 파일 순서대로 발급됩니다.")
    @ApiResponse(responseCode = "400",
            description = "지원하지 않는 형식이거나, 파일 크기·건수가 허용 범위를 벗어난 경우입니다. "
                    + "한 건이라도 형식이 잘못되면 아무것도 발급하지 않습니다.")
    @ApiResponse(responseCode = "401", description = "세션이 없거나 만료된 경우입니다.")
    ResponseEntity<ImageUploadResponse> issue(
            AuthenticatedUser authenticatedUser, ImageUploadRequest request);
}
