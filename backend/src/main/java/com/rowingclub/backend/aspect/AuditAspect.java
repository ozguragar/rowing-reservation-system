package com.rowingclub.backend.aspect;

import com.rowingclub.backend.entity.AuditLog;
import com.rowingclub.backend.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;

    @AfterReturning(
        pointcut = "@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
                   "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
                   "@annotation(org.springframework.web.bind.annotation.DeleteMapping) || " +
                   "@annotation(org.springframework.web.bind.annotation.PatchMapping)",
        returning = "result"
    )
    public void auditDataChange(JoinPoint joinPoint, Object result) {
        try {
            String email = getCurrentUserEmail();
            String action = joinPoint.getSignature().getName();
            String endpoint = getRequestEndpoint();

            AuditLog auditLog = AuditLog.builder()
                    .userEmail(email != null ? email : "anonymous")
                    .action(action)
                    .endpoint(endpoint)
                    .timestamp(LocalDateTime.now())
                    .details(joinPoint.getSignature().toShortString())
                    .ipAddress(getClientIp())
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to save audit log: {}", e.getMessage());
        }
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null) {
            return auth.getPrincipal().toString();
        }
        return null;
    }

    private String getRequestEndpoint() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            return request.getMethod() + " " + request.getRequestURI();
        }
        return "unknown";
    }

    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        HttpServletRequest request = attrs.getRequest();
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
