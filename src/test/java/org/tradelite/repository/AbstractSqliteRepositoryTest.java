package org.tradelite.repository;

import java.util.UUID;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;

@JdbcTest
@ContextConfiguration(
        classes = {DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("classpath:schema.sql")
abstract class AbstractSqliteRepositoryTest {

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        String url = "jdbc:sqlite:file:" + UUID.randomUUID() + "?mode=memory&cache=shared";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "1");
    }
}
