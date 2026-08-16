package com.softeer.race.evaluation.application;

import com.softeer.race.evaluation.domain.PlateNumberLockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 번호판의 잠금 대상 행을 확보한다. 이미 있으면 아무 일도 하지 않는다.
 * <p>
 * <b>이 한 줄을 위해 클래스가 따로 있는 이유는 트랜잭션 전파다.</b> 접수 트랜잭션 안에서 그냥
 * 부르면 데드락이 난다.
 * <p>
 * {@code insert ignore}는 중복을 만나면 그 행에 <b>공유 잠금(S)</b>을 걸고 건너뛴다. 그 잠금은
 * 트랜잭션이 끝날 때까지 남으므로, 바로 뒤에서 같은 행을 {@code for update}로 잡으려 하면 S에서
 * 배타 잠금(X)으로 <b>승격</b>하는 모양이 된다. 같은 번호판에 요청이 셋 이상 몰리면 그 중 둘이
 * 서로의 S를 붙든 채 각자 X를 기다려 데드락이 된다 — 실제로 그렇게 터졌고,
 * {@code VisitQuoteConcurrencyIntegrationTest}의 첫 시나리오가 세 건을 쏘는 이유가 이것이다.
 * <p>
 * 확보를 별도 트랜잭션으로 떼면 S가 <b>여기서 커밋과 함께 풀린다.</b> 접수 트랜잭션은 잠금을 하나도
 * 들지 않은 상태에서 {@code for update}를 걸어 X를 곧바로 얻으므로 승격이 아예 없다.
 * <p>
 * <b>순서를 뒤집어 "먼저 for update 해 보고 없으면 만든다"로 바꾸면 더 나쁘다.</b> 없는 행을
 * {@code for update}로 조회하면 REPEATABLE READ의 갭 잠금이 걸리는데, 이어지는 별도 트랜잭션의
 * insert가 바로 그 갭에 들어가려다 자기를 부른 트랜잭션의 잠금을 기다리게 된다. 스스로 막혀
 * 잠금 대기 시간이 다 찰 때까지 아무도 진행하지 못한다.
 * <p>
 * 별도 트랜잭션이라 커넥션을 하나 더 쓴다. 방문견적 접수는 저빈도 쓰기이고 이 트랜잭션은 문장
 * 하나로 끝나 곧 반납된다.
 * <p>
 * <b>롤백에 딸려 사라지지 않는 것이 문제가 되지 않는다.</b> 접수가 실패해도 이 행은 남는데, 이 행은
 * 진행 중인 신청이 있다는 뜻이 아니라 잠글 자리일 뿐이라 남아도 아무것도 막지 않는다
 * ({@code PlateNumberLock} 참고).
 */
@Component
@RequiredArgsConstructor
public class PlateNumberLockCreator {

    private final PlateNumberLockRepository plateNumberLockRepository;

    /**
     * 자기 호출로는 전파가 걸리지 않는다. 접수 서비스가 이 빈을 주입받아 불러야 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createIfAbsent(String plateNumber) {
        plateNumberLockRepository.insertIfAbsent(plateNumber);
    }
}
