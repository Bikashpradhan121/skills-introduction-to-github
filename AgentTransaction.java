package com.hungerexpress.agent;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity to track individual agent delivery transactions
 * Records each delivery for earning history and reports
 */
@Entity
@Table(name = "agent_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "agent_id", nullable = false)
    private Long agentId; // user_id of the agent
    
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    
    @Column(name = "order_number", length = 50)
    private String orderNumber;
    
    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee;
    
    @Column(name = "bonus", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal bonus = BigDecimal.ZERO;
    
    @Column(name = "total_earning", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalEarning;
    
    @Column(name = "transaction_type", length = 20)
    @Builder.Default
    private String transactionType = "DELIVERY"; // DELIVERY, BONUS, ADJUSTMENT, PAYOUT
    
    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "COMPLETED"; // COMPLETED, PENDING, PAID_OUT
    
    @Column(name = "restaurant_name", length = 200)
    private String restaurantName;
    
    @Column(name = "customer_name", length = 200)
    private String customerName;
    
    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;
    
    @Column(name = "distance_km", precision = 5, scale = 2)
    private BigDecimal distanceKm;
    
    @Column(name = "delivered_at", nullable = false)
    private Instant deliveredAt;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (deliveredAt == null) {
            deliveredAt = Instant.now();
        }
        // Calculate total earning
        if (totalEarning == null) {
            totalEarning = (deliveryFee != null ? deliveryFee : BigDecimal.ZERO)
                .add(bonus != null ? bonus : BigDecimal.ZERO);
        }
    }
}
