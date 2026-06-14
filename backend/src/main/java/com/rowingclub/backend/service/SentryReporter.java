package com.rowingclub.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Minimal, dependency-free Sentry reporter. Does NOT use the Sentry SDK —
 * talks to Sentry's store endpoint directly with an HTTP POST. If SENTRY_DSN
 * is unset, every call is a no-op.
 *
 * Parsed DSN format:  https://{publicKey}@{host}/{projectId}
 * Envelope endpoint:  https://{host}/api/{projectId}/store/
 * Auth header:        Sentry sentry_key={publicKey}, sentry_version=7, ...
 *
 * Fire-and-forget: errors in the reporter itself are swallowed — the app
 * should never fail because of telemetry.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SentryReporter {

    @Value("${sentry.dsn:}")
    private String dsn;

    @Value("${spring.profiles.active:default}")
    private String environment;

    private final ObjectMapper objectMapper;

    private String storeUrl;
    private String publicKey;
    private RestClient client;
    private ExecutorService executor;

    @PostConstruct
    void init() {
        if (dsn == null || dsn.isBlank()) {
            log.info("SentryReporter disabled (no SENTRY_DSN configured)");
            return;
        }
        try {
            URI uri = URI.create(dsn);
            String[] userInfoParts = uri.getUserInfo() == null ? new String[0] : uri.getUserInfo().split(":");
            publicKey = userInfoParts.length > 0 ? userInfoParts[0] : null;
            String projectId = uri.getPath().replaceFirst("^/+", "");
            if (publicKey == null || projectId.isBlank()) {
                log.warn("SentryReporter: DSN missing public key or project id; disabling");
                return;
            }
            storeUrl = uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
                    + "/api/" + projectId + "/store/";
            client = RestClient.create();
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "sentry-reporter");
                t.setDaemon(true);
                return t;
            });
            log.info("SentryReporter enabled (env={}, url={})", environment, storeUrl);
        } catch (Exception e) {
            log.warn("SentryReporter: failed to parse DSN; disabling: {}", e.getMessage());
            storeUrl = null;
        }
    }

    public void capture(Throwable throwable, Map<String, String> extraTags) {
        if (storeUrl == null || executor == null) return;

        Map<String, Object> event = buildEvent(throwable, extraTags);
        executor.submit(() -> {
            try {
                String auth = "Sentry sentry_version=7,sentry_timestamp=" + Instant.now().getEpochSecond()
                        + ",sentry_key=" + publicKey
                        + ",sentry_client=rowingclub-manual/1.0";
                client.post()
                        .uri(storeUrl)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .header("X-Sentry-Auth", auth)
                        .body(objectMapper.writeValueAsString(event))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError,
                                (req, res) -> log.warn("Sentry returned {} while capturing event", res.getStatusCode()))
                        .toBodilessEntity();
            } catch (Exception ignored) {
                // Telemetry must never break the app.
            }
        });
    }

    void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try { executor.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
    }

    private Map<String, Object> buildEvent(Throwable throwable, Map<String, String> extraTags) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_id", UUID.randomUUID().toString().replace("-", ""));
        event.put("timestamp", Instant.now().toString());
        event.put("platform", "java");
        event.put("level", "error");
        event.put("environment", environment);
        event.put("logger", throwable.getClass().getName());
        event.put("message", throwable.getMessage() != null ? throwable.getMessage() : throwable.toString());

        Map<String, String> tags = new HashMap<>();
        tags.put("exception", throwable.getClass().getSimpleName());
        if (extraTags != null) tags.putAll(extraTags);
        event.put("tags", tags);

        Map<String, Object> exception = new LinkedHashMap<>();
        exception.put("type", throwable.getClass().getSimpleName());
        exception.put("value", throwable.getMessage());
        exception.put("module", throwable.getClass().getPackageName());

        Map<String, Object> stacktrace = new LinkedHashMap<>();
        List<Map<String, Object>> frames = new java.util.ArrayList<>();
        for (StackTraceElement ste : throwable.getStackTrace()) {
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("module", ste.getClassName());
            frame.put("function", ste.getMethodName());
            frame.put("filename", ste.getFileName());
            frame.put("lineno", ste.getLineNumber());
            frames.add(frame);
        }
        java.util.Collections.reverse(frames);   // Sentry expects innermost last
        stacktrace.put("frames", frames);
        exception.put("stacktrace", stacktrace);

        event.put("exception", Map.of("values", List.of(exception)));
        return event;
    }
}
