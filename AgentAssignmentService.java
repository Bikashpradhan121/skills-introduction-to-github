package com.hungerexpress.agent;

import com.hungerexpress.orders.OrderEntity;
import com.hungerexpress.orders.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Service for automatically assigning orders to available agents
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentAssignmentService {
    
    private final AgentProfileRepository agentProfileRepository;
    private final OrderRepository orderRepository;
    
    /**
     * Automatically assign an order to the best available agent
     * Uses simple round-robin for now, can be enhanced with distance-based assignment
     * 
     * @param orderId The order to assign
     * @return true if assignment was successful
     */
    @Transactional
    public boolean autoAssignOrder(Long orderId) {
        // DISABLED: Let agents manually accept/reject orders
        // Orders will remain PLACED and unassigned until an agent accepts them
        log.info("Order {} created - waiting for agent to accept", orderId);
        return true;
    }
    
    /**
     * Find the best available agent for assignment
     * Priority: Online status > Lowest active orders > Highest rating
     */
    private Optional<AgentProfile> findBestAvailableAgent() {
        // Get all online agents
        List<AgentProfile> onlineAgents = agentProfileRepository.findAll().stream()
            .filter(agent -> Boolean.TRUE.equals(agent.getIsAvailable()))
            .toList();
        
        if (onlineAgents.isEmpty()) {
            return Optional.empty();
        }
        
        // For now, simple selection: first available agent
        // TODO: Enhance with distance-based assignment using GPS coordinates
        return onlineAgents.stream()
            .sorted(Comparator.comparing(AgentProfile::getRating, Comparator.reverseOrder()))
            .findFirst();
    }
    
    /**
     * Check if there are any available agents
     */
    public boolean hasAvailableAgents() {
        return agentProfileRepository.findAll().stream()
            .anyMatch(agent -> Boolean.TRUE.equals(agent.getIsAvailable()));
    }
    
    /**
     * Get count of available agents
     */
    public long countAvailableAgents() {
        return agentProfileRepository.findAll().stream()
            .filter(agent -> Boolean.TRUE.equals(agent.getIsAvailable()))
            .count();
    }
}
