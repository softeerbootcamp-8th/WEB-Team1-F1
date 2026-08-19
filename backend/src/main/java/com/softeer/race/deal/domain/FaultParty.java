package com.softeer.race.deal.domain;

/** 거래가 깨진 책임이 어느 쪽에 있는지, 대금을 보관하지 않아 기록과 화면 표시가 전부다 */
public enum FaultParty {
    BUYER,
    SELLER
}