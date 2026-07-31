package com.softeer.race.quote.presentation;

import com.softeer.race.quote.application.QuoteService;
import com.softeer.race.quote.presentation.request.QuoteRequest;
import com.softeer.race.quote.presentation.response.QuoteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
public class QuoteController implements QuoteApi {

    private final QuoteService quoteService;

    // 아무것도 만들지 않으므로 201 이 아니라 200 이다
    @Override
    @PostMapping
    public ResponseEntity<QuoteResponse> estimate(@Valid @RequestBody QuoteRequest request) {
        QuoteResponse response = QuoteResponse.from(quoteService.estimate(request.toCommand()));

        return ResponseEntity.ok(response);
    }
}
