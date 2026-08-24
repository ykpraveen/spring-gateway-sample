package com.example.gatewaysample.gateway.web;

import com.example.gatewaysample.gateway.web.exception.InsufficientRoleException;
import com.example.gatewaysample.gateway.web.exception.InvalidApiKeyException;
import com.example.gatewaysample.gateway.web.exception.MissingApiKeyException;
import com.example.gatewaysample.gateway.web.exception.RateLimitExceededException;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Renders the gateway's own credential and rate-limit failures as RFC 9457 Problem Details with a
 * {@code code} extension member, mirroring the {@code @RestControllerAdvice} pattern used in
 * product/pricing-service. A plain {@code WebExceptionHandler} (rather than
 * {@code @RestControllerAdvice}) is used because these failures are raised from gateway filters,
 * not from annotated controllers. The body is written via {@link ServerResponse}/{@link
 * HandlerStrategies} rather than a hand-picked JSON mapper, so it works regardless of which
 * Jackson generation is wired up. Registered ahead of Boot's default handler (order -1) so it
 * gets first refusal on the exception types it knows about.
 */
@Component
@Order(-2)
public class GatewayExceptionHandler implements WebExceptionHandler {

    private static final ServerResponse.Context RESPONSE_CONTEXT = new ServerResponse.Context() {
        private final List<HttpMessageWriter<?>> messageWriters = HandlerStrategies.withDefaults().messageWriters();

        @Override
        public List<HttpMessageWriter<?>> messageWriters() {
            return messageWriters;
        }

        @Override
        public List<ViewResolver> viewResolvers() {
            return List.of();
        }
    };

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status;
        String code;
        String detail;
        String retryAfterSeconds = null;

        if (ex instanceof MissingApiKeyException e) {
            status = HttpStatus.UNAUTHORIZED;
            code = "MISSING_API_KEY";
            detail = e.getMessage();
        } else if (ex instanceof InvalidApiKeyException e) {
            status = HttpStatus.UNAUTHORIZED;
            code = "INVALID_API_KEY";
            detail = e.getMessage();
        } else if (ex instanceof InsufficientRoleException e) {
            status = HttpStatus.FORBIDDEN;
            code = "INSUFFICIENT_ROLE";
            detail = e.getMessage();
        } else if (ex instanceof RateLimitExceededException e) {
            status = HttpStatus.TOO_MANY_REQUESTS;
            code = e.scope().problemCode();
            detail = e.getMessage();
            retryAfterSeconds = "1";
        } else {
            return Mono.error(ex);
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);

        ServerResponse.BodyBuilder responseBuilder =
                ServerResponse.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (retryAfterSeconds != null) {
            responseBuilder.header("Retry-After", retryAfterSeconds);
        }
        return responseBuilder.bodyValue(problem).flatMap(response -> response.writeTo(exchange, RESPONSE_CONTEXT));
    }
}
