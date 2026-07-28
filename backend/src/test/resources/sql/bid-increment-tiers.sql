-- 최저 입찰 상승가 구간표 시드 (확정 5구간, 하한 오름차순)
-- 값과 순서가 곧 FE 계약이라 실제 확정값 그대로 넣는다
-- @SpringBootTest 는 메서드마다 롤백하지 않아 클래스 레벨 @Sql 이 매번 5행을 더 넣는다
-- 매 테스트가 같은 상태에서 시작하도록 먼저 비우고 넣는다
delete from bid_increment_tier;
insert into bid_increment_tier (min_price, increment) values
    (0,         10000),
    (5000000,   50000),
    (30000000,  100000),
    (60000000,  200000),
    (100000000, 500000);
