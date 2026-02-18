package com.analytics.dashboard.controller;

import com.analytics.dashboard.config.AuthContext;
import com.analytics.dashboard.dto.*;
import com.analytics.dashboard.entity.Team;
import com.analytics.dashboard.entity.User;
import com.analytics.dashboard.repository.UserRepository;
import com.analytics.dashboard.service.UserAnalyticsService;
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
class UserAnalyticsControllerTest {

    @Mock
    private UserAnalyticsService analyticsService;
    @Mock
    private AuthContext authContext;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserAnalyticsController controller;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final String FROM = "2025-01-01";
    private static final String TO = "2025-01-31";

    @BeforeEach
    void setUp() {
        lenient().when(authContext.getUserId()).thenReturn(USER_ID);
        lenient().when(authContext.getOrgId()).thenReturn(ORG_ID);
    }

    @Nested
    class GetMySummary {

        @Test
        void returnsOkWithUserSummary() {
            UserSummaryResponse summary = new UserSummaryResponse(
                    USER_ID, "Test User", new AnalyticsSummaryResponse.PeriodRange(FROM, TO),
                    5, 4, 1, 3000, "0.500000", 2000, 1, 5);
            when(analyticsService.getUserSummary(USER_ID, ORG_ID, FROM, TO, null, null))
                    .thenReturn(summary);

            ResponseEntity<?> response = controller.getMySummary(FROM, TO, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(summary);
        }

        @Test
        void passesFiltersToService() {
            UserSummaryResponse summary = new UserSummaryResponse(
                    USER_ID, "Test User", new AnalyticsSummaryResponse.PeriodRange(FROM, TO),
                    0, 0, 0, 0, "0.000000", 0, 0, 0);
            when(analyticsService.getUserSummary(USER_ID, ORG_ID, FROM, TO, "code-review", "SUCCEEDED"))
                    .thenReturn(summary);

            controller.getMySummary(FROM, TO, "code-review", "SUCCEEDED");

            verify(analyticsService).getUserSummary(USER_ID, ORG_ID, FROM, TO, "code-review", "SUCCEEDED");
        }

        @Test
        void usesAuthContextForUserAndOrgId() {
            UserSummaryResponse summary = new UserSummaryResponse(
                    USER_ID, "Test User", new AnalyticsSummaryResponse.PeriodRange(FROM, TO),
                    0, 0, 0, 0, "0.000000", 0, 0, 0);
            when(analyticsService.getUserSummary(USER_ID, ORG_ID, FROM, TO, null, null))
                    .thenReturn(summary);

            controller.getMySummary(FROM, TO, null, null);

            verify(authContext).getUserId();
            verify(authContext).getOrgId();
        }
    }

    @Nested
    class GetMyTimeseries {

        @Test
        void returnsOkWithTimeseries() {
            TimeseriesResponse timeseries = new TimeseriesResponse(null, "DAILY", List.of());
            when(analyticsService.getUserTimeseries(USER_ID, FROM, TO, null, null))
                    .thenReturn(timeseries);

            ResponseEntity<?> response = controller.getMyTimeseries(FROM, TO, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(timeseries);
        }
    }

    @Nested
    class GetMyRuns {

        @Test
        void returnsOkWithRunList() {
            RunListResponse runList = new RunListResponse(List.of(), null, false);
            when(analyticsService.getUserRuns(USER_ID, FROM, TO, null, null, 50))
                    .thenReturn(runList);

            ResponseEntity<?> response = controller.getMyRuns(FROM, TO, null, null, 50);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(runList);
        }

        @Test
        void capsLimitAt200() {
            RunListResponse runList = new RunListResponse(List.of(), null, false);
            when(analyticsService.getUserRuns(USER_ID, FROM, TO, null, null, 200))
                    .thenReturn(runList);

            controller.getMyRuns(FROM, TO, null, null, 500);

            verify(analyticsService).getUserRuns(USER_ID, FROM, TO, null, null, 200);
        }
    }

    @Nested
    class GetRunDetail {

        @Test
        void returnsOkWhenUserIsOwner() {
            UUID runId = UUID.randomUUID();
            RunDetailResponse detail = new RunDetailResponse(
                    runId, ORG_ID, TEAM_ID, USER_ID,
                    "code-review", "Code Review", "gpt-4", "v1", "SUCCEEDED",
                    "2025-01-15T10:00:00Z", "2025-01-15T10:00:05Z", 5000L,
                    500, 500, 1000, "0.050000", "0.050000", "0.100000",
                    null, null);
            when(analyticsService.getRunDetail(runId)).thenReturn(detail);

            ResponseEntity<?> response = controller.getRunDetail(runId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(detail);
        }

        @Test
        void returnsOkWhenUserIsOrgAdmin() {
            UUID runId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            RunDetailResponse detail = new RunDetailResponse(
                    runId, ORG_ID, TEAM_ID, otherUserId,
                    "code-review", "Code Review", "gpt-4", "v1", "SUCCEEDED",
                    "2025-01-15T10:00:00Z", "2025-01-15T10:00:05Z", 5000L,
                    500, 500, 1000, "0.050000", "0.050000", "0.100000",
                    null, null);
            when(analyticsService.getRunDetail(runId)).thenReturn(detail);
            when(authContext.isOrgAdmin()).thenReturn(true);

            ResponseEntity<?> response = controller.getRunDetail(runId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void returnsOkWhenUserIsTeamLeadWithTeamAccess() {
            UUID runId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            RunDetailResponse detail = new RunDetailResponse(
                    runId, ORG_ID, TEAM_ID, otherUserId,
                    "code-review", "Code Review", "gpt-4", "v1", "SUCCEEDED",
                    "2025-01-15T10:00:00Z", "2025-01-15T10:00:05Z", 5000L,
                    500, 500, 1000, "0.050000", "0.050000", "0.100000",
                    null, null);
            when(analyticsService.getRunDetail(runId)).thenReturn(detail);
            when(authContext.isOrgAdmin()).thenReturn(false);
            when(authContext.isTeamLead()).thenReturn(true);
            when(authContext.hasTeamAccess(TEAM_ID)).thenReturn(true);

            ResponseEntity<?> response = controller.getRunDetail(runId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void throwsForbiddenWhenNotOwnerAndNotAdmin() {
            UUID runId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            RunDetailResponse detail = new RunDetailResponse(
                    runId, ORG_ID, TEAM_ID, otherUserId,
                    "code-review", "Code Review", "gpt-4", "v1", "SUCCEEDED",
                    "2025-01-15T10:00:00Z", "2025-01-15T10:00:05Z", 5000L,
                    500, 500, 1000, "0.050000", "0.050000", "0.100000",
                    null, null);
            when(analyticsService.getRunDetail(runId)).thenReturn(detail);
            when(authContext.isOrgAdmin()).thenReturn(false);
            when(authContext.isTeamLead()).thenReturn(false);

            assertThatThrownBy(() -> controller.getRunDetail(runId))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void throwsForbiddenWhenTeamLeadWithoutTeamAccess() {
            UUID runId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            RunDetailResponse detail = new RunDetailResponse(
                    runId, ORG_ID, TEAM_ID, otherUserId,
                    "code-review", "Code Review", "gpt-4", "v1", "SUCCEEDED",
                    "2025-01-15T10:00:00Z", "2025-01-15T10:00:05Z", 5000L,
                    500, 500, 1000, "0.050000", "0.050000", "0.100000",
                    null, null);
            when(analyticsService.getRunDetail(runId)).thenReturn(detail);
            when(authContext.isOrgAdmin()).thenReturn(false);
            when(authContext.isTeamLead()).thenReturn(true);
            when(authContext.hasTeamAccess(TEAM_ID)).thenReturn(false);

            assertThatThrownBy(() -> controller.getRunDetail(runId))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class GetUserSummaryByUserId {

        private final UUID TARGET_USER_ID = UUID.randomUUID();

        private User createTargetUser() {
            User user = new User(TARGET_USER_ID, ORG_ID, "ext-t", "target@test.com", "Target User", "hash", "MEMBER");
            user.setTeams(Set.of(new Team(TEAM_ID, ORG_ID, "ext-1", "Engineering")));
            return user;
        }

        @Test
        void returnsOkWhenOrgAdmin() {
            User targetUser = createTargetUser();
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(authContext.isOrgAdmin()).thenReturn(true);
            UserSummaryResponse summary = new UserSummaryResponse(
                    TARGET_USER_ID, "Target User", new AnalyticsSummaryResponse.PeriodRange(FROM, TO),
                    3, 2, 1, 2000, "0.300000", 3000, 1, 5);
            when(analyticsService.getUserSummary(TARGET_USER_ID, ORG_ID, FROM, TO, null, null))
                    .thenReturn(summary);

            ResponseEntity<?> response = controller.getUserSummary(TARGET_USER_ID, FROM, TO, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(summary);
        }

        @Test
        void returnsOkWhenTeamLeadSharesTeam() {
            User targetUser = createTargetUser();
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(authContext.isOrgAdmin()).thenReturn(false);
            when(authContext.getTeamIds()).thenReturn(List.of(TEAM_ID));
            UserSummaryResponse summary = new UserSummaryResponse(
                    TARGET_USER_ID, "Target User", new AnalyticsSummaryResponse.PeriodRange(FROM, TO),
                    3, 2, 1, 2000, "0.300000", 3000, 1, 5);
            when(analyticsService.getUserSummary(TARGET_USER_ID, ORG_ID, FROM, TO, null, null))
                    .thenReturn(summary);

            ResponseEntity<?> response = controller.getUserSummary(TARGET_USER_ID, FROM, TO, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void throwsForbiddenWhenTeamLeadDoesNotShareTeam() {
            User targetUser = createTargetUser();
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(authContext.isOrgAdmin()).thenReturn(false);
            when(authContext.getTeamIds()).thenReturn(List.of(UUID.randomUUID()));

            assertThatThrownBy(() -> controller.getUserSummary(TARGET_USER_ID, FROM, TO, null, null))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void throwsForbiddenWhenUserInDifferentOrg() {
            UUID differentOrgId = UUID.randomUUID();
            User targetUser = new User(TARGET_USER_ID, differentOrgId, "ext-t", "target@test.com", "Target User", "hash", "MEMBER");
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));

            assertThatThrownBy(() -> controller.getUserSummary(TARGET_USER_ID, FROM, TO, null, null))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void throwsNotFoundWhenUserDoesNotExist() {
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.getUserSummary(TARGET_USER_ID, FROM, TO, null, null))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void passesFiltersToService() {
            User targetUser = createTargetUser();
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(authContext.isOrgAdmin()).thenReturn(true);
            UserSummaryResponse summary = new UserSummaryResponse(
                    TARGET_USER_ID, "Target User", new AnalyticsSummaryResponse.PeriodRange(FROM, TO),
                    0, 0, 0, 0, "0.000000", 0, 0, 0);
            when(analyticsService.getUserSummary(TARGET_USER_ID, ORG_ID, FROM, TO, "code-review", "SUCCEEDED"))
                    .thenReturn(summary);

            controller.getUserSummary(TARGET_USER_ID, FROM, TO, "code-review", "SUCCEEDED");

            verify(analyticsService).getUserSummary(TARGET_USER_ID, ORG_ID, FROM, TO, "code-review", "SUCCEEDED");
        }
    }

    @Nested
    class GetUserTimeseriesByUserId {

        private final UUID TARGET_USER_ID = UUID.randomUUID();

        private User createTargetUser() {
            User user = new User(TARGET_USER_ID, ORG_ID, "ext-t", "target@test.com", "Target User", "hash", "MEMBER");
            user.setTeams(Set.of(new Team(TEAM_ID, ORG_ID, "ext-1", "Engineering")));
            return user;
        }

        @Test
        void returnsOkWhenOrgAdmin() {
            User targetUser = createTargetUser();
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(authContext.isOrgAdmin()).thenReturn(true);
            TimeseriesResponse timeseries = new TimeseriesResponse(null, "DAILY", List.of());
            when(analyticsService.getUserTimeseries(TARGET_USER_ID, FROM, TO, null, null))
                    .thenReturn(timeseries);

            ResponseEntity<?> response = controller.getUserTimeseries(TARGET_USER_ID, FROM, TO, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(timeseries);
        }

        @Test
        void returnsOkWhenTeamLeadSharesTeam() {
            User targetUser = createTargetUser();
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(authContext.isOrgAdmin()).thenReturn(false);
            when(authContext.getTeamIds()).thenReturn(List.of(TEAM_ID));
            TimeseriesResponse timeseries = new TimeseriesResponse(null, "DAILY", List.of());
            when(analyticsService.getUserTimeseries(TARGET_USER_ID, FROM, TO, null, null))
                    .thenReturn(timeseries);

            ResponseEntity<?> response = controller.getUserTimeseries(TARGET_USER_ID, FROM, TO, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void throwsForbiddenWhenTeamLeadDoesNotShareTeam() {
            User targetUser = createTargetUser();
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(authContext.isOrgAdmin()).thenReturn(false);
            when(authContext.getTeamIds()).thenReturn(List.of(UUID.randomUUID()));

            assertThatThrownBy(() -> controller.getUserTimeseries(TARGET_USER_ID, FROM, TO, null, null))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void throwsNotFoundWhenUserDoesNotExist() {
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.getUserTimeseries(TARGET_USER_ID, FROM, TO, null, null))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void passesFiltersToService() {
            User targetUser = createTargetUser();
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(authContext.isOrgAdmin()).thenReturn(true);
            TimeseriesResponse timeseries = new TimeseriesResponse(null, "DAILY", List.of());
            when(analyticsService.getUserTimeseries(TARGET_USER_ID, FROM, TO, "test-gen", "FAILED"))
                    .thenReturn(timeseries);

            controller.getUserTimeseries(TARGET_USER_ID, FROM, TO, "test-gen", "FAILED");

            verify(analyticsService).getUserTimeseries(TARGET_USER_ID, FROM, TO, "test-gen", "FAILED");
        }
    }

    @Nested
    class GetUserRunsByUserId {

        private final UUID TARGET_USER_ID = UUID.randomUUID();

        private User createTargetUser() {
            User user = new User(TARGET_USER_ID, ORG_ID, "ext-t", "target@test.com", "Target User", "hash", "MEMBER");
            user.setTeams(Set.of(new Team(TEAM_ID, ORG_ID, "ext-1", "Engineering")));
            return user;
        }

        @Test
        void returnsOkWhenOrgAdmin() {
            User targetUser = createTargetUser();
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(authContext.isOrgAdmin()).thenReturn(true);
            RunListResponse runList = new RunListResponse(List.of(), null, false);
            when(analyticsService.getUserRuns(TARGET_USER_ID, FROM, TO, null, null, 50))
                    .thenReturn(runList);

            ResponseEntity<?> response = controller.getUserRuns(TARGET_USER_ID, FROM, TO, null, null, 50);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(runList);
        }

        @Test
        void returnsOkWhenTeamLeadSharesTeam() {
            User targetUser = createTargetUser();
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(authContext.isOrgAdmin()).thenReturn(false);
            when(authContext.getTeamIds()).thenReturn(List.of(TEAM_ID));
            RunListResponse runList = new RunListResponse(List.of(), null, false);
            when(analyticsService.getUserRuns(TARGET_USER_ID, FROM, TO, null, null, 50))
                    .thenReturn(runList);

            ResponseEntity<?> response = controller.getUserRuns(TARGET_USER_ID, FROM, TO, null, null, 50);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void throwsForbiddenWhenTeamLeadDoesNotShareTeam() {
            User targetUser = createTargetUser();
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(authContext.isOrgAdmin()).thenReturn(false);
            when(authContext.getTeamIds()).thenReturn(List.of(UUID.randomUUID()));

            assertThatThrownBy(() -> controller.getUserRuns(TARGET_USER_ID, FROM, TO, null, null, 50))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void throwsNotFoundWhenUserDoesNotExist() {
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.getUserRuns(TARGET_USER_ID, FROM, TO, null, null, 50))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void capsLimitAt200() {
            User targetUser = createTargetUser();
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(authContext.isOrgAdmin()).thenReturn(true);
            RunListResponse runList = new RunListResponse(List.of(), null, false);
            when(analyticsService.getUserRuns(TARGET_USER_ID, FROM, TO, null, null, 200))
                    .thenReturn(runList);

            controller.getUserRuns(TARGET_USER_ID, FROM, TO, null, null, 500);

            verify(analyticsService).getUserRuns(TARGET_USER_ID, FROM, TO, null, null, 200);
        }

        @Test
        void passesFiltersToService() {
            User targetUser = createTargetUser();
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(authContext.isOrgAdmin()).thenReturn(true);
            RunListResponse runList = new RunListResponse(List.of(), null, false);
            when(analyticsService.getUserRuns(TARGET_USER_ID, FROM, TO, "code-review", "SUCCEEDED", 50))
                    .thenReturn(runList);

            controller.getUserRuns(TARGET_USER_ID, FROM, TO, "code-review", "SUCCEEDED", 50);

            verify(analyticsService).getUserRuns(TARGET_USER_ID, FROM, TO, "code-review", "SUCCEEDED", 50);
        }
    }
}
