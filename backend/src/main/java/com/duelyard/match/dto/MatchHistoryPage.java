package com.duelyard.match.dto;

import java.util.List;

/** A page of match history. {@code hasMore} avoids an expensive total count. */
public record MatchHistoryPage(List<MatchSummaryResponse> items, int page, int size, boolean hasMore) {
}
