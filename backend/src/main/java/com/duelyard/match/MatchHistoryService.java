package com.duelyard.match;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duelyard.match.dto.MatchHistoryPage;
import com.duelyard.match.dto.MatchSummaryResponse;

/** Read-only match history. Two queries per page regardless of page size. */
@Service
public class MatchHistoryService {

    public static final int MAX_PAGE_SIZE = 50;
    private static final List<MatchStatus> SHOWN = List.of(MatchStatus.COMPLETED, MatchStatus.ABANDONED);

    private final MatchPlayerRepository matchPlayers;

    public MatchHistoryService(MatchPlayerRepository matchPlayers) {
        this.matchPlayers = matchPlayers;
    }

    @Transactional(readOnly = true)
    public MatchHistoryPage history(Long userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Slice<MatchPlayer> slice = matchPlayers.findHistory(userId, SHOWN, PageRequest.of(safePage, safeSize));

        List<UUID> matchIds = slice.getContent().stream().map(mp -> mp.getMatch().getId()).toList();
        Map<UUID, List<MatchPlayer>> opponents = matchIds.isEmpty()
            ? Map.of()
            : matchPlayers.findOpponents(matchIds, userId).stream()
                .collect(Collectors.groupingBy(mp -> mp.getMatch().getId()));

        List<MatchSummaryResponse> items = slice.getContent().stream()
            .map(mp -> toSummary(mp, opponents.getOrDefault(mp.getMatch().getId(), List.of())))
            .toList();
        return new MatchHistoryPage(items, safePage, safeSize, slice.hasNext());
    }

    private static MatchSummaryResponse toSummary(MatchPlayer self, List<MatchPlayer> others) {
        Match match = self.getMatch();
        Integer change = self.getRatingBefore() != null && self.getRatingAfter() != null
            ? self.getRatingAfter() - self.getRatingBefore()
            : null;
        List<MatchSummaryResponse.Opponent> opponents = others.stream()
            .map(o -> new MatchSummaryResponse.Opponent(
                o.getUser().getUsername(),
                o.getUser().getAvatarUrl(),
                o.getResult() == null ? null : o.getResult().name(),
                o.getScore()))
            .toList();
        return new MatchSummaryResponse(
            match.getId(),
            match.getGame().getSlug(),
            match.getGame().getDisplayName(),
            match.getMode().name(),
            match.getStatus().name(),
            match.getEndReason() == null ? null : match.getEndReason().name(),
            self.getResult() == null ? null : self.getResult().name(),
            self.getScore(),
            self.getRatingBefore(),
            self.getRatingAfter(),
            change,
            match.getEndedAt(),
            opponents);
    }
}
