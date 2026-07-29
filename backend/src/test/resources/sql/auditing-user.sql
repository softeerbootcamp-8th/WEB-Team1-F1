-- 감사 시각 통합테스트 픽스처 : 알림이 참조할 사용자 한 명
-- 감사가 검증 대상이라 픽스처는 저장 경로를 타지 않아야 해서 SQL로 넣는다
-- id는 다른 테스트와 겹치지 않게 90번대를 쓴다

insert into users (id, username, email, password, real_name, phone, address, role, created_at, updated_at)
values (91, 'audit', 'audit@race.dev', 'pw', '박감사', '01000000091', '서울 중구', 'GENERAL',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00');
