package com.analytics.dashboard.controller;

import com.analytics.dashboard.config.AuthContext;
import com.analytics.dashboard.dto.*;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamAnalyticsControllerTest {

    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private AuthContext authContext;

    @InjectMocks
    private TeamAnalyticsController controller;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final String FROM = "2025-01-01";
    private static final String TO = "2025-01-31";

    @Nested
    class GetSummary {

        @Test
        void returnsOkWithSummaryWhenAuthorized() {
            when(authContext.hasTeamAccess(TEAM_ID)).thenReturn(true);
            AnalyticsSummaryResponse summary = new AnalyticsSummaryResponse(
                    ORG_ID, new AnalyticsSummaryResponse.PeriodRange(FROM, TO),
                    5, 4, 1, 0, 0, 0.8, 3000, 1500, 1500, "0.500000", 2000, 1800, 3500, 3900);
            when(analyticsService.getTeamSummary(TEAM_ID, FROM, TO, null, null)).thenReturn(summary);

            ResponseEntity<?> response = controller.getSummary(TEAM_ID, FROM, TO, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(summary);
        }

        @Test
        void throwsForbiddenWhenNotAuthorized() {
            when(authContext.hasTeamAccess(TEAM_ID)).thenReturn(false);

            assertThatThrownBy(() -> controller.getSummary(TEAM_ID, FROM, TO, null, null))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void passesFiltersToService() {
            when(authContext.hasTeamAccess(TEAM_ID)).thenReturn(true);
            AnalyticsSummaryResponse summary = new AnalyticsSummaryResponse(
                    ORG_ID, new AnalyticsSummaryResponse.PeriodRange(FROM, TO),
                    0, 0, 0, 0, 0, 0, 0, 0, 0, "0.000000", 0, 0, 0, 0);
            when(analyticsService.getTeamSummary(TEAM_ID, FROM, TO, "test-gen", "FAILED")).thenReturn(summary);

            controller.getSummary(TEAM_ID, FROM, TO, "test-gen", "FAILED");

            verify(analyticsService).getTeamSummary(TEAM_ID, FROM, TO, "test-gen", "FAILED");
        }
    }

    @Nested
    class GetTimeseries {

        @Test
        void returnsOkWithTimeseriesWhenAuthorized() {
            when(authContext.hasTeamAccess(TEAM_ID)).thenReturn(true);
            TimeseriesResponse timeseries = new TimeseriesResponse(ORG_ID, "DAILY", List.of());
            when(analyticsService.getTeamTimeseries(TEAM_ID, FROM, TO, null, null, null))
                    .thenReturn(timeseries);

            ResponseEntity<?> response = controller.getTimeseries(TEAM_ID, FROM, TO, null, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(timeseries);
        }

        @Test
        void throwsForbiddenWhenNotAuthorized() {
            when(authContext.hasTeamAccess(TEAM_ID)).thenReturn(false);

            assertThatThrownBy(() -> controller.getTimeseries(TEAM_ID, FROM, TO, null, null, null))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class GetByUser {

        @Test
        void returnsOkWithUserBreakdownWhenAuthorized() {
            when(authContext.hasTeamAccess(TEAM_ID)).thenReturn(true);
            ByTeamResponse byUser = new ByTeamResponse(ORG_ID,
                    new AnalyticsSummaryResponse.PeriodRange(FROM, TO), List.of());
            when(analyticsService.getTeamByUser(TEAM_ID, FROM, TO, null, null)).thenReturn(byUser);

            ResponseEntity<?> response = controller.getByUser(TEAM_ID, FROM, TO, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(byUser);
        }

        @Test
        void throwsForbiddenWhenNotAuthorized() {
            when(authContext.hasTeamAccess(TEAM_ID)).thenReturn(false);

            assertThatThrownBy(() -> controller.getByUser(TEAM_ID, FROM, TO, null, null))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
