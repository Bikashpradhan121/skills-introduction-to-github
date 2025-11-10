package com.hungerexpress.agent;

import com.hungerexpress.common.CurrentUser;
import com.hungerexpress.user.User;
import com.hungerexpress.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent/profile")
@PreAuthorize("hasRole('AGENT')")
public class AgentProfileController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AgentProfileRepository agentProfileRepository;

    @Autowired
    private AgentOrderRepository agentOrderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Get complete agent profile (merged from user + agent_profile tables)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile() {
        String email = CurrentUser.email();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        AgentProfile agentProfile = agentProfileRepository.findByUserId(user.getId())
            .orElseGet(() -> createDefaultAgentProfile(user.getId()));

        // Calculate total deliveries
        int totalDeliveries = agentOrderRepository.countTotalDeliveries(user.getId());

        // Merge data from both tables
        Map<String, Object> profile = new HashMap<>();
        
        // From User table
        profile.put("id", user.getId());
        profile.put("email", user.getEmail());
        profile.put("fullName", user.getFullName());
        profile.put("phone", user.getPhone());
        profile.put("profilePictureUrl", user.getProfilePictureUrl());
        
        // From AgentProfile table
        profile.put("isAvailable", agentProfile.getIsAvailable() != null ? agentProfile.getIsAvailable() : false);
        profile.put("rating", agentProfile.getRating() != null ? agentProfile.getRating() : 0.0);
        profile.put("totalEarnings", agentProfile.getTotalEarnings() != null ? agentProfile.getTotalEarnings() : 0.0);
        profile.put("vehicleType", agentProfile.getVehicleType());
        profile.put("vehicleNumber", agentProfile.getVehicleNumber());
        profile.put("licenseNumber", agentProfile.getLicenseNumber());
        profile.put("joinedDate", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        
        // Calculated fields
        profile.put("totalDeliveries", totalDeliveries);

        return ResponseEntity.ok(profile);
    }

    /**
     * Update personal information (user table)
     */
    @PutMapping("/personal")
    @Transactional
    public ResponseEntity<Map<String, Object>> updatePersonal(@RequestBody Map<String, String> request) {
        String email = CurrentUser.email();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        // Update fields from user table
        if (request.containsKey("fullName")) {
            user.setFullName(request.get("fullName"));
        }
        if (request.containsKey("phone")) {
            user.setPhone(request.get("phone"));
        }

        userRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Personal information updated successfully");

        return ResponseEntity.ok(response);
    }

    /**
     * Update vehicle information (agent_profile table)
     */
    @PutMapping("/vehicle")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateVehicle(@RequestBody Map<String, String> request) {
        String email = CurrentUser.email();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        AgentProfile agentProfile = agentProfileRepository.findByUserId(user.getId())
            .orElseGet(() -> createDefaultAgentProfile(user.getId()));

        // Update fields from agent_profile table
        if (request.containsKey("vehicleType")) {
            agentProfile.setVehicleType(request.get("vehicleType"));
        }
        if (request.containsKey("vehicleNumber")) {
            agentProfile.setVehicleNumber(request.get("vehicleNumber"));
        }
        if (request.containsKey("licenseNumber")) {
            agentProfile.setLicenseNumber(request.get("licenseNumber"));
        }

        agentProfileRepository.save(agentProfile);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Vehicle information updated successfully");

        return ResponseEntity.ok(response);
    }

    /**
     * Upload profile picture
     */
    @PostMapping("/upload-picture")
    @Transactional
    public ResponseEntity<Map<String, String>> uploadProfilePicture(@RequestParam("file") MultipartFile file) {
        String email = CurrentUser.email();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        try {
            // Create uploads directory if it doesn't exist
            Path uploadPath = Paths.get("uploads/agents");
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".") 
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                : ".jpg";
            String filename = "agent_" + user.getId() + "_" + UUID.randomUUID().toString() + extension;

            // Save file
            Path filePath = uploadPath.resolve(filename);
            Files.write(filePath, file.getBytes());

            // Update user profile picture URL
            String fileUrl = "/uploads/agents/" + filename;
            user.setProfilePictureUrl(fileUrl);
            userRepository.save(user);

            Map<String, String> response = new HashMap<>();
            response.put("url", fileUrl);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Change password
     */
    @PostMapping("/change-password")
    @Transactional
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> request) {
        String email = CurrentUser.email();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        String newPassword = request.get("password");
        if (newPassword == null || newPassword.length() < 6) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Password must be at least 6 characters");
            return ResponseEntity.badRequest().body(error);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Password changed successfully");

        return ResponseEntity.ok(response);
    }

    /**
     * Download agent data (GDPR compliance)
     */
    @GetMapping("/download")
    public ResponseEntity<String> downloadData() {
        String email = CurrentUser.email();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        AgentProfile agentProfile = agentProfileRepository.findByUserId(user.getId()).orElse(null);

        // Create JSON with all user data
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"email\": \"").append(user.getEmail()).append("\",\n");
        json.append("  \"fullName\": \"").append(user.getFullName() != null ? user.getFullName() : "").append("\",\n");
        json.append("  \"phone\": \"").append(user.getPhone() != null ? user.getPhone() : "").append("\",\n");
        json.append("  \"role\": \"").append(user.getRole() != null ? user.getRole() : "").append("\",\n");
        
        if (agentProfile != null) {
            json.append("  \"vehicleType\": \"").append(agentProfile.getVehicleType() != null ? agentProfile.getVehicleType() : "").append("\",\n");
            json.append("  \"vehicleNumber\": \"").append(agentProfile.getVehicleNumber() != null ? agentProfile.getVehicleNumber() : "").append("\",\n");
            json.append("  \"licenseNumber\": \"").append(agentProfile.getLicenseNumber() != null ? agentProfile.getLicenseNumber() : "").append("\",\n");
            json.append("  \"rating\": ").append(agentProfile.getRating() != null ? agentProfile.getRating() : 0).append(",\n");
            json.append("  \"totalEarnings\": ").append(agentProfile.getTotalEarnings() != null ? agentProfile.getTotalEarnings() : 0).append("\n");
        }
        
        json.append("}");

        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=agent-data.json")
            .header("Content-Type", "application/json")
            .body(json.toString());
    }

    // Helper method
    private AgentProfile createDefaultAgentProfile(Long userId) {
        AgentProfile profile = new AgentProfile();
        profile.setUserId(userId);
        profile.setIsAvailable(false);
        profile.setRating(0.0);
        profile.setTotalEarnings(0.0);
        profile.setPendingPayout(0.0);
        profile.setLastStatusChange(Instant.now());
        profile.setCreatedAt(Instant.now());
        return agentProfileRepository.save(profile);
    }
}
