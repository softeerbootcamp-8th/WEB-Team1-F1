package com.softeer.race.sell.application.dto.command;

/** 판매 신청 유스케이스의 입력 전부. 행위 주체인 sellerId도 입력의 일부라 여기 담는다 */
public record SellApplicationCommand(long sellerId, String plateNumber, int mileage) {
}
