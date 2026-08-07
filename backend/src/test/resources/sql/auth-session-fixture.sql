-- 세션 로그인 통합테스트 픽스처
-- IntegrationTestSupport가 테스트마다 테이블을 비우므로 시나리오마다 이 스크립트가 새로 실행된다
-- id는 다른 픽스처와 겹치지 않게 80번대를 쓴다

-- 비밀번호 원문은 'password123', 실제 bcrypt(cost 10) 해시를 그대로 넣는다
-- 로그인이 검증 대상이라 픽스처가 인코딩 경로를 타지 않아야 한다
insert into users (id, username, email, password, real_name, phone, role, created_at, updated_at)
values (81, 'auth_kim', 'auth@race.dev',
        '$2a$10$YPQXv2KU1a3XSqz7XIRHK.x7LgtoTj3UMhvDWQcqLQBPkDvmRVzW.',
        '김레이스', '01000000081', 'GENERAL',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00');

-- PK는 쿠키로 오갈 원문 토큰의 SHA-256 hex다
-- 테스트가 원문을 쿠키에 실어 보내므로 여기서는 MySQL의 sha2로 같은 값을 만든다
-- expires_at은 테스트가 고정한 시각 2026-07-30 12:00:00 기준으로 잡는다
insert into user_session (id, user_id, expires_at, created_at, updated_at)
values
    -- 이미 만료된 세션 : 고정 시각보다 1분 과거
    (sha2('expired-raw-token', 256), 81, '2026-07-30 11:59:00',
     '2026-07-30 11:29:00', '2026-07-30 11:29:00'),
    -- 남은 시간 10분, 임계값 15분 이하라 조회하면 연장된다
    (sha2('renewable-raw-token', 256), 81, '2026-07-30 12:10:00',
     '2026-07-30 11:40:00', '2026-07-30 11:40:00'),
    -- 남은 시간 25분, 임계값보다 많아 조회해도 그대로다
    (sha2('fresh-raw-token', 256), 81, '2026-07-30 12:25:00',
     '2026-07-30 11:55:00', '2026-07-30 11:55:00');
