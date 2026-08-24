package com.everdeliver.api;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NotificationStatsResponse {
    Instant since;
    long total;
    Map<String, Long> byStatus;
    Map<String, Long> byChannel;
    double successRate;
    Double avgLatencyMs;
    Map<String, Long> failuresByChannel;
}
