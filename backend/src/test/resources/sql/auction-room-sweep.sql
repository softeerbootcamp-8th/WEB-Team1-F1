-- 청소 시나리오 픽스처 : 고정 시각 20:45:12 에 진행(LIVE) 단계인 경매
-- 통로 보관함이 컨텍스트에 살아 있는 싱글턴이라 다른 시나리오가 열어 둔 구독이 접속자 수에 섞인다
-- id 는 5번대를 써서 이 시나리오만 쓰는 방을 만든다

insert into users (id, username, email, password, real_name, phone, address, role, created_at, updated_at)
values (5, 'seller5', 'seller5@race.dev', 'pw', '박판매', '01000000005', '서울 강남구', 'GENERAL',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00');

insert into vehicle (id, seller_id, manufacturer, model, model_year, mileage, fuel_type, transmission,
                     plate_number, estimated_price, created_at, updated_at)
values (5, 5, 'HYUNDAI', '아반떼 CN7', 2022, 35000, 'GASOLINE', 'AUTOMATIC',
        '55가5555', 15000000, '2026-08-01 09:00:00', '2026-08-01 09:00:00');

insert into auction_post (id, vehicle_id, thumbnail_url, post_status, published_at, created_at, updated_at)
values (5, 5, 'https://cdn.race.dev/avante-5.jpg', 'PUBLISHED', '2026-08-01 09:00:00',
        '2026-08-01 09:00:00', '2026-08-01 09:00:00');

insert into auction (id, post_id, start_price, current_price, room_open_at, start_time, current_end_time,
                     extension_count, status, price_updated_at, created_at, updated_at)
values (5, 5, 10000000, null, '2026-08-03 20:00:00', '2026-08-03 20:30:00', '2026-08-03 21:00:00',
        0, 'IN_PROGRESS', null, '2026-08-01 09:00:00', '2026-08-01 09:00:00');
