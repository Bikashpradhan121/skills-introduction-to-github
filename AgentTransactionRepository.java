package com.hungerexpress.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for agent transaction records
 */
@Repository
public interface AgentTransactionRepository extends JpaRepository<AgentTransaction, Long> {
    
    /**
     * Find all transactions for a specific agent
     */
    List<AgentTransaction> findByAgentIdOrderByDeliveredAtDesc(Long agentId);
    
    /**
     * Find transactions for an agent within a date range
     */
    @Query("SELECT t FROM AgentTransaction t WHERE t.agentId = :agentId " +
           "AND t.deliveredAt >= :startDate AND t.deliveredAt <= :endDate " +
           "ORDER BY t.deliveredAt DESC")
    List<AgentTransaction> findByAgentIdAndDateRange(
        @Param("agentId") Long agentId,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );
    
    /**
     * Find today's transactions for an agent
     */
    @Query("SELECT t FROM AgentTransaction t WHERE t.agentId = :agentId " +
           "AND t.deliveredAt >= :todayStart " +
           "ORDER BY t.deliveredAt DESC")
    List<AgentTransaction> findTodayTransactions(
        @Param("agentId") Long agentId,
        @Param("todayStart") Instant todayStart
    );
    
    /**
     * Count deliveries by agent
     */
    long countByAgentId(Long agentId);
    
    /**
     * Count deliveries by agent in date range
     */
    @Query("SELECT COUNT(t) FROM AgentTransaction t WHERE t.agentId = :agentId " +
           "AND t.deliveredAt >= :startDate AND t.deliveredAt <= :endDate")
    long countByAgentIdAndDateRange(
        @Param("agentId") Long agentId,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );
}
