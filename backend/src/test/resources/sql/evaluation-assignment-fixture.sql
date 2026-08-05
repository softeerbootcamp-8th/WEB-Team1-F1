-- 평가사 배정 통합테스트 픽스처
--
-- 방문견적 신청 픽스처와 달리 vehicle 과 evaluation 을 직접 심는다. 여기서 확인하는 것은 "이미
-- 접수된 신청이 배정되는가"라서 신청이 미리 있어야 하고, 접수 API 로 만들면 평가가 끝난 상태나
-- 이미 배정된 상태를 만들 방법이 없다(상태를 바꾸는 API 가 아직 없다).
--
-- id 는 500번대를 쓴다. 다른 픽스처가 1~3 · 11~12 · 21~22 · 41 · 50번대 · 70번대 · 80번대 ·
-- 90번대 · 100번대 · 200번대 · 300번대 · 400번대 · 771~772 · 1000을 쓰고 그중 일부는
-- 롤백하지 않으므로, 같은 컨텍스트에 묶이면 중복 키로 깨진다.
--
-- Clock 을 고정하지 않는 테스트가 쓴다. 배정은 시각을 읽지 않으므로(주입된 Clock 이 없다)
-- 방문 날짜를 고정값으로 심어도 시나리오가 날짜에 따라 갈리지 않는다. 세션 만료만 실제 시각
-- 기준이라 NOW(6) 로 심는다.

-- 평가사 두 명. 한 신청을 두고 경합하는 상황을 만들려면 두 명이 필요하다
-- 비밀번호는 쓰지 않는다, 로그인 대신 세션을 직접 심기 때문이다
insert into users (id, username, email, password, real_name, phone, address, role, created_at, updated_at)
values (500, 'assign_kim', 'assign-kim@race.dev', 'pw',
        '김평가', '01000000500', '서울 성동구', 'EVALUATOR', NOW(6), NOW(6)),
       (501, 'assign_lee', 'assign-lee@race.dev', 'pw',
        '이평가', '01000000501', '서울 광진구', 'EVALUATOR', NOW(6), NOW(6));

-- 판매자. 인가가 아직 없어 이 계정으로도 목록 조회와 수락이 통과한다는 것을 이 계정으로 확인한다
-- (자기 차량의 신청을 스스로 수락하는 것까지 지금은 막히지 않는다)
insert into users (id, username, email, password, real_name, phone, address, role, created_at, updated_at)
values (502, 'assign_park', 'assign-park@race.dev', 'pw',
        '박판매', '01000000502', '서울 강남구', 'GENERAL', NOW(6), NOW(6));

-- PK 는 쿠키로 보낼 원문 토큰의 SHA-256 hex 다
-- 만료 시각을 하드코딩하지 않는다, 그 날짜가 지나는 순간 전 시나리오가 401 이 된다
insert into user_session (id, user_id, expires_at, created_at, updated_at)
values (sha2('assign-kim-raw-token', 256), 500, DATE_ADD(NOW(6), INTERVAL 1 HOUR), NOW(6), NOW(6)),
       (sha2('assign-lee-raw-token', 256), 501, DATE_ADD(NOW(6), INTERVAL 1 HOUR), NOW(6), NOW(6)),
       (sha2('assign-park-raw-token', 256), 502, DATE_ADD(NOW(6), INTERVAL 1 HOUR), NOW(6), NOW(6));

-- 진단 전 차량이라 mileage 와 estimated_price 가 비어 있다.
-- 그 값을 채우는 것이 평가사가 방문해서 할 일이고, Vehicle.pendingDiagnosis 가 만드는 상태다
insert into vehicle (id, seller_id, manufacturer, model, model_year, mileage, fuel_type, transmission,
                     plate_number, estimated_price, created_at, updated_at)
values (510, 502, 'HYUNDAI', '그랜저 IG', 2021, null, 'GASOLINE', 'AUTOMATIC',
        '12가3456', null, NOW(6), NOW(6)),
       (511, 502, 'KIA', '쏘렌토 MQ4', 2022, null, 'DIESEL', 'AUTOMATIC',
        '34나5678', null, NOW(6), NOW(6)),
       (512, 502, 'GENESIS', 'G80 RG3', 2023, null, 'GASOLINE', 'AUTOMATIC',
        '56다7890', null, NOW(6), NOW(6)),
       (513, 502, 'BMW', '520i', 2020, null, 'GASOLINE', 'AUTOMATIC',
        '78라9012', null, NOW(6), NOW(6));

-- 510 · 511 은 방문일이 같다. 한 평가사가 같은 날짜의 두 건을 모두 맡을 수 있는지 보는 짝이다 —
-- 건수 상한을 두지 않기로 한 결정을 이 짝이 고정한다
--
-- 512 는 방문일이 뒤라 목록에서 마지막에 온다. 정렬이 방문일 임박순인지 확인한다
--
-- 513 은 평가가 끝난 건이다. evaluator 가 채워져 있어 목록에 오르지 않아야 하고, 수락하면
-- ALREADY_ASSIGNED 가 아니라 NOT_ASSIGNABLE 이어야 한다. 상태를 바꿀 API 가 없어 이 상태는
-- 픽스처로만 만들 수 있고, 그래서 단위테스트가 아니라 여기서 검증한다
insert into evaluation (id, vehicle_id, evaluator_id, visit_date, visit_address, contact_phone,
                        status, reject_reason, created_at, updated_at)
values (520, 510, null, '2026-08-20', '서울 성동구 왕십리로 83', '01011112222',
        'REQUESTED', null, NOW(6), NOW(6)),
       (521, 511, null, '2026-08-20', '서울 광진구 능동로 120', '01033334444',
        'REQUESTED', null, NOW(6), NOW(6)),
       (522, 512, null, '2026-08-25', '서울 강남구 테헤란로 1', '01055556666',
        'REQUESTED', null, NOW(6), NOW(6)),
       (523, 513, 501, '2026-08-18', '서울 마포구 와우산로 94', '01077778888',
        'APPROVED', null, NOW(6), NOW(6));
