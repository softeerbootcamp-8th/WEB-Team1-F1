package com.softeer.race.evaluation.application.dto.command;

import java.util.List;

/**
 * 업로드 주소 발급 요청. 파일 자체가 아니라 파일의 형식과 크기만 온다.
 *
 * @param files 발급받을 파일 목록. 장수와 크기 상한은 요청 검증이 이미 막았다
 */
public record ImageUploadCommand(List<ImageFile> files) {

    /**
     * {@code contentType}이 {@code ImageContentType}이 아니라 문자열이다. enum으로 두면 변환이
     * {@code toCommand()}에서 일어나야 하고, 그러면 "어떤 형식을 받기로 했는가"라는 판정이
     * 컨트롤러에서 끝나 서비스는 이미 걸러진 값만 받는다. 그게 곤란한 이유는 두 가지다.
     * <p>
     * 서비스 테스트가 잘못된 형식을 넣어 볼 수 없게 된다 — enum 자리에 {@code "application/pdf"}를
     * 넘기는 코드는 컴파일되지 않으므로, 규칙 검증이 컨트롤러 테스트로만 밀려난다.
     * 그리고 나중에 다른 호출자가 이 서비스를 부르면 그 판정을 통째로 건너뛴다.
     * <p>
     * 파일 크기와 건수를 요청 검증에 맡기는 것과 어긋나 보이지만 성격이 다르다. 그쪽은 요청이
     * 형식에 맞는지를 보고 몇 번째 파일이 문제인지까지 알려 줄 수 있는 반면, 허용 형식 목록은
     * 바뀔 수 있는 정책이고 {@code ImageContentType}의 확장자 매핑과 짝이라 떼어 놓을 수 없다.
     */
    public record ImageFile(String contentType, long contentLength) {
    }
}
