package com.analytics.dashboard.service;

import com.analytics.dashboard.dto.*;
import com.analytics.dashboard.entity.AgentRun;
import com.analytics.dashboard.entity.Team;
import com.analytics.dashboard.entity.User;
import com.analytics.dashboard.repository.AgentRunRepository;
import com.analytics.dashboard.repository.TeamRepository;
import com.analytics.dashboard.repository.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static com.analytics.dashboard.service.TestRunFactory.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamAnalyticsServiceTest {

    @Mock
    private AgentRunRepository agentRunRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TeamAnalyticsService teamAnalyticsService;

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

            AnalyticsSummaryResponse result = teamAnalyticsService.getTeamSummary(TEAM_ID_1, FROM, TO, null, null);

            assertThat(result.totalRuns()).isEqualTo(1);
            assertThat(result.orgId()).isEqualTo(ORG_ID);
        }

        @Test
        void throwsWhenTeamNotFound() {
            when(agentRunRepository.findTeamFiltered(eq(TEAM_ID_1), any(), any(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());
            when(teamRepository.findById(TEAM_ID_1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> teamAnalyticsService.getTeamSummary(TEAM_ID_1, FROM, TO, null, null))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void passesFiltersToTeamRepository() {
            Team team = new Team(TEAM_ID_1, ORG_ID, "ext-1", "Engineering");
            when(agentRunRepository.findTeamFiltered(eq(TEAM_ID_1), any(), any(), eq("code-review"), eq("FAILED")))
                    .thenReturn(Collections.emptyList());
            when(teamRepository.findById(TEAM_ID_1)).thenReturn(Optional.of(team));

            teamAnalyticsService.getTeamSummary(TEAM_ID_1, FROM, TO, "code-review", "FAILED");

            verify(agentRunRepository).findTeamFiltered(eq(TEAM_ID_1), any(), any(), eq("code-review"), eq("FAILED"));
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

            TimeseriesResponse result = teamAnalyticsService.getTeamTimeseries(TEAM_ID_1, FROM, TO, null, null, null);

            assertThat(result.orgId()).isEqualTo(ORG_ID);
            assertThat(result.granularity()).isEqualTo("DAILY");
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

            ByTeamResponse result = teamAnalyticsService.getTeamByUser(TEAM_ID_1, FROM, TO, null, null);

            assertThat(result.teams()).hasSize(2);
            assertThat(result.orgId()).isEqualTo(ORG_ID);
        }

        @Test
        void throwsWhenTeamNotFound() {
            when(agentRunRepository.findTeamFiltered(eq(TEAM_ID_1), any(), any(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());
            when(teamRepository.findById(TEAM_ID_1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> teamAnalyticsService.getTeamByUser(TEAM_ID_1, FROM, TO, null, null))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }
}
