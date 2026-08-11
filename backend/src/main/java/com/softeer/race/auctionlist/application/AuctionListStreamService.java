package com.softeer.race.auctionlist.application;

import com.softeer.race.auctionlist.application.dto.AuctionCardInfo;
import com.softeer.race.auctionlist.domain.AuctionListRepository;
import com.softeer.race.auctionlist.domain.AuctionListRow;
import com.softeer.race.auctionroom.application.RoomChannel;
import com.softeer.race.common.config.SchedulingConfig;
import com.softeer.race.vehicle.application.VehicleKeywordService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 목록 변화를 열려 있는 구독으로 흘려보내는 서비스
 */
// @Transactional 을 붙이지 않는다, 방송이 소켓 쓰기라 안 받아 가는 구독자 하나에 커넥션이 묶인다
@Service
@RequiredArgsConstructor
public class AuctionListStreamService {

    // 이슈가 요구하는 상한이 1초다, 사람이 드나든 것을 그 안에 목록이 알아야 한다
    private static final long AUDIENCE_INTERVAL_MILLIS = 1_000L;

    private final VehicleKeywordService vehicleKeywordService;
    private final AuctionListRepository auctionListRepository;
    private final AuctionCardAssembler cardAssembler;
    private final AuctionListChannel auctionListChannel;
    private final RoomChannel roomChannel;
    private final Clock clock;

    // 주기 하나에서만 불려 동시성 장치를 두지 않는다, 맵 전체에 걸친 갱신은 어차피 원자적이지 않다
    private final AudienceSnapshot audienceSnapshot = new AudienceSnapshot();

    /**
     * 구독을 명부에 올린다, 경매방과 달리 첫 현황을 보내지 않는다
     */
    // 서버가 보고 있는 페이지를 몰라 "첫 현황"이 정의되지 않는다, 복구는 화면의 재조회 몫이다
    public void subscribe(AuctionListSubscriber subscriber) {
        auctionListChannel.subscribe(subscriber);
    }

    public void unsubscribe(AuctionListSubscriber subscriber) {
        auctionListChannel.unsubscribe(subscriber);
    }

    /**
     * 경매 하나의 카드를 목록 구독 전체에 보낸다, 보는 사람이 없으면 조회도 하지 않는다
     */
    // 커밋 이후에 불리므로 여기서 쓰기를 하면 안 된다, 그 쓰기는 뒤따르는 커밋이 없어 사라진다
    public void broadcastCard(long auctionId) {
        if (!auctionListChannel.hasSubscribers()) {
            return;
        }

        // 구독이 열려 있는 사이에 경매글이 내려갔다면 목록에 없는 카드라 보낼 것이 없다
        auctionListRepository.findRow(auctionId)
                .map(this::toCard)
                .ifPresent(auctionListChannel::broadcastCard);
    }

    /**
     * 지난번과 달라진 시청자 수만 목록 구독 전체에 보낸다
     */
    // 카드와 달리 DB 를 읽지 않는다, 이 수는 경매방 채널 메모리에만 있다
    // 방 입퇴장마다 던지지 않고 주기로 모은다, 팬아웃 상한이 눌리고 디바운스가 따라온다
    @Scheduled(fixedDelay = AUDIENCE_INTERVAL_MILLIS, scheduler = SchedulingConfig.LIST_STREAM)
    public void broadcastAudienceChanges() {
        // 보는 사람이 없으면 방 전체를 훑을 이유가 없다, 이 순회는 주기마다 돈다
        if (!auctionListChannel.hasSubscribers()) {
            return;
        }

        audienceSnapshot.advanceTo(roomChannel.viewerCounts())
                .forEach(auctionListChannel::broadcastAudience);
    }

    // 조회와 같은 모양이어야 한다, 키워드를 비워 보내면 화면이 들고 있던 카드에서 그것이 지워진다
    private AuctionCardInfo toCard(AuctionListRow row) {
        return cardAssembler.assemble(row, LocalDateTime.now(clock),
                vehicleKeywordService.findByVehicleIds(List.of(row.vehicleId())));
    }
}