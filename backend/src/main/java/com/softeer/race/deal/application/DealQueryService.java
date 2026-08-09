package com.softeer.race.deal.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.deal.application.dto.DealCardInfo;
import com.softeer.race.deal.application.dto.DealDetailInfo;
import com.softeer.race.deal.application.dto.DealSliceInfo;
import com.softeer.race.deal.domain.DealListRow;
import com.softeer.race.deal.domain.DealQueryRepository;
import com.softeer.race.deal.exception.DealErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealQueryService {

    // 거래 카드는 차량 사진과 진행 단계까지 그려서 알림 한 줄보다 크다, 한 화면에 들어가는 수가 적다
    private static final int PAGE_SIZE = 10;

    // 첫 페이지는 커서가 없다, id 로 끊어 읽으므로 "아직 아무것도 안 봤다"가 가장 큰 id 로 표현된다
    private static final long FIRST_CURSOR = Long.MAX_VALUE;

    private final DealQueryRepository dealQueryRepository;
    private final Clock clock;

    /**
     * 내가 당사자인 거래 한 페이지, 커서가 없으면 첫 페이지
     */
    public DealSliceInfo list(long userId, Long cursor) {
        long from = (cursor != null) ? cursor : FIRST_CURSOR;

        // 한 건 더 읽어 다음 페이지 유무를 판단한다, 전체를 세는 쿼리를 피하려는 것이다
        List<DealListRow> found = dealQueryRepository.findPage(userId, from, Limit.of(PAGE_SIZE + 1));

        boolean hasNext = found.size() > PAGE_SIZE;
        List<DealListRow> page = hasNext ? found.subList(0, PAGE_SIZE) : found;

        // 내림차순이라 마지막 행의 id 가 다음 페이지의 시작점이다
        Long nextCursor = hasNext ? page.getLast().dealId() : null;

        return new DealSliceInfo(
                page.stream().map(row -> DealCardInfo.of(row, userId)).toList(),
                LocalDateTime.now(clock),
                hasNext,
                nextCursor);
    }

    /**
     * 내 거래 하나의 상세
     *
     * @throws BusinessException 없는 거래이거나 내가 당사자가 아닐 때, 둘을 구분하지 않는다
     */
    public DealDetailInfo detail(long userId, long dealId) {
        return dealQueryRepository.findDetail(dealId, userId)
                .map(row -> DealDetailInfo.of(row, userId, LocalDateTime.now(clock)))
                .orElseThrow(() -> new BusinessException(DealErrorCode.NOT_FOUND));
    }
}
