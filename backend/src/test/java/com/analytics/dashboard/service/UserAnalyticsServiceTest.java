package com.analytics.dashboard.service;

import com.analytics.dashboard.dto.*;
import com.analytics.dashboard.entity.AgentRun;
import com.analytics.dashboard.entity.AgentType;
import com.analytics.dashboard.entity.User;
import com.analytics.dashboard.repository.AgentRunRepository;
import com.analytics.dashboard.repository.AgentTypeRepository;
import com.analytics.dashboard.repository.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static com.analytics.dashboard.service.TestRunFactory.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAnalyticsServiceTest {

    @Mock
    private AgentRunRepository agentRunRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AgentTypeRepository agentTypeRepository;

    @InjectMocks
    private UserAnalyticsService userAnalyticsService;

    @Nested
    class GetUserSummary {

        @Test
        void returnsCorrectUserSummary() {
            User user = new User(USER_ID_1, ORG_ID, "ext-u1", "user1@test.com", "Alice Chen", "hash", "MEMBER");
            when(userRepository.findById(USER_ID_1)).thenReturn(Optional.of(user));

            List<AgentRun> userRuns = List.of(
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L),
                    createFailedRun(TEAM_ID_1, USER_ID_1)
            );
            List<AgentRun> orgRuns = List.of(
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L),
                    createFailedRun(TEAM_ID_1, USER_ID_1),
                    createSucceededRun(TEAM_ID_1, USER_ID_2, 2000L, new BigDecimal("0.20"), 3000L)
            );

            when(agentRunRepository.findUserFiltered(eq(USER_ID_1), any(), any(), isNull(), isNull()))
                    .thenReturn(userRuns);
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(orgRuns);

            UserSummaryResponse result = userAnalyticsService.getUserSummary(USER_ID_1, ORG_ID, FROM, TO, null, null);

            assertThat(result.userId()).isEqualTo(USER_ID_1);
            assertThat(result.displayName()).isEqualTo("Alice Chen");
            assertThat(result.totalRuns()).isEqualTo(2);
            assertThat(result.succeededRuns()).isEqualTo(1);
            assertThat(result.failedRuns()).isEqualTo(1);
            assertThat(result.totalTokens()).isEqualTo(1500L);
            assertThat(result.teamSize()).isEqualTo(2);
        }

        @Test
        void calculatesRankCorrectly() {
            User user = new User(USER_ID_1, ORG_ID, "ext-u1", "user1@test.com", "Alice Chen", "hash", "MEMBER");
            when(userRepository.findById(USER_ID_1)).thenReturn(Optional.of(user));

            // User1 has 1 run, User2 has 3 runs => User1 ranks 2nd
            List<AgentRun> userRuns = List.of(
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L)
            );
            List<AgentRun> orgRuns = List.of(
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L),
                    createSucceededRun(TEAM_ID_1, USER_ID_2, 2000L, new BigDecimal("0.20"), 3000L),
                    createSucceededRun(TEAM_ID_1, USER_ID_2, 2000L, new BigDecimal("0.20"), 3000L),
                    createSucceededRun(TEAM_ID_1, USER_ID_2, 2000L, new BigDecimal("0.20"), 3000L)
            );

            when(agentRunRepository.findUserFiltered(eq(USER_ID_1), any(), any(), isNull(), isNull()))
                    .thenReturn(userRuns);
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(orgRuns);

            UserSummaryResponse result = userAnalyticsService.getUserSummary(USER_ID_1, ORG_ID, FROM, TO, null, null);

            assertThat(result.teamRank()).isEqualTo(2);
            assertThat(result.teamSize()).isEqualTo(2);
        }

        @Test
        void handlesEmptyRunsForUser() {
            when(userRepository.findById(USER_ID_1)).thenReturn(Optional.empty());
            when(agentRunRepository.findUserFiltered(eq(USER_ID_1), any(), any(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());

            UserSummaryResponse result = userAnalyticsService.getUserSummary(USER_ID_1, ORG_ID, FROM, TO, null, null);

            assertThat(result.totalRuns()).isZero();
            assertThat(result.displayName()).isEqualTo("Unknown");
            assertThat(result.totalCost()).isEqualTo("0.000000");
            assertThat(result.avgDurationMs()).isZero();
        }
    }

    @Nested
    class GetUserTimeseries {

        @Test
        void returnsTimeseriesForUser() {
            when(agentRunRepository.findUserFiltered(eq(USER_ID_1), any(), any(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());

            TimeseriesResponse result = userAnalyticsService.getUserTimeseries(USER_ID_1, FROM, TO, null, null);

            assertThat(result.dataPoints()).isEmpty();
            assertThat(result.granularity()).isEqualTo("DAILY");
        }
    }

    @Nested
    class GetUserRuns {

        @Test
        void returnsRunListWithLimit() {
            AgentType type = new AgentType(UUID.randomUUID(), ORG_ID, "code-review", "Code Review");
            List<AgentRun> runs = List.of(
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L),
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 2000L, new BigDecimal("0.20"), 3000L),
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 3000L, new BigDecimal("0.30"), 4000L)
            );

            when(agentRunRepository.findUserFiltered(eq(USER_ID_1), any(), any(), isNull(), isNull()))
                    .thenReturn(runs);
            when(agentTypeRepository.findByOrgId(ORG_ID)).thenReturn(List.of(type));

            RunListResponse result = userAnalyticsService.getUserRuns(USER_ID_1, FROM, TO, null, null, 2);

            assertThat(result.runs()).hasSize(2);
            assertThat(result.hasMore()).isTrue();
        }

        @Test
        void hasMoreIsFalseWhenAllRunsReturned() {
            AgentType type = new AgentType(UUID.randomUUID(), ORG_ID, "code-review", "Code Review");
            List<AgentRun> runs = List.of(
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L)
            );

            when(agentRunRepository.findUserFiltered(eq(USER_ID_1), any(), any(), isNull(), isNull()))
                    .thenReturn(runs);
            when(agentTypeRepository.findByOrgId(ORG_ID)).thenReturn(List.of(type));

            RunListResponse result = userAnalyticsService.getUserRuns(USER_ID_1, FROM, TO, null, null, 50);

            assertThat(result.runs()).hasSize(1);
            assertThat(result.hasMore()).isFalse();
        }

        @Test
        void mapsRunFieldsCorrectly() {
            AgentType type = new AgentType(UUID.randomUUID(), ORG_ID, "code-review", "Code Review");
            AgentRun run = createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L);

            when(agentRunRepository.findUserFiltered(eq(USER_ID_1), any(), any(), isNull(), isNull()))
                    .thenReturn(List.of(run));
            when(agentTypeRepository.findByOrgId(ORG_ID)).thenReturn(List.of(type));

            RunListResponse result = userAnalyticsService.getUserRuns(USER_ID_1, FROM, TO, null, null, 50);

            RunListResponse.RunSummary summary = result.runs().get(0);
            assertThat(summary.runId()).isEqualTo(run.getId());
            assertThat(summary.status()).isEqualTo("SUCCEEDED");
            assertThat(summary.agentTypeDisplayName()).isEqualTo("Code Review");
            assertThat(summary.totalTokens()).isEqualTo(1000L);
            assertThat(summary.durationMs()).isEqualTo(5000L);
        }

        @Test
        void returnsEmptyListForNoRuns() {
            when(agentRunRepository.findUserFiltered(eq(USER_ID_1), any(), any(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());

            RunListResponse result = userAnalyticsService.getUserRuns(USER_ID_1, FROM, TO, null, null, 50);

            assertThat(result.runs()).isEmpty();
            assertThat(result.hasMore()).isFalse();
        }
    }

    @Nested
    class GetRunDetail {

        @Test
        void returnsDetailedRunInformation() {
            UUID runId = UUID.randomUUID();
            AgentRun run = createRun(runId, ORG_ID, TEAM_ID_1, USER_ID_1,
                    "SUCCEEDED", 1000L, new BigDecimal("0.10"), 5000L, "code-review",
                    Instant.parse("2025-01-15T10:00:00Z"));
            run.setErrorCategory(null);
            run.setErrorMessage(null);

            AgentType type = new AgentType(UUID.randomUUID(), ORG_ID, "code-review", "Code Review");

            when(agentRunRepository.findById(runId)).thenReturn(Optional.of(run));
            when(agentTypeRepository.findByOrgIdAndSlug(ORG_ID, "code-review")).thenReturn(Optional.of(type));

            RunDetailResponse result = userAnalyticsService.getRunDetail(runId);

            assertThat(result.runId()).isEqualTo(runId);
            assertThat(result.orgId()).isEqualTo(ORG_ID);
            assertThat(result.teamId()).isEqualTo(TEAM_ID_1);
            assertThat(result.userId()).isEqualTo(USER_ID_1);
            assertThat(result.agentType()).isEqualTo("code-review");
            assertThat(result.agentTypeDisplayName()).isEqualTo("Code Review");
            assertThat(result.status()).isEqualTo("SUCCEEDED");
            assertThat(result.durationMs()).isEqualTo(5000L);
        }

        @Test
        void throwsWhenRunNotFound() {
            UUID runId = UUID.randomUUID();
            when(agentRunRepository.findById(runId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userAnalyticsService.getRunDetail(runId))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining(runId.toString());
        }

        @Test
        void fallsBackToSlugWhenAgentTypeNotFound() {
            UUID runId = UUID.randomUUID();
            AgentRun run = createRun(runId, ORG_ID, TEAM_ID_1, USER_ID_1,
                    "SUCCEEDED", 1000L, new BigDecimal("0.10"), 5000L, "unknown-slug",
                    Instant.parse("2025-01-15T10:00:00Z"));

            when(agentRunRepository.findById(runId)).thenReturn(Optional.of(run));
            when(agentTypeRepository.findByOrgIdAndSlug(ORG_ID, "unknown-slug")).thenReturn(Optional.empty());

            RunDetailResponse result = userAnalyticsService.getRunDetail(runId);

            assertThat(result.agentTypeDisplayName()).isEqualTo("unknown-slug");
        }

        @Test
        void handlesNullFinishedAtAndDuration() {
            UUID runId = UUID.randomUUID();
            AgentRun run = createRun(runId, ORG_ID, TEAM_ID_1, USER_ID_1,
                    "RUNNING", 500L, new BigDecimal("0.05"), null, "code-review",
                    Instant.parse("2025-01-15T10:00:00Z"));
            run.setFinishedAt(null);

            when(agentRunRepository.findById(runId)).thenReturn(Optional.of(run));
            when(agentTypeRepository.findByOrgIdAndSlug(ORG_ID, "code-review")).thenReturn(Optional.empty());

            RunDetailResponse result = userAnalyticsService.getRunDetail(runId);

            assertThat(result.finishedAt()).isNull();
            assertThat(result.durationMs()).isZero();
        }

        @Test
        void includesErrorInformation() {
            UUID runId = UUID.randomUUID();
            AgentRun run = createRun(runId, ORG_ID, TEAM_ID_1, USER_ID_1,
                    "FAILED", 500L, new BigDecimal("0.05"), 2000L, "code-review",
                    Instant.parse("2025-01-15T10:00:00Z"));
            run.setErrorCategory("TIMEOUT");
            run.setErrorMessage("Operation timed out");

            when(agentRunRepository.findById(runId)).thenReturn(Optional.of(run));
            when(agentTypeRepository.findByOrgIdAndSlug(ORG_ID, "code-review")).thenReturn(Optional.empty());

            RunDetailResponse result = userAnalyticsService.getRunDetail(runId);

            assertThat(result.errorCategory()).isEqualTo("TIMEOUT");
            assertThat(result.errorMessage()).isEqualTo("Operation timed out");
        }
    }
}
