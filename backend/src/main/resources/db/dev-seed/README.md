# Development seed boundary

The production migrations implement the business schema. `V8.1` is the approved,
deterministic development catalog for curriculum, problem, and exam flows. The
content is original to this project, contains no user data, and is never loaded
unless the explicit `dev-seed` profile is active.

Activate `APP_PROFILES=dev,dev-seed` in the local `.env`. Use globally unique version numbers
that cannot collide with `db/migration`. Keep the profile unchanged for the lifetime
of that development volume; removing migrations from a previously seeded history
causes Flyway validation errors. To remove seed, recreate the disposable development
volume using the documented reset procedure. Never enable this profile in production
and never copy a seeded volume into production. Re-running normal startup must not
duplicate fixtures. Production migrations remain in `db/migration` only.
