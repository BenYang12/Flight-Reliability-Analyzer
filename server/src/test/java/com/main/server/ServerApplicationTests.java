package com.main.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The single test in this project (PLAN.md Appendix A7 rules out a test suite).
 *
 * <p>It looks like it does nothing, and that impression is wrong.
 * {@code @SpringBootTest} starts the ENTIRE application context: every bean is
 * created, and — because we depend on JPA — Hibernate connects to the database
 * and validates its mapping against the real schema.
 *
 * <p>So an empty method body is a genuine smoke test. If the datasource URL is
 * wrong, the container is down, an entity is mis-annotated, or two beans
 * collide, this fails. It requires a running Postgres.
 */
@SpringBootTest
class ServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
