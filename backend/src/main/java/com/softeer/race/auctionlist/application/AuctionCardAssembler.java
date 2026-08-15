package com.softeer.race.auctionlist.application;

import com.softeer.race.auctionlist.application.dto.AuctionCardInfo;
import com.softeer.race.auctionlist.domain.AuctionListRow;
import com.softeer.race.auctionroom.application.RoomChannel;
import com.softeer.race.auctionroom.domain.RoomPhase;
import com.softeer.race.vehicle.domain.VehicleKeyword;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// 목록 조회와 목록 방송이 같은 카드를 내야 하므로 조립하는 곳을 하나로 둔다
@Component
@RequiredArgsConstructor
class AuctionCardAssembler {

    private final RoomChannel roomChannel;

    // 키워드는 받아서 붙이기만 한다. 몇 장분을 읽을지는 목록과 방송이 서로 달라 부르는 쪽이 안다
    AuctionCardInfo assemble(AuctionListRow row, LocalDateTime now,
                             Map<Long, List<VehicleKeyword>> keywords) {
        // 단계 판정은 경매방과 한 벌을 쓴다. 복제하면 같은 경매가 두 화면에서 다른 단계로 보일 수 있다.
        RoomPhase phase = RoomPhase.at(now, row.roomOpenAt(), row.startTime(), row.currentEndTime());

        // 닫힌 단계는 경매방도 접속자를 세지 않는다. 목록만 다른 수를 보이면 안 된다.
        int connectedCount = phase.allowsConnection()
                ? roomChannel.viewerCount(row.auctionId()) : 0;

        return new AuctionCardInfo(
                row.auctionId(),
                phase,
                row.thumbnailUrl(),
                row.manufacturerType(),
                row.model(),
                row.modelYear(),
                row.mileage(),
                keywords.getOrDefault(row.vehicleId(), List.of()),
                row.startPrice(),
                row.displayPrice(),
                row.roomOpenAt(),
                row.startTime(),
                row.currentEndTime(),
                connectedCount);
    }
}
