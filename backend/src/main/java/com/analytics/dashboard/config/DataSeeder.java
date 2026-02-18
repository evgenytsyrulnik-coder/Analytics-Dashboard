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
 * - 2 organizations (Acme Corporation, Globex Industries)
 * - 5 teams per organization (10 total)
 * - 6 members per team
 * - 4 agent types per organization
 * - ~50+ daily agent runs per user over the last 90 days
 * - Budgets for each organization and every team
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
    static final UUID ORG_ACME_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID ORG_GLOBEX_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

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

    /** Generates a deterministic UUID from an integer for predictable test data. */
    private static UUID uuid(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }

    @Override
    public void run(String... args) {
        if (organizationRepository.count() > 0) {
            log.info("Database already seeded, skipping");
            return;
        }

        log.info("Seeding database with test data...");
        String hash = passwordEncoder.encode("password123");
        Random random = new Random(42); // deterministic seed for reproducibility

        // ==================== Organizations ====================
        Organization acme = new Organization(ORG_ACME_ID, "acme-corp", "Acme Corporation");
        Organization globex = new Organization(ORG_GLOBEX_ID, "globex-ind", "Globex Industries");
        organizationRepository.saveAll(List.of(acme, globex));

        // ==================== Teams (5 per org) ====================
        String[][] acmeTeamDefs = {
            {"platform", "Platform Engineering"},
            {"data-science", "Data Science"},
            {"backend", "Backend Services"},
            {"frontend", "Frontend"},
            {"devops", "DevOps"}
        };
        String[][] globexTeamDefs = {
            {"cloud-infra", "Cloud Infrastructure"},
            {"ml", "Machine Learning"},
            {"mobile", "Mobile"},
            {"security", "Security"},
            {"qa-automation", "QA Automation"}
        };

        List<Team> acmeTeams = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            acmeTeams.add(new Team(uuid(10 + i), ORG_ACME_ID, acmeTeamDefs[i][0], acmeTeamDefs[i][1]));
        }
        teamRepository.saveAll(acmeTeams);

        List<Team> globexTeams = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            globexTeams.add(new Team(uuid(20 + i), ORG_GLOBEX_ID, globexTeamDefs[i][0], globexTeamDefs[i][1]));
        }
        teamRepository.saveAll(globexTeams);

        // ==================== Users ====================
        // Acme: 1 admin (all teams) + 5 team leads + 20 members = 26 users
        //   Each team: 1 admin + 1 lead + 4 members = 6
        // Globex: 2 admins (all teams) + 5 team leads + 15 members = 22 users
        //   Each team: 2 admins + 1 lead + 3 members = 6

        String[] acmeLeadNames = {"Raj Patel", "Sarah Kim", "Tom Wilson", "Mei Zhang", "James O'Brien"};
        String[] acmeMemberNames = {
            "Liam Brown", "Olivia Davis", "Noah Garcia", "Emma Martinez",
            "Aiden Taylor", "Sophia Anderson", "Lucas Thomas", "Isabella Jackson",
            "Mason White", "Ava Harris", "Ethan Clark", "Mia Lewis",
            "Logan Robinson", "Charlotte Walker", "Jacob Young", "Amelia King",
            "Elijah Wright", "Harper Lopez", "Alexander Hill", "Evelyn Scott"
        };

        // -- Acme admin (member of all 5 teams) --
        User acmeAdmin = new User(uuid(100), ORG_ACME_ID, "admin-ext", "admin@acme.com",
                "Alice Chen", hash, "ORG_ADMIN");
        for (Team t : acmeTeams) acmeAdmin.getTeams().add(t);
        userRepository.save(acmeAdmin);

        List<User> acmeUsers = new ArrayList<>();
        acmeUsers.add(acmeAdmin);

        // -- Acme team leads (1 per team) --
        for (int i = 0; i < 5; i++) {
            User lead = new User(uuid(101 + i), ORG_ACME_ID,
                    "acme-lead-" + i, "lead-" + acmeTeamDefs[i][0] + "@acme.com",
                    acmeLeadNames[i], hash, "TEAM_LEAD");
            lead.getTeams().add(acmeTeams.get(i));
            userRepository.save(lead);
            acmeUsers.add(lead);
        }

        // -- Acme members (4 per team, 20 total) --
        for (int i = 0; i < 20; i++) {
            User member = new User(uuid(110 + i), ORG_ACME_ID,
                    "acme-member-" + i, "member" + (i + 1) + "@acme.com",
                    acmeMemberNames[i], hash, "MEMBER");
            member.getTeams().add(acmeTeams.get(i / 4));
            userRepository.save(member);
            acmeUsers.add(member);
        }

        // -- Globex admins (both are members of all 5 teams) --
        String[] globexLeadNames = {"Priya Sharma", "David Lee", "Ana Garcia", "Kevin Nguyen", "Rachel Cohen"};
        String[] globexMemberNames = {
            "Ryan Mitchell", "Zoe Campbell", "Nathan Rivera",
            "Lily Turner", "Owen Phillips", "Grace Evans",
            "Caleb Parker", "Chloe Edwards", "Dylan Collins",
            "Aria Stewart", "Luke Sanchez", "Nora Morris",
            "Henry Rogers", "Ella Reed", "Sebastian Cook"
        };

        User globexAdmin = new User(uuid(200), ORG_GLOBEX_ID, "globex-admin-ext", "admin@globex.com",
                "Diana Ross", hash, "ORG_ADMIN");
        for (Team t : globexTeams) globexAdmin.getTeams().add(t);
        userRepository.save(globexAdmin);

        User globexAdmin2 = new User(uuid(201), ORG_GLOBEX_ID, "globex-admin2-ext", "admin2@globex.com",
                "Edward Kim", hash, "ORG_ADMIN");
        for (Team t : globexTeams) globexAdmin2.getTeams().add(t);
        userRepository.save(globexAdmin2);

        List<User> globexUsers = new ArrayList<>();
        globexUsers.add(globexAdmin);
        globexUsers.add(globexAdmin2);

        // -- Globex team leads (1 per team) --
        for (int i = 0; i < 5; i++) {
            User lead = new User(uuid(202 + i), ORG_GLOBEX_ID,
                    "globex-lead-" + i, "lead-" + globexTeamDefs[i][0] + "@globex.com",
                    globexLeadNames[i], hash, "TEAM_LEAD");
            lead.getTeams().add(globexTeams.get(i));
            userRepository.save(lead);
            globexUsers.add(lead);
        }

        // -- Globex members (3 per team, 15 total) --
        for (int i = 0; i < 15; i++) {
            User member = new User(uuid(210 + i), ORG_GLOBEX_ID,
                    "globex-member-" + i, "member" + (i + 1) + "@globex.com",
                    globexMemberNames[i], hash, "MEMBER");
            member.getTeams().add(globexTeams.get(i / 3));
            userRepository.save(member);
            globexUsers.add(member);
        }

        // ==================== Agent Types (per org) ====================
        String[][] agentTypeDefs = {
            {"code_review", "Code Review Agent"},
            {"test_generator", "Test Generator Agent"},
            {"debugging", "Debugging Agent"},
            {"doc_writer", "Documentation Writer Agent"}
        };

        List<AgentType> acmeAgentTypes = new ArrayList<>();
        List<AgentType> globexAgentTypes = new ArrayList<>();
        for (String[] def : agentTypeDefs) {
            acmeAgentTypes.add(new AgentType(UUID.randomUUID(), ORG_ACME_ID, def[0], def[1]));
            globexAgentTypes.add(new AgentType(UUID.randomUUID(), ORG_GLOBEX_ID, def[0], def[1]));
        }
        agentTypeRepository.saveAll(acmeAgentTypes);
        agentTypeRepository.saveAll(globexAgentTypes);

        // ==================== Agent Runs (90 days, ~50+/user/day) ====================
        String[] models = {"claude-sonnet-4", "claude-opus-4", "claude-haiku-3.5"};
        String[] modelVersions = {"20250514", "20250620", "20250301"};
        String[] statuses = {"SUCCEEDED", "SUCCEEDED", "SUCCEEDED", "SUCCEEDED",
                "SUCCEEDED", "SUCCEEDED", "FAILED", "CANCELLED"};
        String[] errorCategories = {"CONTEXT_LENGTH_EXCEEDED", "TIMEOUT", "RATE_LIMIT", "INTERNAL_ERROR"};
        Instant now = Instant.now();

        log.info("Generating agent runs for Acme Corporation ({} users)...", acmeUsers.size());
        generateAgentRuns(acmeUsers, acmeAgentTypes, ORG_ACME_ID,
                models, modelVersions, statuses, errorCategories, random, now);

        log.info("Generating agent runs for Globex Industries ({} users)...", globexUsers.size());
        generateAgentRuns(globexUsers, globexAgentTypes, ORG_GLOBEX_ID,
                models, modelVersions, statuses, errorCategories, random, now);

        // ==================== Budgets ====================
        seedBudgets(ORG_ACME_ID, acmeTeams);
        seedBudgets(ORG_GLOBEX_ID, globexTeams);

        log.info("Database seeded successfully!");
        log.info("Test users (all passwords: password123):");
        log.info("  Acme Corporation:");
        log.info("    admin@acme.com (ORG_ADMIN)");
        for (int i = 0; i < 5; i++)
            log.info("    lead-{}@acme.com (TEAM_LEAD)", acmeTeamDefs[i][0]);
        log.info("    member1@acme.com ... member20@acme.com (MEMBER)");
        log.info("  Globex Industries:");
        log.info("    admin@globex.com (ORG_ADMIN)");
        log.info("    admin2@globex.com (ORG_ADMIN)");
        for (int i = 0; i < 5; i++)
            log.info("    lead-{}@globex.com (TEAM_LEAD)", globexTeamDefs[i][0]);
        log.info("    member1@globex.com ... member15@globex.com (MEMBER)");
    }

    private void generateAgentRuns(List<User> users, List<AgentType> agentTypes, UUID orgId,
                                   String[] models, String[] modelVersions,
                                   String[] statuses, String[] errorCategories,
                                   Random random, Instant now) {
        // Target: ~55 runs per user per day over 90 days (above the 50 minimum)
        int runsPerUser = 55 * 90; // 4950 runs per user
        List<AgentRun> batch = new ArrayList<>(1000);
        int totalRuns = 0;

        for (User user : users) {
            List<Team> userTeams = new ArrayList<>(user.getTeams());

            for (int i = 0; i < runsPerUser; i++) {
                Team team = userTeams.get(random.nextInt(userTeams.size()));
                AgentType agentType = agentTypes.get(random.nextInt(agentTypes.size()));

                AgentRun run = new AgentRun();
                run.setId(UUID.randomUUID());
                run.setOrgId(orgId);
                run.setTeamId(team.getId());
                run.setUserId(user.getId());
                run.setAgentTypeSlug(agentType.getSlug());

                int modelIdx = random.nextInt(models.length);
                run.setModelName(models[modelIdx]);
                run.setModelVersion(modelVersions[modelIdx]);

                String status = statuses[random.nextInt(statuses.length)];
                run.setStatus(status);

                // Spread starts over last 90 days
                long minutesAgo = random.nextInt(90 * 24 * 60);
                Instant startedAt = now.minus(minutesAgo, ChronoUnit.MINUTES);
                run.setStartedAt(startedAt);

                long durationMs = 5000 + random.nextInt(120000);
                run.setDurationMs(durationMs);
                run.setFinishedAt(startedAt.plusMillis(durationMs));

                long inputTokens = 50000 + random.nextInt(400000);
                long outputTokens = 20000 + random.nextInt(150000);
                run.setInputTokens(inputTokens);
                run.setOutputTokens(outputTokens);
                run.setTotalTokens(inputTokens + outputTokens);

                BigDecimal inputCost = BigDecimal.valueOf(inputTokens * 0.000003)
                        .setScale(6, RoundingMode.HALF_UP);
                BigDecimal outputCost = BigDecimal.valueOf(outputTokens * 0.000015)
                        .setScale(6, RoundingMode.HALF_UP);
                run.setInputCost(inputCost);
                run.setOutputCost(outputCost);
                run.setTotalCost(inputCost.add(outputCost));

                if ("FAILED".equals(status)) {
                    run.setErrorCategory(errorCategories[random.nextInt(errorCategories.length)]);
                    run.setErrorMessage("Simulated error during " + agentType.getDisplayName()
                            + " execution: " + run.getErrorCategory());
                }

                run.setCreatedAt(startedAt);
                batch.add(run);

                if (batch.size() >= 1000) {
                    agentRunRepository.saveAll(batch);
                    batch.clear();
                    totalRuns += 1000;
                    if (totalRuns % 50000 == 0) {
                        log.info("  ... {} agent runs generated so far", totalRuns);
                    }
                }
            }
        }

        if (!batch.isEmpty()) {
            totalRuns += batch.size();
            agentRunRepository.saveAll(batch);
            batch.clear();
        }
        log.info("  Total agent runs for org: {}", totalRuns);
    }

    private void seedBudgets(UUID orgId, List<Team> teams) {
        Budget orgBudget = new Budget();
        orgBudget.setId(UUID.randomUUID());
        orgBudget.setOrgId(orgId);
        orgBudget.setScope("ORGANIZATION");
        orgBudget.setScopeId(orgId);
        orgBudget.setMonthlyLimit(new BigDecimal("50000.000000"));
        orgBudget.setThresholds("[0.50, 0.80, 1.00]");
        orgBudget.setNotificationChannels("[\"IN_APP\", \"EMAIL\"]");
        orgBudget.setCreatedAt(Instant.now());
        orgBudget.setUpdatedAt(Instant.now());
        budgetRepository.save(orgBudget);

        for (Team team : teams) {
            Budget teamBudget = new Budget();
            teamBudget.setId(UUID.randomUUID());
            teamBudget.setOrgId(orgId);
            teamBudget.setScope("TEAM");
            teamBudget.setScopeId(team.getId());
            teamBudget.setMonthlyLimit(new BigDecimal("15000.000000"));
            teamBudget.setThresholds("[0.50, 0.80, 1.00]");
            teamBudget.setNotificationChannels("[\"IN_APP\", \"EMAIL\"]");
            teamBudget.setCreatedAt(Instant.now());
            teamBudget.setUpdatedAt(Instant.now());
            budgetRepository.save(teamBudget);
        }
    }
}
