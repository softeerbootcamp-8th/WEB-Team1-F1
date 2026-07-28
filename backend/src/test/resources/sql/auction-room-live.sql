-- 시나리오 1 픽스처 : 고정 시각 20:45:12 에 진행(LIVE) 단계인 경매
-- 최근 호가 정렬이 id 역순이라 id 순서와 created_at 순서를 일치시킨다
-- id는 시나리오 2와 겹치지 않게 1번대를 쓴다

insert into users (id, email, password, nickname, phone, address, role, created_at, updated_at)
values (1, 'seller1@race.dev', 'pw', '박판매', '01000000001', '서울 강남구', 'GENERAL',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00'),
       (11, 'bidder1@race.dev', 'pw', '김민현', '01000000011', '서울 마포구', 'DEALER',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00'),
       (12, 'bidder2@race.dev', 'pw', '남궁민수', '01000000012', '서울 성동구', 'DEALER',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00');

insert into vehicle (id, seller_id, manufacturer, model, model_year, mileage, fuel_type, transmission,
                     plate_number, estimated_price, created_at, updated_at)
values (1, 1, 'HYUNDAI', '아반떼 CN7', 2022, 35000, 'GASOLINE', 'AUTOMATIC',
        '12가3456', 15000000, '2026-08-01 09:00:00', '2026-08-01 09:00:00');

insert into auction_post (id, vehicle_id, title, post_status, published_at, created_at, updated_at)
values (1, 1, '2022 아반떼 CN7 무사고', 'PUBLISHED', '2026-08-01 09:00:00',
        '2026-08-01 09:00:00', '2026-08-01 09:00:00');

insert into auction (id, post_id, start_price, current_price, room_open_at, start_time, current_end_time,
                     extension_count, status, price_updated_at, created_at, updated_at)
values (1, 1, 10000000, 12500000, '2026-08-03 20:00:00', '2026-08-03 20:30:00', '2026-08-03 21:00:00',
        0, 'IN_PROGRESS', '2026-08-03 20:44:31', '2026-08-01 09:00:00', '2026-08-03 20:44:31');

-- 세 건을 두 사람이 넣었다, 입찰자 수는 3이 아니라 2다
insert into bid (id, auction_id, bidder_id, amount, created_at, updated_at)
values (1, 1, 11, 11000000, '2026-08-03 20:40:05', '2026-08-03 20:40:05'),
       (2, 1, 12, 12000000, '2026-08-03 20:42:18', '2026-08-03 20:42:18'),
       (3, 1, 11, 12500000, '2026-08-03 20:44:31', '2026-08-03 20:44:31');
