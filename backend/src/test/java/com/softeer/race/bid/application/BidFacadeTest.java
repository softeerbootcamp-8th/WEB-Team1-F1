package com.softeer.race.bid.application;

import com.softeer.race.bid.application.dto.BidPlaceInfo;
import com.softeer.race.bid.application.dto.BidPlaced;
import com.softeer.race.bid.domain.AuctionBidSnapshot;
import com.softeer.race.bid.exception.BidErrorCode;
import com.softeer.race.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 잠금 사용의 짝맞춤을 검증한다.
 * <p>
 * 잠금 항목은 마지막 반납이 지우므로, 어떤 경로로 끝나든 release 가 정확히 한 번 불리는 것이
 * 항목이 새지 않는다는 보장의 전부다. 실패 사유를 가려서 지우지 않는 이유다 (#439).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("입찰 앞단의 잠금 짝맞춤")
class BidFacadeTest {

    private static final long AUCTION_ID = 1000L;
    private static final long BIDDER_ID = 11L;
    private static final long AMOUNT = 10_000_000L;

    @Mock
    private AuctionBidGate auctionBidGate;

    @Mock
    private AuctionLockRegistry auctionLockRegistry;

    @Mock
    private BidService bidService;

    @InjectMocks
    private BidFacade bidFacade;

    @BeforeEach
    void setUp() {
        when(auctionLockRegistry.acquire(AUCTION_ID)).thenReturn(new ReentrantLock());
    }

    @Test
    @DisplayName("성립해도 잠금 사용을 반납한다")
    void releasesOnSuccess() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 18, 20, 40);
        when(bidService.place(anyLong(), anyLong(), anyLong())).thenReturn(new BidPlaced(
                new BidPlaceInfo(1L, AMOUNT, now.plusMinutes(1), now),
                new AuctionBidSnapshot(2L, AMOUNT, AMOUNT, now.minusMinutes(1), now.plusMinutes(1))));

        assertThatCode(() -> bidFacade.place(AUCTION_ID, BIDDER_ID, AMOUNT))
                .doesNotThrowAnyException();

        verify(auctionLockRegistry).release(AUCTION_ID);
    }

    @Test
    @DisplayName("없는 경매 판정으로 끝나도 잠금 사용을 반납한다")
    void releasesOnMissingAuction() {
        when(bidService.place(anyLong(), anyLong(), anyLong()))
                .thenThrow(new BusinessException(BidErrorCode.AUCTION_NOT_FOUND));

        assertThatThrownBy(() -> bidFacade.place(AUCTION_ID, BIDDER_ID, AMOUNT))
                .isInstanceOf(BusinessException.class);

        verify(auctionLockRegistry).release(AUCTION_ID);
    }

    @Test
    @DisplayName("거절로 끝나도 잠금 사용을 반납한다")
    void releasesOnRejection() {
        when(bidService.place(anyLong(), anyLong(), anyLong()))
                .thenThrow(new BusinessException(BidErrorCode.BID_AMOUNT_TOO_LOW));

        assertThatThrownBy(() -> bidFacade.place(AUCTION_ID, BIDDER_ID, AMOUNT))
                .isInstanceOf(BusinessException.class);

        verify(auctionLockRegistry).release(AUCTION_ID);
    }

    @Test
    @DisplayName("업무 규칙 밖의 예외로 끝나도 잠금 사용을 반납한다")
    void releasesOnUnexpectedFailure() {
        when(bidService.place(anyLong(), anyLong(), anyLong()))
                .thenThrow(new IllegalStateException("인프라 장애"));

        assertThatThrownBy(() -> bidFacade.place(AUCTION_ID, BIDDER_ID, AMOUNT))
                .isInstanceOf(IllegalStateException.class);

        verify(auctionLockRegistry).release(AUCTION_ID);
    }
}
