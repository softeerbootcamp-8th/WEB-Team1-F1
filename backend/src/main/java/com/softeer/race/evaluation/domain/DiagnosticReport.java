package com.softeer.race.evaluation.domain;

import com.softeer.race.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 평가사가 남긴 진단서. 평가 한 건에 한 부다.
 * <p>
 * <b>{@code fileUrl}은 공개 주소다.</b> 진단서는 경매에 올라가면 입찰자 모두가 보는 자료라
 * 열람을 통제할 이유가 없고, 통제하려 해도 CDN 주소를 아는 사람은 계속 열 수 있다. 조회 API가
 * 권한을 보는 것은 기밀을 지키기 위해서가 아니라 <b>아무에게나 주소를 건네지 않기 위해서</b>다 —
 * 이 구분을 기밀 보장으로 착각하고 그 위에 기능을 얹으면 안 된다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiagnosticReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_id", nullable = false, unique = true)
    private Evaluation evaluation;

    @Column(nullable = false)
    private String fileUrl;

    private DiagnosticReport(Evaluation evaluation, String fileUrl) {
        this.evaluation = evaluation;
        this.fileUrl = fileUrl;
    }

    /**
     * 평가에 진단서를 붙인다.
     * <p>
     * 주소가 우리가 발급한 문서 주소인지는 여기서 보지 않는다. 그 판정은 저장소만 할 수 있어
     * (발급 규칙을 아는 곳이 저장소다) 엔티티가 들고 있을 수 없다. 서비스가 먼저 거른다.
     */
    public static DiagnosticReport attach(Evaluation evaluation, String fileUrl) {
        return new DiagnosticReport(evaluation, fileUrl);
    }

    /**
     * 붙어 있던 진단서를 다른 파일로 갈아 끼운다.
     * <p>
     * 재첨부를 거부하지 않는 이유는 스캔이 잘못됐거나 페이지가 빠진 진단서를 다시 올리는 일이
     * 흔하기 때문이다. 409로 막으면 고칠 방법이 없다.
     * <p>
     * 이전 파일은 저장소에 남는다. 차량 사진도 같은 상태이고(등록은 DB 행만 갈아 끼운다),
     * 객체 정리는 저장소 수명 주기 규칙이 할 일이라 여기서 지우지 않는다.
     */
    public void replaceFile(String fileUrl) {
        this.fileUrl = fileUrl;
    }
}
