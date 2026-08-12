package com.softeer.race.auction.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 발송 후보 한 건, 알림을 만들고 신청을 지우는 데 필요한 값만 읽은 스냅샷
 * <p>
 * 신청에서 경매 · 경매글 · 차량을 차례로 열면 후보 한 건마다 조회가 세 번 더 나간다.
 * 문구에 차량명이 들어가는 이상 반드시 밟는 경로라, 후보를 뽑을 때 한 번에 읽는다.
 */
public record AuctionStartAlertTarget(
        long subscriptionId,
        long auctionId,
        long userId,
        AuctionStatus auctionStatus,
        String vehicleModel
) {

    /**
     * 시작 알림을 보낼 대상인지, 아니면 알림 없이 정리할 대상인지
     * <p>
     * 예약 상태는 후보 조회에서 이미 걸러진다. 여기 오는 것은 시작했거나 이미 끝난 경매뿐이고,
     * 끝난 경매는 서버가 오래 멈춰 시작과 종료가 한 주기에 몰린 경우다.
     */
    public boolean startedAndUnnotified() {
        return auctionStatus == AuctionStatus.IN_PROGRESS;
    }

    /**
     * 후보를 경매별로 묶는다
     * <p>
     * 한 경매의 시작을 신청자 전원에게 알리는 것이 사건 하나이고, 그것이 곧 트랜잭션 하나다.
     * 신청 한 건마다 커밋하면 한 주기에 커밋이 처리 상한만큼 난다.
     * <p>
     * 조회 순서를 유지한다. 상한에 걸렸을 때 어디까지 처리했는지가 정해져야 재현과 검증이 된다.
     */
    public static Map<Long, List<AuctionStartAlertTarget>> groupByAuction(
            List<AuctionStartAlertTarget> targets) {
        return targets.stream()
                .collect(Collectors.groupingBy(AuctionStartAlertTarget::auctionId,
                        LinkedHashMap::new, Collectors.toList()));
    }
}