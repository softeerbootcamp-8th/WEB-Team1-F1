package com.softeer.race.evaluation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 방문견적 접수를 번호판 단위로 직렬화하기 위한 <b>잠금 대상</b>.
 * <p>
 * <b>여기 담긴 것은 데이터가 아니다.</b> 행이 있다고 해서 그 번호판에 진행 중인 신청이 있다는 뜻이
 * 아니고, 없다고 해서 없다는 뜻도 아니다. 진행 중인지의 판정은 지금도 앞으로도
 * {@link EvaluationRepository#existsBlockingVisitQuoteByPlateNumber}가 혼자 한다. 이 행의 존재
 * 이유는 그 판정과 뒤따르는 insert를 <b>한 번에 하나만 통과시키는 것</b>뿐이다.
 * <p>
 * 왜 이런 테이블이 필요한가. 접수는 "진행 중인 신청이 있는지 확인 → 없으면 만든다" 순서인데 이 둘이
 * 원자적이지 않아, 거의 같은 순간에 들어온 두 요청이 모두 "없음"을 읽고 모두 접수된다. 그런데
 * 이것을 막을 방법이 셋 다 막혀 있었다.
 * <ul>
 *   <li>{@code vehicle.plate_number}에는 unique를 걸 수 없다. 신청마다 vehicle 행이 새로 생기고,
 *       같은 차가 판매를 끝낸 뒤 다시 등록될 수 있어 중복이 정상이다.</li>
 *   <li>"진행 중인 신청만 유일" 같은 부분 unique 인덱스를 MySQL이 지원하지 않는다.</li>
 *   <li><b>잠글 행이 없었다.</b> 막아야 하는 것은 아직 존재하지 않는 행이라 {@code FOR UPDATE}를
 *       걸 대상 자체가 없었다.</li>
 * </ul>
 * 세 번째 문제만 풀면 나머지는 따라온다. 그래서 번호판마다 <b>반드시 하나 존재하는 행</b>을 만들어
 * 두고, 접수가 그것을 잠근 뒤 판정과 insert를 한다. 잠금 해제가 곧 커밋이라 해제를 잊을 곳이 없다.
 * <p>
 * 진행 중 여부를 이 테이블에 <b>기록하지 않는</b> 것이 중요하다. 기록하는 순간 같은 사실이
 * {@code evaluation.status}와 여기 두 곳에 살게 되고, 반려·낙찰 종료마다 이쪽을 지워 줘야 한다.
 * 한 곳이라도 빠지면 그 번호판은 영영 신청할 수 없게 된다 — 잠금만 맡기면 그 실패가 아예 불가능하다.
 * <p>
 * 행은 지우지 않는다. 번호판 수만큼만 늘고, 지워 봐야 다음 신청이 다시 만든다.
 * <p>
 * <b>지우면 동시 접수가 그대로 다시 샌다.</b> 컬럼 하나뿐인 빈 테이블이라 쓰이지 않는 잔해로 보이지만,
 * 비어 있는 것이 정상이고 그것이 이 테이블이 제 일을 하고 있다는 뜻이다.
 */
@Entity
@Table(name = "plate_number_lock")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlateNumberLock {

    /**
     * 번호판 그 자체가 식별자다. 대리키를 두면 "번호판당 하나"를 unique 제약으로 따로 걸어야 하는데,
     * 그 유일성이야말로 이 테이블의 전부라 PK 자리에 두는 것이 맞다.
     * <p>
     * 물리 컬럼명을 적어 둔다. 네이티브 upsert가 이 이름을 문자열로 참조하고 있어
     * ({@link PlateNumberLockRepository#insertIfAbsent}) 정적 분석이 둘을 연결하지 못한다.
     */
    @Id
    @Column(name = "plate_number")
    private String plateNumber;
}
