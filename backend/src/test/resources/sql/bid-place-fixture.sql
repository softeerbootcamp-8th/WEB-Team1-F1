-- 입찰 접수 통합테스트 픽스처
-- 고정 시각 2026-08-03 20:45:00 기준으로 짜여 있다
-- id는 다른 픽스처(1·2·3·8번대)와 겹치지 않게 5번대를 쓴다
-- username·email 에 유일 제약이 있고 같은 컨텍스트를 공유하므로 값도 겹치지 않게 나눈다

insert into users (id, username, email, password, real_name, phone, address, role, created_at, updated_at)
values (51, 'bid_seller', 'bid_seller@race.dev', 'pw', '박판매', '01000000051', '서울 강남구', 'GENERAL',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00'),
       (52, 'bid_alice', 'bid_alice@race.dev', 'pw', '김앨리스', '01000000052', '서울 마포구', 'DEALER',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00'),
       (53, 'bid_bob', 'bid_bob@race.dev', 'pw', '이밥', '01000000053', '서울 성동구', 'DEALER',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00');

-- 쿠키로 오갈 원문 토큰의 SHA-256 hex 가 PK 다, auth-session-fixture.sql 과 같은 방식이다
-- 만료 시각을 고정 시각 +45분으로 둔다, 갱신 임계값(15분)보다 멀어서 조회가 세션을 건드리지 않는다
insert into user_session (id, user_id, expires_at, created_at, updated_at)
values (sha2('token-seller', 256), 51, '2026-08-03 21:30:00',
        '2026-08-03 20:30:00', '2026-08-03 20:30:00'),
       (sha2('token-alice', 256), 52, '2026-08-03 21:30:00',
        '2026-08-03 20:30:00', '2026-08-03 20:30:00'),
       (sha2('token-bob', 256), 53, '2026-08-03 21:30:00',
        '2026-08-03 20:30:00', '2026-08-03 20:30:00'),
       -- 고정 시각보다 1분 과거, 만료 판정을 받는다
       (sha2('token-expired', 256), 52, '2026-08-03 20:44:00',
        '2026-08-03 20:14:00', '2026-08-03 20:14:00');

insert into vehicle (id, seller_id, manufacturer, model, model_year, mileage, fuel_type, transmission,
                     plate_number, estimated_price, created_at, updated_at)
values (51, 51, 'HYUNDAI', '그랜저 IG', 2021, 45000, 'GASOLINE', 'AUTOMATIC',
        '51가1111', 24800000, '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
       (52, 51, 'KIA', '쏘렌토 MQ4', 2021, 62000, 'DIESEL', 'AUTOMATIC',
        '52나2222', 28000000, '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
       (53, 51, 'GENESIS', 'G80 RG3', 2023, 21000, 'GASOLINE', 'AUTOMATIC',
        '53다3333', 45000000, '2026-08-01 09:00:00', '2026-08-01 09:00:00');

insert into auction_post (id, vehicle_id, thumbnail_url, post_status, published_at, created_at, updated_at)
values (51, 51, 'https://cdn.race.dev/51.jpg', 'PUBLISHED', '2026-08-01 09:00:00',
        '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
       (52, 52, 'https://cdn.race.dev/52.jpg', 'PUBLISHED', '2026-08-01 09:00:00',
        '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
       (53, 53, 'https://cdn.race.dev/53.jpg', 'PUBLISHED', '2026-08-01 09:00:00',
        '2026-08-01 09:00:00', '2026-08-01 09:00:00');

-- status 는 전부 SCHEDULED 로 둔다
-- 상태 전환 스케줄러가 없어 실제 운영도 이 상태이고, 입찰 판정이 status 가 아니라
-- 서버 시각을 본다는 것을 이 값으로 고정한다. status 로 판정하면 아래 51번도 거절된다
insert into auction (id, post_id, start_price, current_price, room_open_at, start_time,
                     current_end_time, extension_count, status, created_at, updated_at)
values
    -- 51 : 진행 중, 아직 입찰 없음. 첫 입찰 최소 금액은 시작가 그대로 24,800,000
    (51, 51, 24800000, null, '2026-08-03 20:00:00', '2026-08-03 20:30:00',
     '2026-08-03 21:00:00', 0, 'SCHEDULED', '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
    -- 52 : 아직 시작 전(대기). 방은 열렸지만 입찰은 거절돼야 한다
    (52, 52, 20000000, null, '2026-08-03 20:30:00', '2026-08-03 21:00:00',
     '2026-08-03 21:20:00', 0, 'SCHEDULED', '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
    -- 53 : 마감 10초 전. 소프트 클로즈 임계(30초) 안이라 입찰하면 마감이 밀린다
    (53, 53, 10000000, null, '2026-08-03 20:00:00', '2026-08-03 20:30:00',
     '2026-08-03 20:45:10', 0, 'SCHEDULED', '2026-08-01 09:00:00', '2026-08-01 09:00:00');
