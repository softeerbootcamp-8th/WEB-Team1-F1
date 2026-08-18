package com.softeer.race.auctionroom.infrastructure;

import com.softeer.race.auctionroom.application.RoomStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

// 주기 작업을 언제 부를지만 정한다. 무엇을 하는지는 서비스가 알고, 서비스는 자기가 언제 불리는지 모른다
// 주기 값이 설정에 있는 것도 여기서만 보이면 된다
@Component
@RequiredArgsConstructor
public class RoomStreamScheduler {

    private final RoomStreamService roomStreamService;

    @Scheduled(fixedDelayString = "${race.room.heartbeat-interval-seconds}", timeUnit = TimeUnit.SECONDS,
            scheduler = RoomSchedulingConfig.ROOM_STREAM)
    public void sweepClosedSubscriptions() {
        roomStreamService.sweepClosedSubscriptions();
    }

    @Scheduled(fixedDelayString = "${race.room.ended-room-close-interval-seconds}", timeUnit = TimeUnit.SECONDS,
            scheduler = RoomSchedulingConfig.ROOM_CLOSE)
    public void closeStreamEndedRooms() {
        roomStreamService.closeStreamEndedRooms();
    }
}
