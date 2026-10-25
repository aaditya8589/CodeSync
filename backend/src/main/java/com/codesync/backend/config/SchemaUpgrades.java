package com.codesync.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Changes Hibernate's automatic schema update cannot make. It adds tables and columns, but never
 * relaxes a column (a match in the lobby has no start or end time yet) and never updates the
 * check it puts on an enum column (rooms.kind gained INTERVIEW; the column is still checked by
 * Java). Each statement is safe to run on every start. (A migration tool such as Flyway would
 * take over this job.)
 */
@Component
public class SchemaUpgrades implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaUpgrades.class);

    private static final String[] STATEMENTS = {
            "ALTER TABLE IF EXISTS matches ALTER COLUMN started_at DROP NOT NULL",
            "ALTER TABLE IF EXISTS matches ALTER COLUMN ends_at DROP NOT NULL",
            "ALTER TABLE IF EXISTS rooms DROP CONSTRAINT IF EXISTS rooms_kind_check",
    };

    private final JdbcTemplate jdbc;

    public SchemaUpgrades(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String statement : STATEMENTS) {
            jdbc.execute(statement);
        }
        log.debug("Schema upgrades applied");
    }
}
