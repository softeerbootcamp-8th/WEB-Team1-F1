package com.softeer.race.auctionlist.application.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AuctionListInfo(
        List<AuctionCardInfo> content,
        LocalDateTime serverTime,
        boolean hasNext,
        AuctionListCursor nextCursor
) {

}
