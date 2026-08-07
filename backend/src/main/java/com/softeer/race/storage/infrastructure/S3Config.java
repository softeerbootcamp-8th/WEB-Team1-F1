package com.softeer.race.storage.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
public class S3Config {

    private final S3Properties s3Properties;

    /**
     * 자격 증명을 지정하지 않는다. 기본값인 {@code DefaultCredentialsProvider}가 EC2에서는 인스턴스에
     * 붙은 역할을 집는다. 설정 파일에 키를 적을 필요가 없고, 적지 않는 것이 이 방식을 쓰는 이유다.
     * <p>
     * 빈을 만드는 시점에는 자격 증명을 확인하지 않는다. 실제 확인은 서명할 때 일어나므로,
     * 자격 증명이 없는 CI에서도 컨텍스트는 정상적으로 뜬다.
     * <p>
     * <b>{@code aws.s3.endpoint}가 있으면 그쪽으로 보낸다.</b> 로컬 개발용이다. 배포된 버킷은 EC2
     * 인스턴스 역할에만 쓰기를 허용하므로 개발자 노트북에서 서명한 주소는 발급까지만 되고 실제
     * PUT 에서 403 이 된다 — 서명 계산은 로컬이라 권한을 보지 않기 때문에 발급 단계에서는 아무
     * 문제도 드러나지 않는다. 그래서 S3 API 호환 서버(MinIO)를 세우고 엔드포인트만 갈아 끼운다.
     * 갈리는 것은 이 빈뿐이고 {@link S3FileStorage} 아래로는 전부 같은 코드가 돈다.
     */
    @Bean
    public S3Presigner s3Presigner() {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(s3Properties.region()));

        if (s3Properties.endpoint() == null) {
            return builder.build();
        }

        return builder
                .endpointOverride(URI.create(s3Properties.endpoint()))
                // 켜지 않으면 버킷 이름이 호스트 앞에 붙어 http://race-local.localhost:9000/... 이
                // 되고, 그 이름은 풀리지 않아 브라우저가 주소에 닿지도 못한다
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                // 기본 탐색에 맡기면 ~/.aws/credentials 를 집어 MinIO 가 모르는 키로 서명한다
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3Properties.accessKey(), s3Properties.secretKey())))
                .build();
    }
}
