package com.ticketwave.ledger.repository;

import com.ticketwave.ledger.entity.LedgerEntryType;

import java.math.BigDecimal;

public interface LedgerAggregate {
    LedgerEntryType getEntryType();

    BigDecimal getTotal();

    long getCount();
}
