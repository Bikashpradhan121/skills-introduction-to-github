package com.hungerexpress.coupons;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponsController {
    private final CouponRepository coupons;

    @GetMapping("/validate")
    public ResponseEntity<?> validate(@RequestParam String code, @RequestParam BigDecimal amount){
        return coupons.findByCodeAndActiveIsTrue(code.toUpperCase()).map(c -> {
            if (c.getExpiresAt() != null && c.getExpiresAt().isBefore(Instant.now())) return ResponseEntity.status(410).build();
            if (c.getMinAmount() != null && amount.compareTo(c.getMinAmount()) < 0) return ResponseEntity.badRequest().build();
            BigDecimal discount = BigDecimal.ZERO;
            if (c.getPercentOff() != null) discount = amount.multiply(BigDecimal.valueOf(c.getPercentOff()).movePointLeft(2));
            if (c.getAmountOff() != null) discount = discount.add(c.getAmountOff());
            if (discount.compareTo(amount) > 0) discount = amount;
            Map<String,Object> res = new HashMap<>();
            res.put("code", c.getCode());
            res.put("description", c.getDescription());
            res.put("discount", discount);
            return ResponseEntity.ok(res);
        }).orElse(ResponseEntity.notFound().build());
    }
}
