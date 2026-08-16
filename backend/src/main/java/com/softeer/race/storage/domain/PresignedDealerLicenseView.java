package com.softeer.race.storage.domain;

import java.time.LocalDateTime;

/**
 * 사원증을 볼 수 있는 임시 주소. 만료 시각을 함께 주는 이유는 화면이 그 시점을 알아야
 * 주소가 죽었을 때 다시 받아 올 수 있기 때문이다.
 *
 * @param contentType 무엇을 그려야 하는지. 사원증은 이미지와 PDF를 모두 받으므로
 *                    이 값이 없으면 화면이 둘 중 하나를 찍어야 하고, 틀리면 아무것도 보이지 않는다
 */
public record PresignedDealerLicenseView(
        String viewUrl,
        String contentType,
        LocalDateTime expiresAt
) {
}
