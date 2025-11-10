package com.hungerexpress.revenue;

import com.hungerexpress.common.CurrentUser;
import com.hungerexpress.orders.OrderEntity;
import com.hungerexpress.orders.OrderRepository;
import com.hungerexpress.orders.OrderStatus;
import com.hungerexpress.restaurant.Restaurant;
import com.hungerexpress.restaurant.RestaurantRepository;
import com.hungerexpress.user.User;
import com.hungerexpress.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/revenue")
@RequiredArgsConstructor
public class RevenueController {
    
    private final RevenueRepository revenueRepo;
    private final OrderRepository orderRepo;
    private final UserRepository userRepo;
    private final RestaurantRepository restaurantRepo;
    
    private static final BigDecimal PLATFORM_FEE_PERCENTAGE = new BigDecimal("0.15"); // 15%
    
    // Get owner revenue summary
    @GetMapping("/owner")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<OwnerRevenueSummary> getOwnerRevenue() {
        String email = CurrentUser.email();
        if (email == null) return ResponseEntity.status(401).build();
        
        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        
        // Calculate or update revenue for current month
        String currentMonth = RevenueEntity.getCurrentMonthYear();
        updateRevenueForOwner(user.getId(), currentMonth);
        
        // Get all revenue records
        List<RevenueEntity> revenues = revenueRepo.findByOwnerIdOrderByMonthYearDesc(user.getId());
        
        BigDecimal totalRevenue = revenueRepo.getTotalRevenueByOwnerId(user.getId());
        BigDecimal totalEarnings = revenueRepo.getTotalNetEarningsByOwnerId(user.getId());
        
        List<MonthlyRevenueDto> monthlyData = revenues.stream()
            .map(r -> new MonthlyRevenueDto(
                r.getMonthYear(),
                r.getTotalOrders(),
                r.getTotalRevenue(),
                r.getPlatformFee(),
                r.getNetEarnings()
            ))
            .collect(Collectors.toList());
        
        OwnerRevenueSummary summary = new OwnerRevenueSummary(
            totalRevenue != null ? totalRevenue : BigDecimal.ZERO,
            totalEarnings != null ? totalEarnings : BigDecimal.ZERO,
            monthlyData
        );
        
        return ResponseEntity.ok(summary);
    }
    
    // Get restaurant-specific revenue
    @GetMapping("/restaurant/{restaurantId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<MonthlyRevenueDto>> getRestaurantRevenue(@PathVariable Long restaurantId) {
        String email = CurrentUser.email();
        if (email == null) return ResponseEntity.status(401).build();
        
        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        
        Restaurant restaurant = restaurantRepo.findById(restaurantId).orElse(null);
        if (restaurant == null || !restaurant.getOwnerId().equals(user.getId())) {
            return ResponseEntity.notFound().build();
        }
        
        String currentMonth = RevenueEntity.getCurrentMonthYear();
        updateRevenueForRestaurant(user.getId(), restaurantId, currentMonth);
        
        List<RevenueEntity> revenues = revenueRepo.findByRestaurantIdOrderByMonthYearDesc(restaurantId);
        
        List<MonthlyRevenueDto> monthlyData = revenues.stream()
            .map(r -> new MonthlyRevenueDto(
                r.getMonthYear(),
                r.getTotalOrders(),
                r.getTotalRevenue(),
                r.getPlatformFee(),
                r.getNetEarnings()
            ))
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(monthlyData);
    }
    
    // Admin: Get all revenue
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RevenueEntity>> getAllRevenue() {
        return ResponseEntity.ok(revenueRepo.findAll());
    }
    
    // Helper: Update revenue for owner
    private void updateRevenueForOwner(Long ownerId, String monthYear) {
        // Get all restaurants for owner
        List<Restaurant> restaurants = restaurantRepo.findByOwnerId(ownerId);
        
        for (Restaurant restaurant : restaurants) {
            updateRevenueForRestaurant(ownerId, restaurant.getId(), monthYear);
        }
    }
    
    // Helper: Update revenue for specific restaurant
    private void updateRevenueForRestaurant(Long ownerId, Long restaurantId, String monthYear) {
        // Parse month year to get date range
        YearMonth ym = YearMonth.parse(monthYear);
        java.time.Instant start = ym.atDay(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
        java.time.Instant end = ym.atEndOfMonth().atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toInstant();
        
        // Get all delivered orders for this restaurant in this month
        List<OrderEntity> orders = orderRepo.findAll().stream()
            .filter(o -> o.getRestaurantId() != null && o.getRestaurantId().equals(restaurantId))
            .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
            .filter(o -> o.getCreatedAt().isAfter(start) && o.getCreatedAt().isBefore(end))
            .collect(Collectors.toList());
        
        BigDecimal totalRevenue = orders.stream()
            .map(OrderEntity::getTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal platformFee = totalRevenue.multiply(PLATFORM_FEE_PERCENTAGE);
        BigDecimal netEarnings = totalRevenue.subtract(platformFee);
        
        // Find or create revenue record
        RevenueEntity revenue = revenueRepo.findByOwnerIdAndRestaurantIdAndMonthYear(ownerId, restaurantId, monthYear)
            .orElse(RevenueEntity.builder()
                .ownerId(ownerId)
                .restaurantId(restaurantId)
                .monthYear(monthYear)
                .build());
        
        revenue.setTotalOrders(orders.size());
        revenue.setTotalRevenue(totalRevenue);
        revenue.setPlatformFee(platformFee);
        revenue.setNetEarnings(netEarnings);
        revenue.setLastUpdated(java.time.Instant.now());
        
        revenueRepo.save(revenue);
    }
    
    // DTOs
    record OwnerRevenueSummary(
        BigDecimal totalRevenue,
        BigDecimal totalEarnings,
        List<MonthlyRevenueDto> monthlyBreakdown
    ) {}
    
    record MonthlyRevenueDto(
        String month,
        Integer totalOrders,
        BigDecimal totalRevenue,
        BigDecimal platformFee,
        BigDecimal netEarnings
    ) {}
}
