package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.auth.presentation.annotation.RequireRole;
import com.softeer.race.evaluation.application.VisitQuoteService;
import com.softeer.race.evaluation.application.dto.info.VisitQuoteInfo;
import com.softeer.race.evaluation.presentation.request.VisitQuoteRequest;
import com.softeer.race.evaluation.presentation.request.VisitQuotePrecheckRequest;
import com.softeer.race.evaluation.presentation.response.VisitQuotePrecheckResponse;
import com.softeer.race.evaluation.presentation.response.VisitQuoteResponse;
import com.softeer.race.user.domain.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visit-quotes")
@RequiredArgsConstructor
public class VisitQuoteController implements VisitQuoteApi {

    private final VisitQuoteService visitQuoteService;

    @Override
    @PostMapping("/precheck")
    @RequireRole({Role.GENERAL, Role.DEALER})
    public ResponseEntity<VisitQuotePrecheckResponse> precheck(
            @LoginUser AuthenticatedUser authenticatedUser,
            @Valid @RequestBody VisitQuotePrecheckRequest request) {
        return ResponseEntity.ok(
                VisitQuotePrecheckResponse.from(visitQuoteService.precheck(request.toCommand())));
    }

    @Override
    @PostMapping
    @RequireRole({Role.GENERAL, Role.DEALER})
    public ResponseEntity<VisitQuoteResponse> request(
            @LoginUser AuthenticatedUser authenticatedUser,
            @Valid @RequestBody VisitQuoteRequest request) {

        VisitQuoteInfo info = visitQuoteService.request(request.toCommand(authenticatedUser.id()));

        // SellController처럼 created(URI)를 쓰지 않는다. 신청 단건을 조회할 엔드포인트가 아직 없어
        // Location에 넣을 수 있는 주소가 전부 404를 가리킨다. 조회 API가 생기면 그때 붙인다
        return ResponseEntity.status(HttpStatus.CREATED).body(VisitQuoteResponse.from(info));
    }
}
