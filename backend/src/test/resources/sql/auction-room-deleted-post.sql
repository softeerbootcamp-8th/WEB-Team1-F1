-- 시나리오 3 픽스처 : 소프트 삭제된 경매글에 붙은 진행(LIVE) 단계 경매
-- 진행 중으로 둔 이유는 삭제 조건이 빠지면 200 LIVE 가 나가 404 단정이 깨지게 하기 위해서다
-- id는 다른 시나리오와 겹치지 않게 3번대를 쓴다

insert into users (id, username, email, password, real_name, phone, address, role, created_at, updated_at)
values (31, 'seller3', 'seller3@race.dev', 'pw', '정판매', '01000000031', '서울 종로구', 'GENERAL',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00');

insert into vehicle (id, seller_id, manufacturer, model, model_year, mileage, fuel_type, transmission,
                     plate_number, estimated_price, created_at, updated_at)
values (3, 31, 'GENESIS', 'G80 RG3', 2023, 21000, 'GASOLINE', 'AUTOMATIC',
        '56다7890', 45000000, '2026-08-01 09:00:00', '2026-08-01 09:00:00');

insert into auction_post (id, vehicle_id, thumbnail_url, post_status, published_at, deleted_at,
                          created_at, updated_at)
values (3, 3, 'https://cdn.race.dev/g80-3.jpg', 'PUBLISHED', '2026-08-01 09:00:00', '2026-08-03 20:40:00',
        '2026-08-01 09:00:00', '2026-08-03 20:40:00');

insert into auction (id, post_id, start_price, room_open_at, start_time, current_end_time,
                     extension_count, status, created_at, updated_at)
values (3, 3, 40000000, '2026-08-03 20:00:00', '2026-08-03 20:30:00', '2026-08-03 21:00:00',
        0, 'IN_PROGRESS', '2026-08-01 09:00:00', '2026-08-01 09:00:00');
