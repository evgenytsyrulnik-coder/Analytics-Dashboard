package com.analytics.dashboard.ingestion;

import com.analytics.dashboard.entity.AgentRun;
import com.analytics.dashboard.entity.AgentType;
import com.analytics.dashboard.entity.Team;
import com.analytics.dashboard.entity.User;
import com.analytics.dashboard.repository.AgentRunRepository;
import com.analytics.dashboard.repository.AgentTypeRepository;
import com.analytics.dashboard.repository.TeamRepository;
import com.analytics.dashboard.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Emulates Kafka event ingestion by periodically generating fake agent run events.
 * This simulates the real-time flow: Agent Platform -> Kafka -> Consumer -> Database.
 */
@Component
@ConditionalOnProperty(name = "app.kafka-emulator.enabled", havingValue = "true")
public class KafkaEventEmulator {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventEmulator.class);

    private static final String[] MODELS = {"claude-sonnet-4", "claude-opus-4", "claude-haiku-3.5"};
    private static final String[] MODEL_VERSIONS = {"20250514", "20250620", "20250301"};
    private static final String[] STATUSES = {
            "SUCCEEDED", "SUCCEEDED", "SUCCEEDED", "SUCCEEDED",
            "SUCCEEDED", "SUCCEEDED", "SUCCEEDED", "FAILED", "CANCELLED"
    };
    private static final String[] ERROR_CATEGORIES = {
            "CONTEXT_LENGTH_EXCEEDED", "TIMEOUT", "RATE_LIMIT",
            "INTERNAL_ERROR", "INVALID_INPUT"
    };

    private static final double INPUT_COST_PER_TOKEN = 0.000003;
    private static final double OUTPUT_COST_PER_TOKEN = 0.000015;
    private static final int MIN_INPUT_TOKENS = 10_000;
    private static final int INPUT_TOKEN_RANGE = 500_000;
    private static final int MIN_OUTPUT_TOKENS = 5_000;
    private static final int OUTPUT_TOKEN_RANGE = 200_000;
    private static final int MIN_DURATION_MS = 5_000;
    private static final int DURATION_RANGE_MS = 180_000;
    private static final int MAX_START_OFFSET_MINUTES = 120;

    private final AgentRunRepository agentRunRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final AgentTypeRepository agentTypeRepository;
    private final Random random = new Random();

    @Value("${app.kafka-emulator.batch-size:5}")
    private int batchSize;

    public KafkaEventEmulator(AgentRunRepository agentRunRepository,
                               UserRepository userRepository,
                               TeamRepository teamRepository,
                               AgentTypeRepository agentTypeRepository) {
        this.agentRunRepository = agentRunRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.agentTypeRepository = agentTypeRepository;
    }

    @Scheduled(fixedDelayString = "${app.kafka-emulator.interval-seconds:30}000")
    public void emulateKafkaIngestion() {
        List<User> users = userRepository.findAll();
        List<Team> teams = teamRepository.findAll();
        List<AgentType> agentTypes = agentTypeRepository.findAll();

        if (users.isEmpty() || teams.isEmpty() || agentTypes.isEmpty()) {
            return;
        }

        log.info("Kafka emulator: generating {} agent run events", batchSize);

        for (int i = 0; i < batchSize; i++) {
            AgentRun run = buildRandomRun(users, teams, agentTypes);
            agentRunRepository.save(run);
        }

        log.info("Kafka emulator: {} events ingested successfully", batchSize);
    }

    private AgentRun buildRandomRun(List<User> users, List<Team> teams, List<AgentType> agentTypes) {
        User user = pickRandom(users);
        Team team = findUserTeam(user, teams);
        AgentType agentType = pickRandom(agentTypes);
        String status = pickRandom(STATUSES);
        int modelIdx = random.nextInt(MODELS.length);

        AgentRun run = new AgentRun();
        run.setId(UUID.randomUUID());
        run.setOrgId(user.getOrgId());
        run.setTeamId(team.getId());
        run.setUserId(user.getId());
        run.setAgentTypeSlug(agentType.getSlug());
        run.setModelName(MODELS[modelIdx]);
        run.setModelVersion(MODEL_VERSIONS[modelIdx]);
        run.setStatus(status);

        Instant startedAt = Instant.now().minus(random.nextInt(MAX_START_OFFSET_MINUTES), ChronoUnit.MINUTES);
        run.setStartedAt(startedAt);

        long durationMs = MIN_DURATION_MS + random.nextInt(DURATION_RANGE_MS);
        run.setDurationMs(durationMs);
        run.setFinishedAt(startedAt.plusMillis(durationMs));

        long inputTokens = MIN_INPUT_TOKENS + random.nextInt(INPUT_TOKEN_RANGE);
        long outputTokens = MIN_OUTPUT_TOKENS + random.nextInt(OUTPUT_TOKEN_RANGE);
        run.setInputTokens(inputTokens);
        run.setOutputTokens(outputTokens);
        run.setTotalTokens(inputTokens + outputTokens);

        BigDecimal inputCost = BigDecimal.valueOf(inputTokens * INPUT_COST_PER_TOKEN).setScale(6, RoundingMode.HALF_UP);
        BigDecimal outputCost = BigDecimal.valueOf(outputTokens * OUTPUT_COST_PER_TOKEN).setScale(6, RoundingMode.HALF_UP);
        run.setInputCost(inputCost);
        run.setOutputCost(outputCost);
        run.setTotalCost(inputCost.add(outputCost));

        if ("FAILED".equals(status)) {
            String errorCategory = pickRandom(ERROR_CATEGORIES);
            run.setErrorCategory(errorCategory);
            run.setErrorMessage("Simulated error: " + errorCategory);
        }

        run.setCreatedAt(Instant.now());
        return run;
    }

    private Team findUserTeam(User user, List<Team> teams) {
        return teams.stream()
                .filter(t -> user.getTeams().stream().anyMatch(ut -> ut.getId().equals(t.getId())))
                .findFirst()
                .orElse(pickRandom(teams));
    }

    private <T> T pickRandom(List<T> items) {
        return items.get(random.nextInt(items.size()));
    }

    private String pickRandom(String[] items) {
        return items[random.nextInt(items.length)];
    }
}
