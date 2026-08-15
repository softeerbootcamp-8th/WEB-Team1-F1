package com.softeer.race.auctionlist.presentation.response;

import com.softeer.race.auctionlist.application.dto.AuctionCardInfo;
import com.softeer.race.auctionroom.domain.RoomPhase;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.VehicleKeyword;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "목록 카드 한 장")
public record AuctionCardResponse(

        @Schema(description = "경매 식별자, 경매방 입장에 쓴다", example = "1")
        Long auctionId,

        @Schema(description = "단계, 응답을 만든 시각 기준이다", example = "LIVE")
        RoomPhase phase,

        @Schema(description = "대표 이미지, 없으면 null", example = "https://cdn.race.dev/1.jpg")
        String thumbnailUrl,

        @Schema(description = "제조사", example = "HYUNDAI")
        Manufacturer manufacturer,

        @Schema(description = "차량 모델명", example = "아반떼 CN7")
        String model,

        @Schema(description = "연식", example = "2022")
        Integer modelYear,

        @Schema(description = "주행거리(km)", example = "35000")
        Integer mileage,

        @Schema(description = "평가사가 진단에서 확인한 키워드, 표시 순서대로, 없으면 빈 배열", example = "[\"ACCIDENT_FREE\", \"UNDERBODY_INTACT\"]")
        List<VehicleKeyword> keywords,

        @Schema(description = "시작가(원)", example = "10000000")
        Long startPrice,

        @Schema(description = "현재가(원), 입찰이 없으면 시작가와 같다", example = "11000000")
        Long currentPrice,

        @Schema(description = "방이 열리는 시각", example = "2026-08-03T11:20:00")
        LocalDateTime openAt,

        @Schema(description = "입찰이 시작되는 시각", example = "2026-08-03T11:50:00")
        LocalDateTime startAt,

        @Schema(description = "마감 시각", example = "2026-08-03T12:10:00")
        LocalDateTime endAt,

        @Schema(description = "지금 방을 보고 있는 사람 수, 닫힌 단계는 0", example = "12")
        int viewerCount
) {
    public static AuctionCardResponse from(AuctionCardInfo info) {
        return new AuctionCardResponse(
                info.auctionId(),
                info.phase(),
                info.thumbnailUrl(),
                info.manufacturer(),
                info.model(),
                info.modelYear(),
                info.mileage(),
                info.keywords(),
                info.startPrice(),
                info.currentPrice(),
                info.openAt(),
                info.startAt(),
                info.endAt(),
                info.viewerCount());
    }
}
