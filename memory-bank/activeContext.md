# Active Context

## Current Work Focus

### JdbcTemplate Migration (#314) (April 26, 2026) — COMPLETE
Migrated all 4 SQLite repository implementations and DevDataSeeder from raw JDBC to Spring's JdbcTemplate. Evaluated ORM options in #305 — concluded that Hibernate/JPA and lighter ORMs (jOOQ, MyBatis, JDBI, Spring Data JDBC) are all poor fits for this project (no entity relationships, SQLite-specific SQL, only 4 tables). JdbcTemplate is the right middle ground.

**Key Changes:**
- Added `spring-boot-starter-jdbc` (HikariCP + JdbcTemplate + schema.sql auto-init)
- Added `spring-boot-starter-jdbc-test` (test scope, provides `@JdbcTest` for Spring Boot 4)
- Centralized all DDL into `src/main/resources/schema.sql` (4 tables, 6 indexes)
- Created `DatabaseDirectoryInitializer` (@PostConstruct for DB dir creation)
- DataSource auto-configured via `spring.datasource.*` properties with HikariCP (pool size 1)
- Removed manual `DataSource` bean from `BeanConfig`
- All 4 repositories rewritten: constructor takes `JdbcTemplate`, no `initializeSchema()`
- Added `PriceQuoteRepository.saveAll()` batch method + `PriceQuoteResponse.timestamp` field
- DevDataSeeder: routes inserts through `PriceQuoteRepository`, uses `JdbcTemplate` for queries
- Repository tests: `@JdbcTest` slice tests with `@AutoConfigureTestDatabase(replace = NONE)`
- Dropped 12 error-path tests (tested raw JDBC exception wrapping, no longer applicable)
- Spring `DataAccessException` propagates naturally (no manual `IllegalStateException` wrapping)

**Build:** All tests pass, 97% coverage maintained, spotless clean.

### EMA Pullback Buy Alert (#307) (April 21, 2026) — COMPLETE
Real-time EMA pullback buy alerts. Sends Telegram alerts when a stock pulls back below EMA 9/21 but stays above EMA 50/100/200, with positive RS vs SPY and VFI confirmation.
