package com.softeer.race.deal.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.deal.application.dto.DealDocumentUploadInfo;
import com.softeer.race.deal.domain.Deal;
import com.softeer.race.deal.domain.DealRepository;
import com.softeer.race.deal.domain.DealSide;
import com.softeer.race.deal.exception.DealErrorCode;
import com.softeer.race.storage.domain.FileCategory;
import com.softeer.race.storage.domain.FileStorage;
import com.softeer.race.storage.domain.UploadContentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자가 낼 서류를 올릴 주소를 발급한다
 * <p>
 * <b>발급 경로를 거래 아래에 따로 둔 이유.</b> 공용 발급({@code /api/uploads/presigned})은 차량
 * 평가 경로라 평가사만 부를 수 있다. 거기에 판매자를 끼워 넣으려면 역할 목록을 넓혀야 하는데,
 * 그러면 로그인한 사람 누구나 서류 주소를 받아 갈 수 있게 된다. 서류를 낼 자격은 역할이 아니라
 * "이 거래에서 지금 움직일 판매자인가"라 거래가 판정해야 하고, 그 판정을 하려면 거래 번호가
 * 경로에 있어야 한다.
 * <p>
 * <b>발급 단계에서 형식을 못 박는다.</b> 등록({@code submitTransport})도 문서 주소인지 다시 보지만,
 * 거기서만 보면 사용자는 사진을 다 올린 뒤에야 거부당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealDocumentUploadService {

    private final DealRepository dealRepository;
    private final FileStorage fileStorage;

    /**
     * {@code contentType} 이 문자열인 것은 형식 판정을 서비스가 하기 위해서다. enum 으로 받으면
     * 변환이 컨트롤러에서 끝나 "무엇을 서류로 받기로 했는가"를 서비스가 건너뛴다.
     */
    public DealDocumentUploadInfo issue(long userId, long dealId, String contentType,
                                        long contentLength) {
        requireSellerTurn(dealId, userId);

        UploadContentType documentType = documentTypeOf(contentType);
        documentType.validateSize(contentLength);

        return DealDocumentUploadInfo.from(fileStorage.presign(documentType, contentLength));
    }

    /**
     * 지금 서류를 낼 수 있는 사람인지
     * <p>
     * 세 갈래로 갈린다. 당사자가 아니면 거래 조회와 같게 없는 것으로 답하고(404), 끝난 거래는
     * 낼 서류가 없으니 단계 문제로(409), 아직 구매자를 기다리는 중이면 차례 문제로(403) 답한다.
     * 진행 API 와 같은 코드를 쓰는 것이 중요하다 — 화면이 발급 실패와 등록 실패를 같은 분기로
     * 처리한다.
     */
    private void requireSellerTurn(long dealId, long userId) {
        Deal deal = dealRepository.findById(dealId)
                .orElseThrow(() -> new BusinessException(DealErrorCode.NOT_FOUND));

        if (deal.sideOf(userId) != DealSide.SELLER) {
            throw new BusinessException(DealErrorCode.NOT_PARTICIPANT);
        }

        DealSide turn = deal.getStatus().waitingFor();
        if (turn == null) {
            throw new BusinessException(DealErrorCode.INVALID_TRANSITION);
        }
        if (turn != DealSide.SELLER) {
            throw new BusinessException(DealErrorCode.NOT_PARTICIPANT);
        }
    }

    /**
     * 모르는 형식과 서류로 받지 않는 형식을 한 코드로 묶는다. 발급을 요청한 쪽에는 둘 다
     * "이 파일로는 서류를 낼 수 없다"는 한 가지 사실이고, 여기서 저장소 코드가 새어 나가면
     * 화면이 안내를 두 갈래로 들고 있어야 한다.
     */
    private UploadContentType documentTypeOf(String contentType) {
        UploadContentType uploadContentType;
        try {
            uploadContentType = UploadContentType.from(contentType);
        } catch (BusinessException exception) {
            throw new BusinessException(DealErrorCode.UNSUPPORTED_DOCUMENT_TYPE);
        }

        if (uploadContentType.category() != FileCategory.DOCUMENT) {
            throw new BusinessException(DealErrorCode.UNSUPPORTED_DOCUMENT_TYPE);
        }

        return uploadContentType;
    }
}
