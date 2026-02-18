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

    private final AgentRunRepository agentRunRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final AgentTypeRepository agentTypeRepository;
    private final Random random = new Random();

    private static final String[] MODELS = {"claude-sonnet-4", "claude-opus-4", "claude-haiku-3.5"};
    private static final String[] MODEL_VERSIONS = {"20250514", "20250620", "20250301"};
    private static final String[] STATUSES = {"SUCCEEDED", "SUCCEEDED", "SUCCEEDED", "SUCCEEDED",
            "SUCCEEDED", "SUCCEEDED", "SUCCEEDED", "FAILED", "CANCELLED"};
    private static final String[] ERROR_CATEGORIES = {"CONTEXT_LENGTH_EXCEEDED", "TIMEOUT", "RATE_LIMIT",
            "INTERNAL_ERROR", "INVALID_INPUT"};

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
            User user = users.get(random.nextInt(users.size()));
            Team team = teams.stream()
                    .filter(t -> user.getTeams().stream().anyMatch(ut -> ut.getId().equals(t.getId())))
                    .findFirst()
                    .orElse(teams.get(random.nextInt(teams.size())));
            AgentType agentType = agentTypes.get(random.nextInt(agentTypes.size()));

            AgentRun run = new AgentRun();
            run.setId(UUID.randomUUID());
            run.setOrgId(user.getOrgId());
            run.setTeamId(team.getId());
            run.setUserId(user.getId());
            run.setAgentTypeSlug(agentType.getSlug());

            int modelIdx = random.nextInt(MODELS.length);
            run.setModelName(MODELS[modelIdx]);
            run.setModelVersion(MODEL_VERSIONS[modelIdx]);

            String status = STATUSES[random.nextInt(STATUSES.length)];
            run.setStatus(status);

            // Random start time within the last 2 hours
            Instant startedAt = Instant.now().minus(random.nextInt(120), ChronoUnit.MINUTES);
            run.setStartedAt(startedAt);

            long durationMs = 5000 + random.nextInt(180000); // 5s to 185s
            run.setDurationMs(durationMs);
            run.setFinishedAt(startedAt.plusMillis(durationMs));

            long inputTokens = 10000 + random.nextInt(500000);
            long outputTokens = 5000 + random.nextInt(200000);
            run.setInputTokens(inputTokens);
            run.setOutputTokens(outputTokens);
            run.setTotalTokens(inputTokens + outputTokens);

            BigDecimal inputCost = BigDecimal.valueOf(inputTokens * 0.000003).setScale(6, RoundingMode.HALF_UP);
            BigDecimal outputCost = BigDecimal.valueOf(outputTokens * 0.000015).setScale(6, RoundingMode.HALF_UP);
            run.setInputCost(inputCost);
            run.setOutputCost(outputCost);
            run.setTotalCost(inputCost.add(outputCost));

            if ("FAILED".equals(status)) {
                run.setErrorCategory(ERROR_CATEGORIES[random.nextInt(ERROR_CATEGORIES.length)]);
                run.setErrorMessage("Simulated error: " + run.getErrorCategory());
            }

            run.setCreatedAt(Instant.now());
            agentRunRepository.save(run);
        }

        log.info("Kafka emulator: {} events ingested successfully", batchSize);
    }
}
