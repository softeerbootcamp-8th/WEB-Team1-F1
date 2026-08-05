package com.softeer.race.evaluation.application.dto.command;

import java.util.List;

/**
 * 평가 결과 제출 유스케이스의 입력 전부. 행위 주체인 evaluatorId도 입력의 일부라 여기 담는다.
 * <p>
 * 진단서 첨부와 달리 Command로 묶는다. 값이 여섯 개인데 그중 {@code mileage}와
 * {@code estimatedPrice}는 <b>둘 다 숫자라 자리를 바꿔 넣어도 컴파일된다.</b> 파라미터로 늘어놓으면
 * 주행거리 자리에 금액이 들어간 호출이 조용히 통과하고, 그 값은 그대로 차량에 저장된다.
 * <p>
 * 파일은 오지 않는다. 사진과 진단서 모두 클라이언트가 저장소에 직접 올린 뒤 그 주소만 보낸다.
 *
 * @param mileage             평가사가 실측한 주행거리(km)
 * @param estimatedPrice      평가사가 산정한 예상 시세(원)
 * @param imageUrls           차량 사진 주소. 보낸 순서가 표시 순서이고 첫 장이 대표 이미지가 된다
 * @param diagnosticReportUrl 진단서 PDF 주소
 */
public record EvaluationResultSubmitCommand(
        long evaluationId,
        long evaluatorId,
        int mileage,
        long estimatedPrice,
        List<String> imageUrls,
        String diagnosticReportUrl
) {
}
