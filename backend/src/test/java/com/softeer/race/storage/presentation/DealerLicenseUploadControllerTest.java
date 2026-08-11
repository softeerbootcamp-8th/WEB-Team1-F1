package com.softeer.race.storage.presentation;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.common.presentation.GlobalExceptionHandler;
import com.softeer.race.storage.application.DealerLicenseUploadService;
import com.softeer.race.storage.application.UploadService;
import com.softeer.race.storage.application.dto.info.DealerLicenseUploadInfo;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UploadController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("자동차매매사원증 업로드 컨트롤러")
class DealerLicenseUploadControllerTest {

    private static final String PATH = "/api/uploads/dealer-license/presigned";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DealerLicenseUploadService dealerLicenseUploadService;

    @MockitoBean
    private UploadService uploadService;

    @MockitoBean
    private SessionService sessionService;

    @Test
    @DisplayName("세션 없이 비공개 업로드 주소를 발급하고 조회 URL은 노출하지 않는다")
    void issueWithoutSession() throws Exception {
        when(dealerLicenseUploadService.issue(anyString(), anyLong()))
                .thenReturn(new DealerLicenseUploadInfo(
                        "dealer-licenses/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg",
                        "https://s3/upload?X-Amz-Signature=x",
                        LocalDateTime.of(2026, 8, 11, 12, 0)));

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contentType":"image/jpeg","contentLength":2481920}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value(
                        "dealer-licenses/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg"))
                .andExpect(jsonPath("$.uploadUrl").exists())
                .andExpect(jsonPath("$.expiresAt").value("2026-08-11T12:00:00"))
                .andExpect(jsonPath("$.fileUrl").doesNotExist());

        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("0바이트 파일은 요청 검증에서 거부한다")
    void rejectEmptyFile() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contentType":"image/png","contentLength":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("contentLength"));
    }

    @Test
    @DisplayName("10MB를 넘는 파일은 요청 검증에서 거부한다")
    void rejectOversizedFile() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contentType":"application/pdf","contentLength":10485761}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("contentLength"));
    }
}
