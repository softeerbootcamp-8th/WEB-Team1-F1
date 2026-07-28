-- 감사 시각 통합테스트 픽스처 : 알림이 참조할 사용자 한 명
-- User 에 생성자가 아직 없어 SQL로 넣는다, 생성자가 들어오는 PR에서 자바로 옮긴다
-- id는 다른 테스트와 겹치지 않게 90번대를 쓴다

insert into users (id, email, password, nickname, phone, address, role, created_at, updated_at)
values (91, 'audit@race.dev', 'pw', '박감사', '01000000091', '서울 중구', 'GENERAL',
        '2026-07-01 10:00:00', '2026-07-01 10:00:00');
