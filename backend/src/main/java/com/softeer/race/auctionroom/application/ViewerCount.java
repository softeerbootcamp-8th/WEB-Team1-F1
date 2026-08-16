package com.softeer.race.auctionroom.application;

/**
 * 방을 보고 있는 사람 수, 들고 나는 것만으로 바뀌므로 현황과 따로 나간다
 */
public record ViewerCount(
        long auctionId,
        int viewerCount,
        long sequence
) implements RoomMessage {

    // 사람 수는 오르내려서 값으로는 순서를 가릴 수 없다, 세는 것과 번호를 매기는 것을 채널이 함께 해 준다
    @Override
    public boolean isStalerThan(RoomMessage sent) {
        return sent instanceof ViewerCount broadcasted && sequence < broadcasted.sequence();
    }
}
