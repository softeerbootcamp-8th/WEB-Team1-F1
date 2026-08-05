package com.softeer.race.progress.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.progress.application.dto.SellerProgressDetailInfo;
import com.softeer.race.progress.application.dto.SellerProgressInfo;
import com.softeer.race.progress.domain.SellerProgressRepository;
import com.softeer.race.progress.exception.ProgressErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 판매자가 자기 차들이 어디까지 왔는지 보는 조회
 * <p>
 * 페이지네이션을 두지 않는다. 한 사람이 파는 차는 몇 대 수준이고, 목록의 정렬 기준이 곧 신청
 * 순서라 커서를 붙일 만한 깊은 스크롤이 생기지 않는다. 필요해지면 그때 {@code AuctionListService}가
 * 쓰는 커서 방식을 그대로 가져오면 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerProgressService {

    private final SellerProgressRepository sellerProgressRepository;

    public List<SellerProgressInfo> list(long sellerId) {
        return sellerProgressRepository.findAllBySeller(sellerId).stream()
                .map(SellerProgressInfo::from)
                .toList();
    }

    public SellerProgressDetailInfo detail(long sellerId, long vehicleId) {
        return sellerProgressRepository.findOneBySeller(sellerId, vehicleId)
                .map(SellerProgressDetailInfo::from)
                .orElseThrow(() -> new BusinessException(ProgressErrorCode.PROGRESS_NOT_FOUND));
    }
}
