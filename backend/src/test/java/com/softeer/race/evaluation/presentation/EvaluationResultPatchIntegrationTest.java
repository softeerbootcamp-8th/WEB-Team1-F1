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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 평가 결과 항목별 수정을 컨트롤러에서 DB까지
 * <p>
 * 1. 한 항목만 바뀐다
 * 주행거리만 보냈을 때 시세 · 사진 · 진단서 · 키워드가 <b>DB에서</b> 그대로인지. 이 유스케이스의
 * 존재 이유라, 나머지가 지워지거나 비워지면 이슈 이전으로 돌아간 것이다
 * <p>
 * 2. 사진 낱장 조작
 * 목록에 한 장을 더하면 추가, 빼면 삭제, 순서를 바꾸면 sort_order가 따라가는지
 * <p>
 * 3. 대표 사진 동기화
 * 첫 장을 갈면 {@code vehicle.main_photo_url}이 새 첫 장으로 따라가는지. 이게 빠지면 목록에서
 * 지운 사진이 경매 목록 썸네일에 계속 남는다
 * <p>
 * 4. 결과가 없으면 못 고친다
 * 아직 제출되지 않은 평가에 409. 이 관문이 부분 수정이 {@code Vehicle} 불변식을 깨지 않는 근거다
 * <p>
 * 5. 부분 반영 방지
 * 진단서 자리에 이미지 주소를 보내면 400이고 <b>그때 기존 사진이 살아남는지</b>
 * <p>
 * 6. 수정 자격
 * 담당이 아닌 평가사가 막히는지
 * <p>
 * <b>Clock을 고정하지 않는다.</b> 픽스처의 세션 만료가 DB의 실제 시각(NOW(6))으로 심기므로
 * 앱 Clock만 옮기면 전 시나리오가 401이 된다.
 */
@DisplayName("평가 결과 항목별 수정 통합 테스트")
@Transactional
@Sql("/sql/evaluation-result-patch-fixture.sql")
class EvaluationResultPatchIntegrationTest extends IntegrationTestSupport {

    /** 701(박평가)에게 배정되고 결과 제출까지 끝난 신청 */
    private static final long EVALUATION_ID = 700L;
    private static final long VEHICLE_ID = 700L;

    /** 배정만 받고 아직 결과를 내지 않은 신청 */
    private static final long UNSUBMITTED_EVALUATION_ID = 701L;
    private static final long UNSUBMITTED_VEHICLE_ID = 701L;

    private static final String EVALUATOR_TOKEN = "patch-eval-token";
    private static final String OTHER_EVALUATOR_TOKEN = "patch-eval2-token";

    // 테스트 설정의 aws.s3.cdn-base-url과 같아야 한다. 다르면 전부 UNMANAGED_*_URL로 떨어진다
    private static final String CDN_BASE_URL = "https://cdn.test.local";

    /** 픽스처가 심어 둔 두 장. 첫 장이 대표다 */
    private static final String IMAGE_1 =
            CDN_BASE_URL + "/images/2026/08/11111111-0d47-4a19-9b2f-6c1d5e7a8b90.jpg";
    private static final String IMAGE_2 =
            CDN_BASE_URL + "/images/2026/08/22222222-0d47-4a19-9b2f-6c1d5e7a8b90.jpg";
    private static final String NEW_IMAGE =
            CDN_BASE_URL + "/images/2026/08/33333333-0d47-4a19-9b2f-6c1d5e7a8b90.jpg";

    private static final String DOCUMENT_URL =
            CDN_BASE_URL + "/documents/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";
    private static final String NEW_DOCUMENT_URL =
            CDN_BASE_URL + "/documents/2026/08/aaaaaaaa-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";

    private static final int MILEAGE = 45_000;
    private static final long ESTIMATED_PRICE = 21_500_000L;

    @Test
    @DisplayName("주행거리만 보내면 나머지는 DB에서 그대로다")
    void patchesMileageOnly() throws Exception {
        // when
        patchResult(EVALUATION_ID, EVALUATOR_TOKEN, """
                { "mileage": 46000 }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileage").value(46000))
                // 응답은 바꾼 것만이 아니라 결과 전부다. 판매자 화면을 그대로 다시 그릴 수 있어야 한다
                .andExpect(jsonPath("$.estimatedPrice").value(ESTIMATED_PRICE))
                .andExpect(jsonPath("$.imageUrls.length()").value(2))
                .andExpect(jsonPath("$.diagnosticReportUrl").value(DOCUMENT_URL))
                .andExpect(jsonPath("$.keywords.length()").value(2));

        // then : 여기서 무언가 비면 사진 한 장을 바꾸려고 전부를 다시 보내야 했던 문제가 그대로다
        Map<String, Object> vehicle = vehicleRow();
        assertThat(vehicle.get("mileage")).isEqualTo(46_000);
        assertThat(vehicle.get("estimated_price")).isEqualTo(ESTIMATED_PRICE);
        assertThat(vehicle.get("main_photo_url")).isEqualTo(IMAGE_1);
        assertThat(vehicle.get("diagnostic_report_url")).isEqualTo(DOCUMENT_URL);

        assertThat(imageUrls()).containsExactly(IMAGE_1, IMAGE_2);
        assertThat(keywords()).containsExactly("ACCIDENT_FREE", "NO_LEAK");
        assertThat(statusOf(EVALUATION_ID)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("진단서만 갈아 끼워도 주행거리·시세·사진은 그대로다")
    void patchesReportOnly() throws Exception {
        // when
        patchResult(EVALUATION_ID, EVALUATOR_TOKEN, """
                { "diagnosticReportUrl": "%s" }
                """.formatted(NEW_DOCUMENT_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosticReportUrl").value(NEW_DOCUMENT_URL));

        // then
        Map<String, Object> vehicle = vehicleRow();
        assertThat(vehicle.get("diagnostic_report_url")).isEqualTo(NEW_DOCUMENT_URL);
        assertThat(vehicle.get("mileage")).isEqualTo(MILEAGE);
        assertThat(vehicle.get("estimated_price")).isEqualTo(ESTIMATED_PRICE);
        assertThat(imageUrls()).containsExactly(IMAGE_1, IMAGE_2);
    }

    @Test
    @DisplayName("목록에 한 장을 더하면 사진이 추가된다")
    void addsOneImage() throws Exception {
        // when : 기존 두 장 뒤에 한 장을 붙여 보낸다
        patchResult(EVALUATION_ID, EVALUATOR_TOKEN, """
                { "imageUrls": [%s] }
                """.formatted(quoted(List.of(IMAGE_1, IMAGE_2, NEW_IMAGE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrls.length()").value(3));

        // then : sort_order 가 보낸 순서대로 다시 매겨진다
        assertThat(imageUrls()).containsExactly(IMAGE_1, IMAGE_2, NEW_IMAGE);
        assertThat(vehicleRow().get("main_photo_url")).isEqualTo(IMAGE_1);
    }

    @Test
    @DisplayName("목록에서 한 장을 빼면 그 사진만 삭제된다")
    void removesOneImage() throws Exception {
        // when : 둘째 장만 빼고 보낸다
        patchResult(EVALUATION_ID, EVALUATOR_TOKEN, """
                { "imageUrls": [%s] }
                """.formatted(quoted(List.of(IMAGE_1))))
                .andExpect(status().isOk());

        // then
        assertThat(imageUrls()).containsExactly(IMAGE_1);
    }

    @Test
    @DisplayName("순서를 바꿔 보내면 대표 사진이 새 첫 장으로 따라간다")
    void reordersImagesAndSyncsMainPhoto() throws Exception {
        // when : 대표였던 IMAGE_1을 뒤로 보낸다
        patchResult(EVALUATION_ID, EVALUATOR_TOKEN, """
                { "imageUrls": [%s] }
                """.formatted(quoted(List.of(IMAGE_2, IMAGE_1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrls[0]").value(IMAGE_2));

        // then : 이게 없으면 목록의 첫 장과 대표 사진이 어긋나
        //        경매 목록 썸네일이 갤러리와 다른 사진을 가리킨다
        assertThat(imageUrls()).containsExactly(IMAGE_2, IMAGE_1);
        assertThat(vehicleRow().get("main_photo_url")).isEqualTo(IMAGE_2);
    }

    @Test
    @DisplayName("대표였던 사진을 빼면 대표가 남은 첫 장으로 옮겨간다")
    void removingMainPhotoMovesIt() throws Exception {
        // when
        patchResult(EVALUATION_ID, EVALUATOR_TOKEN, """
                { "imageUrls": [%s] }
                """.formatted(quoted(List.of(IMAGE_2, NEW_IMAGE))))
                .andExpect(status().isOk());

        // then : 지워진 사진 주소가 대표로 남으면 목록에 없는 사진이 카드에 계속 보인다
        assertThat(imageUrls()).containsExactly(IMAGE_2, NEW_IMAGE);
        assertThat(vehicleRow().get("main_photo_url")).isEqualTo(IMAGE_2);
    }

    @Test
    @DisplayName("사진만 바꿔도 키워드는 그대로 남는다")
    void patchingImagesKeepsKeywords() throws Exception {
        // when
        patchResult(EVALUATION_ID, EVALUATOR_TOKEN, """
                { "imageUrls": [%s] }
                """.formatted(quoted(List.of(NEW_IMAGE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keywords.length()").value(2));

        // then : 사진 교체가 키워드 교체를 함께 부르면 평가사가 매긴 것이 사라진다
        assertThat(keywords()).containsExactly("ACCIDENT_FREE", "NO_LEAK");
    }

    @Test
    @DisplayName("키워드 빈 배열은 전부 지우고 사진은 건드리지 않는다")
    void clearsKeywords() throws Exception {
        // when
        patchResult(EVALUATION_ID, EVALUATOR_TOKEN, """
                { "keywords": [] }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keywords").isEmpty());

        // then
        assertThat(keywords()).isEmpty();
        assertThat(imageUrls()).containsExactly(IMAGE_1, IMAGE_2);
    }

    /**
     * 같은 키워드를 그대로 다시 보내는 정상 흐름이 유니크 제약 위반으로 500이 되지 않는지.
     * 교체가 지우기 전에 넣으면 그렇게 된다({@code VehicleKeywordService.replace}의 flush).
     */
    @Test
    @DisplayName("겹치는 키워드를 그대로 다시 보내도 500이 되지 않는다")
    void replacesOverlappingKeywords() throws Exception {
        // when : 하나는 그대로 두고 하나를 바꿔 보낸다
        patchResult(EVALUATION_ID, EVALUATOR_TOKEN, """
                { "keywords": ["ACCIDENT_FREE", "GOOD_TIRE"] }
                """)
                .andExpect(status().isOk());

        // then : 뺀 키워드는 남지 않는다
        assertThat(keywords()).containsExactly("ACCIDENT_FREE", "GOOD_TIRE");
    }

    @Test
    @DisplayName("아직 결과가 제출되지 않은 평가는 409")
    void rejectsUnsubmittedResult() throws Exception {
        // when & then : 이 관문이 없으면 주행거리만 채워지고 시세가 빈 차량이 만들어져,
        //               "경매가 붙은 차량은 주행거리가 채워져 있다"는 불변식이 깨진다
        patchResult(UNSUBMITTED_EVALUATION_ID, EVALUATOR_TOKEN, """
                { "mileage": 46000 }
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_RESULT_NOT_SUBMITTED"));

        // 차량은 진단 전 그대로여야 한다
        assertThat(mileageOf(UNSUBMITTED_VEHICLE_ID)).isNull();
    }

    @Test
    @DisplayName("진단서 자리에 이미지 주소를 보내면 400이고 기존 사진이 살아남는다")
    void rejectsImageAsDocument() throws Exception {
        // when & then : 사진까지 함께 보냈으므로 검증이 뒤에 있었다면 사진이 먼저 갈렸을 것이다
        patchResult(EVALUATION_ID, EVALUATOR_TOKEN, """
                {
                  "imageUrls": [%s],
                  "diagnosticReportUrl": "%s"
                }
                """.formatted(quoted(List.of(NEW_IMAGE)), IMAGE_1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVALUATION_UNMANAGED_DOCUMENT_URL"));

        assertThat(imageUrls()).containsExactly(IMAGE_1, IMAGE_2);
        assertThat(vehicleRow().get("diagnostic_report_url")).isEqualTo(DOCUMENT_URL);
    }

    @Test
    @DisplayName("사진에 발급하지 않은 주소가 섞이면 400이고 기존 사진이 그대로 남는다")
    void rejectsUnmanagedImage() throws Exception {
        // when : 첫 장은 우리 주소지만 두 번째가 외부 주소다
        patchResult(EVALUATION_ID, EVALUATOR_TOKEN, """
                { "imageUrls": [%s] }
                """.formatted(quoted(List.of(IMAGE_1, "https://evil.example.com/x.jpg"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VEHICLE_UNMANAGED_IMAGE_URL"));

        // then : 검증이 삭제보다 먼저 일어나지 않으면 여기서 기존 사진까지 잃는다
        assertThat(imageUrls()).containsExactly(IMAGE_1, IMAGE_2);
    }

    @Test
    @DisplayName("바꿀 항목을 하나도 보내지 않으면 400")
    void rejectsEmptyBody() throws Exception {
        // when & then : 200 no-op으로 두면 필드 이름을 틀린 요청이 성공으로 보인다
        patchResult(EVALUATION_ID, EVALUATOR_TOKEN, "{}")
                .andExpect(status().isBadRequest());

        assertThat(vehicleRow().get("mileage")).isEqualTo(MILEAGE);
    }

    @Test
    @DisplayName("담당이 아닌 평가사는 403이고 아무것도 바뀌지 않는다")
    void rejectsOtherEvaluator() throws Exception {
        // when & then : 평가사 계정이지만 이 건의 담당이 아니다
        patchResult(EVALUATION_ID, OTHER_EVALUATOR_TOKEN, """
                { "mileage": 46000 }
                """)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_ASSIGNED_EVALUATOR"));

        assertThat(vehicleRow().get("mileage")).isEqualTo(MILEAGE);
    }

    private ResultActions patchResult(long evaluationId, String rawToken, String body)
            throws Exception {
        return mockMvc.perform(patch("/api/evaluations/" + evaluationId + "/result")
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, rawToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String quoted(List<String> values) {
        return String.join(",", values.stream().map(value -> "\"" + value + "\"").toList());
    }

    // 대표 사진과 진단서를 함께 뽑는다. 사진은 vehicle_image 로도 확인하지만 그쪽은 다른 쓰기
    // 경로라, 둘이 어긋나는 것은 이 두 컬럼을 나란히 봐야 잡힌다
    private Map<String, Object> vehicleRow() {
        return jdbcTemplate.queryForMap(
                "select mileage, estimated_price, main_photo_url, diagnostic_report_url"
                        + " from vehicle where id = ?", VEHICLE_ID);
    }

    private Integer mileageOf(long vehicleId) {
        return jdbcTemplate.queryForObject(
                "select mileage from vehicle where id = ?", Integer.class, vehicleId);
    }

    private List<String> imageUrls() {
        return jdbcTemplate.queryForList(
                "select image_url from vehicle_image where vehicle_id = ? order by sort_order",
                String.class, VEHICLE_ID);
    }

    private List<String> keywords() {
        return jdbcTemplate.queryForList(
                "select keyword from vehicle_keyword_tag where vehicle_id = ? order by keyword",
                String.class, VEHICLE_ID);
    }

    private String statusOf(long evaluationId) {
        return jdbcTemplate.queryForObject(
                "select status from evaluation where id = ?", String.class, evaluationId);
    }
}
