-- 방문견적 신청 통합테스트 픽스처 — 판매자 계정만 심는다
-- 차량 카탈로그는 vehicle-catalog-fixture.sql이 심고, 테스트가 두 스크립트를 함께 실행한다
-- IntegrationTestSupport가 테스트마다 테이블을 비우므로 시나리오마다 이 스크립트가 새로 실행된다
--
-- id는 400번대를 쓴다. 다른 픽스처가 1~3 · 11~12 · 21~22 · 41 · 50번대 · 70번대 · 80번대 ·
-- 90번대 · 100번대 · 200번대 · 300번대 · 771~772 · 1000을 쓰고 그중 일부는 롤백하지 않으므로,
-- 같은 컨텍스트에 묶이면 중복 키로 깨진다

-- 비밀번호는 이 테스트에서 쓰지 않는다, 로그인 대신 세션을 직접 심기 때문이다(SessionFixture)
insert into users (id, username, email, password, real_name, phone, role, created_at, updated_at)
values (400, 'visit_kim', 'visit@race.dev', 'pw',
        '김방문', '01000000400', 'GENERAL',
        NOW(6), NOW(6));

-- 같은 차량을 다른 회원이 신청해도 막히는지 확인할 두 번째 계정
insert into users (id, username, email, password, real_name, phone, role, created_at, updated_at)
values (401, 'visit_lee', 'visit2@race.dev', 'pw',
        '이방문', '01000000401', 'GENERAL',
        NOW(6), NOW(6));

-- 반려 후 재신청 시나리오가 쓸 평가사. 반려는 배정된 평가사만 할 수 있어, 신청을 실제로
-- 반려 상태까지 끌고 가려면 수락할 사람이 필요하다
insert into users (id, username, email, password, real_name, phone, role, created_at, updated_at)
values (402, 'visit_eval', 'visit-eval@race.dev', 'pw',
        '박평가', '01000000402', 'EVALUATOR',
        NOW(6), NOW(6));

-- 로그인 세션은 여기서 심지 않는다, 테이블이 아니라 Redis 에 산다
-- 이 파일과 짝이 되는 세션은 SessionFixture 가 심는다

-- vehicle과 evaluation은 일부러 심지 않는다
-- 차량과 신청을 API가 만든다는 것이 요구사항의 핵심이라, 픽스처에 없어야 "만들어졌다"를 증명할 수 있다
