-- 진단서 첨부·조회 통합테스트 픽스처
-- 평가는 API로 만들지 않고 여기서 심는다 — 진단서를 붙일 대상이 있어야 시작할 수 있고,
-- 방문견적 접수 흐름은 VisitQuoteIntegrationTest가 이미 검증한다
--
-- id는 600번대를 쓴다. 다른 픽스처가 1~3 · 11~12 · 21~22 · 41 · 50번대 · 70번대 · 80번대 ·
-- 90번대 · 100번대 · 200번대 · 300번대 · 400번대 · 500번대 · 771~772 · 1000을 쓰므로 겹치지 않는다
-- (500번대는 평가사 배정 픽스처가 쓴다)

-- 판매자(600), 담당 평가사(601), 담당이 아닌 평가사(602), 평가와 무관한 일반 회원(603)
--
-- 602가 필요한 이유는 "평가사면 통과"와 "이 건의 담당이면 통과"를 갈라 보기 위해서다.
-- 평가사 계정 하나로는 두 규칙이 같은 결과를 내 어느 쪽이 지켜지는지 알 수 없다
insert into users (id, username, email, password, real_name, phone, role, created_at, updated_at)
values (600, 'report_seller', 'report_seller@race.dev', 'pw',
        '김판매', '01000000600', 'GENERAL', NOW(6), NOW(6)),
       (601, 'report_eval', 'report_eval@race.dev', 'pw',
        '박평가', '01000000601', 'EVALUATOR', NOW(6), NOW(6)),
       (602, 'report_eval2', 'report_eval2@race.dev', 'pw',
        '최평가', '01000000602', 'EVALUATOR', NOW(6), NOW(6)),
       (603, 'report_other', 'report_other@race.dev', 'pw',
        '이무관', '01000000603', 'GENERAL', NOW(6), NOW(6)),
-- 다른 판매자. 목록이 요청자 것만 돌려주는지 확인하려면 남의 신청이 하나는 있어야 한다
       (604, 'report_seller2', 'report_seller2@race.dev', 'pw',
        '정판매', '01000000604', 'GENERAL', NOW(6), NOW(6));

-- PK는 쿠키로 보낼 원문 토큰의 SHA-256 hex다
-- 만료 시각을 하드코딩하지 않는다, 그 날짜가 지나는 순간 전 시나리오가 401이 된다
insert into user_session (id, user_id, expires_at, created_at, updated_at)
values (sha2('report-seller-token', 256), 600, DATE_ADD(NOW(6), INTERVAL 1 HOUR), NOW(6), NOW(6)),
       (sha2('report-eval-token', 256), 601, DATE_ADD(NOW(6), INTERVAL 1 HOUR), NOW(6), NOW(6)),
       (sha2('report-eval2-token', 256), 602, DATE_ADD(NOW(6), INTERVAL 1 HOUR), NOW(6), NOW(6)),
       (sha2('report-other-token', 256), 603, DATE_ADD(NOW(6), INTERVAL 1 HOUR), NOW(6), NOW(6)),
       (sha2('report-seller2-token', 256), 604, DATE_ADD(NOW(6), INTERVAL 1 HOUR), NOW(6), NOW(6));

-- 진단 전 차량이라 주행거리와 예상 시세가 비어 있다(Vehicle.pendingDiagnosis가 만드는 상태)
insert into vehicle (id, seller_id, manufacturer, model, model_year, mileage, fuel_type, transmission,
                     plate_number, estimated_price, created_at, updated_at)
values (600, 600, 'HYUNDAI', '아반떼 CN7', 2022, NULL, 'GASOLINE', 'AUTOMATIC', '60가6000', NULL,
        NOW(6), NOW(6)),
       (601, 600, 'KIA', '쏘렌토 하이브리드', 2023, NULL, 'HYBRID', 'AUTOMATIC', '60나6001', NULL,
        NOW(6), NOW(6)),
       (602, 600, 'GENESIS', 'G80', 2021, NULL, 'GASOLINE', 'AUTOMATIC', '60다6002', NULL,
        NOW(6), NOW(6)),
       (604, 604, 'BMW', '520i', 2020, NULL, 'GASOLINE', 'AUTOMATIC', '60라6004', NULL,
        NOW(6), NOW(6));

-- 600: 601에게 배정된 진행 중 신청. 진단서를 붙일 주 대상이다
--      배정을 SQL로 심는다. 수락 API를 거쳐 만들면 이 테스트가 배정 흐름의 정상 동작에 얹히게
--      되는데, 여기서 확인하려는 것은 "배정된 사람만 붙일 수 있는가"라 그 전제는 주어져야 한다
-- 601: 반려되어 끝난 신청. 배정은 되어 있으므로 담당자 판정이 아니라 상태에서 걸려야 한다
--      반려 API 로 만들 수도 있지만 여기서 확인하려는 것은 "끝난 신청이 막히는가"라 그 전제는
--      주어져야 한다. 배정을 SQL 로 심는 것과 같은 이유다
-- 602: 아직 아무도 수락하지 않은 신청. 담당자가 없어 누구도 붙일 수 없다
insert into evaluation (id, vehicle_id, evaluator_id, visit_date, visit_address, contact_phone,
                        status, reject_reason, created_at, updated_at)
values (600, 600, 601, DATE_ADD(CURDATE(), INTERVAL 3 DAY), '서울 성동구 왕십리로 83',
        '01012345678', 'REQUESTED', NULL, NOW(6), NOW(6)),
       (601, 601, 601, DATE_ADD(CURDATE(), INTERVAL 3 DAY), '서울 성동구 왕십리로 83',
        '01012345678', 'REJECTED', '차량 상태 확인 불가', NOW(6), NOW(6)),
       (602, 602, NULL, DATE_ADD(CURDATE(), INTERVAL 3 DAY), '서울 성동구 왕십리로 83',
        '01012345678', 'REQUESTED', NULL, NOW(6), NOW(6)),
-- 604: 다른 판매자(604)의 신청. 600의 목록에 섞여 나오면 안 된다
       (604, 604, NULL, DATE_ADD(CURDATE(), INTERVAL 5 DAY), '서울 서초구 강남대로 1',
        '01012345678', 'REQUESTED', NULL, NOW(6), NOW(6));

-- 판매 신청이 카탈로그에서 복제해 넣는 제조사 홍보 이미지. 실물이 아니라 자리를 채우던 임시값이다
-- 결과 제출이 이걸 지우는지 확인해야 한다 — 남겨 두면 대표 이미지 규칙이 sortOrder 최솟값이라
-- 실물 사진을 등록해도 홍보 이미지가 계속 대표로 남고, 그 상태로 경매 썸네일이 만들어진다
insert into vehicle_image (id, vehicle_id, image_url, sort_order, created_at, updated_at)
values (600, 600, 'https://cdn.race.dev/vehicles/catalog.jpg', 1, NOW(6), NOW(6));

-- vehicle.diagnostic_report_url은 일부러 비워 둔다, 제출이 채운다는 것을 증명해야 한다
