package com.softeer.race.auth.presentation.annotation;

import com.softeer.race.user.domain.Role;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 지정한 역할 중 하나를 가진 인증 주체만 핸들러를 호출할 수 있다. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    Role[] value();
}
