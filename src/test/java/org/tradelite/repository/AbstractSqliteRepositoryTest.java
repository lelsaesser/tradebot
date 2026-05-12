package org.tradelite.repository;

import java.util.UUID;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("classpath:schema.sql")
abstract class AbstractSqliteRepositoryTest {

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        String url = "jdbc:sqlite:file:" + UUID.randomUUID() + "?mode=memory&cache=shared";
        registry.add("spring.datasource.url", () -> url);
    }
}
