-- 데모 차량 안내 픽스처
--
-- vehicle-catalog-fixture 를 늘려 쓰지 않고 따로 판다. 그쪽은 시세 조회·차량 조회가 함께 쓰는
-- 픽스처라 기준가와 감가 계산이 값으로 검증되고 있어, 상한 10을 넘기려고 차를 더 넣으면
-- 그 테스트들이 세는 건수와 고르는 차가 같이 흔들린다.
--
-- id 는 1100번대를 쓴다. 다른 픽스처가 1~3 · 11~12 · 21~22 · 41 · 50번대 · 70번대 · 80번대 ·
-- 90번대 · 100번대 · 200번대 · 300번대 · 400번대 · 500번대 · 600번대 · 700번대 · 900번대 ·
-- 771~772 · 1000을 쓰고 그중 일부는 롤백하지 않으므로, 같은 컨텍스트에 묶이면 중복 키로 깨진다.
--
-- Clock 을 고정하지 않는 테스트가 쓴다. 이 안내는 시각을 읽지 않는다 — 상한도 정렬도 시각과
-- 무관하고, 진행 중 판정은 상태로만 한다. 방문일은 심되 시나리오가 그 날짜에 갈리지 않는다.

-- 판매자 한 명. 진행 중 신청을 만들려면 vehicle.seller_id 가 가리킬 회원이 있어야 한다.
-- 세션은 심지 않는다 — 이 API 는 비로그인으로 호출되고, 그것이 시나리오 1이다
insert into users (id, username, email, password, real_name, phone, role, created_at, updated_at)
values (1100, 'demo_seller', 'demo-seller@race.dev', 'pw',
        '데모판매', '01000001100', 'GENERAL', NOW(6), NOW(6));

-- 데모 차량 원장 13대. 상한이 10이라 10대만으로는 "잘랐다"와 "이게 전부다"를 구별할 수 없고,
-- 진행 중 2대를 빼고도 11대가 남아야 자르는 동작이 드러난다
--
--   1101  진행 중(REQUESTED)  → 빠진다
--   1102  진행 중(APPROVED)   → 빠진다. 진단이 끝나도 출품 동의가 남아 흐름이 계속된다
--   1103  반려(REJECTED)      → 나온다. 반려된 차는 다시 신청할 수 있다
--   1104~1113  걸린 신청 없음 → 나온다
--
-- 남는 11대 중 앞의 10대(1103~1112)가 응답이고 1113은 잘린다. 1113이 목록의 꼬리를 확인하는
-- 유일한 차량이라, 여기서 대수를 줄이면 상한 시나리오가 통과해도 아무것도 증명하지 못한다
--
-- base_price 는 값이 검증되지 않는다. 응답에 실리지 않는 것이 도입 조건이라, 이 픽스처에서는
-- NOT NULL 을 채우는 역할만 한다. main_image_url 도 같은 이유로 전부 null 이다
insert into vehicle_catalog (id, plate_number, owner_name, manufacturer, model, model_year,
                             fuel_type, transmission, base_price, main_image_url)
values (1101, '11가1101', '진행중일', 'HYUNDAI', '아반떼 CN7', 2022,
        'GASOLINE', 'AUTOMATIC', 20000000, null),
       (1102, '11가1102', '진행중이', 'KIA', 'K5', 2021,
        'GASOLINE', 'AUTOMATIC', 25000000, null),
       (1103, '11가1103', '반려삼', 'HYUNDAI', '쏘나타 DN8', 2020,
        'GASOLINE', 'AUTOMATIC', 22000000, null),
       (1104, '11가1104', '가용사', 'KIA', '쏘렌토', 2022,
        'DIESEL', 'AUTOMATIC', 40000000, null),
       (1105, '11가1105', '가용오', 'GENESIS', 'G80', 2021,
        'GASOLINE', 'AUTOMATIC', 60000000, null),
       (1106, '11가1106', '가용육', 'BMW', '520i', 2020,
        'GASOLINE', 'AUTOMATIC', 68000000, null),
       (1107, '11가1107', '가용칠', 'HYUNDAI', '그랜저 IG', 2019,
        'GASOLINE', 'AUTOMATIC', 34000000, null),
       (1108, '11가1108', '가용팔', 'KIA', '카니발', 2023,
        'DIESEL', 'AUTOMATIC', 45000000, null),
       (1109, '11가1109', '가용구', 'HYUNDAI', '투싼 NX4', 2022,
        'HYBRID', 'AUTOMATIC', 33000000, null),
       (1110, '11가1110', '가용십', 'MERCEDES_BENZ', 'E250', 2021,
        'GASOLINE', 'AUTOMATIC', 72000000, null),
       (1111, '11가1111', '가용십일', 'KIA', '모닝', 2018,
        'GASOLINE', 'MANUAL', 14000000, null),
       (1112, '11가1112', '가용십이', 'HYUNDAI', '팰리세이드', 2023,
        'DIESEL', 'AUTOMATIC', 52000000, null),
       (1113, '11가1113', '잘린십삼', 'GENESIS', 'GV70', 2022,
        'GASOLINE', 'AUTOMATIC', 65000000, null);

-- 진행 중 판정이 걸릴 차량들. vehicle 은 신청마다 새로 생기는 행이고 카탈로그와는 번호판으로만
-- 이어진다 — 그래서 여기 심는 번호판이 위 카탈로그와 정확히 같아야 판정이 걸린다
insert into vehicle (id, seller_id, manufacturer, model, model_year, mileage, fuel_type, transmission,
                     plate_number, estimated_price, created_at, updated_at)
values (1120, 1100, 'HYUNDAI', '아반떼 CN7', 2022, null, 'GASOLINE', 'AUTOMATIC',
        '11가1101', null, NOW(6), NOW(6)),
       (1121, 1100, 'KIA', 'K5', 2021, 32000, 'GASOLINE', 'AUTOMATIC',
        '11가1102', 21000000, NOW(6), NOW(6)),
       (1122, 1100, 'HYUNDAI', '쏘나타 DN8', 2020, null, 'GASOLINE', 'AUTOMATIC',
        '11가1103', null, NOW(6), NOW(6));

-- 세 상태를 모두 심는다. REQUESTED 만으로는 "진행 중이면 빠진다"까지만 보이고, 안내가 실제로
-- 막아야 하는 것은 APPROVED 다 — 진단이 끝난 차를 안내하면 넣는 순간 중복으로 거절된다.
-- REJECTED 는 반대로 빠지면 안 되는 쪽이라, 셋이 한 벌이어야 판정이 상태 집합과 일치하는지 보인다
insert into evaluation (id, vehicle_id, evaluator_id, visit_date, visit_address, contact_phone,
                        status, reject_reason, created_at, updated_at)
values (1130, 1120, null, '2026-08-20', '서울 성동구 왕십리로 83', '01011111101',
        'REQUESTED', null, NOW(6), NOW(6)),
       (1131, 1121, null, '2026-08-21', '서울 광진구 능동로 120', '01011111102',
        'APPROVED', null, NOW(6), NOW(6)),
       (1132, 1122, null, '2026-08-22', '서울 강남구 테헤란로 1', '01011111103',
        'REJECTED', '차량 상태가 매입 기준에 미달합니다', NOW(6), NOW(6));
