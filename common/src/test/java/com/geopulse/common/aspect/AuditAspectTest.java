package com.geopulse.common.aspect;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditAspectTest {

    static class SampleService {
        @Audited(action = "TEST_ACTION", entityIdParam = "entityId")
        public String doWork(String entityId) {
            return "ok-" + entityId;
        }

        @Audited(action = "FAIL_ACTION")
        public void fail() {
            throw new IllegalStateException("boom");
        }
    }

    private SampleService proxy;

    @BeforeEach
    void setUp() {
        AuditAspect aspect = new AuditAspect("test-service");
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.addAspect(aspect);
        this.proxy = factory.getProxy();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditsSuccessfulMethodCallWithActor() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-123", "n/a"));

        String res = proxy.doWork("device-99");
        assertThat(res).isEqualTo("ok-device-99");
    }

    @Test
    void auditsFailingMethodCallWithSystemActor() {
        assertThatThrownBy(() -> proxy.fail())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }
}
