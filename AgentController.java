package com.hungerexpress.agent;

import com.hungerexpress.common.CurrentUser;
import com.hungerexpress.user.User;
import com.hungerexpress.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@PreAuthorize("hasRole('AGENT')")
public class AgentController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AgentProfileRepository agentProfileRepository;

    @Autowired
    private AgentOrderRepository agentOrderRepository;
    
    @Autowired
    private AgentEarningsService agentEarningsService;
    
    @Autowired
    private AgentTransactionRepository transactionRepository;

    /**
     * Get agent overview/dashboard stats
     */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        return getStats();
    }
    
    /**
     * Get agent stats (alias for overview)
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        String email = CurrentUser.email();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        AgentProfile profile = agentProfileRepository.findByUserId(user.getId())
            .orElseGet(() -> createDefaultAgentProfile(user.getId()));

        // Calculate today's stats
        LocalDate today = LocalDate.now();
        int todayDeliveries = agentOrderRepository.countTodayDeliveries(user.getId(), today);
        int activeOrders = agentOrderRepository.countActiveOrders(user.getId());
        double todayEarnings = agentOrderRepository.sumTodayEarnings(user.getId(), today);
        int totalDeliveries = agentOrderRepository.countTotalDeliveries(user.getId());
        double totalEarnings = profile.getTotalEarnings() != null ? profile.getTotalEarnings() : 0.0;
        double avgPerDelivery = totalDeliveries > 0 ? totalEarnings / totalDeliveries : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("todayDeliveries", todayDeliveries);
        stats.put("activeOrders", activeOrders);
        stats.put("todayEarnings", todayEarnings);
        stats.put("totalDeliveries", totalDeliveries);
        stats.put("totalEarnings", totalEarnings);
        stats.put("averagePerDelivery", Math.round(avgPerDelivery * 100.0) / 100.0);
        stats.put("rating", profile.getRating() != null ? profile.getRating() : 4.5);
        stats.put("isAvailable", profile.getIsAvailable() != null ? profile.getIsAvailable() : false);
        stats.put("agentName", user.getFullName() != null ? user.getFullName() : "Agent");

        return ResponseEntity.ok(stats);
    }

    /**
     * Get agent availability status
     */
    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> getAvailability() {
        String email = CurrentUser.email();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        AgentProfile profile = agentProfileRepository.findByUserId(user.getId())
            .orElseGet(() -> createDefaultAgentProfile(user.getId()));

        LocalDate today = LocalDate.now();
        int todayDeliveries = agentOrderRepository.countTodayDeliveries(user.getId(), today);
        double todayOnlineHours = calculateTodayOnlineHours(profile);

        Map<String, Object> status = new HashMap<>();
        status.put("isAvailable", profile.getIsAvailable() != null ? profile.getIsAvailable() : false);
        status.put("lastStatusChange", profile.getLastStatusChange() != null ? profile.getLastStatusChange().toString() : Instant.now().toString());
        status.put("todayOnlineHours", todayOnlineHours);
        status.put("todayDeliveries", todayDeliveries);

        return ResponseEntity.ok(status);
    }

    /**
     * Toggle agent availability (online/offline)
     */
    @PostMapping("/toggle-availability")
    @Transactional
    public ResponseEntity<Map<String, Object>> toggleAvailability(@RequestBody Map<String, Boolean> request) {
        String email = CurrentUser.email();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        AgentProfile profile = agentProfileRepository.findByUserId(user.getId())
            .orElseGet(() -> createDefaultAgentProfile(user.getId()));

        Boolean newStatus = request.getOrDefault("isAvailable", !profile.getIsAvailable());
        profile.setIsAvailable(newStatus);
        profile.setLastStatusChange(Instant.now());
        agentProfileRepository.save(profile);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("isAvailable", newStatus);
        response.put("message", newStatus ? "You are now ONLINE and will receive delivery requests" : "You are now OFFLINE and will not receive new requests");

        return ResponseEntity.ok(response);
    }

    /**
     * Get assigned orders
     */
    @GetMapping("/orders")
    public ResponseEntity<List<Map<String, Object>>> getAssignedOrders() {
        String email = CurrentUser.email();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        // Get orders assigned to this agent
        List<Map<String, Object>> orders = agentOrderRepository.findActiveOrdersByAgentId(user.getId());

        return ResponseEntity.ok(orders);
    }

    /**
     * Mark order as picked up
     */
    @PostMapping("/orders/{orderId}/pickup")
    @Transactional
    public ResponseEntity<Map<String, Object>> markPickedUp(@PathVariable Long orderId) {
        String email = CurrentUser.email();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        boolean success = agentOrderRepository.markAsPickedUp(orderId, user.getId());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "Order marked as picked up" : "Failed to update order");

        return success ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    /**
     * Mark order as delivered
     */
    @PostMapping("/orders/{orderId}/deliver")
    @Transactional
    public ResponseEntity<Map<String, Object>> markDelivered(@PathVariable Long orderId) {
        String email = CurrentUser.email();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        boolean success = agentOrderRepository.markAsDelivered(orderId, user.getId());
        
        // Update agent earnings if delivery was successful
        if (success) {
            agentEarningsService.updateEarningsOnDelivery(orderId);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "Order delivered successfully!" : "Failed to update order");

        return success ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    /**
     * Get map orders (orders with location data)
     */
    @GetMapping("/map/orders")
    public ResponseEntity<List<Map<String, Object>>> getMapOrders() {
        String email = CurrentUser.email();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        List<Map<String, Object>> orders = agentOrderRepository.findOrdersWithLocations(user.getId());

        return ResponseEntity.ok(orders);
    }

    /**
     * Get earnings summary
     */
    @GetMapping("/earnings/summary")
    public ResponseEntity<Map<String, Object>> getEarningsSummary() {
        String email = CurrentUser.email();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        AgentProfile profile = agentProfileRepository.findByUserId(user.getId())
            .orElseGet(() -> createDefaultAgentProfile(user.getId()));

        LocalDate today = LocalDate.now();
        double totalEarnings = profile.getTotalEarnings() != null ? profile.getTotalEarnings() : 0.0;
        double todayEarnings = agentOrderRepository.sumTodayEarnings(user.getId(), today);
        double weekEarnings = agentOrderRepository.sumWeekEarnings(user.getId(), today);
        double monthEarnings = agentOrderRepository.sumMonthEarnings(user.getId(), today);
        int totalDeliveries = agentOrderRepository.countTotalDeliveries(user.getId());
        double avgPerDelivery = totalDeliveries > 0 ? totalEarnings / totalDeliveries : 0.0;

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalEarnings", totalEarnings);
        summary.put("todayEarnings", todayEarnings);
        summary.put("weekEarnings", weekEarnings);
        summary.put("monthEarnings", monthEarnings);
        summary.put("totalDeliveries", totalDeliveries);
        summary.put("averagePerDelivery", Math.round(avgPerDelivery * 100.0) / 100.0);
        summary.put("pendingPayout", profile.getPendingPayout() != null ? profile.getPendingPayout() : 0.0);
        summary.put("lastPayoutDate", profile.getLastPayoutDate() != null ? profile.getLastPayoutDate().toString() : null);

        return ResponseEntity.ok(summary);
    }

    /**
     * Get earnings transactions (recent deliveries)
     */
    @GetMapping("/earnings/transactions")
    public ResponseEntity<List<Map<String, Object>>> getTransactions() {
        String email = CurrentUser.email();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        // Get recent transactions from the new transaction table
        List<AgentTransaction> transactions = transactionRepository.findByAgentIdOrderByDeliveredAtDesc(user.getId());
        
        // Limit to 50 most recent
        List<AgentTransaction> recentTransactions = transactions.stream()
            .limit(50)
            .toList();
        
        // Map to response format
        List<Map<String, Object>> response = recentTransactions.stream()
            .map(t -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", t.getId());
                map.put("orderNumber", t.getOrderNumber());
                map.put("deliveryFee", t.getDeliveryFee());
                map.put("bonus", t.getBonus());
                map.put("total", t.getTotalEarning());
                map.put("status", t.getStatus());
                map.put("transactionType", t.getTransactionType());
                map.put("date", t.getDeliveredAt().toString());
                map.put("restaurantName", t.getRestaurantName());
                map.put("customerName", t.getCustomerName());
                map.put("deliveryAddress", t.getDeliveryAddress());
                return map;
            })
            .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Request payout
     */
    @PostMapping("/earnings/payout")
    @Transactional
    public ResponseEntity<Map<String, Object>> requestPayout() {
        String email = CurrentUser.email();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        AgentProfile profile = agentProfileRepository.findByUserId(user.getId())
            .orElseGet(() -> createDefaultAgentProfile(user.getId()));

        double pendingAmount = profile.getPendingPayout() != null ? profile.getPendingPayout() : 0.0;
        
        if (pendingAmount < 500) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Minimum payout amount is ₹500");
            return ResponseEntity.badRequest().body(response);
        }

        // Process payout request
        profile.setPendingPayout(0.0);
        profile.setLastPayoutDate(Instant.now());
        agentProfileRepository.save(profile);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Payout request submitted. Amount will be transferred within 2-3 business days.");
        response.put("amount", pendingAmount);

        return ResponseEntity.ok(response);
    }

    // Helper methods

    private AgentProfile createDefaultAgentProfile(Long userId) {
        // Check if profile was deleted and needs recreation
        AgentProfile profile = new AgentProfile();
        profile.setUserId(userId);
        profile.setIsAvailable(false); // Default to offline for new agents
        profile.setRating(5.0); // Start with good rating
        profile.setTotalEarnings(0.0);
        profile.setPendingPayout(0.0);
        profile.setVehicleType("BIKE"); // Default vehicle
        profile.setLastStatusChange(Instant.now());
        profile.setCreatedAt(Instant.now());
        
        System.out.println("[AgentController] Created new agent profile for user: " + userId);
        return agentProfileRepository.save(profile);
    }

    private double calculateTodayOnlineHours(AgentProfile profile) {
        // Placeholder: In production, track online/offline timestamps
        return profile.getIsAvailable() ? 0.5 : 0.0;
    }
}
