package com.hungerexpress.agent;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "agent_profile")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentProfile {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;
    
    @Column(name = "is_available")
    private Boolean isAvailable;
    
    @Column(name = "rating")
    private Double rating;
    
    @Column(name = "total_earnings")
    private Double totalEarnings;
    
    @Column(name = "pending_payout")
    private Double pendingPayout;
    
    @Column(name = "last_status_change")
    private Instant lastStatusChange;
    
    @Column(name = "last_payout_date")
    private Instant lastPayoutDate;
    
    @Column(name = "vehicle_type")
    private String vehicleType;
    
    @Column(name = "vehicle_number")
    private String vehicleNumber;
    
    @Column(name = "license_number")
    private String licenseNumber;
    
    @Column(name = "current_latitude")
    private Double currentLatitude;
    
    @Column(name = "current_longitude")
    private Double currentLongitude;
    
    @Column(name = "last_location_update")
    private Instant lastLocationUpdate;
    
    @Column(name = "created_at")
    private Instant createdAt;
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
