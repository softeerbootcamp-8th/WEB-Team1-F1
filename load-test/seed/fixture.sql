-- 측정용 경매 데이터. 회차마다 다시 넣는다.
--
-- 시각을 상수로 박지 않고 실행 시점(NOW()) 기준으로 계산한다. 상수로 박으면
-- 다음 회차에는 이미 마감된 경매가 되어 입찰이 전부 거절된다.
--
-- 계정은 여기서 만들지 않는다. 비밀번호 해시는 서버가 만들어야 로그인이 되므로
-- accounts.sh 가 API로 먼저 만든다. 이 파일은 그 계정 중 하나를 판매자로 쓴다.

-- 최저 상승가 구간표. main/resources 에 없어서 넣지 않으면 입찰이 전부 서버 오류가 된다.
delete from bid_increment_band;
insert into bid_increment_band (min_price, increment)
values (0, 10000),
       (5000000, 50000),
       (30000000, 100000),
       (60000000, 200000),
       (100000000, 500000);

-- 이전 회차 정리. 9000번대만 지운다.
delete from bid where auction_id >= 9000;
delete from auction where id >= 9000;
delete from auction_post where id >= 9000;
delete from vehicle where id >= 9000;

-- 판매자는 입찰자 풀 밖의 별도 계정이다. 판매자 본인의 입찰은 거절되므로,
-- 입찰에 쓰는 계정과 겹치면 그 VU 는 계속 400만 받는다.
-- 입찰자 수를 바꿔도 참조가 깨지지 않도록 번호가 아니라 이름으로 묶는다.
-- 이 계정이 없으면 fixture.sh 가 먼저 끊는다. 여기까지 오면 있다고 봐도 된다.
set @seller_id = (select id from users where username = 'load_seller');

-- 시나리오 A : 한 경매에 몰린다
insert into vehicle (id, seller_id, manufacturer, model, model_year, mileage, fuel_type, transmission,
                     plate_number, estimated_price, created_at, updated_at)
values (9001, @seller_id, 'GENESIS', 'G90 RS4', 2024, 12000, 'GASOLINE', 'AUTOMATIC',
        '90가9001', 100000000, now(), now());

insert into auction_post (id, vehicle_id, published_at, created_at, updated_at)
values (9001, 9001, now(), now(), now());

-- status 는 SCHEDULED 로 넣는다. 입찰 판정은 상태가 아니라 서버 시각을 보고,
-- 진행 스케줄러가 다음 틱에 IN_PROGRESS 로 옮긴다. 손으로 맞춰 넣으면
-- 스케줄러가 하는 일을 측정에서 빼는 셈이 된다.
--
-- 마감은 2시간 뒤다. 측정 시간보다 마감이 빠르면 중간에 대상이 사라진다.
insert into auction (id, post_id, start_price, current_price, room_open_at, start_time,
                     current_end_time, extension_count, status, created_at, updated_at)
values (9001, 9001, 100000000, null,
        date_sub(now(), interval 60 minute),
        date_sub(now(), interval 30 minute),
        date_add(now(), interval 120 minute),
        0, 'SCHEDULED', now(), now());

-- 시나리오 B : 같은 초에 마감시킨다
-- 마감 시각을 전부 같게 두고, 입찰을 미리 넣어 낙찰 대상이 있게 만든다.
-- 마감까지 남은 시간은 실행 준비 시간을 고려해 넉넉히 잡고, 실제 측정은
-- 그 시각에 맞춰 collect.sh 를 돌린다.
insert into vehicle (id, seller_id, manufacturer, model, model_year, mileage, fuel_type, transmission,
                     plate_number, estimated_price, created_at, updated_at)
select 9100 + seq, @seller_id, 'HYUNDAI', '그랜저 GN7', 2023, 30000, 'GASOLINE', 'AUTOMATIC',
       concat('91가', 9100 + seq), 100000000, now(), now()
from (select 1 as seq union all select 2 union all select 3 union all select 4 union all select 5) s;

insert into auction_post (id, vehicle_id, published_at, created_at, updated_at)
select 9100 + seq, 9100 + seq, now(), now(), now()
from (select 1 as seq union all select 2 union all select 3 union all select 4 union all select 5) s;

insert into auction (id, post_id, start_price, current_price, room_open_at, start_time,
                     current_end_time, extension_count, status, created_at, updated_at)
select 9100 + seq, 9100 + seq, 100000000, null,
       date_sub(now(), interval 60 minute),
       date_sub(now(), interval 30 minute),
       date_add(now(), interval 10 minute),
       0, 'SCHEDULED', now(), now()
from (select 1 as seq union all select 2 union all select 3 union all select 4 union all select 5) s;

select '시드 완료' as result,
       (select count(*) from auction where id >= 9000) as auctions,
       (select count(*) from users where username like 'load\_user\_%') as accounts;
