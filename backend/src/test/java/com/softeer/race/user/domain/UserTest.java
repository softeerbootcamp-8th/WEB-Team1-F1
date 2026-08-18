package com.softeer.race.user.domain;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.user.exception.UserErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 이름은 가운데를 가려 내보내므로 두 글자보다 짧으면 가릴 자리가 없다
// 저장을 막지 않으면 그 사람이 입찰한 경매방을 읽는 순간 터진다
class UserTest {

    @DisplayName("두 글자보다 짧은 이름으로는 만들 수 없다")
    @ParameterizedTest(name = "이름 \"{0}\"")
    @ValueSource(strings = {"김", " 김 ", " "})
    void rejectsNameShorterThanTwoLetters(String realName) {
        assertThatThrownBy(() -> create(realName))
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).errorCode())
                .isEqualTo(UserErrorCode.INVALID_REAL_NAME);
    }

    @Test
    @DisplayName("두 글자면 만들 수 있다, 마스킹이 가능한 최소 길이다")
    void acceptsTwoLetterName() {
        assertThat(create("김철").getRealName()).isEqualTo("김철");
    }

    @Test
    @DisplayName("가입한 회원은 이용할 수 있는 상태로 시작한다")
    void startsActive() {
        User user = create(Role.GENERAL);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isSuspended()).isFalse();
    }

    // 정지가 역할까지 바꾸면 해제할 때 무엇으로 되돌릴지 알 수 없다
    @Test
    @DisplayName("정지해도 역할은 그대로 남고 사유가 붙는다")
    void suspendKeepsRole() {
        User user = create(Role.DEALER);

        user.suspend("허위 매물을 반복 등록했습니다.");

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(user.getSuspendReason()).isEqualTo("허위 매물을 반복 등록했습니다.");
        assertThat(user.getRole()).isEqualTo(Role.DEALER);
    }

    // 남아 있는 사유가 곧 지금 정지 중이라는 뜻이어야 한다
    @Test
    @DisplayName("해제하면 원래 역할로 돌아오고 사유가 지워진다")
    void activateClearsReason() {
        User user = create(Role.DEALER);
        user.suspend("허위 매물을 반복 등록했습니다.");

        user.activate();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getSuspendReason()).isNull();
        assertThat(user.getRole()).isEqualTo(Role.DEALER);
    }

    @DisplayName("일반 회원과 딜러는 정지할 수 있다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Role.class, names = {"GENERAL", "DEALER"})
    void allowsSuspendingServiceUsers(Role role) {
        assertThatCode(() -> create(role).validateSuspendable()).doesNotThrowAnyException();
    }

    /*
     * 관리자를 막는 것이 곧 자기 자신을 정지할 수 없다는 보장이다. 이 경로는 /api/admin/** 뒤에
     * 있어 요청자가 언제나 관리자이므로, 관리자가 대상이 될 수 없으면 자기 자신도 될 수 없다.
     * 그래서 요청자와 대상을 따로 비교하지 않는다 — 막는 것은 이 판정 하나다
     */
    @DisplayName("관리자와 평가사는 정지할 수 없다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Role.class, names = {"ADMIN", "EVALUATOR"})
    void rejectsSuspendingNonServiceUsers(Role role) {
        assertThatThrownBy(() -> create(role).validateSuspendable())
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).errorCode())
                .isEqualTo(UserErrorCode.NOT_SUSPENDABLE_ROLE);
    }

    // 관리자 둘이 같은 목록을 열어 두고 차례로 눌렀을 때 뒤의 사유가 앞의 사유를 덮지 않아야 한다
    @Test
    @DisplayName("이미 정지된 회원은 다시 정지할 수 없다")
    void rejectsSuspendingTwice() {
        User user = create(Role.GENERAL);
        user.suspend("허위 매물을 반복 등록했습니다.");

        assertThatThrownBy(user::validateSuspendable)
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).errorCode())
                .isEqualTo(UserErrorCode.ALREADY_SUSPENDED);
    }

    @Test
    @DisplayName("정지되지 않은 회원은 해제할 수 없다")
    void rejectsActivatingActiveUser() {
        assertThatThrownBy(create(Role.GENERAL)::validateActivatable)
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).errorCode())
                .isEqualTo(UserErrorCode.ALREADY_ACTIVE);
    }

    private static User create(String realName) {
        return User.create("alice", "alice@race.com", "encoded", realName, "01011112222", Role.GENERAL);
    }

    private static User create(Role role) {
        return User.create("alice", "alice@race.com", "encoded", "김철수", "01011112222", role);
    }
}