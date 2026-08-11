package com.softeer.race.evaluation.application.dto.command;

import com.softeer.race.vehicle.domain.VehicleKeyword;
import java.util.List;

/**
 * 이미 제출된 평가 결과에서 <b>바꾸려는 항목만</b> 담은 입력.
 * <p>
 * {@link EvaluationResultSubmitCommand}와 필드가 겹치지만 합치지 않는다. 두 유스케이스의 null이
 * 뜻하는 바가 정반대다 — 제출에서 빠진 값은 <b>잘못된 요청</b>이고, 여기서 빠진 값은 <b>건드리지
 * 말라</b>다. 하나로 합치면 그 구분이 타입에서 사라져 필드를 빼먹은 제출이 "그대로 두기"로 조용히
 * 통과하고, 주행거리가 빈 차량이 만들어진다.
 * <p>
 * 그래서 원시 타입을 쓰지 않는다. {@code int mileage}로 두면 보내지 않은 것과 0을 보낸 것이
 * 구별되지 않아, 사진만 바꾸려던 요청이 주행거리를 0으로 덮는다.
 * <p>
 * <b>{@code imageUrls}는 목록 전체다.</b> 낱장을 더하고 빼는 연산이 아니라 "수정 뒤의 사진은 이
 * 목록"이라는 대체이고, 빠진 주소가 곧 삭제이며 배열 순서가 곧 표시 순서다. 낱장 조작을 별도
 * 엔드포인트로 나누면 삭제와 추가 사이에 판매자가 사진이 빠진 결과를 보게 된다.
 *
 * @param mileage             바꿀 실측 주행거리(km). null이면 그대로 둔다
 * @param estimatedPrice      바꿀 예상 시세(원). null이면 그대로 둔다
 * @param imageUrls           수정 뒤의 사진 목록 전부. null이면 그대로 두고, 비어 있을 수는 없다
 * @param diagnosticReportUrl 갈아 끼울 진단서 PDF 주소. null이면 그대로 둔다
 * @param keywords            수정 뒤의 키워드 전부. null이면 그대로 두고, 빈 목록이면 전부 지운다
 */
public record EvaluationResultPatchCommand(
        long evaluationId,
        long evaluatorId,
        Integer mileage,
        Long estimatedPrice,
        List<String> imageUrls,
        String diagnosticReportUrl,
        List<VehicleKeyword> keywords
) {
}
