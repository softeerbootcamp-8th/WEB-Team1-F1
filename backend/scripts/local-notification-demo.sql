-- 로컬 알림·토스트 수동 테스트 데이터
--
-- 모든 계정 비밀번호: password123
-- seller1 / seller2: 판매자, eval1: 평가사, buyer1 / buyer2: 입찰자
-- 시세 조회: 김민수 / 12가3456
--
-- 기존 로컬 데이터를 모두 비우고 다시 넣는다. 운영 DB에는 실행하지 않는다.

set names utf8mb4;
set foreign_key_checks = 0;

truncate table notification;
truncate table user_session;
truncate table deal;
truncate table bid;
truncate table auction;
truncate table auction_post;
truncate table evaluation;
truncate table vehicle_keyword_tag;
truncate table vehicle_image;
truncate table vehicle;
truncate table vehicle_catalog;
truncate table bid_increment_band;
truncate table users;

set foreign_key_checks = 1;

set @now = now(6);
set @password = '$2a$10$YPQXv2KU1a3XSqz7XIRHK.x7LgtoTj3UMhvDWQcqLQBPkDvmRVzW.';

-- 로그인 계정
insert into users
    (id, username, email, password, real_name, phone, role, created_at, updated_at)
values
    (1, 'seller1', 'seller1@race.local', @password, '김민수', '01011110001', 'GENERAL', @now, @now),
    (2, 'seller2', 'seller2@race.local', @password, '이서연', '01022220002', 'GENERAL', @now, @now),
    (3, 'eval1', 'eval1@race.local', @password, '박평가', '01033330003', 'EVALUATOR', @now, @now),
    (4, 'buyer1', 'buyer1@race.local', @password, '최구매', '01044440004', 'DEALER', @now, @now),
    (5, 'buyer2', 'buyer2@race.local', @password, '정입찰', '01055550005', 'DEALER', @now, @now);

-- 시세 조회·방문평가에서 사용하는 차량 원장
insert into vehicle_catalog
    (id, plate_number, owner_name, manufacturer, model, model_year,
     fuel_type, transmission, base_price, main_image_url)
values
    (1, '12가3456', '김민수', 'HYUNDAI', '그랜저 IG', 2021,
     'GASOLINE', 'AUTOMATIC', 34000000, '/hero.png'),
    (2, '34나5678', '이서연', 'KIA', '쏘렌토', 2022,
     'DIESEL', 'AUTOMATIC', 40000000, '/hero.png'),
    (3, '56다7811', '김민수', 'KIA', 'K5', 2022,
     'GASOLINE', 'AUTOMATIC', 30000000, '/hero.png'),
    (4, '78라9812', '이서연', 'GENESIS', 'G70', 2023,
     'GASOLINE', 'AUTOMATIC', 52000000, '/hero.png');

-- 가격대별 최소 입찰 상승가
insert into bid_increment_band (id, min_price, increment)
values
    (1, 0, 10000),
    (2, 5000000, 50000),
    (3, 30000000, 100000),
    (4, 60000000, 200000),
    (5, 100000000, 500000);

-- 출품·거래·평가에 사용할 차량
insert into vehicle
    (id, seller_id, manufacturer, model, model_year, mileage, fuel_type, transmission,
     plate_number, estimated_price, main_photo_url, diagnostic_report_url, created_at, updated_at)
values
    -- seller1 진행 중 경매
    (1001, 1, 'KIA', 'K5', 2022, 38000, 'GASOLINE', 'AUTOMATIC',
     '56다7811', 24000000, '/hero.png', '/sample-diagnostic.pdf', @now, @now),
    -- seller2 시작 전 경매
    (1002, 2, 'GENESIS', 'G70', 2023, 21000, 'GASOLINE', 'AUTOMATIC',
     '78라9812', 42000000, '/hero.png', '/sample-diagnostic.pdf', @now, @now),
    -- seller1 종료 거래
    (1003, 1, 'HYUNDAI', '아반떼 CN7', 2021, 51000, 'GASOLINE', 'AUTOMATIC',
     '11가1003', 22000000, '/hero.png', '/sample-diagnostic.pdf', @now, @now),
    -- seller2 일정 확정 대기 거래
    (1004, 2, 'KIA', '쏘렌토', 2022, 32000, 'DIESEL', 'AUTOMATIC',
     '22나1004', 31000000, '/hero.png', '/sample-diagnostic.pdf', @now, @now),
    -- seller2 유찰 경매
    (1005, 2, 'BMW', '520i', 2020, 68000, 'GASOLINE', 'AUTOMATIC',
     '33다1005', 39000000, '/hero.png', '/sample-diagnostic.pdf', @now, @now),
    -- seller1 승인 완료, 아직 경매글 미등록
    (1006, 1, 'HYUNDAI', '그랜저 IG', 2021, 45000, 'GASOLINE', 'AUTOMATIC',
     '12가3456', 23200000, '/hero.png', '/sample-diagnostic.pdf', @now, @now),
    -- seller2 평가 처리 대기
    (1007, 2, 'KIA', '쏘렌토', 2022, null, 'DIESEL', 'AUTOMATIC',
     '34나5678', null, null, null, @now, @now);

-- 경매방·목록의 이미지와 키워드
insert into vehicle_image
    (id, vehicle_id, image_url, sort_order, created_at, updated_at)
values
    (1, 1001, '/hero.png', 1, @now, @now),
    (2, 1002, '/hero.png', 1, @now, @now),
    (3, 1003, '/hero.png', 1, @now, @now),
    (4, 1004, '/hero.png', 1, @now, @now),
    (5, 1005, '/hero.png', 1, @now, @now),
    (6, 1006, '/hero.png', 1, @now, @now);

insert into vehicle_keyword_tag
    (id, vehicle_id, keyword, created_at, updated_at)
values
    (1, 1001, 'ACCIDENT_FREE', @now, @now),
    (2, 1001, 'CLEAN_INTERIOR', @now, @now),
    (3, 1002, 'GOOD_TIRE', @now, @now),
    (4, 1003, 'NO_DAMAGE', @now, @now),
    (5, 1004, 'UNDERBODY_INTACT', @now, @now),
    (6, 1005, 'NO_LEAK', @now, @now),
    (7, 1006, 'ACCIDENT_FREE', @now, @now);

-- seller1은 승인 알림을 눌러 경매글을 직접 등록할 수 있다.
-- seller2의 신청은 eval1이 승인 또는 반려할 수 있다.
insert into evaluation
    (id, vehicle_id, evaluator_id, visit_date, visit_address, contact_phone,
     status, reject_reason, created_at, updated_at)
values
    (1001, 1006, 3, date_add(curdate(), interval 1 day), '서울특별시 강남구 테헤란로 1',
     '01011110001', 'APPROVED', null, @now, @now),
    (1002, 1007, 3, date_add(curdate(), interval 1 day), '서울특별시 마포구 월드컵로 2',
     '01022220002', 'REQUESTED', null, @now, @now);

insert into auction_post
    (id, vehicle_id, published_at, deleted_at, created_at, updated_at)
values
    (1001, 1001, date_sub(@now, interval 1 day), null, @now, @now),
    (1002, 1002, @now, null, @now, @now),
    (1003, 1003, date_sub(@now, interval 3 day), null, @now, @now),
    (1004, 1004, date_sub(@now, interval 3 day), null, @now, @now),
    (1005, 1005, date_sub(@now, interval 3 day), null, @now, @now);

-- 1001: 지금 바로 입찰 가능
-- 1002: 경매방이 아직 열리지 않은 예약 경매
-- 1003·1004: 거래 테스트용 낙찰 경매
-- 1005: 판매자 유찰 알림 확인용
insert into auction
    (id, post_id, winner_id, start_price, current_price, top_bidder_id,
     room_open_at, start_time, current_end_time, extension_count,
     status, price_updated_at, created_at, updated_at)
values
    (1001, 1001, null, 20000000, null, null,
     date_sub(@now, interval 35 minute), date_sub(@now, interval 5 minute),
     date_add(@now, interval 2 hour), 0, 'IN_PROGRESS', null, @now, @now),
    (1002, 1002, null, 40000000, null, null,
     date_add(@now, interval 360 minute), date_add(@now, interval 390 minute),
     date_add(@now, interval 410 minute), 0, 'SCHEDULED', null, @now, @now),
    (1003, 1003, 4, 22000000, 25000000, 4,
     date_sub(@now, interval 2 day), date_sub(@now, interval 2 day),
     date_sub(@now, interval 2 day), 0, 'ENDED', date_sub(@now, interval 2 day), @now, @now),
    (1004, 1004, 5, 30000000, 31000000, 5,
     date_sub(@now, interval 2 day), date_sub(@now, interval 2 day),
     date_sub(@now, interval 2 day), 0, 'ENDED', date_sub(@now, interval 2 day), @now, @now),
    (1005, 1005, null, 39000000, null, null,
     date_sub(@now, interval 2 day), date_sub(@now, interval 2 day),
     date_sub(@now, interval 2 day), 0, 'FAILED', null, @now, @now);

insert into bid
    (id, auction_id, bidder_id, amount, created_at, updated_at)
values
    (1001, 1003, 4, 25000000, date_sub(@now, interval 2 day), @now),
    (1002, 1004, 5, 31000000, date_sub(@now, interval 2 day), @now);

-- buyer1은 구매 확정 가능, buyer2는 인도 일정 확정 가능
insert into deal
    (id, auction_id, seller_id, buyer_id, status, final_price, status_changed_at,
     cancellation_reason, document_url, transport_at, transport_location,
     delivery_at, delivery_location, version, created_at, updated_at)
values
    (1001, 1003, 1, 4, 'BUYER_CONFIRM_PENDING', 25000000, @now,
     null, null, null, null, null, null, 0, @now, @now),
    (1002, 1004, 2, 5, 'BUYER_SCHEDULE_PENDING', 31000000, @now,
     null, '/sample-contract.pdf', date_add(@now, interval 1 day), '서울 성동구 성수이로 10',
     null, null, 0, @now, @now);

-- 로그인 직후 알림 벨에서 바로 볼 수 있는 서버 알림 예시
insert into notification
    (id, user_id, type, message, is_read, reference_id, created_at, updated_at)
values
    (1, 1, 'EVAL_APPROVED', '그랜저 IG 차량 평가가 승인되었습니다. 경매글을 등록해 주세요.', 0, 1001,
     date_sub(@now, interval 5 minute), @now),
    (2, 1, 'AUCTION_SOLD', '아반떼 CN7 차량이 25,000,000원에 낙찰되었습니다.', 1, 1003,
     date_sub(@now, interval 1 hour), @now),
    (3, 2, 'AUCTION_FAILED', '520i 경매가 입찰 없이 종료되었습니다.', 0, 1005,
     date_sub(@now, interval 30 minute), @now),
    (4, 4, 'AUCTION_WON', '아반떼 CN7 차량을 25,000,000원에 낙찰받았습니다.', 0, 1001,
     date_sub(@now, interval 1 hour), @now),
    (5, 5, 'DEAL_BUYER_SCHEDULE_REQUIRED', '판매자가 탁송 일정을 등록했습니다. 인도 일정을 정해 주세요.', 0, 1002,
     date_sub(@now, interval 20 minute), @now),
    (6, 5, 'AUCTION_ENDED', '아반떼 CN7 경매가 25,000,000원에 종료되었습니다.', 1, 1003,
     date_sub(@now, interval 1 hour), @now);
