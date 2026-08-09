package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 평가 결과 제출을 컨트롤러에서 DB까지
 * <p>
 * 1. 한 번에 반영
 * 차량 · 사진 · 진단서 · 상태 <b>네 곳이 함께</b> 바뀌는지. 이 유스케이스를 조각내지 않은 이유가
 * 여기 있다 — 하나라도 빠지면 주행거리가 빈 차가 경매로 넘어간다
 * <p>
 * 2. 재제출
 * 다시 보내면 사진과 진단서가 늘지 않고 갈아 끼워지는지
 * <p>
 * 3. 종류 구분과 부분 반영 방지
 * 진단서 자리에 이미지 주소를, 사진 자리에 외부 주소를 보내면 거부되고 <b>그때 기존 사진이
 * 살아남는지</b>. 검증을 삭제보다 먼저 두는 결정을 고정한다
 * <p>
 * 3-1. 카탈로그 이미지 교체
 * 판매 신청이 넣어 둔 홍보 이미지가 실물로 갈리는지. 사진 등록 API를 지우면서 그 API의
 * 통합 테스트가 보증하던 것을 여기로 옮겼다
 * <p>
 * 4. 제출 자격
 * 담당이 아닌 평가사와 무관한 회원이 막히는지, 아직 아무도 수락하지 않은 신청은 누구에게도
 * 막히는지
 * <p>
 * 5. 종료된 평가
 * 반려된 신청에는 못 붙이는지. 배정은 되어 있는 건이라 담당자가 아니라 상태에서 걸려야 한다
 * <p>
 * <b>Clock을 고정하지 않는다.</b> 픽스처의 세션 만료가 DB의 실제 시각(NOW(6))으로 심기므로
 * 앱 Clock만 옮기면 전 시나리오가 401이 된다.
 */
@DisplayName("평가 결과 제출 통합 테스트")
@Transactional
@Sql("/sql/diagnostic-report-fixture.sql")
class EvaluationResultIntegrationTest extends IntegrationTestSupport {

    /** 601(박평가)에게 배정된 진행 중 신청 */
    private static final long EVALUATION_ID = 600L;
    private static final long VEHICLE_ID = 600L;
    private static final long EVALUATOR_ID = 601L;
    private static final long REJECTED_EVALUATION_ID = 601L;
    private static final long UNASSIGNED_EVALUATION_ID = 602L;

    private static final String EVALUATOR_TOKEN = "report-eval-token";
    private static final String OTHER_EVALUATOR_TOKEN = "report-eval2-token";
    private static final String STRANGER_TOKEN = "report-other-token";

    // 테스트 설정의 aws.s3.cdn-base-url과 같아야 한다. 다르면 전부 UNMANAGED_DOCUMENT_URL로 떨어진다
    private static final String CDN_BASE_URL = "https://cdn.test.local";
    private static final String IMAGE_1 =
            CDN_BASE_URL + "/images/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg";
    private static final String IMAGE_2 =
            CDN_BASE_URL + "/images/2026/08/aaaaaaaa-0d47-4a19-9b2f-6c1d5e7a8b90.jpg";
    private static final String DOCUMENT_URL =
            CDN_BASE_URL + "/documents/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";
    private static final String NEW_DOCUMENT_URL =
            CDN_BASE_URL + "/documents/2026/08/aaaaaaaa-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";

    /** 픽스처가 심어 둔 카탈로그 홍보 이미지. 실물이 올라오면 사라져야 한다 */
    private static final String CATALOG_IMAGE = "https://cdn.race.dev/vehicles/catalog.jpg";

    private static final int MILEAGE = 45_000;
    private static final long ESTIMATED_PRICE = 21_500_000L;

    @Test
    @DisplayName("한 번의 제출로 차량·사진·진단서·상태가 모두 바뀐다")
    void submit() throws Exception {
        // when
        submit(EVALUATION_ID, EVALUATOR_TOKEN, DOCUMENT_URL, IMAGE_1, IMAGE_2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.mileage").value(MILEAGE))
                .andExpect(jsonPath("$.imageUrls.length()").value(2))
                .andExpect(jsonPath("$.submittedAt").exists());

        // then : 네 곳이 함께 바뀌어야 한다. 차량 갱신이 빠지면 주행거리가 빈 차가 경매로 넘어가고,
        //        그 불변식에 경매 목록과 경매방이 기대고 있다
        Map<String, Object> vehicle = vehicleRow();
        assertThat(vehicle.get("mileage")).isEqualTo(MILEAGE);
        assertThat(vehicle.get("estimated_price")).isEqualTo(ESTIMATED_PRICE);
        assertThat(vehicle.get("main_photo_url")).isEqualTo(IMAGE_1);
        assertThat(vehicle.get("diagnostic_report_url")).isEqualTo(DOCUMENT_URL);

        assertThat(imageUrls()).containsExactly(IMAGE_1, IMAGE_2);
        assertThat(reportFileUrl()).isEqualTo(DOCUMENT_URL);
        assertThat(statusOf(EVALUATION_ID)).isEqualTo("APPROVED");

        // 제출은 배정을 건드리지 않는다. 담당자는 픽스처가 심어 둔 그대로다
        assertThat(assignedEvaluator(EVALUATION_ID)).isEqualTo(EVALUATOR_ID);
    }

    @Test
    @DisplayName("다시 제출하면 사진과 진단서가 늘지 않고 갈아 끼워진다")
    void submitReplaces() throws Exception {
        // given : 잘못 적은 주행거리를 고치는 흐름이다
        submit(EVALUATION_ID, EVALUATOR_TOKEN, DOCUMENT_URL, IMAGE_1, IMAGE_2)
                .andExpect(status().isOk());

        // when : 사진 한 장, 다른 진단서로 다시 제출한다
        submit(EVALUATION_ID, EVALUATOR_TOKEN, NEW_DOCUMENT_URL, IMAGE_2)
                .andExpect(status().isOk());

        // then : 사진은 늘지 않고 갈리며 진단서는 새 주소로 덮인다
        assertThat(imageUrls()).containsExactly(IMAGE_2);
        assertThat(reportCount()).isEqualTo(1);
        assertThat(reportFileUrl()).isEqualTo(NEW_DOCUMENT_URL);
    }

    @Test
    @DisplayName("진단서 자리에 이미지 주소를 보내면 400이고 기존 사진이 살아남는다")
    void submitRejectsImageAsDocument() throws Exception {
        // given
        submit(EVALUATION_ID, EVALUATOR_TOKEN, DOCUMENT_URL, IMAGE_1, IMAGE_2)
                .andExpect(status().isOk());

        // when & then
        submit(EVALUATION_ID, EVALUATOR_TOKEN, IMAGE_1, IMAGE_2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVALUATION_UNMANAGED_DOCUMENT_URL"));

        // 주소 검증을 맨 앞에 둔 이유가 여기 있다.
        // 사진을 먼저 갈아 끼웠다면 기존 두 장이 지워진 채로 400이 나갔을 것이다
        assertThat(imageUrls()).containsExactly(IMAGE_1, IMAGE_2);
        assertThat(reportFileUrl()).isEqualTo(DOCUMENT_URL);
    }

    /**
     * 판매 신청이 넣어 둔 카탈로그 홍보 이미지가 제출로 사라지는지. 남겨 두면 대표 이미지 규칙이
     * sortOrder 최솟값이라 실물을 올려도 홍보 이미지가 계속 대표로 남고, 그 상태로 경매 썸네일이
     * 만들어진다. 사진 등록 API를 지우면서 그 API의 통합 테스트가 보증하던 것을 여기로 옮겼다.
     */
    @Test
    @DisplayName("제출하면 카탈로그 홍보 이미지가 사라지고 실물 첫 장이 대표가 된다")
    void submitReplacesCatalogImage() throws Exception {
        // when
        submit(EVALUATION_ID, EVALUATOR_TOKEN, DOCUMENT_URL, IMAGE_1, IMAGE_2)
                .andExpect(status().isOk());

        // then : 경매 생성이 sortOrder 최솟값을 대표 이미지로 집어 간다
        assertThat(imageUrls()).containsExactly(IMAGE_1, IMAGE_2);
    }

    @Test
    @DisplayName("사진에 발급하지 않은 주소가 섞이면 400이고 기존 사진이 그대로 남는다")
    void submitRejectsUnmanagedImage() throws Exception {
        // when : 첫 장은 우리 주소지만 두 번째가 외부 주소다
        submit(EVALUATION_ID, EVALUATOR_TOKEN, DOCUMENT_URL,
                IMAGE_1, "https://evil.example.com/x.jpg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VEHICLE_UNMANAGED_IMAGE_URL"));

        // then : 검증이 삭제보다 먼저 일어나지 않으면 여기서 기존 사진까지 잃는다.
        //        차량도 손대지 않은 상태여야 한다
        assertThat(imageUrls()).containsExactly(CATALOG_IMAGE);
        assertThat(vehicleRow().get("mileage")).isNull();
    }

    @Test
    @DisplayName("담당이 아닌 평가사는 403")
    void submitRejectsOtherEvaluator() throws Exception {
        // when & then : 평가사 계정이지만 이 건의 담당이 아니다.
        //               "평가사면 통과"로 구현했다면 여기서 200이 나와 깨진다
        submit(EVALUATION_ID, OTHER_EVALUATOR_TOKEN, DOCUMENT_URL, IMAGE_1)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_ASSIGNED_EVALUATOR"));

        assertThat(reportCount()).isZero();
        assertThat(vehicleRow().get("mileage")).isNull();
    }

    @Test
    @DisplayName("평가와 무관한 회원도 403")
    void submitRejectsStranger() throws Exception {
        submit(EVALUATION_ID, STRANGER_TOKEN, DOCUMENT_URL, IMAGE_1)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_ASSIGNED_EVALUATOR"));
    }

    @Test
    @DisplayName("아직 아무도 수락하지 않은 신청에는 누구도 제출할 수 없다")
    void submitRejectsUnassignedEvaluation() throws Exception {
        // when & then : 403이 아니라 409다. 평가사에게도 같은 답이 나가야 한다
        submit(UNASSIGNED_EVALUATION_ID, EVALUATOR_TOKEN, DOCUMENT_URL, IMAGE_1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_EVALUATOR_NOT_ASSIGNED"));
    }

    @Test
    @DisplayName("반려되어 끝난 평가에는 409")
    void submitRejectsRejectedEvaluation() throws Exception {
        // when & then : 배정은 되어 있는 건이라 담당자가 아니라 상태에서 걸려야 한다
        submit(REJECTED_EVALUATION_ID, EVALUATOR_TOKEN, DOCUMENT_URL, IMAGE_1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_DIAGNOSABLE"));
    }

    private ResultActions submit(long evaluationId, String rawToken,
                                 String documentUrl, String... imageUrls) throws Exception {
        String images = String.join(",", List.of(imageUrls).stream()
                .map(url -> "\"" + url + "\"")
                .toList());

        return mockMvc.perform(put("/api/evaluations/" + evaluationId + "/result")
                .cookie(cookie(rawToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "mileage": %d,
                          "estimatedPrice": %d,
                          "imageUrls": [%s],
                          "diagnosticReportUrl": "%s"
                        }
                        """.formatted(MILEAGE, ESTIMATED_PRICE, images, documentUrl)));
    }

    private static Cookie cookie(String rawToken) {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, rawToken);
    }

    // 대표 사진과 진단서를 함께 뽑는다. 사진은 vehicle_image 로도 확인하지만 그쪽은 다른 쓰기 경로라,
    // 차량에 값을 채울 때 둘이 뒤바뀌는 것은 이 두 컬럼을 나란히 봐야 잡힌다
    private Map<String, Object> vehicleRow() {
        return jdbcTemplate.queryForMap(
                "select mileage, estimated_price, main_photo_url, diagnostic_report_url"
                        + " from vehicle where id = ?", VEHICLE_ID);
    }

    private List<String> imageUrls() {
        return jdbcTemplate.queryForList(
                "select image_url from vehicle_image where vehicle_id = ? order by sort_order",
                String.class, VEHICLE_ID);
    }

    private String reportFileUrl() {
        return jdbcTemplate.queryForObject(
                "select diagnostic_report_url from vehicle where id = ?",
                String.class, VEHICLE_ID);
    }

    private int reportCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from vehicle where id = ? and diagnostic_report_url is not null",
                Integer.class, VEHICLE_ID);
    }

    private String statusOf(long evaluationId) {
        return jdbcTemplate.queryForObject(
                "select status from evaluation where id = ?", String.class, evaluationId);
    }

    private Long assignedEvaluator(long evaluationId) {
        return jdbcTemplate.queryForObject(
                "select evaluator_id from evaluation where id = ?", Long.class, evaluationId);
    }
}
