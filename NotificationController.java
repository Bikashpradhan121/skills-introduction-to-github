package com.hungerexpress.notification;

import com.hungerexpress.common.CurrentUser;
import com.hungerexpress.user.User;
import com.hungerexpress.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    
    private final NotificationService notificationService;
    private final UserRepository userRepo;
    
    // Get all notifications for current user
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificationEntity>> getNotifications() {
        String email = CurrentUser.email();
        if (email == null) return ResponseEntity.status(401).build();
        
        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        
        List<NotificationEntity> notifications = notificationService.getUserNotifications(user.getId());
        return ResponseEntity.ok(notifications);
    }
    
    // Get unread notifications
    @GetMapping("/unread")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificationEntity>> getUnreadNotifications() {
        String email = CurrentUser.email();
        if (email == null) return ResponseEntity.status(401).build();
        
        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        
        List<NotificationEntity> notifications = notificationService.getUnreadNotifications(user.getId());
        return ResponseEntity.ok(notifications);
    }
    
    // Get unread count
    @GetMapping("/unread/count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UnreadCountResponse> getUnreadCount() {
        String email = CurrentUser.email();
        if (email == null) return ResponseEntity.status(401).build();
        
        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        
        Long count = notificationService.getUnreadCount(user.getId());
        return ResponseEntity.ok(new UnreadCountResponse(count));
    }
    
    // Mark notification as read
    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }
    
    // Mark all as read
    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAllAsRead() {
        String email = CurrentUser.email();
        if (email == null) return ResponseEntity.status(401).build();
        
        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok().build();
    }
    
    // Clean old notifications
    @DeleteMapping("/clean")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cleanOldNotifications() {
        String email = CurrentUser.email();
        if (email == null) return ResponseEntity.status(401).build();
        
        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        
        notificationService.cleanOldNotifications(user.getId());
        return ResponseEntity.ok().build();
    }
    
    record UnreadCountResponse(Long count) {}
}
