-- 진단서 첨부·조회 통합테스트 픽스처
-- 평가는 API로 만들지 않고 여기서 심는다 — 진단서를 붙일 대상이 있어야 시작할 수 있고,
-- 방문견적 접수 흐름은 VisitQuoteIntegrationTest가 이미 검증한다
--
-- id는 600번대를 쓴다. 다른 픽스처가 1~3 · 11~12 · 21~22 · 41 · 50번대 · 70번대 · 80번대 ·
-- 90번대 · 100번대 · 200번대 · 300번대 · 400번대 · 500번대 · 771~772 · 1000을 쓰므로 겹치지 않는다
-- (500번대는 평가사 배정 픽스처가 쓴다)

-- 판매자(600), 평가사(601), 평가와 무관한 일반 회원(603)
--
-- 역할이 갈린 계정을 심는 것은 인가 때문이 아니다. 지금은 로그인만 확인하므로 셋 다 똑같이
-- 통과하고, 그 사실을 고정하는 것이 무관한 회원(603) 시나리오의 목적이다.
-- 인가가 들어오면 그 테스트가 먼저 깨지면서 이 픽스처가 그대로 인가 테스트의 재료가 된다
insert into users (id, username, email, password, real_name, phone, address, role, created_at, updated_at)
values (600, 'report_seller', 'report_seller@race.dev', 'pw',
        '김판매', '01000000600', '서울 성동구', 'GENERAL', NOW(6), NOW(6)),
       (601, 'report_eval', 'report_eval@race.dev', 'pw',
        '박평가', '01000000601', '서울 광진구', 'EVALUATOR', NOW(6), NOW(6)),
       (603, 'report_other', 'report_other@race.dev', 'pw',
        '이무관', '01000000603', '서울 용산구', 'GENERAL', NOW(6), NOW(6));

-- PK는 쿠키로 보낼 원문 토큰의 SHA-256 hex다
-- 만료 시각을 하드코딩하지 않는다, 그 날짜가 지나는 순간 전 시나리오가 401이 된다
insert into user_session (id, user_id, expires_at, created_at, updated_at)
values (sha2('report-seller-token', 256), 600, DATE_ADD(NOW(6), INTERVAL 1 HOUR), NOW(6), NOW(6)),
       (sha2('report-eval-token', 256), 601, DATE_ADD(NOW(6), INTERVAL 1 HOUR), NOW(6), NOW(6)),
       (sha2('report-other-token', 256), 603, DATE_ADD(NOW(6), INTERVAL 1 HOUR), NOW(6), NOW(6));

-- 진단 전 차량이라 주행거리와 예상 시세가 비어 있다(Vehicle.pendingDiagnosis가 만드는 상태)
insert into vehicle (id, seller_id, manufacturer, model, model_year, mileage, fuel_type, transmission,
                     plate_number, estimated_price, created_at, updated_at)
values (600, 600, 'HYUNDAI', '아반떼 CN7', 2022, NULL, 'GASOLINE', 'AUTOMATIC', '60가6000', NULL,
        NOW(6), NOW(6)),
       (601, 600, 'KIA', '쏘렌토 하이브리드', 2023, NULL, 'HYBRID', 'AUTOMATIC', '60나6001', NULL,
        NOW(6), NOW(6));

-- 600: 진행 중이고 아직 아무도 수락하지 않은 신청. 진단서를 붙일 대상이다
--      evaluator_id를 비워 두는 것은 배정이 별개 흐름이기 때문이다. 배정은 평가사가 대기 목록에서
--      수락할 때 일어나고(EvaluationAssignmentService), 진단서 첨부는 그것을 대신하지 않는다 —
--      첨부 후에도 이 칸이 비어 있는지를 통합 테스트가 확인한다
-- 601: 반려되어 끝난 신청. 여기에 진단서를 붙이면 409여야 한다
--      REJECTED로 만드는 공개 경로가 없어 SQL로만 심을 수 있고, 그래서 이 시나리오는 통합에만 있다
insert into evaluation (id, vehicle_id, evaluator_id, visit_date, visit_address, contact_phone,
                        status, reject_reason, created_at, updated_at)
values (600, 600, NULL, DATE_ADD(CURDATE(), INTERVAL 3 DAY), '서울 성동구 왕십리로 83',
        '01012345678', 'REQUESTED', NULL, NOW(6), NOW(6)),
       (601, 601, NULL, DATE_ADD(CURDATE(), INTERVAL 3 DAY), '서울 성동구 왕십리로 83',
        '01012345678', 'REJECTED', '차량 상태 확인 불가', NOW(6), NOW(6));

-- diagnostic_report는 일부러 심지 않는다
-- 첨부가 행을 만든다는 것이 요구사항이라, 픽스처에 없어야 "만들어졌다"를 증명할 수 있다
