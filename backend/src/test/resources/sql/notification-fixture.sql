-- 알림 조회·읽음 처리 통합테스트 픽스처 — 회원 둘만 심는다
-- 알림 자체는 픽스처에 넣지 않는다, 도메인 생성 경로로 만들어야 문구와 링크 규칙이 함께 검증된다
-- IntegrationTestSupport가 테스트마다 테이블을 비우므로 시나리오마다 이 스크립트가 새로 실행된다
-- id는 다른 픽스처(51 · 81 · 90 · 100번대 · 카탈로그 200번대)와 겹치지 않게 70번대를 쓴다

-- 비밀번호는 이 테스트에서 쓰지 않는다, 로그인 대신 세션을 직접 심기 때문이다(SessionFixture)
insert into users (id, username, email, password, real_name, phone, role, created_at, updated_at)
values (71, 'notif_me', 'notif-me@race.dev', 'pw',
        '김알림', '01000000071', 'GENERAL',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00'),
       (72, 'notif_other', 'notif-other@race.dev', 'pw',
        '박타인', '01000000072', 'DEALER',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00');

-- 로그인 세션은 여기서 심지 않는다, 테이블이 아니라 Redis 에 산다
-- 이 파일과 짝이 되는 세션은 SessionFixture 가 심는다
