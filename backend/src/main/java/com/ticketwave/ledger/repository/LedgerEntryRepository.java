package com.ticketwave.ledger.repository;

import com.ticketwave.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /**
     * One grouped SUM+COUNT per entry type for the reconciliation report,
     * instead of loading every entry in the range into memory to sum in
     * Java — a date range on a busy ledger could otherwise be a very large
     * result set.
     */
    @Query("""
            SELECT le.entryType AS entryType, COALESCE(SUM(le.amount), 0) AS total, COUNT(le) AS count
            FROM LedgerEntry le
            WHERE le.recordedAt >= :from AND le.recordedAt < :to
            GROUP BY le.entryType
            """)
    List<LedgerAggregate> aggregateBetween(@Param("from") Instant from, @Param("to") Instant to);
}
