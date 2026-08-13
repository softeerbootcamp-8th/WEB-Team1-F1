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

-- 로그인 세션은 여기서 심지 않는다, 테이블이 아니라 Redis 에 산다
-- 이 파일과 짝이 되는 세션은 SessionFixture 가 심는다
