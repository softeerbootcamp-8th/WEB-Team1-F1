package com.softeer.race.evaluation.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlateNumberLockRepository extends JpaRepository<PlateNumberLock, String> {

    /**
     * 이 번호판의 잠금 대상 행을 확보한다. 이미 있으면 아무 일도 하지 않는다.
     * <p>
     * <b>{@code save}가 아니라 네이티브 upsert인 이유가 트랜잭션 오염이다.</b> JPA로 넣고
     * {@code DataIntegrityViolationException}을 잡으면, 그 시점에 영속성 컨텍스트가 이미 오염되고
     * 트랜잭션이 rollback-only로 마킹돼 있다. 예외를 삼켜도 뒤따르는 접수 작업이 커밋될 수 없다.
     * 네이티브 {@code insert ignore}는 애초에 예외를 만들지 않아 이 문제를 피한다.
     * <p>
     * <b>{@code on duplicate key update}가 아니라 {@code insert ignore}인 이유는 데드락이다.</b>
     * 전자는 중복을 만나면 공유 잠금(S)을 얻은 뒤 같은 문장 안에서 배타 잠금(X)으로 승격하는데,
     * 같은 번호판에 세 건 이상이 몰리면 그 중 둘이 서로의 S를 붙든 채 X를 기다려 물린다.
     * {@code insert ignore}는 S를 얻고 건너뛰기만 해 승격이 없다.
     * <p>
     * 그 S도 트랜잭션이 끝날 때까지는 남는다. 그래서 이 쿼리는 <b>반드시 {@code PlateNumberLockCreator}의
     * 별도 트랜잭션에서 불려야 하고</b>, 접수 트랜잭션에서 직접 부르면 뒤따르는
     * {@link #findByPlateNumberForUpdate}가 S→X 승격이 되어 결국 같은 데드락이 난다.
     * <p>
     * 그 대가로 {@code insert ignore}는 길이 초과 같은 다른 오류도 경고로 낮춘다. 그래서
     * {@code plate_number}의 폭을 {@code vehicle.plate_number}와 같게 두어야 한다 — 좁아지면
     * 번호판이 조용히 잘린 채 들어가고, 잘린 값으로 잠그니 서로 다른 두 번호판이 같은 잠금을 쓴다.
     */
    @Modifying
    @Query(value = "insert ignore into plate_number_lock (plate_number) values (:plateNumber)",
            nativeQuery = true)
    void insertIfAbsent(@Param("plateNumber") String plateNumber);

    /**
     * 이 번호판의 접수 순서를 잡는다. 잠금은 호출한 트랜잭션이 끝날 때 함께 풀린다.
     * <p>
     * 반환값은 쓰이지 않는다. 이 호출의 결과물은 돌려받는 엔티티가 아니라 <b>얻어 낸 잠금</b>이고,
     * 그것은 트랜잭션에 남는다. 그래도 {@code Optional}을 그대로 내보내는 것은 행이 없다는 사실을
     * 호출자가 알아야 하기 때문이다 — {@link #insertIfAbsent} 직후라면 그 상황은 서버 결함이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from PlateNumberLock l where l.plateNumber = :plateNumber")
    Optional<PlateNumberLock> findByPlateNumberForUpdate(@Param("plateNumber") String plateNumber);
}
