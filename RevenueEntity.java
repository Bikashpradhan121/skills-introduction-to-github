package com.hungerexpress.revenue;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@Entity
@Table(name = "revenue", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "restaurant_id", "month_year"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RevenueEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
    
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;
    
    @Column(name = "month_year", nullable = false, length = 7) // Format: 2025-01
    private String monthYear;
    
    @Column(name = "total_orders", nullable = false)
    @Builder.Default
    private Integer totalOrders = 0;
    
    @Column(name = "total_revenue", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    
    @Column(name = "platform_fee", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal platformFee = BigDecimal.ZERO;
    
    @Column(name = "net_earnings", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal netEarnings = BigDecimal.ZERO;
    
    @Column(name = "last_updated")
    private java.time.Instant lastUpdated;
    
    // Helper method to get current month year
    public static String getCurrentMonthYear() {
        return YearMonth.now().toString();
    }
    
    // Helper method to parse month year to LocalDate
    public LocalDate getMonthAsDate() {
        return YearMonth.parse(this.monthYear).atDay(1);
    }
}
