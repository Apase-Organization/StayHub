package com.stayhub.config;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@org.springframework.core.annotation.Order(0)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed-data", name = "enabled", havingValue = "true")
public class DataSeedRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final ResourceLoader resourceLoader;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM properties WHERE city IN ('Hanoi', 'Ho Chi Minh', 'Da Nang', 'Da Lat', 'Phu Quoc')",
                Integer.class
            );
            if (count != null && count >= 15) {
                log.info("Seed properties already exist (count={}), skipping seed initialization.", count);
                return;
            }
            log.info("Seeding famous destination properties, room types, rate plans, and 180-day inventory...");
            Resource resource = resourceLoader.getResource("classpath:db/seed/seed_famous_destinations.sql");
            if (!resource.exists()) {
                log.warn("Seed script classpath:db/seed/seed_famous_destinations.sql not found, skipping.");
                return;
            }
            String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            jdbcTemplate.execute(sql);
            log.info("Successfully seeded famous destination properties and room types!");
        } catch (Exception e) {
            log.error("Failed to seed initial destination data: {}", e.getMessage(), e);
        }
    }
}
