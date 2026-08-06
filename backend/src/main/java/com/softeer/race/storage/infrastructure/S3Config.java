package com.softeer.race.storage.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@RequiredArgsConstructor
public class S3Config {

    private final S3Properties s3Properties;

    /**
     * 자격 증명을 지정하지 않는다. 기본값인 {@code DefaultCredentialsProvider}가 EC2에서는 인스턴스에
     * 붙은 역할을, 로컬에서는 {@code ~/.aws/credentials}를 알아서 집는다. 설정 파일에 키를 적을
     * 필요가 없고, 적지 않는 것이 이 방식을 쓰는 이유다.
     * <p>
     * 빈을 만드는 시점에는 자격 증명을 확인하지 않는다. 실제 확인은 서명할 때 일어나므로,
     * 자격 증명이 없는 CI에서도 컨텍스트는 정상적으로 뜬다.
     */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(s3Properties.region()))
                .build();
    }
}
