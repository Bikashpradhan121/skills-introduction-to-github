package com.hungerexpress.agent;

import com.hungerexpress.orders.OrderEntity;
import com.hungerexpress.orders.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for managing agent earnings and payouts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentEarningsService {
    
    private final AgentProfileRepository agentProfileRepository;
    private final OrderRepository orderRepository;
    private final AgentTransactionRepository transactionRepository;
    
    /**
     * Update agent earnings when an order is delivered
     * Called automatically when order status changes to DELIVERED
     * 
     * @param orderId The delivered order ID
     */
    @Transactional
    public void updateEarningsOnDelivery(Long orderId) {
        try {
            // Get the order
            OrderEntity order = orderRepository.findById(orderId).orElse(null);
            if (order == null) {
                log.warn("Order {} not found for earnings update", orderId);
                return;
            }
            
            // Check if order has an assigned agent
            Long agentId = order.getAssignedTo();
            if (agentId == null) {
                log.warn("Order {} has no assigned agent", orderId);
                return;
            }
            
            // Get agent profile
            AgentProfile agentProfile = agentProfileRepository.findByUserId(agentId).orElse(null);
            if (agentProfile == null) {
                log.warn("Agent profile not found for user {}", agentId);
                return;
            }
            
            // Calculate earning (delivery fee)
            BigDecimal deliveryFee = order.getDeliveryFee();
            if (deliveryFee == null || deliveryFee.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Order {} has no delivery fee", orderId);
                return;
            }
            
            double earningAmount = deliveryFee.doubleValue();
            
            // Update total earnings
            double currentEarnings = agentProfile.getTotalEarnings() != null ? agentProfile.getTotalEarnings() : 0.0;
            agentProfile.setTotalEarnings(currentEarnings + earningAmount);
            
            // Update pending payout
            double currentPending = agentProfile.getPendingPayout() != null ? agentProfile.getPendingPayout() : 0.0;
            agentProfile.setPendingPayout(currentPending + earningAmount);
            
            agentProfileRepository.save(agentProfile);
            
            // Create transaction record
            AgentTransaction transaction = AgentTransaction.builder()
                .agentId(agentId)
                .orderId(orderId)
                .orderNumber("ORD-" + orderId)
                .deliveryFee(deliveryFee)
                .bonus(BigDecimal.ZERO) // No bonus for now
                .totalEarning(deliveryFee)
                .transactionType("DELIVERY")
                .status("COMPLETED")
                .restaurantName(order.getRestaurantId() != null ? "Restaurant #" + order.getRestaurantId() : "Unknown")
                .customerName(order.getShipName())
                .deliveryAddress(buildAddress(order))
                .deliveredAt(order.getDeliveredAt() != null ? order.getDeliveredAt() : java.time.Instant.now())
                .build();
            
            transactionRepository.save(transaction);
            
            log.info("Updated earnings for agent {}: +₹{} (Total: ₹{}, Pending: ₹{})", 
                agentId, earningAmount, agentProfile.getTotalEarnings(), agentProfile.getPendingPayout());
            log.info("Created transaction record #{} for order #{}", transaction.getId(), orderId);
            
        } catch (Exception e) {
            log.error("Error updating earnings for order {}: {}", orderId, e.getMessage(), e);
        }
    }
    
    /**
     * Build delivery address from order
     */
    private String buildAddress(OrderEntity order) {
        StringBuilder addr = new StringBuilder();
        if (order.getShipLine1() != null) addr.append(order.getShipLine1());
        if (order.getShipLine2() != null) {
            if (addr.length() > 0) addr.append(", ");
            addr.append(order.getShipLine2());
        }
        if (order.getShipCity() != null) {
            if (addr.length() > 0) addr.append(", ");
            addr.append(order.getShipCity());
        }
        return addr.length() > 0 ? addr.toString() : "Address not provided";
    }
    
    /**
     * Recalculate all earnings for an agent from their delivered orders
     * Useful for fixing inconsistencies
     * 
     * @param agentId The agent user ID
     */
    @Transactional
    public void recalculateAgentEarnings(Long agentId) {
        try {
            AgentProfile agentProfile = agentProfileRepository.findByUserId(agentId).orElse(null);
            if (agentProfile == null) {
                log.warn("Agent profile not found for user {}", agentId);
                return;
            }
            
            // Get all delivered orders for this agent
            List<OrderEntity> deliveredOrders = orderRepository.findAll().stream()
                .filter(o -> agentId.equals(o.getAssignedTo()))
                .filter(o -> "DELIVERED".equals(o.getStatus().name()))
                .toList();
            
            // Calculate total earnings
            double totalEarnings = deliveredOrders.stream()
                .map(OrderEntity::getDeliveryFee)
                .filter(fee -> fee != null && fee.compareTo(BigDecimal.ZERO) > 0)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();
            
            // Update profile
            agentProfile.setTotalEarnings(totalEarnings);
            
            // Pending payout = total earnings - already paid out
            // For now, assume all earnings are pending (no payout history tracking yet)
            double paidOut = 0.0; // TODO: Track payout history
            agentProfile.setPendingPayout(totalEarnings - paidOut);
            
            agentProfileRepository.save(agentProfile);
            
            log.info("Recalculated earnings for agent {}: ₹{} from {} deliveries", 
                agentId, totalEarnings, deliveredOrders.size());
            
        } catch (Exception e) {
            log.error("Error recalculating earnings for agent {}: {}", agentId, e.getMessage(), e);
        }
    }
}
