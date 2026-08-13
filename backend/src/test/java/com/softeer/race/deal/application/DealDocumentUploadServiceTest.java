package com.softeer.race.deal.application;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auctionpost.domain.AuctionPost;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.deal.application.dto.DealDocumentUploadInfo;
import com.softeer.race.deal.domain.CancellationReason;
import com.softeer.race.deal.domain.Deal;
import com.softeer.race.deal.domain.DealRepository;
import com.softeer.race.deal.exception.DealErrorCode;
import com.softeer.race.storage.domain.FileStorage;
import com.softeer.race.storage.domain.PresignedUpload;
import com.softeer.race.storage.domain.UploadContentType;
import com.softeer.race.storage.exception.StorageErrorCode;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * 판매 서류 발급의 자격 판정
 * <p>
 * 여기서 볼 것은 "누가 언제 서류 주소를 받을 수 있는가"다. 발급 자체는 저장소가 하므로 목으로
 * 두고, 자격이 없으면 저장소까지 가지 않는지를 함께 확인한다 — 발급이 먼저 일어나면 쓰이지 않을
 * 객체 자리가 남는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("판매 서류 업로드 주소 발급")
class DealDocumentUploadServiceTest {

    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 8, 8, 10, 0);
    private static final LocalDateTime START_TIME = LocalDateTime.of(2026, 8, 8, 11, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 9, 12, 0);

    private static final long DEAL_ID = 12L;
    private static final long SELLER_ID = 1L;
    private static final long BUYER_ID = 2L;
    private static final long STRANGER_ID = 3L;

    private static final long START_PRICE = 20_000_000L;
    private static final long FINAL_PRICE = 30_000_000L;

    private static final long PDF_SIZE = 2_481_920L;

    private static final String KEY =
            "documents/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";

    @Mock
    private DealRepository dealRepository;

    @Mock
    private FileStorage fileStorage;

    @InjectMocks
    private DealDocumentUploadService dealDocumentUploadService;

    @Test
    @DisplayName("판매자 차례이면 문서 주소를 발급한다")
    void 판매자_차례() {
        Deal deal = sellerTurnDeal();
        given(dealRepository.findById(DEAL_ID)).willReturn(Optional.of(deal));
        given(fileStorage.presign(UploadContentType.PDF, PDF_SIZE)).willReturn(
                new PresignedUpload(KEY, "https://bucket.s3.ap-northeast-2.amazonaws.com/" + KEY,
                        "https://cdn.race.dev/" + KEY, NOW.plusMinutes(10)));

        DealDocumentUploadInfo info = issue(SELLER_ID);

        // 저장해야 하는 값은 업로드 주소가 아니라 조회 주소 쪽이다
        assertThat(info.key()).isEqualTo(KEY);
        assertThat(info.fileUrl()).isEqualTo("https://cdn.race.dev/" + KEY);
        assertThat(info.expiresAt()).isEqualTo(NOW.plusMinutes(10));
    }

    @Test
    @DisplayName("구매자는 이 자리에서 낼 서류가 없어 403 이다")
    void 구매자() {
        given(dealRepository.findById(DEAL_ID)).willReturn(Optional.of(sellerTurnDeal()));

        assertThatThrownBy(() -> issue(BUYER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DealErrorCode.NOT_PARTICIPANT);

        then(fileStorage).should(never()).presign(any(), anyLong());
    }

    @Test
    @DisplayName("당사자가 아니면 없는 거래로 답한다, 403 과 갈리면 거래의 존재가 새어 나간다")
    void 무관한_사용자() {
        given(dealRepository.findById(DEAL_ID)).willReturn(Optional.of(sellerTurnDeal()));

        assertThatThrownBy(() -> issue(STRANGER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DealErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("아직 구매자 차례이면 403 이다")
    void 구매자_차례() {
        // 구매 확정 전이다. 판매자가 미리 서류를 올려 두면 확정하지 않은 거래에 서류가 생긴다
        given(dealRepository.findById(DEAL_ID)).willReturn(Optional.of(deal()));

        assertThatThrownBy(() -> issue(SELLER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DealErrorCode.NOT_PARTICIPANT);
    }

    @Test
    @DisplayName("끝난 거래는 낼 서류가 없어 409 다, 차례 문제로 답하면 무엇이 틀렸는지 알 수 없다")
    void 끝난_거래() {
        Deal cancelled = deal();
        cancelled.cancel(CancellationReason.BUYER_CANCELLED, NOW);
        given(dealRepository.findById(DEAL_ID)).willReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> issue(SELLER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DealErrorCode.INVALID_TRANSITION);
    }

    @Test
    @DisplayName("없는 거래는 404 다")
    void 없는_거래() {
        given(dealRepository.findById(DEAL_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> issue(SELLER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DealErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("이미지는 우리가 아는 형식이어도 서류로 받지 않는다")
    void 이미지_거부() {
        given(dealRepository.findById(DEAL_ID)).willReturn(Optional.of(sellerTurnDeal()));

        // 발급 단계에서 막지 않으면 사용자는 파일을 다 올린 뒤 등록에서야 거부당한다
        assertThatThrownBy(() -> dealDocumentUploadService.issue(
                SELLER_ID, DEAL_ID, "image/jpeg", PDF_SIZE))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DealErrorCode.UNSUPPORTED_DOCUMENT_TYPE);

        then(fileStorage).should(never()).presign(any(), anyLong());
    }

    @Test
    @DisplayName("모르는 형식도 같은 코드로 답한다")
    void 모르는_형식() {
        given(dealRepository.findById(DEAL_ID)).willReturn(Optional.of(sellerTurnDeal()));

        assertThatThrownBy(() -> dealDocumentUploadService.issue(
                SELLER_ID, DEAL_ID, "application/zip", PDF_SIZE))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DealErrorCode.UNSUPPORTED_DOCUMENT_TYPE);
    }

    @Test
    @DisplayName("문서 상한을 넘으면 발급하지 않는다")
    void 너무_큰_파일() {
        given(dealRepository.findById(DEAL_ID)).willReturn(Optional.of(sellerTurnDeal()));

        assertThatThrownBy(() -> dealDocumentUploadService.issue(
                SELLER_ID, DEAL_ID, "application/pdf", UploadContentType.MAX_DOCUMENT_SIZE + 1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", StorageErrorCode.FILE_TOO_LARGE);
    }

    private DealDocumentUploadInfo issue(long userId) {
        return dealDocumentUploadService.issue(userId, DEAL_ID, "application/pdf", PDF_SIZE);
    }

    /** 구매 확정까지 끝나 판매자가 서류를 낼 차례인 거래 */
    private Deal sellerTurnDeal() {
        Deal deal = deal();
        deal.confirmPurchase(NOW);

        return deal;
    }

    private Deal deal() {
        return Deal.start(auction(), user(SELLER_ID, "박판매", Role.GENERAL),
                user(BUYER_ID, "김구매", Role.DEALER), FINAL_PRICE, NOW);
    }

    // 식별자로 당사자를 가리는 판정이라 id 가 있어야 한다, 저장을 거치지 않으므로 직접 심는다
    private User user(long id, String realName, Role role) {
        User user = User.create("user" + id, "user" + id + "@race.dev", "encoded",
                realName, "0101111%04d".formatted(id), role);
        ReflectionTestUtils.setField(user, "id", id);

        return user;
    }

    private Auction auction() {
        return Auction.schedule(AuctionPost.create(null, PUBLISHED_AT), START_PRICE, START_TIME);
    }
}
