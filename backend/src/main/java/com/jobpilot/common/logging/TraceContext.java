package com.jobpilot.common.logging;

import org.slf4j.MDC;

public final class TraceContext {

    public static final String MDC_KEY = "requestTraceId";
    private static final ThreadLocal<String> REQUEST_TRACE = new ThreadLocal<>();

    private TraceContext() {
    }

    public static String getTraceId() {
        String traceId = REQUEST_TRACE.get();
        if (traceId == null) {
            traceId = MDC.get(MDC_KEY);
        }
        return traceId == null ? "unavailable" : traceId;
    }

    static void setTraceId(String traceId) {
        REQUEST_TRACE.set(traceId);
        MDC.put(MDC_KEY, traceId);
    }

    static void clear() {
        REQUEST_TRACE.remove();
        MDC.remove(MDC_KEY);
    }
}
