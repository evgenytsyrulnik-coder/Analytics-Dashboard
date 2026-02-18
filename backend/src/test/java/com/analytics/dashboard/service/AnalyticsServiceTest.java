package com.analytics.dashboard.service;

import com.analytics.dashboard.dto.*;
import com.analytics.dashboard.entity.AgentRun;
import com.analytics.dashboard.entity.AgentType;
import com.analytics.dashboard.entity.Team;
import com.analytics.dashboard.entity.User;
import com.analytics.dashboard.repository.AgentRunRepository;
import com.analytics.dashboard.repository.AgentTypeRepository;
import com.analytics.dashboard.repository.TeamRepository;
import com.analytics.dashboard.repository.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private AgentRunRepository agentRunRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AgentTypeRepository agentTypeRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID TEAM_ID_1 = UUID.randomUUID();
    private static final UUID TEAM_ID_2 = UUID.randomUUID();
    private static final UUID USER_ID_1 = UUID.randomUUID();
    private static final UUID USER_ID_2 = UUID.randomUUID();
    private static final String FROM = "2025-01-01";
    private static final String TO = "2025-01-31";

    private AgentRun createRun(UUID id, UUID orgId, UUID teamId, UUID userId,
                                String status, long totalTokens, BigDecimal totalCost,
                                Long durationMs, String agentTypeSlug, Instant startedAt) {
        AgentRun run = new AgentRun();
        run.setId(id);
        run.setOrgId(orgId);
        run.setTeamId(teamId);
        run.setUserId(userId);
        run.setStatus(status);
        run.setInputTokens(totalTokens / 2);
        run.setOutputTokens(totalTokens / 2);
        run.setTotalTokens(totalTokens);
        run.setInputCost(totalCost.divide(BigDecimal.valueOf(2)));
        run.setOutputCost(totalCost.divide(BigDecimal.valueOf(2)));
        run.setTotalCost(totalCost);
        run.setDurationMs(durationMs);
        run.setAgentTypeSlug(agentTypeSlug != null ? agentTypeSlug : "code-review");
        run.setStartedAt(startedAt != null ? startedAt : Instant.parse("2025-01-15T10:00:00Z"));
        run.setFinishedAt(durationMs != null ? run.getStartedAt().plusMillis(durationMs) : null);
        run.setModelName("gpt-4");
        run.setModelVersion("v1");
        run.setCreatedAt(Instant.now());
        return run;
    }

    private AgentRun createSucceededRun(UUID teamId, UUID userId, long tokens, BigDecimal cost, long durationMs) {
        return createRun(UUID.randomUUID(), ORG_ID, teamId, userId, "SUCCEEDED",
                tokens, cost, durationMs, "code-review", Instant.parse("2025-01-15T10:00:00Z"));
    }

    private AgentRun createFailedRun(UUID teamId, UUID userId) {
        return createRun(UUID.randomUUID(), ORG_ID, teamId, userId, "FAILED",
                500L, new BigDecimal("0.05"), 2000L, "code-review", Instant.parse("2025-01-15T10:00:00Z"));
    }

    @Nested
    class GetOrgSummary {

        @Test
        void returnsCorrectSummaryForMultipleRuns() {
            List<AgentRun> runs = List.of(
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L),
                    createSucceededRun(TEAM_ID_1, USER_ID_2, 2000L, new BigDecimal("0.20"), 3000L),
                    createFailedRun(TEAM_ID_2, USER_ID_1)
            );
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(runs);

            AnalyticsSummaryResponse result = analyticsService.getOrgSummary(ORG_ID, FROM, TO, null, null, null);

            assertThat(result.totalRuns()).isEqualTo(3);
            assertThat(result.succeededRuns()).isEqualTo(2);
            assertThat(result.failedRuns()).isEqualTo(1);
            assertThat(result.totalTokens()).isEqualTo(3500L);
            assertThat(result.orgId()).isEqualTo(ORG_ID);
        }

        @Test
        void returnsZerosForEmptyRunList() {
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());

            AnalyticsSummaryResponse result = analyticsService.getOrgSummary(ORG_ID, FROM, TO, null, null, null);

            assertThat(result.totalRuns()).isZero();
            assertThat(result.succeededRuns()).isZero();
            assertThat(result.failedRuns()).isZero();
            assertThat(result.successRate()).isZero();
            assertThat(result.totalTokens()).isZero();
            assertThat(result.totalCost()).isEqualTo("0.000000");
            assertThat(result.avgDurationMs()).isZero();
            assertThat(result.p50DurationMs()).isZero();
        }

        @Test
        void calculatesSuccessRateCorrectly() {
            List<AgentRun> runs = List.of(
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L),
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 3000L),
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 4000L),
                    createFailedRun(TEAM_ID_1, USER_ID_1)
            );
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(runs);

            AnalyticsSummaryResponse result = analyticsService.getOrgSummary(ORG_ID, FROM, TO, null, null, null);

            assertThat(result.successRate()).isEqualTo(0.75);
        }

        @Test
        void calculatesDurationPercentilesCorrectly() {
            List<AgentRun> runs = new ArrayList<>();
            for (int i = 1; i <= 100; i++) {
                runs.add(createSucceededRun(TEAM_ID_1, USER_ID_1, 100L, new BigDecimal("0.01"), (long) i * 100));
            }
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(runs);

            AnalyticsSummaryResponse result = analyticsService.getOrgSummary(ORG_ID, FROM, TO, null, null, null);

            assertThat(result.p50DurationMs()).isEqualTo(5000L);
            assertThat(result.p95DurationMs()).isEqualTo(9500L);
            assertThat(result.p99DurationMs()).isEqualTo(9900L);
        }

        @Test
        void passesFiltersToRepository() {
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), eq(TEAM_ID_1), eq("code-review"), eq("SUCCEEDED")))
                    .thenReturn(Collections.emptyList());

            analyticsService.getOrgSummary(ORG_ID, FROM, TO, TEAM_ID_1, "code-review", "SUCCEEDED");

            verify(agentRunRepository).findFiltered(eq(ORG_ID), any(), any(), eq(TEAM_ID_1), eq("code-review"), eq("SUCCEEDED"));
        }

        @Test
        void aggregatesCostWithCorrectScale() {
            List<AgentRun> runs = List.of(
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.123456"), 5000L),
                    createSucceededRun(TEAM_ID_1, USER_ID_2, 2000L, new BigDecimal("0.654321"), 3000L)
            );
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(runs);

            AnalyticsSummaryResponse result = analyticsService.getOrgSummary(ORG_ID, FROM, TO, null, null, null);

            assertThat(result.totalCost()).isEqualTo("0.777777");
        }

        @Test
        void countsCancelledAndRunningStatus() {
            AgentRun cancelledRun = createRun(UUID.randomUUID(), ORG_ID, TEAM_ID_1, USER_ID_1,
                    "CANCELLED", 100L, BigDecimal.ONE, 1000L, "code-review", Instant.parse("2025-01-15T10:00:00Z"));
            AgentRun runningRun = createRun(UUID.randomUUID(), ORG_ID, TEAM_ID_1, USER_ID_1,
                    "RUNNING", 100L, BigDecimal.ONE, null, "code-review", Instant.parse("2025-01-15T10:00:00Z"));
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(List.of(cancelledRun, runningRun));

            AnalyticsSummaryResponse result = analyticsService.getOrgSummary(ORG_ID, FROM, TO, null, null, null);

            assertThat(result.cancelledRuns()).isEqualTo(1);
            assertThat(result.runningRuns()).isEqualTo(1);
        }

        @Test
        void handlesRunsWithNullDuration() {
            AgentRun runWithDuration = createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L);
            AgentRun runWithoutDuration = createRun(UUID.randomUUID(), ORG_ID, TEAM_ID_1, USER_ID_1,
                    "RUNNING", 500L, new BigDecimal("0.05"), null, "code-review", Instant.parse("2025-01-15T10:00:00Z"));
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(List.of(runWithDuration, runWithoutDuration));

            AnalyticsSummaryResponse result = analyticsService.getOrgSummary(ORG_ID, FROM, TO, null, null, null);

            assertThat(result.avgDurationMs()).isEqualTo(5000L);
            assertThat(result.p50DurationMs()).isEqualTo(5000L);
        }

        @Test
        void separatesInputAndOutputTokens() {
            AgentRun run = new AgentRun();
            run.setId(UUID.randomUUID());
            run.setOrgId(ORG_ID);
            run.setTeamId(TEAM_ID_1);
            run.setUserId(USER_ID_1);
            run.setStatus("SUCCEEDED");
            run.setInputTokens(300L);
            run.setOutputTokens(700L);
            run.setTotalTokens(1000L);
            run.setInputCost(new BigDecimal("0.03"));
            run.setOutputCost(new BigDecimal("0.07"));
            run.setTotalCost(new BigDecimal("0.10"));
            run.setDurationMs(5000L);
            run.setAgentTypeSlug("code-review");
            run.setStartedAt(Instant.parse("2025-01-15T10:00:00Z"));
            run.setFinishedAt(Instant.parse("2025-01-15T10:00:05Z"));
            run.setCreatedAt(Instant.now());

            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(List.of(run));

            AnalyticsSummaryResponse result = analyticsService.getOrgSummary(ORG_ID, FROM, TO, null, null, null);

            assertThat(result.totalInputTokens()).isEqualTo(300L);
            assertThat(result.totalOutputTokens()).isEqualTo(700L);
            assertThat(result.totalTokens()).isEqualTo(1000L);
        }
    }

    @Nested
    class GetTeamSummary {

        @Test
        void returnsCorrectSummaryForTeam() {
            Team team = new Team(TEAM_ID_1, ORG_ID, "ext-1", "Engineering");
            List<AgentRun> runs = List.of(
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L)
            );
            when(agentRunRepository.findTeamFiltered(eq(TEAM_ID_1), any(), any(), isNull(), isNull()))
                    .thenReturn(runs);
            when(teamRepository.findById(TEAM_ID_1)).thenReturn(Optional.of(team));

            AnalyticsSummaryResponse result = analyticsService.getTeamSummary(TEAM_ID_1, FROM, TO, null, null);

            assertThat(result.totalRuns()).isEqualTo(1);
            assertThat(result.orgId()).isEqualTo(ORG_ID);
        }

        @Test
        void throwsWhenTeamNotFound() {
            when(agentRunRepository.findTeamFiltered(eq(TEAM_ID_1), any(), any(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());
            when(teamRepository.findById(TEAM_ID_1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> analyticsService.getTeamSummary(TEAM_ID_1, FROM, TO, null, null))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void passesFiltersToTeamRepository() {
            Team team = new Team(TEAM_ID_1, ORG_ID, "ext-1", "Engineering");
            when(agentRunRepository.findTeamFiltered(eq(TEAM_ID_1), any(), any(), eq("code-review"), eq("FAILED")))
                    .thenReturn(Collections.emptyList());
            when(teamRepository.findById(TEAM_ID_1)).thenReturn(Optional.of(team));

            analyticsService.getTeamSummary(TEAM_ID_1, FROM, TO, "code-review", "FAILED");

            verify(agentRunRepository).findTeamFiltered(eq(TEAM_ID_1), any(), any(), eq("code-review"), eq("FAILED"));
        }
    }

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

            UserSummaryResponse result = analyticsService.getUserSummary(USER_ID_1, ORG_ID, FROM, TO, null, null);

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

            UserSummaryResponse result = analyticsService.getUserSummary(USER_ID_1, ORG_ID, FROM, TO, null, null);

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

            UserSummaryResponse result = analyticsService.getUserSummary(USER_ID_1, ORG_ID, FROM, TO, null, null);

            assertThat(result.totalRuns()).isZero();
            assertThat(result.displayName()).isEqualTo("Unknown");
            assertThat(result.totalCost()).isEqualTo("0.000000");
            assertThat(result.avgDurationMs()).isZero();
        }
    }

    @Nested
    class GetOrgTimeseries {

        @Test
        void groupsRunsByDay() {
            AgentRun run1 = createRun(UUID.randomUUID(), ORG_ID, TEAM_ID_1, USER_ID_1,
                    "SUCCEEDED", 1000L, new BigDecimal("0.10"), 5000L, "code-review",
                    Instant.parse("2025-01-10T10:00:00Z"));
            AgentRun run2 = createRun(UUID.randomUUID(), ORG_ID, TEAM_ID_1, USER_ID_1,
                    "SUCCEEDED", 2000L, new BigDecimal("0.20"), 3000L, "code-review",
                    Instant.parse("2025-01-10T15:00:00Z"));
            AgentRun run3 = createRun(UUID.randomUUID(), ORG_ID, TEAM_ID_1, USER_ID_1,
                    "FAILED", 500L, new BigDecimal("0.05"), 2000L, "code-review",
                    Instant.parse("2025-01-11T09:00:00Z"));

            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(List.of(run1, run2, run3));

            TimeseriesResponse result = analyticsService.getOrgTimeseries(ORG_ID, FROM, TO, null, null, null, null);

            assertThat(result.dataPoints()).hasSize(2);
            assertThat(result.granularity()).isEqualTo("DAILY");

            TimeseriesResponse.DataPoint day1 = result.dataPoints().get(0);
            assertThat(day1.timestamp()).isEqualTo("2025-01-10T00:00:00Z");
            assertThat(day1.totalRuns()).isEqualTo(2);
            assertThat(day1.succeededRuns()).isEqualTo(2);
            assertThat(day1.failedRuns()).isZero();

            TimeseriesResponse.DataPoint day2 = result.dataPoints().get(1);
            assertThat(day2.timestamp()).isEqualTo("2025-01-11T00:00:00Z");
            assertThat(day2.totalRuns()).isEqualTo(1);
            assertThat(day2.failedRuns()).isEqualTo(1);
        }

        @Test
        void returnsEmptyDataPointsForNoRuns() {
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());

            TimeseriesResponse result = analyticsService.getOrgTimeseries(ORG_ID, FROM, TO, null, null, null, null);

            assertThat(result.dataPoints()).isEmpty();
            assertThat(result.orgId()).isEqualTo(ORG_ID);
        }

        @Test
        void usesCustomGranularity() {
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());

            TimeseriesResponse result = analyticsService.getOrgTimeseries(ORG_ID, FROM, TO, null, null, null, "HOURLY");

            assertThat(result.granularity()).isEqualTo("HOURLY");
        }

        @Test
        void defaultsToDailyGranularity() {
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());

            TimeseriesResponse result = analyticsService.getOrgTimeseries(ORG_ID, FROM, TO, null, null, null, null);

            assertThat(result.granularity()).isEqualTo("DAILY");
        }

        @Test
        void dataPointsSortedByDate() {
            AgentRun laterRun = createRun(UUID.randomUUID(), ORG_ID, TEAM_ID_1, USER_ID_1,
                    "SUCCEEDED", 100L, BigDecimal.ONE, 1000L, "code-review",
                    Instant.parse("2025-01-20T10:00:00Z"));
            AgentRun earlierRun = createRun(UUID.randomUUID(), ORG_ID, TEAM_ID_1, USER_ID_1,
                    "SUCCEEDED", 100L, BigDecimal.ONE, 1000L, "code-review",
                    Instant.parse("2025-01-05T10:00:00Z"));

            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(List.of(laterRun, earlierRun));

            TimeseriesResponse result = analyticsService.getOrgTimeseries(ORG_ID, FROM, TO, null, null, null, null);

            assertThat(result.dataPoints().get(0).timestamp()).isLessThan(result.dataPoints().get(1).timestamp());
        }
    }

    @Nested
    class GetTeamTimeseries {

        @Test
        void returnsTimeseriesForTeam() {
            Team team = new Team(TEAM_ID_1, ORG_ID, "ext-1", "Engineering");
            when(agentRunRepository.findTeamFiltered(eq(TEAM_ID_1), any(), any(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());
            when(teamRepository.findById(TEAM_ID_1)).thenReturn(Optional.of(team));

            TimeseriesResponse result = analyticsService.getTeamTimeseries(TEAM_ID_1, FROM, TO, null, null, null);

            assertThat(result.orgId()).isEqualTo(ORG_ID);
            assertThat(result.granularity()).isEqualTo("DAILY");
        }
    }

    @Nested
    class GetUserTimeseries {

        @Test
        void returnsTimeseriesForUser() {
            when(agentRunRepository.findUserFiltered(eq(USER_ID_1), any(), any(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());

            TimeseriesResponse result = analyticsService.getUserTimeseries(USER_ID_1, FROM, TO, null, null);

            assertThat(result.dataPoints()).isEmpty();
            assertThat(result.granularity()).isEqualTo("DAILY");
        }
    }

    @Nested
    class GetByTeam {

        @Test
        void breaksDownRunsByTeam() {
            Team team1 = new Team(TEAM_ID_1, ORG_ID, "ext-1", "Engineering");
            Team team2 = new Team(TEAM_ID_2, ORG_ID, "ext-2", "Data Science");

            List<AgentRun> runs = List.of(
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L),
                    createSucceededRun(TEAM_ID_1, USER_ID_2, 2000L, new BigDecimal("0.20"), 3000L),
                    createSucceededRun(TEAM_ID_2, USER_ID_1, 1500L, new BigDecimal("0.15"), 4000L)
            );

            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(runs);
            when(teamRepository.findByOrgId(ORG_ID)).thenReturn(List.of(team1, team2));

            ByTeamResponse result = analyticsService.getByTeam(ORG_ID, FROM, TO, null, null);

            assertThat(result.teams()).hasSize(2);
            // Sorted by totalRuns desc: team1 has 2, team2 has 1
            assertThat(result.teams().get(0).teamName()).isEqualTo("Engineering");
            assertThat(result.teams().get(0).totalRuns()).isEqualTo(2);
            assertThat(result.teams().get(1).teamName()).isEqualTo("Data Science");
            assertThat(result.teams().get(1).totalRuns()).isEqualTo(1);
        }

        @Test
        void calculatesSuccessRatePerTeam() {
            Team team = new Team(TEAM_ID_1, ORG_ID, "ext-1", "Engineering");
            List<AgentRun> runs = List.of(
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L),
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 3000L),
                    createFailedRun(TEAM_ID_1, USER_ID_1)
            );

            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(runs);
            when(teamRepository.findByOrgId(ORG_ID)).thenReturn(List.of(team));

            ByTeamResponse result = analyticsService.getByTeam(ORG_ID, FROM, TO, null, null);

            assertThat(result.teams()).hasSize(1);
            assertThat(result.teams().get(0).successRate()).isCloseTo(0.6667, within(0.001));
        }

        @Test
        void filtersRunsWithNullTeamId() {
            Team team = new Team(TEAM_ID_1, ORG_ID, "ext-1", "Engineering");
            AgentRun runNoTeam = createRun(UUID.randomUUID(), ORG_ID, null, USER_ID_1,
                    "SUCCEEDED", 1000L, new BigDecimal("0.10"), 5000L, "code-review",
                    Instant.parse("2025-01-15T10:00:00Z"));
            AgentRun runWithTeam = createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L);

            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(List.of(runNoTeam, runWithTeam));
            when(teamRepository.findByOrgId(ORG_ID)).thenReturn(List.of(team));

            ByTeamResponse result = analyticsService.getByTeam(ORG_ID, FROM, TO, null, null);

            assertThat(result.teams()).hasSize(1);
            assertThat(result.teams().get(0).totalRuns()).isEqualTo(1);
        }

        @Test
        void handlesUnknownTeam() {
            UUID unknownTeamId = UUID.randomUUID();
            AgentRun run = createRun(UUID.randomUUID(), ORG_ID, unknownTeamId, USER_ID_1,
                    "SUCCEEDED", 1000L, new BigDecimal("0.10"), 5000L, "code-review",
                    Instant.parse("2025-01-15T10:00:00Z"));

            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(List.of(run));
            when(teamRepository.findByOrgId(ORG_ID)).thenReturn(Collections.emptyList());

            ByTeamResponse result = analyticsService.getByTeam(ORG_ID, FROM, TO, null, null);

            assertThat(result.teams()).hasSize(1);
            assertThat(result.teams().get(0).teamName()).isEqualTo("Unknown");
        }
    }

    @Nested
    class GetByAgentType {

        @Test
        void breaksDownRunsByAgentType() {
            AgentType type1 = new AgentType(UUID.randomUUID(), ORG_ID, "code-review", "Code Review");
            AgentType type2 = new AgentType(UUID.randomUUID(), ORG_ID, "test-gen", "Test Generator");

            AgentRun run1 = createRun(UUID.randomUUID(), ORG_ID, TEAM_ID_1, USER_ID_1,
                    "SUCCEEDED", 1000L, new BigDecimal("0.10"), 5000L, "code-review",
                    Instant.parse("2025-01-15T10:00:00Z"));
            AgentRun run2 = createRun(UUID.randomUUID(), ORG_ID, TEAM_ID_1, USER_ID_1,
                    "SUCCEEDED", 2000L, new BigDecimal("0.20"), 3000L, "test-gen",
                    Instant.parse("2025-01-15T10:00:00Z"));

            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(List.of(run1, run2));
            when(agentTypeRepository.findByOrgId(ORG_ID)).thenReturn(List.of(type1, type2));

            ByAgentTypeResponse result = analyticsService.getByAgentType(ORG_ID, FROM, TO, null, null);

            assertThat(result.agentTypes()).hasSize(2);
        }

        @Test
        void handlesUnknownAgentType() {
            AgentRun run = createRun(UUID.randomUUID(), ORG_ID, TEAM_ID_1, USER_ID_1,
                    "SUCCEEDED", 1000L, new BigDecimal("0.10"), 5000L, "unknown-type",
                    Instant.parse("2025-01-15T10:00:00Z"));

            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(List.of(run));
            when(agentTypeRepository.findByOrgId(ORG_ID)).thenReturn(Collections.emptyList());

            ByAgentTypeResponse result = analyticsService.getByAgentType(ORG_ID, FROM, TO, null, null);

            assertThat(result.agentTypes()).hasSize(1);
            assertThat(result.agentTypes().get(0).displayName()).isEqualTo("unknown-type");
        }

        @Test
        void sortsByTotalRunsDescending() {
            AgentType type1 = new AgentType(UUID.randomUUID(), ORG_ID, "code-review", "Code Review");
            AgentType type2 = new AgentType(UUID.randomUUID(), ORG_ID, "test-gen", "Test Generator");

            AgentRun run1 = createRun(UUID.randomUUID(), ORG_ID, TEAM_ID_1, USER_ID_1,
                    "SUCCEEDED", 1000L, new BigDecimal("0.10"), 5000L, "code-review",
                    Instant.parse("2025-01-15T10:00:00Z"));
            AgentRun run2 = createRun(UUID.randomUUID(), ORG_ID, TEAM_ID_1, USER_ID_1,
                    "SUCCEEDED", 2000L, new BigDecimal("0.20"), 3000L, "test-gen",
                    Instant.parse("2025-01-15T10:00:00Z"));
            AgentRun run3 = createRun(UUID.randomUUID(), ORG_ID, TEAM_ID_1, USER_ID_1,
                    "SUCCEEDED", 2000L, new BigDecimal("0.20"), 3000L, "test-gen",
                    Instant.parse("2025-01-15T11:00:00Z"));

            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(List.of(run1, run2, run3));
            when(agentTypeRepository.findByOrgId(ORG_ID)).thenReturn(List.of(type1, type2));

            ByAgentTypeResponse result = analyticsService.getByAgentType(ORG_ID, FROM, TO, null, null);

            assertThat(result.agentTypes().get(0).agentType()).isEqualTo("test-gen");
            assertThat(result.agentTypes().get(0).totalRuns()).isEqualTo(2);
            assertThat(result.agentTypes().get(1).agentType()).isEqualTo("code-review");
        }
    }

    @Nested
    class GetTopUsers {

        @Test
        void returnsTopUsersSortedByRuns() {
            User user1 = new User(USER_ID_1, ORG_ID, "ext-1", "user1@test.com", "User One", "hash", "MEMBER");
            User user2 = new User(USER_ID_2, ORG_ID, "ext-2", "user2@test.com", "User Two", "hash", "MEMBER");
            Team team = new Team(TEAM_ID_1, ORG_ID, "ext-1", "Engineering");

            List<AgentRun> runs = List.of(
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L),
                    createSucceededRun(TEAM_ID_1, USER_ID_2, 2000L, new BigDecimal("0.20"), 3000L),
                    createSucceededRun(TEAM_ID_1, USER_ID_2, 3000L, new BigDecimal("0.30"), 4000L)
            );

            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(runs);
            when(userRepository.findByOrgId(ORG_ID)).thenReturn(List.of(user1, user2));
            when(teamRepository.findByOrgId(ORG_ID)).thenReturn(List.of(team));

            TopUsersResponse result = analyticsService.getTopUsers(ORG_ID, FROM, TO, null, "runs", 10);

            assertThat(result.users()).hasSize(2);
            assertThat(result.users().get(0).userId()).isEqualTo(USER_ID_2);
            assertThat(result.users().get(0).totalRuns()).isEqualTo(2);
            assertThat(result.sortBy()).isEqualTo("runs");
        }

        @Test
        void sortsByTokens() {
            User user1 = new User(USER_ID_1, ORG_ID, "ext-1", "user1@test.com", "User One", "hash", "MEMBER");
            User user2 = new User(USER_ID_2, ORG_ID, "ext-2", "user2@test.com", "User Two", "hash", "MEMBER");
            Team team = new Team(TEAM_ID_1, ORG_ID, "ext-1", "Engineering");

            List<AgentRun> runs = List.of(
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 5000L, new BigDecimal("0.10"), 5000L),
                    createSucceededRun(TEAM_ID_1, USER_ID_2, 1000L, new BigDecimal("0.20"), 3000L),
                    createSucceededRun(TEAM_ID_1, USER_ID_2, 1000L, new BigDecimal("0.20"), 3000L)
            );

            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(runs);
            when(userRepository.findByOrgId(ORG_ID)).thenReturn(List.of(user1, user2));
            when(teamRepository.findByOrgId(ORG_ID)).thenReturn(List.of(team));

            TopUsersResponse result = analyticsService.getTopUsers(ORG_ID, FROM, TO, null, "tokens", 10);

            assertThat(result.users().get(0).userId()).isEqualTo(USER_ID_1);
            assertThat(result.users().get(0).totalTokens()).isEqualTo(5000L);
        }

        @Test
        void respectsLimit() {
            User user1 = new User(USER_ID_1, ORG_ID, "ext-1", "u1@test.com", "User One", "hash", "MEMBER");
            User user2 = new User(USER_ID_2, ORG_ID, "ext-2", "u2@test.com", "User Two", "hash", "MEMBER");
            Team team = new Team(TEAM_ID_1, ORG_ID, "ext-1", "Engineering");

            List<AgentRun> runs = List.of(
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L),
                    createSucceededRun(TEAM_ID_1, USER_ID_2, 2000L, new BigDecimal("0.20"), 3000L)
            );

            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(runs);
            when(userRepository.findByOrgId(ORG_ID)).thenReturn(List.of(user1, user2));
            when(teamRepository.findByOrgId(ORG_ID)).thenReturn(List.of(team));

            TopUsersResponse result = analyticsService.getTopUsers(ORG_ID, FROM, TO, null, null, 1);

            assertThat(result.users()).hasSize(1);
        }

        @Test
        void defaultsSortByToRuns() {
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());
            when(userRepository.findByOrgId(ORG_ID)).thenReturn(Collections.emptyList());
            when(teamRepository.findByOrgId(ORG_ID)).thenReturn(Collections.emptyList());

            TopUsersResponse result = analyticsService.getTopUsers(ORG_ID, FROM, TO, null, null, 10);

            assertThat(result.sortBy()).isEqualTo("runs");
        }

        @Test
        void handlesUnknownUser() {
            Team team = new Team(TEAM_ID_1, ORG_ID, "ext-1", "Engineering");
            List<AgentRun> runs = List.of(
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L)
            );

            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(runs);
            when(userRepository.findByOrgId(ORG_ID)).thenReturn(Collections.emptyList());
            when(teamRepository.findByOrgId(ORG_ID)).thenReturn(List.of(team));

            TopUsersResponse result = analyticsService.getTopUsers(ORG_ID, FROM, TO, null, null, 10);

            assertThat(result.users().get(0).displayName()).isEqualTo("Unknown");
        }
    }

    @Nested
    class GetTeamByUser {

        @Test
        void breaksDownTeamRunsByUser() {
            Team team = new Team(TEAM_ID_1, ORG_ID, "ext-1", "Engineering");
            User user1 = new User(USER_ID_1, ORG_ID, "ext-1", "u1@test.com", "User One", "hash", "MEMBER");
            User user2 = new User(USER_ID_2, ORG_ID, "ext-2", "u2@test.com", "User Two", "hash", "MEMBER");

            List<AgentRun> runs = List.of(
                    createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L),
                    createSucceededRun(TEAM_ID_1, USER_ID_2, 2000L, new BigDecimal("0.20"), 3000L)
            );

            when(agentRunRepository.findTeamFiltered(eq(TEAM_ID_1), any(), any(), isNull(), isNull()))
                    .thenReturn(runs);
            when(teamRepository.findById(TEAM_ID_1)).thenReturn(Optional.of(team));
            when(userRepository.findByOrgId(ORG_ID)).thenReturn(List.of(user1, user2));

            ByTeamResponse result = analyticsService.getTeamByUser(TEAM_ID_1, FROM, TO, null, null);

            assertThat(result.teams()).hasSize(2);
            assertThat(result.orgId()).isEqualTo(ORG_ID);
        }

        @Test
        void throwsWhenTeamNotFound() {
            when(agentRunRepository.findTeamFiltered(eq(TEAM_ID_1), any(), any(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());
            when(teamRepository.findById(TEAM_ID_1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> analyticsService.getTeamByUser(TEAM_ID_1, FROM, TO, null, null))
                    .isInstanceOf(NoSuchElementException.class);
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

            RunListResponse result = analyticsService.getUserRuns(USER_ID_1, FROM, TO, null, null, 2);

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

            RunListResponse result = analyticsService.getUserRuns(USER_ID_1, FROM, TO, null, null, 50);

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

            RunListResponse result = analyticsService.getUserRuns(USER_ID_1, FROM, TO, null, null, 50);

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

            RunListResponse result = analyticsService.getUserRuns(USER_ID_1, FROM, TO, null, null, 50);

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

            RunDetailResponse result = analyticsService.getRunDetail(runId);

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

            assertThatThrownBy(() -> analyticsService.getRunDetail(runId))
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

            RunDetailResponse result = analyticsService.getRunDetail(runId);

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

            RunDetailResponse result = analyticsService.getRunDetail(runId);

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

            RunDetailResponse result = analyticsService.getRunDetail(runId);

            assertThat(result.errorCategory()).isEqualTo("TIMEOUT");
            assertThat(result.errorMessage()).isEqualTo("Operation timed out");
        }
    }

    @Nested
    class GetOrgRuns {

        @Test
        void returnsPagedRunsWithCorrectMapping() {
            User user = new User(USER_ID_1, ORG_ID, "ext-1", "user1@test.com", "User One", "hash", "MEMBER");
            Team team = new Team(TEAM_ID_1, ORG_ID, "ext-1", "Engineering");
            AgentType type = new AgentType(UUID.randomUUID(), ORG_ID, "code-review", "Code Review");

            AgentRun run = createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L);
            Page<AgentRun> page = new PageImpl<>(List.of(run), PageRequest.of(0, 25), 1);

            when(agentRunRepository.findOrgFilteredPaged(eq(ORG_ID), any(), any(),
                    isNull(), isNull(), isNull(), eq(false), eq(List.of()), eq(PageRequest.of(0, 25))))
                    .thenReturn(page);
            when(userRepository.findByOrgId(ORG_ID)).thenReturn(List.of(user));
            when(teamRepository.findByOrgId(ORG_ID)).thenReturn(List.of(team));
            when(agentTypeRepository.findByOrgId(ORG_ID)).thenReturn(List.of(type));

            PagedRunListResponse result = analyticsService.getOrgRuns(
                    ORG_ID, FROM, TO, null, null, null, null, 0, 25);

            assertThat(result.runs()).hasSize(1);
            assertThat(result.page()).isZero();
            assertThat(result.totalPages()).isEqualTo(1);
            assertThat(result.totalElements()).isEqualTo(1);

            PagedRunListResponse.RunItem item = result.runs().get(0);
            assertThat(item.runId()).isEqualTo(run.getId());
            assertThat(item.userId()).isEqualTo(USER_ID_1);
            assertThat(item.userName()).isEqualTo("User One");
            assertThat(item.teamId()).isEqualTo(TEAM_ID_1);
            assertThat(item.teamName()).isEqualTo("Engineering");
            assertThat(item.agentType()).isEqualTo("code-review");
            assertThat(item.agentTypeDisplayName()).isEqualTo("Code Review");
            assertThat(item.status()).isEqualTo("SUCCEEDED");
            assertThat(item.totalTokens()).isEqualTo(1000L);
        }

        @Test
        void returnsEmptyPageWhenNoRuns() {
            Page<AgentRun> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 25), 0);

            when(agentRunRepository.findOrgFilteredPaged(eq(ORG_ID), any(), any(),
                    isNull(), isNull(), isNull(), eq(false), eq(List.of()), eq(PageRequest.of(0, 25))))
                    .thenReturn(emptyPage);
            when(userRepository.findByOrgId(ORG_ID)).thenReturn(List.of());
            when(teamRepository.findByOrgId(ORG_ID)).thenReturn(List.of());
            when(agentTypeRepository.findByOrgId(ORG_ID)).thenReturn(List.of());

            PagedRunListResponse result = analyticsService.getOrgRuns(
                    ORG_ID, FROM, TO, null, null, null, null, 0, 25);

            assertThat(result.runs()).isEmpty();
            assertThat(result.totalElements()).isZero();
            assertThat(result.totalPages()).isZero();
        }

        @Test
        void passesStatusFilterCorrectly() {
            Page<AgentRun> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 25), 0);
            List<String> statuses = List.of("SUCCEEDED", "FAILED");

            when(agentRunRepository.findOrgFilteredPaged(eq(ORG_ID), any(), any(),
                    isNull(), isNull(), isNull(), eq(true), eq(statuses), eq(PageRequest.of(0, 25))))
                    .thenReturn(emptyPage);
            when(userRepository.findByOrgId(ORG_ID)).thenReturn(List.of());
            when(teamRepository.findByOrgId(ORG_ID)).thenReturn(List.of());
            when(agentTypeRepository.findByOrgId(ORG_ID)).thenReturn(List.of());

            analyticsService.getOrgRuns(ORG_ID, FROM, TO, null, null, statuses, null, 0, 25);

            verify(agentRunRepository).findOrgFilteredPaged(eq(ORG_ID), any(), any(),
                    isNull(), isNull(), isNull(), eq(true), eq(statuses), eq(PageRequest.of(0, 25)));
        }

        @Test
        void passesAllFiltersToRepository() {
            Page<AgentRun> emptyPage = new PageImpl<>(List.of(), PageRequest.of(1, 10), 0);

            when(agentRunRepository.findOrgFilteredPaged(eq(ORG_ID), any(), any(),
                    eq(TEAM_ID_1), eq(USER_ID_1), eq("code-review"), eq(false), eq(List.of()),
                    eq(PageRequest.of(1, 10))))
                    .thenReturn(emptyPage);
            when(userRepository.findByOrgId(ORG_ID)).thenReturn(List.of());
            when(teamRepository.findByOrgId(ORG_ID)).thenReturn(List.of());
            when(agentTypeRepository.findByOrgId(ORG_ID)).thenReturn(List.of());

            analyticsService.getOrgRuns(ORG_ID, FROM, TO, TEAM_ID_1, USER_ID_1, null, "code-review", 1, 10);

            verify(agentRunRepository).findOrgFilteredPaged(eq(ORG_ID), any(), any(),
                    eq(TEAM_ID_1), eq(USER_ID_1), eq("code-review"), eq(false), eq(List.of()),
                    eq(PageRequest.of(1, 10)));
        }

        @Test
        void handlesUnknownUserAndTeam() {
            AgentRun run = createSucceededRun(TEAM_ID_1, USER_ID_1, 1000L, new BigDecimal("0.10"), 5000L);
            Page<AgentRun> page = new PageImpl<>(List.of(run), PageRequest.of(0, 25), 1);

            when(agentRunRepository.findOrgFilteredPaged(eq(ORG_ID), any(), any(),
                    isNull(), isNull(), isNull(), eq(false), eq(List.of()), eq(PageRequest.of(0, 25))))
                    .thenReturn(page);
            when(userRepository.findByOrgId(ORG_ID)).thenReturn(List.of());
            when(teamRepository.findByOrgId(ORG_ID)).thenReturn(List.of());
            when(agentTypeRepository.findByOrgId(ORG_ID)).thenReturn(List.of());

            PagedRunListResponse result = analyticsService.getOrgRuns(
                    ORG_ID, FROM, TO, null, null, null, null, 0, 25);

            PagedRunListResponse.RunItem item = result.runs().get(0);
            assertThat(item.userName()).isEqualTo("Unknown");
            assertThat(item.teamName()).isEqualTo("Unknown");
            assertThat(item.agentTypeDisplayName()).isEqualTo("code-review");
        }

        @Test
        void handlesRunWithNullTeamId() {
            AgentRun run = createRun(UUID.randomUUID(), ORG_ID, null, USER_ID_1,
                    "SUCCEEDED", 1000L, new BigDecimal("0.10"), 5000L, "code-review",
                    Instant.parse("2025-01-15T10:00:00Z"));
            User user = new User(USER_ID_1, ORG_ID, "ext-1", "user1@test.com", "User One", "hash", "MEMBER");
            Page<AgentRun> page = new PageImpl<>(List.of(run), PageRequest.of(0, 25), 1);

            when(agentRunRepository.findOrgFilteredPaged(eq(ORG_ID), any(), any(),
                    isNull(), isNull(), isNull(), eq(false), eq(List.of()), eq(PageRequest.of(0, 25))))
                    .thenReturn(page);
            when(userRepository.findByOrgId(ORG_ID)).thenReturn(List.of(user));
            when(teamRepository.findByOrgId(ORG_ID)).thenReturn(List.of());
            when(agentTypeRepository.findByOrgId(ORG_ID)).thenReturn(List.of());

            PagedRunListResponse result = analyticsService.getOrgRuns(
                    ORG_ID, FROM, TO, null, null, null, null, 0, 25);

            PagedRunListResponse.RunItem item = result.runs().get(0);
            assertThat(item.teamId()).isNull();
            assertThat(item.teamName()).isEqualTo("Unknown");
        }

        @Test
        void handlesRunWithNullFinishedAtAndDuration() {
            AgentRun run = createRun(UUID.randomUUID(), ORG_ID, TEAM_ID_1, USER_ID_1,
                    "RUNNING", 500L, new BigDecimal("0.05"), null, "code-review",
                    Instant.parse("2025-01-15T10:00:00Z"));
            run.setFinishedAt(null);
            Page<AgentRun> page = new PageImpl<>(List.of(run), PageRequest.of(0, 25), 1);

            when(agentRunRepository.findOrgFilteredPaged(eq(ORG_ID), any(), any(),
                    isNull(), isNull(), isNull(), eq(false), eq(List.of()), eq(PageRequest.of(0, 25))))
                    .thenReturn(page);
            when(userRepository.findByOrgId(ORG_ID)).thenReturn(List.of());
            when(teamRepository.findByOrgId(ORG_ID)).thenReturn(List.of());
            when(agentTypeRepository.findByOrgId(ORG_ID)).thenReturn(List.of());

            PagedRunListResponse result = analyticsService.getOrgRuns(
                    ORG_ID, FROM, TO, null, null, null, null, 0, 25);

            PagedRunListResponse.RunItem item = result.runs().get(0);
            assertThat(item.finishedAt()).isNull();
            assertThat(item.durationMs()).isZero();
            assertThat(item.status()).isEqualTo("RUNNING");
        }
    }
}
