package com.softeer.race.notification.domain;

import com.softeer.race.common.domain.MaskedName;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

import static com.softeer.race.notification.domain.NotificationType.AUCTION_ENDED;
import static com.softeer.race.notification.domain.NotificationType.AUCTION_FAILED;
import static com.softeer.race.notification.domain.NotificationType.AUCTION_SOLD;
import static com.softeer.race.notification.domain.NotificationType.AUCTION_WON;
import static com.softeer.race.notification.domain.NotificationType.DEAL_CONFIRMED;
import static com.softeer.race.notification.domain.NotificationType.OUTBID;
import static com.softeer.race.notification.domain.NotificationType.AUCTION_STARTED;

/**
 * 알림 종류와 발행 시점에 완성한 문구
 * <p>
 * 문구를 조회할 때 조립하면 차량명이나 가격이 바뀔 때 과거 알림까지 현재 값으로 바뀐다.
 * 사건이 일어난 시점의 값으로 여기서 완성해 Notification 에 그대로 저장한다.
 */
public record NotificationContent(NotificationType type, String message) {

    public static final int MAX_MESSAGE_LENGTH = 1_000;

    private static final DateTimeFormatter SCHEDULE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm", Locale.KOREA);

    public NotificationContent {
        Objects.requireNonNull(type, "알림 종류는 필수입니다.");

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("알림 문구는 비어 있을 수 없습니다.");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException(
                    "알림 문구가 너무 깁니다, 길이 " + message.length());
        }
    }

    public static NotificationContent defaultOf(NotificationType type) {
        return new NotificationContent(type, type.defaultMessage());
    }

    public static NotificationContent auctionStarted(String vehicleModel) {
        return new NotificationContent(AUCTION_STARTED,
                "%s 경매가 시작되었습니다. 지금 입찰할 수 있습니다."
                        .formatted(text(vehicleModel)));
    }

    public static NotificationContent outbid(
            String vehicleModel, MaskedName bidderName, long amount) {
        Objects.requireNonNull(bidderName, "마스킹된 입찰자 이름은 필수입니다.");

        return new NotificationContent(OUTBID,
                "%s 경매에서 %s님이 %s원에 입찰했습니다."
                        .formatted(text(vehicleModel), bidderName.value(), money(amount)));
    }

    public static NotificationContent auctionWon(String vehicleModel, long finalPrice) {
        return new NotificationContent(AUCTION_WON,
                "%s 차량을 %s원에 낙찰받았습니다."
                        .formatted(text(vehicleModel), money(finalPrice)));
    }

    public static NotificationContent auctionSold(String vehicleModel, long finalPrice) {
        return new NotificationContent(AUCTION_SOLD,
                "%s 차량이 %s원에 낙찰되었습니다."
                        .formatted(text(vehicleModel), money(finalPrice)));
    }

    public static NotificationContent auctionEnded(String vehicleModel, long finalPrice) {
        return new NotificationContent(AUCTION_ENDED,
                "%s 경매가 %s원에 종료되었습니다."
                        .formatted(text(vehicleModel), money(finalPrice)));
    }

    public static NotificationContent auctionFailed(String vehicleModel) {
        return new NotificationContent(AUCTION_FAILED,
                "%s 경매가 입찰 없이 종료되었습니다.".formatted(text(vehicleModel)));
    }

    public static NotificationContent dealConfirmedForBuyer(
            String vehicleModel, LocalDateTime deliveryAt, String deliveryLocation) {
        return dealConfirmed(vehicleModel, deliveryAt, deliveryLocation, "인수");
    }

    public static NotificationContent dealConfirmedForSeller(
            String vehicleModel, LocalDateTime deliveryAt, String deliveryLocation) {
        return dealConfirmed(vehicleModel, deliveryAt, deliveryLocation, "인도");
    }

    private static NotificationContent dealConfirmed(
            String vehicleModel, LocalDateTime deliveryAt,
            String deliveryLocation, String action) {
        Objects.requireNonNull(deliveryAt, "인도 일시는 필수입니다.");

        return new NotificationContent(DEAL_CONFIRMED,
                "%s 차량을 %s에 %s에서 %s합니다."
                        .formatted(text(vehicleModel), deliveryAt.format(SCHEDULE_FORMAT),
                                text(deliveryLocation), action));
    }

    private static String money(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("알림 금액은 0보다 커야 합니다, 값 " + amount);
        }

        // 서버 로케일이 바뀌어도 저장 문구의 천 단위 구분자가 달라지지 않게 고정한다
        return String.format(Locale.KOREA, "%,d", amount);
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("알림에 넣을 값은 비어 있을 수 없습니다.");
        }

        // 개행·탭·연속 공백이 알림 한 건의 모양을 깨뜨리지 않도록 한 칸으로 접는다
        return value.strip().replaceAll("\\s+", " ");
    }
}
