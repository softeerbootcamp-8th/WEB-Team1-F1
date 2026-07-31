package com.softeer.race.sell.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.sell.presentation.request.SellApplicationRequest;
import com.softeer.race.sell.presentation.response.SellApplicationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Sell", description = "판매 신청 API")
public interface SellApi {

    @Operation(summary = "판매 신청",
            description = "로그인한 회원이 번호판을 보내면 서버가 제원을 조회해 차량을 등록하고, "
                    + "발행된 경매글과 예약된 경매를 함께 만듭니다. 제원은 클라이언트가 보낸 값을 쓰지 않습니다. "
                    + "경매는 신청 시각으로부터 1시간 뒤에 시작하고 30분 전부터 입장할 수 있으며, "
                    + "생성 즉시 경매 목록에 노출됩니다. 세션 쿠키가 필요합니다.")
    ResponseEntity<SellApplicationResponse> apply(
            AuthenticatedUser authenticatedUser, SellApplicationRequest request);
}
