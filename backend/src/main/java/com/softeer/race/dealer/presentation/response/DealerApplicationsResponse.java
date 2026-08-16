package com.softeer.race.dealer.presentation.response;

import com.softeer.race.dealer.application.dto.info.DealerApplicationSummaryInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 배열을 그대로 내려보내지 않고 객체로 감싼다. 나중에 총 건수나 페이징을 붙일 때 최상위 타입이
 * 배열이면 응답 형태 자체가 바뀌어 프론트가 함께 깨진다.
 */
@Schema(description = "딜러 심사 신청 목록")
public record DealerApplicationsResponse(
        @Schema(description = "신청 목록. 접수 순")
        List<DealerApplicationSummaryResponse> applications
) {

    public static DealerApplicationsResponse from(List<DealerApplicationSummaryInfo> infos) {
        return new DealerApplicationsResponse(
                infos.stream().map(DealerApplicationSummaryResponse::from).toList());
    }
}
