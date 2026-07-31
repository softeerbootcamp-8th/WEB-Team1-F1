-- 판매 신청 통합테스트 픽스처 — 판매자 계정과 세션만 심는다
-- 차량 카탈로그는 vehicle-catalog-fixture.sql이 심고, 테스트가 두 스크립트를 함께 실행한다
-- IntegrationTestSupport가 테스트마다 테이블을 비우므로 시나리오마다 이 스크립트가 새로 실행된다
-- id는 다른 픽스처(1 · 81 · 100번대 · 카탈로그 200번대)와 겹치지 않게 90번대를 쓴다

-- 비밀번호는 이 테스트에서 쓰지 않는다, 로그인 대신 세션을 직접 심기 때문이다
insert into users (id, username, email, password, real_name, phone, address, role, created_at, updated_at)
values (90, 'sell_kim', 'sell@race.dev', 'pw',
        '김판매', '01000000090', '서울 성동구', 'GENERAL',
        NOW(6), NOW(6));

-- PK는 쿠키로 보낼 원문 토큰의 SHA-256 hex다
-- 이 테스트는 시각 이중 읽기 회귀를 잡으려고 Clock을 고정하지 않고 실제 시스템 시각을 쓴다
-- 그래서 만료 시각도 하드코딩하면 안 된다, 그 날짜가 지나는 순간 전 시나리오가 401이 된다
insert into user_session (id, user_id, expires_at, created_at, updated_at)
values (sha2('sell-raw-token', 256), 90, DATE_ADD(NOW(6), INTERVAL 1 HOUR), NOW(6), NOW(6));

-- vehicle과 vehicle_image는 일부러 심지 않는다
-- 차량을 API가 만든다는 것이 요구사항의 핵심이라, 픽스처에 없어야 "만들어졌다"를 증명할 수 있다
