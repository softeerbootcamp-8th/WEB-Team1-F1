package com.softeer.race.progress.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.progress.application.EvaluatorProgressService;
import com.softeer.race.progress.application.SellerProgressService;
import com.softeer.race.progress.presentation.response.EvaluatorTaskListResponse;
import com.softeer.race.progress.presentation.response.SellerProgressDetailResponse;
import com.softeer.race.progress.presentation.response.SellerProgressListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class ProgressController implements ProgressApi {

    private final SellerProgressService sellerProgressService;
    private final EvaluatorProgressService evaluatorProgressService;

    @Override
    @GetMapping("/me")
    public ResponseEntity<SellerProgressListResponse> listMine(@LoginUser AuthenticatedUser user) {
        return ResponseEntity.ok(SellerProgressListResponse.from(sellerProgressService.list(user.id())));
    }

    @Override
    @GetMapping("/me/{vehicleId}")
    public ResponseEntity<SellerProgressDetailResponse> detail(@LoginUser AuthenticatedUser user,
                                                               @PathVariable long vehicleId) {

        return ResponseEntity.ok(
                SellerProgressDetailResponse.from(sellerProgressService.detail(user.id(), vehicleId)));
    }

    @Override
    @GetMapping("/evaluations")
    public ResponseEntity<EvaluatorTaskListResponse> listEvaluatorTasks(@LoginUser AuthenticatedUser user) {
        return ResponseEntity.ok(
                EvaluatorTaskListResponse.from(evaluatorProgressService.listTasks(user.id())));
    }
}
