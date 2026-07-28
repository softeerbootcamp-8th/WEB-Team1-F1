-- 시나리오 2 픽스처 : 고정 시각 20:45:12 에 완전 종료(CLOSED) 단계인 경매
-- 19:00 마감 + 결과 확인 5분 이 이미 지났다
-- id는 시나리오 1과 겹치지 않게 2번대를 쓴다

insert into users (id, email, password, nickname, phone, address, role, created_at, updated_at)
values (21, 'seller2@race.dev', 'pw', '최판매', '01000000021', '서울 서초구', 'GENERAL',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00'),
       (22, 'bidder3@race.dev', 'pw', '이준호', '01000000022', '서울 용산구', 'DEALER',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00');

insert into vehicle (id, seller_id, manufacturer, model, model_year, mileage, fuel_type, transmission,
                     plate_number, estimated_price, created_at, updated_at)
values (2, 21, 'KIA', '쏘렌토 MQ4', 2021, 62000, 'DIESEL', 'AUTOMATIC',
        '34나5678', 28000000, '2026-08-01 09:00:00', '2026-08-01 09:00:00');

insert into auction_post (id, vehicle_id, title, post_status, published_at, created_at, updated_at)
values (2, 2, '2021 쏘렌토 MQ4', 'PUBLISHED', '2026-08-01 09:00:00',
        '2026-08-01 09:00:00', '2026-08-01 09:00:00');

insert into auction (id, post_id, winner_id, start_price, current_price, room_open_at, start_time,
                     current_end_time, extension_count, status, price_updated_at, created_at, updated_at)
values (2, 2, 22, 20000000, 21000000, '2026-08-03 18:00:00', '2026-08-03 18:30:00',
        '2026-08-03 19:00:00', 0, 'ENDED', '2026-08-03 18:55:40', '2026-08-01 09:00:00',
        '2026-08-03 19:00:00');

insert into bid (id, auction_id, bidder_id, amount, created_at, updated_at)
values (11, 2, 22, 21000000, '2026-08-03 18:55:40', '2026-08-03 18:55:40');
