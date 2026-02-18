package com.analytics.dashboard.config;

import com.analytics.dashboard.entity.*;
import com.analytics.dashboard.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Seeds the H2 database with test data:
 * - 1 organization (Acme Corp)
 * - 2 teams (Platform, Data Science)
 * - 3 users with different roles (ORG_ADMIN, TEAM_LEAD, MEMBER)
 * - 4 agent types
 * - ~200 historical agent runs spread over the last 30 days
 * - 2 budgets (org-level + team-level)
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final OrganizationRepository organizationRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final AgentTypeRepository agentTypeRepository;
    private final AgentRunRepository agentRunRepository;
    private final BudgetRepository budgetRepository;
    private final PasswordEncoder passwordEncoder;

    // Fixed UUIDs for predictable test data
    static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID TEAM_PLATFORM_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID TEAM_DATASCI_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");
    static final UUID USER_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    static final UUID USER_LEAD_ID = UUID.fromString("00000000-0000-0000-0000-000000000200");
    static final UUID USER_MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000300");

    public DataSeeder(OrganizationRepository organizationRepository,
                      TeamRepository teamRepository,
                      UserRepository userRepository,
                      AgentTypeRepository agentTypeRepository,
                      AgentRunRepository agentRunRepository,
                      BudgetRepository budgetRepository,
                      PasswordEncoder passwordEncoder) {
        this.organizationRepository = organizationRepository;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.agentTypeRepository = agentTypeRepository;
        this.agentRunRepository = agentRunRepository;
        this.budgetRepository = budgetRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (organizationRepository.count() > 0) {
            log.info("Database already seeded, skipping");
            return;
        }

        log.info("Seeding database with test data...");

        // --- Organization ---
        Organization org = new Organization(ORG_ID, "acme-corp", "Acme Corporation");
        organizationRepository.save(org);

        // --- Teams ---
        Team platformTeam = new Team(TEAM_PLATFORM_ID, ORG_ID, "platform", "Platform Engineering");
        Team dataSciTeam = new Team(TEAM_DATASCI_ID, ORG_ID, "data-science", "Data Science");
        teamRepository.save(platformTeam);
        teamRepository.save(dataSciTeam);

        // --- Users (3 test users with different roles) ---
        // All passwords are "password123"
        String hash = passwordEncoder.encode("password123");

        User admin = new User(USER_ADMIN_ID, ORG_ID, "admin-ext", "admin@acme.com",
                "Alice Chen", hash, "ORG_ADMIN");
        admin.getTeams().add(platformTeam);
        admin.getTeams().add(dataSciTeam);

        User lead = new User(USER_LEAD_ID, ORG_ID, "lead-ext", "lead@acme.com",
                "Bob Martinez", hash, "TEAM_LEAD");
        lead.getTeams().add(platformTeam);

        User member = new User(USER_MEMBER_ID, ORG_ID, "member-ext", "member@acme.com",
                "Carol Johnson", hash, "MEMBER");
        member.getTeams().add(dataSciTeam);

        userRepository.save(admin);
        userRepository.save(lead);
        userRepository.save(member);

        // --- Agent Types ---
        AgentType codeReview = new AgentType(UUID.randomUUID(), ORG_ID, "code_review", "Code Review Agent");
        AgentType testGen = new AgentType(UUID.randomUUID(), ORG_ID, "test_generator", "Test Generator Agent");
        AgentType debugging = new AgentType(UUID.randomUUID(), ORG_ID, "debugging", "Debugging Agent");
        AgentType docWriter = new AgentType(UUID.randomUUID(), ORG_ID, "doc_writer", "Documentation Writer Agent");
        agentTypeRepository.saveAll(List.of(codeReview, testGen, debugging, docWriter));

        List<AgentType> agentTypes = List.of(codeReview, testGen, debugging, docWriter);
        List<User> users = List.of(admin, lead, member);
        String[] models = {"claude-sonnet-4", "claude-opus-4", "claude-haiku-3.5"};
        String[] modelVersions = {"20250514", "20250620", "20250301"};
        String[] statuses = {"SUCCEEDED", "SUCCEEDED", "SUCCEEDED", "SUCCEEDED",
                "SUCCEEDED", "SUCCEEDED", "FAILED", "CANCELLED"};
        String[] errorCategories = {"CONTEXT_LENGTH_EXCEEDED", "TIMEOUT", "RATE_LIMIT", "INTERNAL_ERROR"};
        Random random = new Random(42); // deterministic seed for reproducibility

        // --- Generate ~200 agent runs over the last 30 days ---
        Instant now = Instant.now();
        for (int i = 0; i < 200; i++) {
            User user = users.get(random.nextInt(users.size()));
            Team team = user.getTeams().iterator().next();
            AgentType agentType = agentTypes.get(random.nextInt(agentTypes.size()));

            AgentRun run = new AgentRun();
            run.setId(UUID.randomUUID());
            run.setOrgId(ORG_ID);
            run.setTeamId(team.getId());
            run.setUserId(user.getId());
            run.setAgentTypeSlug(agentType.getSlug());

            int modelIdx = random.nextInt(models.length);
            run.setModelName(models[modelIdx]);
            run.setModelVersion(modelVersions[modelIdx]);

            String status = statuses[random.nextInt(statuses.length)];
            run.setStatus(status);

            // Spread starts over last 30 days
            long hoursAgo = random.nextInt(30 * 24);
            Instant startedAt = now.minus(hoursAgo, ChronoUnit.HOURS)
                    .minus(random.nextInt(60), ChronoUnit.MINUTES);
            run.setStartedAt(startedAt);

            long durationMs = 5000 + random.nextInt(120000);
            run.setDurationMs(durationMs);
            run.setFinishedAt(startedAt.plusMillis(durationMs));

            long inputTokens = 50000 + random.nextInt(400000);
            long outputTokens = 20000 + random.nextInt(150000);
            run.setInputTokens(inputTokens);
            run.setOutputTokens(outputTokens);
            run.setTotalTokens(inputTokens + outputTokens);

            BigDecimal inputCost = BigDecimal.valueOf(inputTokens * 0.000003).setScale(6, RoundingMode.HALF_UP);
            BigDecimal outputCost = BigDecimal.valueOf(outputTokens * 0.000015).setScale(6, RoundingMode.HALF_UP);
            run.setInputCost(inputCost);
            run.setOutputCost(outputCost);
            run.setTotalCost(inputCost.add(outputCost));

            if ("FAILED".equals(status)) {
                run.setErrorCategory(errorCategories[random.nextInt(errorCategories.length)]);
                run.setErrorMessage("Simulated error during " + agentType.getDisplayName() +
                        " execution: " + run.getErrorCategory());
            }

            run.setCreatedAt(startedAt);
            agentRunRepository.save(run);
        }

        // --- Budgets ---
        Budget orgBudget = new Budget();
        orgBudget.setId(UUID.randomUUID());
        orgBudget.setOrgId(ORG_ID);
        orgBudget.setScope("ORGANIZATION");
        orgBudget.setScopeId(ORG_ID);
        orgBudget.setMonthlyLimit(new BigDecimal("50000.000000"));
        orgBudget.setThresholds("[0.50, 0.80, 1.00]");
        orgBudget.setNotificationChannels("[\"IN_APP\", \"EMAIL\"]");
        orgBudget.setCreatedAt(Instant.now());
        orgBudget.setUpdatedAt(Instant.now());
        budgetRepository.save(orgBudget);

        Budget teamBudget = new Budget();
        teamBudget.setId(UUID.randomUUID());
        teamBudget.setOrgId(ORG_ID);
        teamBudget.setScope("TEAM");
        teamBudget.setScopeId(TEAM_PLATFORM_ID);
        teamBudget.setMonthlyLimit(new BigDecimal("15000.000000"));
        teamBudget.setThresholds("[0.50, 0.80, 1.00]");
        teamBudget.setNotificationChannels("[\"IN_APP\", \"EMAIL\"]");
        teamBudget.setCreatedAt(Instant.now());
        teamBudget.setUpdatedAt(Instant.now());
        budgetRepository.save(teamBudget);

        log.info("Database seeded successfully!");
        log.info("Test users:");
        log.info("  admin@acme.com / password123 (ORG_ADMIN)");
        log.info("  lead@acme.com  / password123 (TEAM_LEAD)");
        log.info("  member@acme.com / password123 (MEMBER)");
    }
}
