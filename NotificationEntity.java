package com.hungerexpress.notification;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "notification", indexes = {
    @Index(name = "idx_notif_user", columnList = "user_id"),
    @Index(name = "idx_notif_read", columnList = "is_read"),
    @Index(name = "idx_notif_created", columnList = "created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private NotificationType type;
    
    @Column(nullable = false, length = 200)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String message;
    
    @Column(name = "related_id") // Order ID, Restaurant ID, etc.
    private Long relatedId;
    
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    @Column(name = "read_at")
    private Instant readAt;
    
    public enum NotificationType {
        ORDER_PLACED,
        ORDER_PREPARING,
        ORDER_DISPATCHED,
        ORDER_DELIVERED,
        ORDER_CANCELLED,
        PAYMENT_SUCCESS,
        PAYMENT_FAILED,
        RESTAURANT_APPROVED,
        RESTAURANT_REJECTED,
        MENU_ITEM_APPROVED,
        MENU_ITEM_REJECTED,
        DELIVERY_ASSIGNED,
        REVIEW_RECEIVED,
        PROMO_ALERT,
        SYSTEM_ALERT
    }
}
