package com.hungerexpress.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final NotificationRepository notificationRepo;
    
    // Send notification to user
    public NotificationEntity sendNotification(Long userId, NotificationEntity.NotificationType type, 
                                              String title, String message, Long relatedId) {
        NotificationEntity notification = NotificationEntity.builder()
            .userId(userId)
            .type(type)
            .title(title)
            .message(message)
            .relatedId(relatedId)
            .build();
        
        return notificationRepo.save(notification);
    }
    
    // Order notifications
    public void notifyOrderPlaced(Long userId, Long orderId, String restaurantName) {
        sendNotification(userId, NotificationEntity.NotificationType.ORDER_PLACED,
            "Order Placed Successfully",
            "Your order from " + restaurantName + " has been placed. We'll notify you when it's being prepared.",
            orderId);
    }
    
    public void notifyOrderPreparing(Long userId, Long orderId, String restaurantName) {
        sendNotification(userId, NotificationEntity.NotificationType.ORDER_PREPARING,
            "Order is Being Prepared",
            restaurantName + " is now preparing your order. It will be ready soon!",
            orderId);
    }
    
    public void notifyOrderDispatched(Long userId, Long orderId, String agentName) {
        sendNotification(userId, NotificationEntity.NotificationType.ORDER_DISPATCHED,
            "Order Dispatched",
            "Your order is on the way! " + agentName + " is delivering your order.",
            orderId);
    }
    
    public void notifyOrderDelivered(Long userId, Long orderId) {
        sendNotification(userId, NotificationEntity.NotificationType.ORDER_DELIVERED,
            "Order Delivered",
            "Your order has been delivered. Enjoy your meal!",
            orderId);
    }
    
    public void notifyOrderCancelled(Long userId, Long orderId, String reason) {
        sendNotification(userId, NotificationEntity.NotificationType.ORDER_CANCELLED,
            "Order Cancelled",
            "Your order has been cancelled. Reason: " + reason,
            orderId);
    }
    
    // Payment notifications
    public void notifyPaymentSuccess(Long userId, Long orderId, String amount) {
        sendNotification(userId, NotificationEntity.NotificationType.PAYMENT_SUCCESS,
            "Payment Successful",
            "Payment of ₹" + amount + " received successfully.",
            orderId);
    }
    
    public void notifyPaymentFailed(Long userId, Long orderId, String reason) {
        sendNotification(userId, NotificationEntity.NotificationType.PAYMENT_FAILED,
            "Payment Failed",
            "Payment failed: " + reason + ". Please try again.",
            orderId);
    }
    
    // Restaurant notifications
    public void notifyRestaurantApproved(Long ownerId, Long restaurantId, String restaurantName) {
        sendNotification(ownerId, NotificationEntity.NotificationType.RESTAURANT_APPROVED,
            "Restaurant Approved",
            "Congratulations! " + restaurantName + " has been approved and is now live on the platform.",
            restaurantId);
    }
    
    public void notifyRestaurantRejected(Long ownerId, Long restaurantId, String restaurantName, String reason) {
        sendNotification(ownerId, NotificationEntity.NotificationType.RESTAURANT_REJECTED,
            "Restaurant Application Rejected",
            restaurantName + " application was not approved. Reason: " + reason,
            restaurantId);
    }
    
    // Delivery agent notifications
    public void notifyDeliveryAssigned(Long agentId, Long orderId, String restaurantName) {
        sendNotification(agentId, NotificationEntity.NotificationType.DELIVERY_ASSIGNED,
            "New Delivery Assignment",
            "You have a new delivery from " + restaurantName + ". Please accept or decline.",
            orderId);
    }
    
    // Get user notifications
    public List<NotificationEntity> getUserNotifications(Long userId) {
        return notificationRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    public List<NotificationEntity> getUnreadNotifications(Long userId) {
        return notificationRepo.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }
    
    public Long getUnreadCount(Long userId) {
        return notificationRepo.countUnreadByUserId(userId);
    }
    
    // Mark as read
    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepo.markAsRead(notificationId, Instant.now());
    }
    
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepo.markAllAsReadForUser(userId, Instant.now());
    }
    
    // Clean old notifications (older than 90 days)
    @Transactional
    public void cleanOldNotifications(Long userId) {
        Instant cutoff = Instant.now().minus(90, java.time.temporal.ChronoUnit.DAYS);
        notificationRepo.deleteByUserIdAndCreatedAtBefore(userId, cutoff);
    }
}
