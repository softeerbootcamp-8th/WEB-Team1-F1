package com.softeer.race.deal.application;

import static com.softeer.race.notification.domain.NotificationType.DEAL_BUYER_SCHEDULE_REQUIRED;
import static com.softeer.race.notification.domain.NotificationType.DEAL_CANCELLED;
import static com.softeer.race.notification.domain.NotificationType.DEAL_CONFIRMED;
import static com.softeer.race.notification.domain.NotificationType.DEAL_SELLER_SUBMIT_REQUIRED;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.deal.domain.CancellationReason;
import com.softeer.race.deal.domain.Deal;
import com.softeer.race.deal.domain.DealRepository;
import com.softeer.race.deal.domain.DealSide;
import com.softeer.race.deal.exception.DealErrorCode;
import com.softeer.race.notification.application.NotificationPublisher;
import com.softeer.race.storage.domain.FileCategory;
import com.softeer.race.storage.domain.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 거래를 다음 단계로 옮긴다
 * <p>
 * 무엇을 했는지만 받고 목적지는 거래가 정한다. 목적지를 요청에서 받으면 화면이 상태 머신을
 * 알아야 하고, 화면과 서버의 단계 표가 어긋나는 순간 조용히 틀린다.
 * <p>
 * <b>비관적 락을 쓰지 않는다.</b> 단계마다 움직일 수 있는 사람이 정확히 한 명이라 두 주체가
 * 같은 거래를 동시에 옮길 경로가 없다. 남는 경합은 같은 사람의 중복 클릭이고, 순차로 들어오면
 * 전이 검사가, 진짜로 겹치면 Deal 의 @Version 이 두 번째를 커밋에서 떨어뜨린다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DealProgressService {

    private final DealRepository dealRepository;
    private final NotificationPublisher notificationPublisher;
    private final FileStorage fileStorage;
    private final Clock clock;

    /**
     * 구매자가 구매를 확정한다, 판매자에게 서류와 일정을 요청한다
     */
    public void confirmPurchase(long userId, long dealId) {
        Deal deal = myTurn(dealId, userId);

        deal.confirmPurchase(now());

        notificationPublisher.publish(deal.getSeller().getId(), DEAL_SELLER_SUBMIT_REQUIRED, dealId);
    }

    /**
     * 판매자가 서류와 탁송 일정을 낸다, 구매자에게 확인을 요청한다
     */
    public void submitTransport(long userId, long dealId, String documentUrl,
                                LocalDateTime transportAt, String transportLocation) {
        // 거래를 읽기 전에 본다, 값이 애초에 말이 안 되면 DB 를 건드릴 이유가 없다
        validateManagedDocument(documentUrl);

        Deal deal = myTurn(dealId, userId);

        deal.submitTransport(documentUrl, transportAt, transportLocation, now());

        notificationPublisher.publish(deal.getBuyer().getId(), DEAL_BUYER_SCHEDULE_REQUIRED, dealId);
    }

    /**
     * 구매자가 인도 일정을 잡아 거래가 확정된다
     * <p>
     * 확정만 양쪽에 알린다. 앞 단계들은 다음에 움직일 한 사람에게만 가면 되지만, 확정은 둘 다
     * 그 날짜에 나가야 하는 결과다
     */
    public void confirmDelivery(long userId, long dealId,
                                LocalDateTime deliveryAt, String deliveryLocation) {
        Deal deal = myTurn(dealId, userId);

        deal.confirmDelivery(deliveryAt, deliveryLocation, now());

        notificationPublisher.publish(deal.getBuyer().getId(), DEAL_CONFIRMED, dealId);
        notificationPublisher.publish(deal.getSeller().getId(), DEAL_CONFIRMED, dealId);
    }

    /**
     * 거래를 그만둔다, 그만둔 쪽이 귀책으로 남는다
     * <p>
     * 차례와 무관하게 양쪽 다 할 수 있다. 상대를 기다리는 쪽도 그만둘 수 있어야 한다
     */
    public void cancel(long userId, long dealId) {
        Deal deal = find(dealId);
        DealSide side = sideOf(deal, userId);

        deal.cancel(side == DealSide.BUYER
                ? CancellationReason.BUYER_CANCELLED
                : CancellationReason.SELLER_CANCELLED, now());

        long counterpartId = side == DealSide.BUYER
                ? deal.getSeller().getId()
                : deal.getBuyer().getId();

        notificationPublisher.publish(counterpartId, DEAL_CANCELLED, dealId);
    }

    /**
     * 지금 움직일 차례인 사람의 거래
     * <p>
     * 상태가 곧 차례라 별도 컬럼이 필요 없다. 끝난 거래는 차례가 비어 있는데, 여기서 막지 않고
     * 도메인의 전이 검사로 넘긴다 — 당사자에게 "상대 차례"(403)라고 답하면 무엇이 틀렸는지 알 수 없다
     */
    private Deal myTurn(long dealId, long userId) {
        Deal deal = find(dealId);
        DealSide turn = deal.getStatus().waitingFor();

        if (turn != null && sideOf(deal, userId) != turn) {
            throw new BusinessException(DealErrorCode.NOT_PARTICIPANT);
        }

        return deal;
    }

    /**
     * 우리가 발급한 문서 주소인지
     * <p>
     * 종류를 DOCUMENT 로 못 박는다. "우리가 발급한 주소인가"만 물으면 차량 사진도 우리가 발급한
     * 것이라 통과해 서류 자리에 사진이 박힌다. 차량 이미지·진단서가 쓰는 판정과 같은 것이다.
     */
    private void validateManagedDocument(String documentUrl) {
        if (!fileStorage.isManagedUrl(documentUrl, FileCategory.DOCUMENT)) {
            throw new BusinessException(DealErrorCode.UNMANAGED_DOCUMENT_URL);
        }
    }

    private Deal find(long dealId) {
        return dealRepository.findById(dealId)
                .orElseThrow(() -> new BusinessException(DealErrorCode.NOT_FOUND));
    }

    /**
     * 남의 거래는 조회와 같은 이유로 없는 것과 같게 답한다 — 권한 없음으로 갈리면
     * 그 번호의 거래가 존재한다는 사실이 새어 나간다
     */
    private DealSide sideOf(Deal deal, long userId) {
        if (deal.getBuyer().getId() == userId) {
            return DealSide.BUYER;
        }
        if (deal.getSeller().getId() == userId) {
            return DealSide.SELLER;
        }

        throw new BusinessException(DealErrorCode.NOT_FOUND);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
