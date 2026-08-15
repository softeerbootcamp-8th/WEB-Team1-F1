package com.softeer.race.user.domain;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.user.exception.UserErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
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

    private static User create(String realName) {
        return User.create("alice", "alice@race.com", "encoded", realName, "01011112222", Role.GENERAL);
    }
}