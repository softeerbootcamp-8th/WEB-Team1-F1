-- 알림 조회·읽음 처리 통합테스트 픽스처 — 회원 둘과 내 세션만 심는다
-- 알림 자체는 픽스처에 넣지 않는다, 도메인 생성 경로로 만들어야 문구와 링크 규칙이 함께 검증된다
-- IntegrationTestSupport가 테스트마다 테이블을 비우므로 시나리오마다 이 스크립트가 새로 실행된다
-- id는 다른 픽스처(51 · 81 · 90 · 100번대 · 카탈로그 200번대)와 겹치지 않게 70번대를 쓴다

-- 비밀번호는 이 테스트에서 쓰지 않는다, 로그인 대신 세션을 직접 심기 때문이다
insert into users (id, username, email, password, real_name, phone, address, role, created_at, updated_at)
values (71, 'notif_me', 'notif-me@race.dev', 'pw',
        '김알림', '01000000071', '서울 마포구', 'GENERAL',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00'),
       (72, 'notif_other', 'notif-other@race.dev', 'pw',
        '박타인', '01000000072', '서울 용산구', 'DEALER',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00');

-- PK는 쿠키로 보낼 원문 토큰의 SHA-256 hex다
-- 만료 시각은 테스트가 고정한 2026-08-03 12:00:00 이후여야 한다
-- 남은 시간을 넉넉히 둬 슬라이딩 연장 임계(15분)에 걸리지 않게 한다, 걸리면 조회마다 UPDATE가 섞인다
-- 타인 계정의 세션은 심지 않는다, 그 사람으로 로그인해 볼 시나리오가 없다
insert into user_session (id, user_id, expires_at, created_at, updated_at)
values (sha2('notification-my-token', 256), 71, '2026-08-03 13:00:00',
        '2026-08-03 11:30:00', '2026-08-03 11:30:00');
