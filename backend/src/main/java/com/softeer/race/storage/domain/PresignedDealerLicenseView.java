package com.softeer.race.storage.domain;

import java.time.LocalDateTime;

/**
 * 사원증을 볼 수 있는 임시 주소. 만료 시각을 함께 주는 이유는 화면이 그 시점을 알아야
 * 주소가 죽었을 때 다시 받아 올 수 있기 때문이다.
 */
public record PresignedDealerLicenseView(
        String viewUrl,
        LocalDateTime expiresAt
) {
}
