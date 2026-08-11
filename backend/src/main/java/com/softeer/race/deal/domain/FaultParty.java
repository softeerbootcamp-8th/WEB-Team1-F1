package com.softeer.race.deal.domain;

/** 거래가 깨진 책임이 어느 쪽에 있는지, 보증금 향방이 여기서 갈린다 */
public enum FaultParty {
    BUYER,
    SELLER
}