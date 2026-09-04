package com.jobpilot.search.dto;

import java.util.List;

public final class SearchDtos {
    private SearchDtos() { }

    public record SearchResultItem(
            String id, String type, String title, String subtitle,
            String status, String targetUrl) { }

    public record SearchGroup(String type, List<SearchResultItem> items) { }

    public record GlobalSearchResult(
            String query, List<String> requestedTypes, List<SearchGroup> groups,
            int total, int limit) { }
}
