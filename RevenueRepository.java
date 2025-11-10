package com.hungerexpress.revenue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RevenueRepository extends JpaRepository<RevenueEntity, Long> {
    
    Optional<RevenueEntity> findByOwnerIdAndRestaurantIdAndMonthYear(Long ownerId, Long restaurantId, String monthYear);
    
    List<RevenueEntity> findByOwnerIdOrderByMonthYearDesc(Long ownerId);
    
    List<RevenueEntity> findByRestaurantIdOrderByMonthYearDesc(Long restaurantId);
    
    @Query("SELECT r FROM RevenueEntity r WHERE r.ownerId = :ownerId AND r.monthYear >= :fromMonth AND r.monthYear <= :toMonth ORDER BY r.monthYear DESC")
    List<RevenueEntity> findByOwnerIdAndDateRange(@Param("ownerId") Long ownerId, @Param("fromMonth") String fromMonth, @Param("toMonth") String toMonth);
    
    @Query("SELECT SUM(r.totalRevenue) FROM RevenueEntity r WHERE r.ownerId = :ownerId")
    java.math.BigDecimal getTotalRevenueByOwnerId(@Param("ownerId") Long ownerId);
    
    @Query("SELECT SUM(r.netEarnings) FROM RevenueEntity r WHERE r.ownerId = :ownerId")
    java.math.BigDecimal getTotalNetEarningsByOwnerId(@Param("ownerId") Long ownerId);
}
