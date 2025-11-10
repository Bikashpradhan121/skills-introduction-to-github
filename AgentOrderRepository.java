package com.hungerexpress.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public interface AgentOrderRepository extends JpaRepository<AgentOrderAssignment, Long> {
    
    // Find all orders assigned to an agent
    List<AgentOrderAssignment> findByAgentId(Long agentId);

    // Find latest assignment row for an order
    AgentOrderAssignment findTopByOrderIdOrderByAssignedAtDesc(Long orderId);

    // Count today's completed deliveries (uses orders table directly)
    @Query(value = "SELECT COUNT(*) FROM orders o " +
            "WHERE o.assigned_to = :agentId " +
            "AND o.status = 'DELIVERED' " +
            "AND DATE(o.delivered_at) = :today",
            nativeQuery = true)
    int countTodayDeliveries(@Param("agentId") Long agentId, @Param("today") LocalDate today);

    // Count active orders for agent (PREPARING or OUT_FOR_DELIVERY)
    @Query(value = "SELECT COUNT(*) FROM orders o " +
            "WHERE o.assigned_to = :agentId " +
            "AND o.status IN ('PREPARING','OUT_FOR_DELIVERY')",
            nativeQuery = true)
    int countActiveOrders(@Param("agentId") Long agentId);

    // Sum today's earnings (delivery_fee of delivered orders)
    @Query(value = "SELECT COALESCE(SUM(o.delivery_fee), 0) FROM orders o " +
            "WHERE o.assigned_to = :agentId " +
            "AND o.status = 'DELIVERED' " +
            "AND DATE(o.delivered_at) = :today",
            nativeQuery = true)
    double sumTodayEarnings(@Param("agentId") Long agentId, @Param("today") LocalDate today);

    // Sum last 7 days earnings relative to provided date
    @Query(value = "SELECT COALESCE(SUM(o.delivery_fee), 0) FROM orders o " +
            "WHERE o.assigned_to = :agentId " +
            "AND o.status = 'DELIVERED' " +
            "AND DATE(o.delivered_at) >= DATE_SUB(:weekStart, INTERVAL 7 DAY)",
            nativeQuery = true)
    double sumWeekEarnings(@Param("agentId") Long agentId, @Param("weekStart") LocalDate weekStart);

    // Sum last 30 days earnings relative to provided date
    @Query(value = "SELECT COALESCE(SUM(o.delivery_fee), 0) FROM orders o " +
            "WHERE o.assigned_to = :agentId " +
            "AND o.status = 'DELIVERED' " +
            "AND DATE(o.delivered_at) >= DATE_SUB(:monthStart, INTERVAL 30 DAY)",
            nativeQuery = true)
    double sumMonthEarnings(@Param("agentId") Long agentId, @Param("monthStart") LocalDate monthStart);

    // Count total deliveries
    @Query(value = "SELECT COUNT(*) FROM orders o WHERE o.assigned_to = :agentId AND o.status = 'DELIVERED'",
            nativeQuery = true)
    int countTotalDeliveries(@Param("agentId") Long agentId);

    // Get active orders with full details
    @Query(value = "SELECT o.id, CONCAT('ORD-', o.id) AS orderNumber, o.status, " +
            "r.name AS restaurantName, r.address AS restaurantAddress, r.phone AS restaurantPhone, " +
            "u.full_name AS customerName, CONCAT(COALESCE(o.ship_line1,''), ', ', COALESCE(o.ship_city,'')) AS customerAddress, " +
            "o.ship_phone AS customerPhone, o.total AS totalAmount, o.delivery_fee, " +
            "'2.5 km' AS distance, '15 mins' AS estimatedTime, o.created_at AS createdAt " +
            "FROM orders o " +
            "JOIN restaurant r ON o.restaurant_id = r.id " +
            "JOIN users u ON o.user_id = u.id " +
            "WHERE o.assigned_to = :agentId " +
            "AND o.status IN ('PREPARING','OUT_FOR_DELIVERY') " +
            "ORDER BY o.created_at ASC",
            nativeQuery = true)
    List<Map<String, Object>> findActiveOrdersByAgentId(@Param("agentId") Long agentId);

    // Get orders with location data for map
    @Query(value = "SELECT o.id, CONCAT('ORD-', o.id) AS orderNumber, o.status, " +
            "r.name AS restaurantName, r.latitude AS restaurantLat, r.longitude AS restaurantLng, " +
            "u.full_name AS customerName, NULL AS customerLat, NULL AS customerLng " +
            "FROM orders o " +
            "JOIN restaurant r ON o.restaurant_id = r.id " +
            "JOIN users u ON o.user_id = u.id " +
            "WHERE o.assigned_to = :agentId " +
            "AND o.status IN ('PREPARING','OUT_FOR_DELIVERY')",
            nativeQuery = true)
    List<Map<String, Object>> findOrdersWithLocations(@Param("agentId") Long agentId);

    // Get recent transactions (delivered orders with any successful/known payment if present)
    @Query(value = "SELECT o.id, o.delivered_at AS date, CONCAT('ORD-', o.id) AS orderNumber, " +
            "o.delivery_fee AS deliveryFee, 0 AS bonus, o.delivery_fee AS total, " +
            "COALESCE(p.provider, 'CASH') AS paymentMethod, " +
            "CASE WHEN COALESCE(p.status,'') IN ('SUCCESS','CAPTURED','PAID','COMPLETED') THEN 'PAID' ELSE COALESCE(p.status,'PENDING') END AS status " +
            "FROM orders o " +
            "LEFT JOIN payment p ON p.order_id = o.id " +
            "WHERE o.assigned_to = :agentId " +
            "AND o.status = 'DELIVERED' " +
            "ORDER BY o.delivered_at DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Map<String, Object>> findRecentTransactions(@Param("agentId") Long agentId, @Param("limit") int limit);

    // Mark order as picked up
    @Modifying
    @Query(value = "UPDATE orders SET status = 'OUT_FOR_DELIVERY', dispatched_at = NOW() " +
            "WHERE id = :orderId AND assigned_to = :agentId",
            nativeQuery = true)
    int updateStatusToPickedUp(@Param("orderId") Long orderId, @Param("agentId") Long agentId);

    // Mark order as delivered
    @Modifying
    @Query(value = "UPDATE orders SET status = 'DELIVERED', delivered_at = NOW() " +
            "WHERE id = :orderId AND assigned_to = :agentId",
            nativeQuery = true)
    int updateStatusToDelivered(@Param("orderId") Long orderId, @Param("agentId") Long agentId);

    // Helper methods
    default boolean markAsPickedUp(Long orderId, Long agentId) {
        return updateStatusToPickedUp(orderId, agentId) > 0;
    }

    default boolean markAsDelivered(Long orderId, Long agentId) {
        return updateStatusToDelivered(orderId, agentId) > 0;
    }
}
