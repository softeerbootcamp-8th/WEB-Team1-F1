-- 스트림 시나리오 픽스처 : 고정 시각 20:45:12 에 진행(LIVE) 단계인 경매
-- 통로 보관함은 컨텍스트에 살아 있는 싱글턴이라, 열어 둔 구독이 다른 테스트의 접속자 수에 섞이지 않게
-- id 는 다른 시나리오와 겹치지 않는 4번대를 쓴다. username 과 email 의 유일 제약도 같은 이유로 나눈다

insert into users (id, username, email, password, real_name, phone, address, role, created_at, updated_at)
values (4, 'seller4', 'seller4@race.dev', 'pw', '박판매', '01000000004', '서울 강남구', 'GENERAL',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00'),
       (41, 'bidder41', 'bidder41@race.dev', 'pw', '김민현', '01000000041', '서울 마포구', 'DEALER',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00');

insert into vehicle (id, seller_id, manufacturer, model, model_year, mileage, fuel_type, transmission,
                     plate_number, estimated_price, created_at, updated_at)
values (4, 4, 'HYUNDAI', '아반떼 CN7', 2022, 35000, 'GASOLINE', 'AUTOMATIC',
        '44가4444', 15000000, '2026-08-01 09:00:00', '2026-08-01 09:00:00');

insert into auction_post (id, vehicle_id, thumbnail_url, post_status, published_at, created_at, updated_at)
values (4, 4, 'https://cdn.race.dev/avante-4.jpg', 'PUBLISHED', '2026-08-01 09:00:00',
        '2026-08-01 09:00:00', '2026-08-01 09:00:00');

insert into auction (id, post_id, start_price, current_price, room_open_at, start_time, current_end_time,
                     extension_count, status, price_updated_at, created_at, updated_at)
values (4, 4, 10000000, 12500000, '2026-08-03 20:00:00', '2026-08-03 20:30:00', '2026-08-03 21:00:00',
        0, 'IN_PROGRESS', '2026-08-03 20:44:31', '2026-08-01 09:00:00', '2026-08-03 20:44:31');

insert into bid (id, auction_id, bidder_id, amount, created_at, updated_at)
values (41, 4, 41, 12500000, '2026-08-03 20:44:31', '2026-08-03 20:44:31');
