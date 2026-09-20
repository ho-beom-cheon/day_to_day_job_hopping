# Migration provenance

`migration/V1__spring_session.sql` is copied byte-for-byte from
`org.springframework.session:spring-session-jdbc:3.5.7`,
`org/springframework/session/jdbc/schema-postgresql.sql` (Apache License 2.0).
The dependency version is managed by Spring Boot 3.5.16.

These are framework-owned session infrastructure tables, separate from the
business tables. Framework UUID session keys are not business entity IDs;
business IDs remain PostgreSQL bigint / Java Long / JSON and TypeScript string.
V2 implements the archived detailed business dictionary, V3 adds schedule previews
and FINAL tests, V4 adds OIDC profile/state, and V5 adds curriculum and learning
operation extensions. V6 adds problem reports, TRUE_FALSE support, and wrong-answer
review/resolution evidence. Applied migrations stay immutable. Provenance and
differences are recorded in docs/contracts.

Run `./mvnw -Ppostgres-it verify` against an available Docker engine to verify
clean PostgreSQL migration, full application startup, and JDBC session round-trip.
The integration profile must fail rather than silently skip when Docker is missing.
