package com.hungerexpress.revenue;

import com.hungerexpress.agent.AgentOrderAssignment;
import com.hungerexpress.agent.AgentOrderRepository;
import com.hungerexpress.common.CurrentUser;
import com.hungerexpress.orders.OrderEntity;
import com.hungerexpress.orders.OrderRepository;
import com.hungerexpress.orders.OrderStatus;
import com.hungerexpress.user.User;
import com.hungerexpress.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/agent/earnings")
@RequiredArgsConstructor
public class AgentEarningsController {
    
    private final UserRepository userRepo;
    private final AgentOrderRepository agentOrderRepo;
    private final OrderRepository orderRepo;
    
    private static final BigDecimal BASE_DELIVERY_FEE = new BigDecimal("50.00");
    private static final BigDecimal PER_KM_RATE = new BigDecimal("10.00");
    
    // Get agent earnings summary (both root and /summary path for compatibility)
    @GetMapping(value = {"", "/summary"})
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<Map<String, Object>> getEarnings() {
        String email = CurrentUser.email();
        if (email == null) return ResponseEntity.status(401).build();
        
        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        
        // Get all agent assignments
        List<AgentOrderAssignment> assignments = agentOrderRepo.findByAgentId(user.getId());
        
        // Calculate total earnings
        BigDecimal totalEarnings = BigDecimal.ZERO;
        int totalDeliveries = 0;
        
        // Group by month
        Map<String, MonthlyAgentEarnings> monthlyMap = new HashMap<>();
        
        for (AgentOrderAssignment assignment : assignments) {
            OrderEntity order = orderRepo.findById(assignment.getOrderId()).orElse(null);
            if (order == null || order.getStatus() != OrderStatus.DELIVERED) {
                continue;
            }
            
            totalDeliveries++;
            
            // Calculate earnings for this delivery
            BigDecimal deliveryEarnings = calculateDeliveryEarnings(order);
            totalEarnings = totalEarnings.add(deliveryEarnings);
            
            // Group by month
            String monthYear = YearMonth.from(order.getDeliveredAt().atZone(ZoneId.systemDefault())).toString();
            
            MonthlyAgentEarnings monthly = monthlyMap.getOrDefault(monthYear, 
                new MonthlyAgentEarnings(monthYear, 0, BigDecimal.ZERO));
            
            monthly.deliveries++;
            monthly.earnings = monthly.earnings.add(deliveryEarnings);
            
            monthlyMap.put(monthYear, monthly);
        }
        
        // Convert to list and sort
        List<MonthlyAgentEarnings> monthlyList = new ArrayList<>(monthlyMap.values());
        monthlyList.sort((a, b) -> b.month.compareTo(a.month));
        
        // Calculate today, week, month earnings
        Instant now = Instant.now();
        Instant todayStart = now.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant weekStart = now.minus(7, java.time.temporal.ChronoUnit.DAYS);
        Instant monthStart = now.minus(30, java.time.temporal.ChronoUnit.DAYS);
        
        BigDecimal todayEarnings = BigDecimal.ZERO;
        BigDecimal weekEarnings = BigDecimal.ZERO;
        BigDecimal monthEarnings = BigDecimal.ZERO;
        
        for (AgentOrderAssignment assignment : assignments) {
            OrderEntity order = orderRepo.findById(assignment.getOrderId()).orElse(null);
            if (order == null || order.getStatus() != OrderStatus.DELIVERED || order.getDeliveredAt() == null) continue;
            
            BigDecimal earning = calculateDeliveryEarnings(order);
            
            if (order.getDeliveredAt().isAfter(todayStart)) {
                todayEarnings = todayEarnings.add(earning);
            }
            if (order.getDeliveredAt().isAfter(weekStart)) {
                weekEarnings = weekEarnings.add(earning);
            }
            if (order.getDeliveredAt().isAfter(monthStart)) {
                monthEarnings = monthEarnings.add(earning);
            }
        }
        
        // Return format expected by frontend
        Map<String, Object> response = new HashMap<>();
        response.put("totalEarnings", totalEarnings.doubleValue());
        response.put("todayEarnings", todayEarnings.doubleValue());
        response.put("weekEarnings", weekEarnings.doubleValue());
        response.put("monthEarnings", monthEarnings.doubleValue());
        response.put("totalDeliveries", totalDeliveries);
        response.put("averagePerDelivery", totalDeliveries > 0 ? totalEarnings.divide(BigDecimal.valueOf(totalDeliveries), 2, java.math.RoundingMode.HALF_UP).doubleValue() : 0.0);
        response.put("pendingPayout", totalEarnings.doubleValue()); // All earnings are pending for now
        response.put("lastPayoutDate", now.minus(7, java.time.temporal.ChronoUnit.DAYS).toString());
        
        return ResponseEntity.ok(response);
    }
    
    // Get delivery history with earnings (both /history and /transactions paths)
    @GetMapping(value = {"/history", "/transactions"})
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<List<Map<String, Object>>> getDeliveryHistory() {
        String email = CurrentUser.email();
        if (email == null) return ResponseEntity.status(401).build();
        
        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        
        List<AgentOrderAssignment> assignments = agentOrderRepo.findByAgentId(user.getId());
        
        List<Map<String, Object>> history = assignments.stream()
            .map(assignment -> {
                OrderEntity order = orderRepo.findById(assignment.getOrderId()).orElse(null);
                if (order == null) return null;
                
                BigDecimal earning = order.getStatus() == OrderStatus.DELIVERED 
                    ? calculateDeliveryEarnings(order) 
                    : BigDecimal.ZERO;
                
                Map<String, Object> txn = new HashMap<>();
                txn.put("id", assignment.getOrderId());
                txn.put("orderNumber", "ORD-" + assignment.getOrderId());
                txn.put("date", assignment.getAssignedAt() != null ? assignment.getAssignedAt().toString() : Instant.now().toString());
                txn.put("deliveryFee", earning.doubleValue());
                txn.put("bonus", 0.0);
                txn.put("total", earning.doubleValue());
                txn.put("totalEarning", earning.doubleValue());
                txn.put("status", order.getStatus() == OrderStatus.DELIVERED ? "COMPLETED" : "PENDING");
                
                return txn;
            })
            .filter(Objects::nonNull)
            .sorted((a, b) -> {
                String dateA = (String) a.get("date");
                String dateB = (String) b.get("date");
                if (dateA == null && dateB == null) return 0;
                if (dateA == null) return 1;
                if (dateB == null) return -1;
                return dateB.compareTo(dateA);
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(history);
    }
    
    // Admin: Get all agent earnings
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AgentEarningsAdmin>> getAllAgentEarnings() {
        List<User> agents = userRepo.findAll().stream()
            .filter(u -> "AGENT".equals(u.getRole()))
            .collect(Collectors.toList());
        
        List<AgentEarningsAdmin> result = agents.stream()
            .map(agent -> {
                List<AgentOrderAssignment> assignments = agentOrderRepo.findByAgentId(agent.getId());
                
                BigDecimal totalEarnings = BigDecimal.ZERO;
                int deliveries = 0;
                
                for (AgentOrderAssignment assignment : assignments) {
                    OrderEntity order = orderRepo.findById(assignment.getOrderId()).orElse(null);
                    if (order != null && order.getStatus() == OrderStatus.DELIVERED) {
                        totalEarnings = totalEarnings.add(calculateDeliveryEarnings(order));
                        deliveries++;
                    }
                }
                
                return new AgentEarningsAdmin(
                    agent.getId(),
                    agent.getFullName(),
                    agent.getEmail(),
                    deliveries,
                    totalEarnings
                );
            })
            .sorted((a, b) -> b.totalEarnings.compareTo(a.totalEarnings))
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }
    
    // Helper: Calculate delivery earnings
    private BigDecimal calculateDeliveryEarnings(OrderEntity order) {
        // Base formula: Base fee + distance-based rate
        // For now, use delivery fee from order or default
        BigDecimal deliveryFee = order.getDeliveryFee();
        if (deliveryFee == null || deliveryFee.compareTo(BigDecimal.ZERO) == 0) {
            deliveryFee = BASE_DELIVERY_FEE;
        }
        
        // Agent gets 80% of delivery fee
        return deliveryFee.multiply(new BigDecimal("0.80"));
    }
    
    // DTOs
    record AgentEarningsSummary(
        BigDecimal totalEarnings,
        Integer totalDeliveries,
        BigDecimal averagePerDelivery,
        List<MonthlyAgentEarnings> monthlyBreakdown
    ) {}
    
    static class MonthlyAgentEarnings {
        String month;
        Integer deliveries;
        BigDecimal earnings;
        
        MonthlyAgentEarnings(String month, Integer deliveries, BigDecimal earnings) {
            this.month = month;
            this.deliveries = deliveries;
            this.earnings = earnings;
        }
    }
    
    record DeliveryEarningDto(
        Long orderId,
        String status,
        BigDecimal orderTotal,
        BigDecimal earning,
        Instant assignedAt,
        Instant deliveredAt,
        String deliveryAddress
    ) {}
    
    record AgentEarningsAdmin(
        Long agentId,
        String agentName,
        String agentEmail,
        Integer totalDeliveries,
        BigDecimal totalEarnings
    ) {}
}
