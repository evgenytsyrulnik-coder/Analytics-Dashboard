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

import static com.analytics.dashboard.service.TestRunFactory.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrgAnalyticsServiceTest {

    @Mock
    private AgentRunRepository agentRunRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AgentTypeRepository agentTypeRepository;

    @InjectMocks
    private OrgAnalyticsService orgAnalyticsService;

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

            AnalyticsSummaryResponse result = orgAnalyticsService.getOrgSummary(ORG_ID, FROM, TO, null, null, null);

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

            AnalyticsSummaryResponse result = orgAnalyticsService.getOrgSummary(ORG_ID, FROM, TO, null, null, null);

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

            AnalyticsSummaryResponse result = orgAnalyticsService.getOrgSummary(ORG_ID, FROM, TO, null, null, null);

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

            AnalyticsSummaryResponse result = orgAnalyticsService.getOrgSummary(ORG_ID, FROM, TO, null, null, null);

            assertThat(result.p50DurationMs()).isEqualTo(5000L);
            assertThat(result.p95DurationMs()).isEqualTo(9500L);
            assertThat(result.p99DurationMs()).isEqualTo(9900L);
        }

        @Test
        void passesFiltersToRepository() {
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), eq(TEAM_ID_1), eq("code-review"), eq("SUCCEEDED")))
                    .thenReturn(Collections.emptyList());

            orgAnalyticsService.getOrgSummary(ORG_ID, FROM, TO, TEAM_ID_1, "code-review", "SUCCEEDED");

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

            AnalyticsSummaryResponse result = orgAnalyticsService.getOrgSummary(ORG_ID, FROM, TO, null, null, null);

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

            AnalyticsSummaryResponse result = orgAnalyticsService.getOrgSummary(ORG_ID, FROM, TO, null, null, null);

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

            AnalyticsSummaryResponse result = orgAnalyticsService.getOrgSummary(ORG_ID, FROM, TO, null, null, null);

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

            AnalyticsSummaryResponse result = orgAnalyticsService.getOrgSummary(ORG_ID, FROM, TO, null, null, null);

            assertThat(result.totalInputTokens()).isEqualTo(300L);
            assertThat(result.totalOutputTokens()).isEqualTo(700L);
            assertThat(result.totalTokens()).isEqualTo(1000L);
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

            TimeseriesResponse result = orgAnalyticsService.getOrgTimeseries(ORG_ID, FROM, TO, null, null, null, null);

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

            TimeseriesResponse result = orgAnalyticsService.getOrgTimeseries(ORG_ID, FROM, TO, null, null, null, null);

            assertThat(result.dataPoints()).isEmpty();
            assertThat(result.orgId()).isEqualTo(ORG_ID);
        }

        @Test
        void usesCustomGranularity() {
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());

            TimeseriesResponse result = orgAnalyticsService.getOrgTimeseries(ORG_ID, FROM, TO, null, null, null, "HOURLY");

            assertThat(result.granularity()).isEqualTo("HOURLY");
        }

        @Test
        void defaultsToDailyGranularity() {
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());

            TimeseriesResponse result = orgAnalyticsService.getOrgTimeseries(ORG_ID, FROM, TO, null, null, null, null);

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

            TimeseriesResponse result = orgAnalyticsService.getOrgTimeseries(ORG_ID, FROM, TO, null, null, null, null);

            assertThat(result.dataPoints().get(0).timestamp()).isLessThan(result.dataPoints().get(1).timestamp());
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

            ByTeamResponse result = orgAnalyticsService.getByTeam(ORG_ID, FROM, TO, null, null);

            assertThat(result.teams()).hasSize(2);
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

            ByTeamResponse result = orgAnalyticsService.getByTeam(ORG_ID, FROM, TO, null, null);

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

            ByTeamResponse result = orgAnalyticsService.getByTeam(ORG_ID, FROM, TO, null, null);

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

            ByTeamResponse result = orgAnalyticsService.getByTeam(ORG_ID, FROM, TO, null, null);

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

            ByAgentTypeResponse result = orgAnalyticsService.getByAgentType(ORG_ID, FROM, TO, null, null);

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

            ByAgentTypeResponse result = orgAnalyticsService.getByAgentType(ORG_ID, FROM, TO, null, null);

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

            ByAgentTypeResponse result = orgAnalyticsService.getByAgentType(ORG_ID, FROM, TO, null, null);

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

            TopUsersResponse result = orgAnalyticsService.getTopUsers(ORG_ID, FROM, TO, null, "runs", 10);

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

            TopUsersResponse result = orgAnalyticsService.getTopUsers(ORG_ID, FROM, TO, null, "tokens", 10);

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

            TopUsersResponse result = orgAnalyticsService.getTopUsers(ORG_ID, FROM, TO, null, null, 1);

            assertThat(result.users()).hasSize(1);
        }

        @Test
        void defaultsSortByToRuns() {
            when(agentRunRepository.findFiltered(eq(ORG_ID), any(), any(), isNull(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());
            when(userRepository.findByOrgId(ORG_ID)).thenReturn(Collections.emptyList());
            when(teamRepository.findByOrgId(ORG_ID)).thenReturn(Collections.emptyList());

            TopUsersResponse result = orgAnalyticsService.getTopUsers(ORG_ID, FROM, TO, null, null, 10);

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

            TopUsersResponse result = orgAnalyticsService.getTopUsers(ORG_ID, FROM, TO, null, null, 10);

            assertThat(result.users().get(0).displayName()).isEqualTo("Unknown");
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

            PagedRunListResponse result = orgAnalyticsService.getOrgRuns(
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

            PagedRunListResponse result = orgAnalyticsService.getOrgRuns(
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

            orgAnalyticsService.getOrgRuns(ORG_ID, FROM, TO, null, null, statuses, null, 0, 25);

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

            orgAnalyticsService.getOrgRuns(ORG_ID, FROM, TO, TEAM_ID_1, USER_ID_1, null, "code-review", 1, 10);

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

            PagedRunListResponse result = orgAnalyticsService.getOrgRuns(
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

            PagedRunListResponse result = orgAnalyticsService.getOrgRuns(
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

            PagedRunListResponse result = orgAnalyticsService.getOrgRuns(
                    ORG_ID, FROM, TO, null, null, null, null, 0, 25);

            PagedRunListResponse.RunItem item = result.runs().get(0);
            assertThat(item.finishedAt()).isNull();
            assertThat(item.durationMs()).isZero();
            assertThat(item.status()).isEqualTo("RUNNING");
        }
    }
}
