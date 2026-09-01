package com.fundit.project.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 통합 테스트 공통 기반. 컨테이너를 static으로 한 번만 띄워 테스트 클래스마다 재기동하지 않는다.
 * 운영과 같은 PostgreSQL 16을 쓰고 Flyway 마이그레이션을 그대로 태워, JSONB 매핑이나
 * 복합 FK 같은 PostgreSQL 고유 동작까지 검증 대상에 포함시킨다.
 */
@ActiveProfiles("test")
@SpringBootTest
public abstract class IntegrationTestSupport {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * 컨테이너를 재사용하는 만큼 테스트 간 데이터가 남지 않게 매번 비운다.
     * projects에 딸린 테이블이 외래키로 물려 있어 CASCADE로 함께 지운다 —
     * categories는 projects가 참조하는 쪽이라 그대로 남는다.
     */
    @BeforeEach
    void clearProjectData() {
        jdbcTemplate.execute("TRUNCATE TABLE projects RESTART IDENTITY CASCADE");
    }
}
