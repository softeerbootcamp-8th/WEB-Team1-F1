package com.softeer.race.sample.presentation;

import com.softeer.race.sample.application.SampleService;
import com.softeer.race.sample.presentation.dto.request.SampleRequest;
import com.softeer.race.sample.presentation.dto.response.SampleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sample", description = "환경 검증용 샘플 API")
@RestController
@RequestMapping("/api/samples")
public class SampleController {

    private final SampleService sampleService;

    public SampleController(SampleService sampleService) {
        this.sampleService = sampleService;
    }

    @Operation(summary = "인사 메시지 생성", description = "name을 받아 인사 메시지를 반환합니다.")
    @PostMapping("/greet")
    public SampleResponse greet(@RequestBody SampleRequest request) {
        String message = sampleService.greet(request.name());
        return new SampleResponse(message);
    }
}
