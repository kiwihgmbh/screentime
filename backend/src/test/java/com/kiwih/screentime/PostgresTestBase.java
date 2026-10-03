package com.kiwih.screentime;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The same PostgreSQL version as production. Tests that touch the database run
 * against the real thing, because the schema is full of Postgres specific
 * detail: timestamptz, partial unique indexes and check constraints.
 *
 * One container for the whole test run, started here and never stopped.
 * {@code @Container} would tie it to a single test class and leave the next
 * class with a dead database; Testcontainers' own reaper removes it when the
 * JVM exits.
 */
public abstract class PostgresTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
