package com.softeer.race.storage.application;

import com.softeer.race.storage.application.dto.command.UploadCommand;
import com.softeer.race.storage.application.dto.info.UploadInfo;
import com.softeer.race.storage.domain.FileStorage;
import com.softeer.race.storage.domain.PresignedUpload;
import com.softeer.race.storage.domain.UploadContentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 업로드 주소 발급.
 * <p>
 * {@code @Transactional}을 붙이지 않는다. DB를 건드리지 않으므로 트랜잭션을 열면 커넥션만 잡고
 * 아무것도 하지 않는다.
 * <p>
 * 발급은 S3 엔드포인트를 호출하지 않는다. 서명 계산 자체는 로컬이고, 권한 평가도 여기서는
 * 일어나지 않는다 — 그건 클라이언트가 받은 주소로 실제 PUT 할 때 S3가 한다. 그래서 권한이
 * 없어도 발급은 성공하고 업로드 단계에서 403이 난다.
 * <p>
 * 다만 자격 증명을 얻는 과정은 완전히 로컬이 아니다. EC2에서는 인스턴스 메타데이터 서비스를
 * 호출해 임시 자격 증명을 받아 오므로 첫 발급과 갱신 시점에 왕복이 한 번 생긴다. SDK가 캐싱하니
 * 매 요청마다는 아니다.
 */
@Service
@RequiredArgsConstructor
public class UploadService {

    private final FileStorage fileStorage;

    /**
     * 요청한 파일 수만큼 서명된 주소를 발급한다.
     * <p>
     * 형식과 크기 검증을 전부 끝낸 뒤에 발급을 시작한다. 한 건씩 검증하며 발급하면 목록 중간에
     * 잘못된 파일이 있을 때 앞의 것들은 이미 발급된 채로 400이 나가고, 클라이언트는 그 주소들을
     * 받지 못해 아무도 쓰지 않는 객체가 올라갈 자리만 남는다.
     */
    public UploadInfo issue(UploadCommand command) {
        record Validated(UploadContentType contentType, long contentLength) {
        }

        List<Validated> validated = command.files().stream()
                .map(file -> {
                    UploadContentType contentType = UploadContentType.from(file.contentType());

                    // 크기 상한이 형식마다 달라 형식을 알아낸 뒤에야 판정할 수 있다. 요청 검증은
                    // 어떤 형식으로도 통과할 수 없는 절대 상한까지만 걸러 준다
                    contentType.validateSize(file.contentLength());

                    return new Validated(contentType, file.contentLength());
                })
                .toList();

        List<PresignedUpload> uploads = validated.stream()
                .map(file -> fileStorage.presign(file.contentType(), file.contentLength()))
                .toList();

        return new UploadInfo(uploads);
    }
}
