-- 방문견적 신청 통합테스트 픽스처 — 판매자 계정과 세션만 심는다
-- 차량 카탈로그는 vehicle-catalog-fixture.sql이 심고, 테스트가 두 스크립트를 함께 실행한다
-- IntegrationTestSupport가 테스트마다 테이블을 비우므로 시나리오마다 이 스크립트가 새로 실행된다
--
-- id는 400번대를 쓴다. 다른 픽스처가 1~3 · 11~12 · 21~22 · 41 · 50번대 · 70번대 · 80번대 ·
-- 90번대 · 100번대 · 200번대 · 300번대 · 771~772 · 1000을 쓰고 그중 일부는 롤백하지 않으므로,
-- 같은 컨텍스트에 묶이면 중복 키로 깨진다

-- 비밀번호는 이 테스트에서 쓰지 않는다, 로그인 대신 세션을 직접 심기 때문이다
insert into users (id, username, email, password, real_name, phone, address, role, created_at, updated_at)
values (400, 'visit_kim', 'visit@race.dev', 'pw',
        '김방문', '01000000400', '서울 성동구', 'GENERAL',
        NOW(6), NOW(6));

-- 같은 차량을 다른 회원이 신청해도 막히는지 확인할 두 번째 계정
insert into users (id, username, email, password, real_name, phone, address, role, created_at, updated_at)
values (401, 'visit_lee', 'visit2@race.dev', 'pw',
        '이방문', '01000000401', '서울 광진구', 'GENERAL',
        NOW(6), NOW(6));

-- PK는 쿠키로 보낼 원문 토큰의 SHA-256 hex다
-- 만료 시각을 하드코딩하지 않는다, 그 날짜가 지나는 순간 전 시나리오가 401이 된다
insert into user_session (id, user_id, expires_at, created_at, updated_at)
values (sha2('visit-quote-raw-token', 256), 400, DATE_ADD(NOW(6), INTERVAL 1 HOUR), NOW(6), NOW(6)),
       (sha2('visit-quote-other-raw-token', 256), 401, DATE_ADD(NOW(6), INTERVAL 1 HOUR), NOW(6), NOW(6));

-- vehicle과 evaluation은 일부러 심지 않는다
-- 차량과 신청을 API가 만든다는 것이 요구사항의 핵심이라, 픽스처에 없어야 "만들어졌다"를 증명할 수 있다
