package com.softeer.race.progress.presentation.response;

import com.softeer.race.progress.application.dto.SellerProgressInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "내 진행 상황 목록")
public record SellerProgressListResponse(

        @Schema(description = "최근에 신청한 것부터")
        List<SellerProgressResponse> content
) {

    public static SellerProgressListResponse from(List<SellerProgressInfo> infos) {
        return new SellerProgressListResponse(
                infos.stream().map(SellerProgressResponse::from).toList());
    }
}
