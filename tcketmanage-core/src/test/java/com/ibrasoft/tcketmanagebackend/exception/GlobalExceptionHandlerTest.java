package com.ibrasoft.tcketmanagebackend.exception;

import com.ibrasoft.tcketmanagebackend.model.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpServletRequest request = new MockHttpServletRequest("GET", "/tcket/events");

    @Test
    void mapsNotFoundTo404() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotFound(new ResourceNotFoundException("no such event"), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("no such event", response.getBody().getMessage());
    }

    @Test
    void mapsUnexpectedTo500() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGeneric(new RuntimeException("boom"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred", response.getBody().getMessage());
    }

    /**
     * Denials must leave this advice untouched so Spring Security's {@code ExceptionTranslationFilter}
     * can turn them into a 403 (or an authentication challenge). Answering them here — which the
     * catch-all at highest precedence would otherwise do — turns every denied call to a core endpoint
     * into a 500.
     */
    @Test
    void rethrowsAccessDeniedRatherThanAnsweringIt() {
        AccessDeniedException denial = new AccessDeniedException("denied");

        AccessDeniedException thrown = assertThrows(AccessDeniedException.class,
                () -> handler.rethrowSecurity(denial));

        assertSame(denial, thrown);
    }

    @Test
    void rethrowsAuthenticationFailureRatherThanAnsweringIt() {
        InsufficientAuthenticationException unauthenticated =
                new InsufficientAuthenticationException("anonymous");

        assertSame(unauthenticated, assertThrows(InsufficientAuthenticationException.class,
                () -> handler.rethrowSecurity(unauthenticated)));
    }

    /**
     * The rethrow only helps if Spring actually routes security exceptions to it rather than to the
     * catch-all, so assert the resolution the container will perform.
     */
    @Test
    void securityExceptionsResolveToTheRethrowingHandler() throws Exception {
        ExceptionHandlerMethodResolver resolver =
                new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);
        Method rethrow = GlobalExceptionHandler.class.getMethod("rethrowSecurity", RuntimeException.class);

        assertEquals(rethrow, resolver.resolveMethod(new AccessDeniedException("denied")));
        assertEquals(rethrow, resolver.resolveMethod(new InsufficientAuthenticationException("anonymous")));

        Method generic = resolver.resolveMethod(new IllegalStateException("boom"));
        assertNotNull(generic);
        assertEquals("handleGeneric", generic.getName());
    }
}
