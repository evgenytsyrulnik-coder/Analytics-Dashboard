package com.analytics.dashboard.controller;

import com.analytics.dashboard.config.AuthContext;
import com.analytics.dashboard.dto.*;
import com.analytics.dashboard.entity.AgentType;
import com.analytics.dashboard.entity.Team;
import com.analytics.dashboard.repository.AgentTypeRepository;
import com.analytics.dashboard.repository.BudgetRepository;
import com.analytics.dashboard.repository.TeamRepository;
import com.analytics.dashboard.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrgAnalyticsControllerTest {

    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private AgentTypeRepository agentTypeRepository;
    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private AuthContext authContext;

    @InjectMocks
    private OrgAnalyticsController controller;

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final String FROM = "2025-01-01";
    private static final String TO = "2025-01-31";

    @BeforeEach
    void setUp() {
        lenient().when(authContext.getOrgId()).thenReturn(ORG_ID);
    }

    @Nested
    class GetSummary {

        @Test
        void returnsOkWithSummary() {
            AnalyticsSummaryResponse summary = new AnalyticsSummaryResponse(
                    ORG_ID, new AnalyticsSummaryResponse.PeriodRange(FROM, TO),
                    10, 8, 2, 0, 0, 0.8, 5000, 2500, 2500, "1.000000", 3000, 2500, 4500, 4900);
            when(analyticsService.getOrgSummary(ORG_ID, FROM, TO, null, null, null)).thenReturn(summary);

            ResponseEntity<?> response = controller.getSummary(ORG_ID, FROM, TO, null, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(summary);
        }

        @Test
        void passesAllFiltersToService() {
            UUID teamId = UUID.randomUUID();
            AnalyticsSummaryResponse summary = new AnalyticsSummaryResponse(
                    ORG_ID, new AnalyticsSummaryResponse.PeriodRange(FROM, TO),
                    0, 0, 0, 0, 0, 0, 0, 0, 0, "0.000000", 0, 0, 0, 0);
            when(analyticsService.getOrgSummary(ORG_ID, FROM, TO, teamId, "code-review", "SUCCEEDED"))
                    .thenReturn(summary);

            controller.getSummary(ORG_ID, FROM, TO, teamId, "code-review", "SUCCEEDED");

            verify(analyticsService).getOrgSummary(ORG_ID, FROM, TO, teamId, "code-review", "SUCCEEDED");
        }

        @Test
        void throwsSecurityExceptionForWrongOrg() {
            UUID wrongOrgId = UUID.randomUUID();

            assertThatThrownBy(() -> controller.getSummary(wrongOrgId, FROM, TO, null, null, null))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Access denied");
        }
    }

    @Nested
    class GetTimeseries {

        @Test
        void returnsOkWithTimeseries() {
            TimeseriesResponse timeseries = new TimeseriesResponse(ORG_ID, "DAILY", List.of());
            when(analyticsService.getOrgTimeseries(ORG_ID, FROM, TO, null, null, null, null))
                    .thenReturn(timeseries);

            ResponseEntity<?> response = controller.getTimeseries(ORG_ID, FROM, TO, null, null, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(timeseries);
        }

        @Test
        void throwsSecurityExceptionForWrongOrg() {
            UUID wrongOrgId = UUID.randomUUID();

            assertThatThrownBy(() -> controller.getTimeseries(wrongOrgId, FROM, TO, null, null, null, null))
                    .isInstanceOf(SecurityException.class);
        }
    }

    @Nested
    class GetByTeam {

        @Test
        void returnsOkWithTeamBreakdown() {
            ByTeamResponse byTeam = new ByTeamResponse(ORG_ID,
                    new AnalyticsSummaryResponse.PeriodRange(FROM, TO), List.of());
            when(analyticsService.getByTeam(ORG_ID, FROM, TO, null, null)).thenReturn(byTeam);

            ResponseEntity<?> response = controller.getByTeam(ORG_ID, FROM, TO, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(byTeam);
        }
    }

    @Nested
    class GetByAgentType {

        @Test
        void returnsOkWithAgentTypeBreakdown() {
            ByAgentTypeResponse byType = new ByAgentTypeResponse(ORG_ID,
                    new AnalyticsSummaryResponse.PeriodRange(FROM, TO), List.of());
            when(analyticsService.getByAgentType(ORG_ID, FROM, TO, null, null)).thenReturn(byType);

            ResponseEntity<?> response = controller.getByAgentType(ORG_ID, FROM, TO, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(byType);
        }
    }

    @Nested
    class GetTopUsers {

        @Test
        void returnsOkWithTopUsers() {
            TopUsersResponse topUsers = new TopUsersResponse(ORG_ID, "runs", List.of());
            when(analyticsService.getTopUsers(ORG_ID, FROM, TO, null, "runs", 10)).thenReturn(topUsers);

            ResponseEntity<?> response = controller.getTopUsers(ORG_ID, FROM, TO, null, "runs", 10);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(topUsers);
        }

        @Test
        void capsLimitAt50() {
            TopUsersResponse topUsers = new TopUsersResponse(ORG_ID, "runs", List.of());
            when(analyticsService.getTopUsers(ORG_ID, FROM, TO, null, "runs", 50)).thenReturn(topUsers);

            controller.getTopUsers(ORG_ID, FROM, TO, null, "runs", 100);

            verify(analyticsService).getTopUsers(ORG_ID, FROM, TO, null, "runs", 50);
        }
    }

    @Nested
    class GetTeams {

        @Test
        void returnsTeamList() {
            UUID teamId = UUID.randomUUID();
            Team team = new Team(teamId, ORG_ID, "ext-1", "Engineering");
            when(teamRepository.findByOrgId(ORG_ID)).thenReturn(List.of(team));

            ResponseEntity<?> response = controller.getTeams(ORG_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void throwsSecurityExceptionForWrongOrg() {
            UUID wrongOrgId = UUID.randomUUID();

            assertThatThrownBy(() -> controller.getTeams(wrongOrgId))
                    .isInstanceOf(SecurityException.class);
        }
    }

    @Nested
    class GetAgentTypes {

        @Test
        void returnsAgentTypeList() {
            AgentType type = new AgentType(UUID.randomUUID(), ORG_ID, "code-review", "Code Review");
            when(agentTypeRepository.findByOrgId(ORG_ID)).thenReturn(List.of(type));

            ResponseEntity<?> response = controller.getAgentTypes(ORG_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    class GetBudgets {

        @Test
        void returnsBudgetList() {
            when(budgetRepository.findByOrgId(ORG_ID)).thenReturn(List.of());

            ResponseEntity<?> response = controller.getBudgets(ORG_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    class OrgValidation {

        @Test
        void allEndpointsRejectWrongOrg() {
            UUID wrongOrgId = UUID.randomUUID();

            assertThatThrownBy(() -> controller.getSummary(wrongOrgId, FROM, TO, null, null, null))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> controller.getTimeseries(wrongOrgId, FROM, TO, null, null, null, null))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> controller.getByTeam(wrongOrgId, FROM, TO, null, null))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> controller.getByAgentType(wrongOrgId, FROM, TO, null, null))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> controller.getTopUsers(wrongOrgId, FROM, TO, null, "runs", 10))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> controller.getTeams(wrongOrgId))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> controller.getAgentTypes(wrongOrgId))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> controller.getBudgets(wrongOrgId))
                    .isInstanceOf(SecurityException.class);
        }
    }
}
