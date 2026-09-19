# Migration provenance

`migration/V1__spring_session.sql` is copied byte-for-byte from
`org.springframework.session:spring-session-jdbc:3.5.7`,
`org/springframework/session/jdbc/schema-postgresql.sql` (Apache License 2.0).
The dependency version is managed by Spring Boot 3.5.16.

These are framework-owned session infrastructure tables, separate from the
46 business tables. Framework UUID session keys are not business entity IDs;
business IDs remain PostgreSQL bigint / Java Long / JSON and TypeScript string.
No business migration has been reconstructed from the summary (GAP-002).

Run `./mvnw -Ppostgres-it verify` against an available Docker engine to verify
clean PostgreSQL migration, full application startup, and JDBC session round-trip.
The integration profile must fail rather than silently skip when Docker is missing.
