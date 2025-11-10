package com.hungerexpress.agent;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "agent_order_assignment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentOrderAssignment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "agent_id", nullable = false)
    private Long agentId;
    
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    
    @Column(name = "assigned_at")
    private Instant assignedAt;
    
    @Column(name = "picked_up_at")
    private Instant pickedUpAt;
    
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    
    @Column(name = "status")
    private String status;
    
    @PrePersist
    protected void onCreate() {
        if (assignedAt == null) assignedAt = Instant.now();
        if (status == null || status.isBlank()) status = "ASSIGNED";
    }
}
