-- 평가 결과 항목별 수정 통합테스트 픽스처
--
-- 여기서는 <이미 결과가 제출된 상태>가 출발점이라 그 상태를 SQL로 심는다. 제출 API를 먼저
-- 호출해 만들면 이 테스트가 제출 흐름의 정상 동작에 얹히게 되는데, 확인하려는 것은 "그 뒤에
-- 한 항목만 고칠 수 있는가"라 그 전제는 주어져야 한다(진단서 픽스처가 배정을 심는 것과 같은 이유).
--
-- id는 700번대를 쓴다. 다른 픽스처가 1~3 · 11~12 · 21~22 · 41 · 50번대 · 70번대 · 80번대 ·
-- 90번대 · 100번대 · 200번대 · 300번대 · 400번대 · 500번대 · 600번대 · 771~772 · 1000을 쓰므로
-- 겹치지 않는다(771~772를 피해 700~760만 쓴다)

-- 판매자(700), 담당 평가사(701), 담당이 아닌 평가사(702)
insert into users (id, username, email, password, real_name, phone, role, created_at, updated_at)
values (700, 'patch_seller', 'patch_seller@race.dev', 'pw',
        '김판매', '01000000700', 'GENERAL', NOW(6), NOW(6)),
       (701, 'patch_eval', 'patch_eval@race.dev', 'pw',
        '박평가', '01000000701', 'EVALUATOR', NOW(6), NOW(6)),
       (702, 'patch_eval2', 'patch_eval2@race.dev', 'pw',
        '최평가', '01000000702', 'EVALUATOR', NOW(6), NOW(6));

-- 로그인 세션은 여기서 심지 않는다, 테이블이 아니라 Redis 에 산다
-- 이 파일과 짝이 되는 세션은 SessionFixture 가 심는다

-- 700: 결과가 이미 제출된 차량. 네 칸(주행거리·시세·대표 사진·진단서)이 모두 채워져 있다
-- 701: 아직 진단 전인 차량. 주행거리와 시세가 비어 있어 항목별 수정이 거부돼야 한다
insert into vehicle (id, seller_id, manufacturer, model, model_year, mileage, fuel_type, transmission,
                     plate_number, estimated_price, main_photo_url, diagnostic_report_url,
                     created_at, updated_at)
values (700, 700, 'HYUNDAI', '아반떼 CN7', 2022, 45000, 'GASOLINE', 'AUTOMATIC', '70가7000', 21500000,
        'https://cdn.test.local/images/2026/08/11111111-0d47-4a19-9b2f-6c1d5e7a8b90.jpg',
        'https://cdn.test.local/documents/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf',
        NOW(6), NOW(6)),
       (701, 700, 'KIA', '쏘렌토 하이브리드', 2023, NULL, 'HYBRID', 'AUTOMATIC', '70나7001', NULL,
        NULL, NULL, NOW(6), NOW(6));

-- 700: 결과 제출까지 끝나 APPROVED인 신청. 항목별 수정의 주 대상이다
-- 701: 배정만 받고 아직 결과를 내지 않은 신청. 상태가 아니라 차량이 비었다는 것에서 걸려야 한다
insert into evaluation (id, vehicle_id, evaluator_id, visit_date, visit_address, contact_phone,
                        status, reject_reason, created_at, updated_at)
values (700, 700, 701, DATE_ADD(CURDATE(), INTERVAL 3 DAY), '서울 성동구 왕십리로 83',
        '01012345678', 'APPROVED', NULL, NOW(6), NOW(6)),
       (701, 701, 701, DATE_ADD(CURDATE(), INTERVAL 3 DAY), '서울 성동구 왕십리로 83',
        '01012345678', 'REQUESTED', NULL, NOW(6), NOW(6));

-- 제출된 사진 두 장. 첫 장(sort_order = 1)이 vehicle.main_photo_url과 같아야 한다 —
-- 수정 뒤에도 그 관계가 유지되는지가 이 픽스처로 확인할 것 중 하나다
insert into vehicle_image (id, vehicle_id, image_url, sort_order, created_at, updated_at)
values (700, 700, 'https://cdn.test.local/images/2026/08/11111111-0d47-4a19-9b2f-6c1d5e7a8b90.jpg',
        1, NOW(6), NOW(6)),
       (701, 700, 'https://cdn.test.local/images/2026/08/22222222-0d47-4a19-9b2f-6c1d5e7a8b90.jpg',
        2, NOW(6), NOW(6));

-- 제출 때 매긴 키워드. 사진만 고치는 요청이 이걸 지우지 않는지 확인한다
insert into vehicle_keyword_tag (id, vehicle_id, keyword, created_at, updated_at)
values (700, 700, 'ACCIDENT_FREE', NOW(6), NOW(6)),
       (701, 700, 'UNDERBODY_INTACT', NOW(6), NOW(6));
