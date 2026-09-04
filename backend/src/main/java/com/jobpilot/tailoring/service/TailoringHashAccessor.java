package com.jobpilot.tailoring.service;

public final class TailoringHashAccessor {
    private TailoringHashAccessor() { }
    public static String sha256(String value) { return TailoringHash.sha256(value); }
}
