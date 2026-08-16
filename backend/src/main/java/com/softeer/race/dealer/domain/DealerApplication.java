package com.softeer.race.dealer.domain;

import com.softeer.race.common.domain.BaseTimeEntity;
import com.softeer.race.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 딜러 자격을 얻으려고 낸 심사 신청. 접수되면 관리자가 판정할 때까지 {@code PENDING}으로 대기하고,
 * 승인된 신청의 신청자만 {@code Role.DEALER}가 된다.
 * <p>
 * 회원 필드가 아니라 별도 엔티티인 이유는 두 가지다. 반려 사유와 심사 시각이 {@code users}에 섞이지
 * 않고, 관리자 대기 목록이 이 테이블의 인덱스만으로 끝난다.
 * <p>
 * <b>사원증은 회원이 아니라 이 신청에 붙는다.</b> 회원의 속성이 아니라 신청에 첨부한 서류이고,
 * {@code users}에 두면 재신청이 생기는 순간 어느 신청의 서류인지 알 수 없다.
 */
@Getter
@Entity
// 사원증 키가 겹치면 같은 사원증으로 여러 사람이 딜러가 된다. 제약명을 직접 지정하는 이유는
// User 와 같다 — 자동 생성된 이름으로는 위반 시 어떤 컬럼인지 가려낼 수 없다
//
// 인덱스 둘은 이 테이블을 읽는 두 화면과 하나씩 짝이다. 관리자 대기 목록은 status 로 좁혀
// 접수 순으로 읽고, 내 신청 조회는 신청자로 좁혀 최신 한 건만 본다.
// 두 인덱스 모두 뒤에 id 를 적지 않는다 — InnoDB 의 보조 인덱스는 PK 를 뒤에 달고 있어
// (status) 만으로 이미 (status, id) 순서다
@Table(name = "dealer_application",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dealer_application_license_key", columnNames = "license_key"),
        indexes = {
                @Index(name = "idx_dealer_application_status", columnList = "status"),
                @Index(name = "idx_dealer_application_applicant", columnList = "applicant_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DealerApplication extends BaseTimeEntity {

    /**
     * 반려 사유의 상한. 컬럼 폭과 요청 검증이 이 한 값을 함께 본다.
     * <p>
     * 사유는 신청자에게 무엇이 부족했는지 알리는 한두 문장이다. 넉넉히 잡을 이유가 없고,
     * 좁게 두면 화면이 잘라 보일 걱정도 없다. {@code Evaluation.MAX_REJECT_REASON_LENGTH}와 같은 값이다.
     */
    public static final int MAX_REJECT_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant;

    /** 외부 조회 URL이 아니라 비공개 S3 객체 키만 저장한다. */
    @Column(name = "license_key", nullable = false, length = 255)
    private String licenseKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DealerApplicationStatus status;

    /**
     * 반려 사유. 대기 중이거나 승인된 신청에서는 비어 있다 — 그 null이 "반려되지 않았다"를 뜻한다.
     * <p>
     * 길이를 못 박는다. 기본값(255)에 기대면 컬럼 폭이 요청 검증과 따로 놀아, 검증 상한을 늘리는
     * 순간 저장에서 잘리거나 터진다.
     */
    @Column(length = MAX_REJECT_REASON_LENGTH)
    private String rejectReason;

    private DealerApplication(User applicant, String licenseKey) {
        this.applicant = applicant;
        this.licenseKey = licenseKey;
        this.status = DealerApplicationStatus.PENDING;
    }

    /** 사원증을 붙여 심사를 요청한다. 사원증이 실제로 올라온 파일인지는 서비스가 저장소에 확인한다. */
    public static DealerApplication apply(User applicant, String licenseKey) {
        return new DealerApplication(applicant, licenseKey);
    }
}
