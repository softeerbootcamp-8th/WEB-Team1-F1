package com.softeer.race.deal.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.deal.application.DealQueryService;
import com.softeer.race.deal.presentation.response.DealSliceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deals")
@RequiredArgsConstructor
public class DealController implements DealApi {

    private final DealQueryService dealQueryService;

    @Override
    @GetMapping
    public ResponseEntity<DealSliceResponse> list(
            @LoginUser AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) Long cursor) {

        DealSliceResponse response = DealSliceResponse.from(
                dealQueryService.list(authenticatedUser.id(), cursor));

        return ResponseEntity.ok(response);
    }
}