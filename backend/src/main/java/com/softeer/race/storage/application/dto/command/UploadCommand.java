package com.softeer.race.storage.application.dto.command;

import java.util.List;

/**
 * 업로드 주소 발급 요청. 파일 자체가 아니라 파일의 형식과 크기만 온다.
 *
 * @param files 발급받을 파일 목록. 건수와 절대 크기 상한은 요청 검증이 이미 막았다
 */
public record UploadCommand(List<UploadFile> files) {

    /**
     * {@code contentType}이 {@code UploadContentType}이 아니라 문자열이다. enum으로 두면 변환이
     * {@code toCommand()}에서 일어나야 하고, 그러면 "어떤 형식을 받기로 했는가"라는 판정이
     * 컨트롤러에서 끝나 서비스는 이미 걸러진 값만 받는다. 그게 곤란한 이유는 두 가지다.
     * <p>
     * 서비스 테스트가 잘못된 형식을 넣어 볼 수 없게 된다 — enum 자리에 {@code "image/gif"}를
     * 넘기는 코드는 컴파일되지 않으므로, 규칙 검증이 컨트롤러 테스트로만 밀려난다.
     * 그리고 나중에 다른 호출자가 이 서비스를 부르면 그 판정을 통째로 건너뛴다.
     * <p>
     * 파일 크기는 두 곳이 나눠 본다. 요청 검증은 어떤 형식으로도 통과할 수 없는 절대 상한만 보고,
     * 형식별 상한은 서비스가 본다 — 상한이 형식에 딸린 값이라 형식을 알아낸 뒤에야 판정할 수 있다.
     * 건수를 요청 검증에 맡기는 것은 그쪽이 몇 번째 파일이 문제인지까지 알려 줄 수 있어서다.
     */
    public record UploadFile(String contentType, long contentLength) {
    }
}
