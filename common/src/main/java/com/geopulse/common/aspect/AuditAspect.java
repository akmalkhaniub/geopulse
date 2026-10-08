package com.geopulse.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Cross-cutting audit aspect. Ordered outside @Transactional (Ordered.LOWEST_PRECEDENCE - 1).
 * Extracts caller identity (JWT sub or "system") and execution duration.
 * Raw method arguments (PII, GPS coords) are strictly omitted from log statements.
 */
@Slf4j
@Aspect
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class AuditAspect {

    private final String serviceName;

    public AuditAspect(String serviceName) {
        this.serviceName = serviceName;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        String actor = currentActor();
        String entityId = extractEntityId(pjp, audited);
        long t0 = System.nanoTime();
        try {
            Object result = pjp.proceed();
            long durationMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("AUDIT service={} actor={} action={} entityId={} method={} outcome=OK durationMs={}",
                    serviceName, actor, audited.action(), entityId, pjp.getSignature().toShortString(), durationMs);
            return result;
        } catch (Throwable ex) {
            long durationMs = (System.nanoTime() - t0) / 1_000_000;
            log.warn("AUDIT service={} actor={} action={} entityId={} method={} outcome=FAILED error={} durationMs={}",
                    serviceName, actor, audited.action(), entityId, pjp.getSignature().toShortString(),
                    ex.getClass().getSimpleName(), durationMs);
            throw ex;
        }
    }

    static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return "system";
        }
        return auth.getName();
    }

    private static String extractEntityId(ProceedingJoinPoint pjp, Audited audited) {
        String param = audited.entityIdParam();
        if (!param.isEmpty()) {
            String[] names = ((MethodSignature) pjp.getSignature()).getParameterNames();
            Object[] args = pjp.getArgs();
            if (names != null) {
                for (int i = 0; i < names.length; i++) {
                    if (names[i].equals(param) && args[i] != null) {
                        return args[i].toString();
                    }
                }
            }
        }
        return "none";
    }
}
