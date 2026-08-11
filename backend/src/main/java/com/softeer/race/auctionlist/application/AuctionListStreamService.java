package com.softeer.race.auctionlist.application;

import com.softeer.race.auctionlist.application.dto.AuctionCardInfo;
import com.softeer.race.auctionlist.domain.AuctionListRepository;
import com.softeer.race.auctionlist.domain.AuctionListRow;
import com.softeer.race.vehicle.application.VehicleKeywordService;
import lombok.RequiredArgsConstructor;
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

    private final VehicleKeywordService vehicleKeywordService;
    private final AuctionListRepository auctionListRepository;
    private final AuctionCardAssembler cardAssembler;
    private final AuctionListChannel auctionListChannel;
    private final Clock clock;

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

    // 조회와 같은 모양이어야 한다, 키워드를 비워 보내면 화면이 들고 있던 카드에서 그것이 지워진다
    private AuctionCardInfo toCard(AuctionListRow row) {
        return cardAssembler.assemble(row, LocalDateTime.now(clock),
                vehicleKeywordService.findByVehicleIds(List.of(row.vehicleId())));
    }
}