# Development seed boundary

The business schema is implemented by V2–V5. At stage 6 the user chose to keep
operational curriculum content empty and verify with test-only fixtures. Those
fixtures live under src/test and are never packaged or applied to this database.
Do not create guessed operational content or real user data.

Once approved data exists, add deterministic Flyway migrations here and activate
`APP_PROFILES=dev,dev-seed` in the local `.env`. Use globally unique version numbers
that cannot collide with `db/migration`. Keep the profile unchanged for the lifetime
of that development volume; removing migrations from a previously seeded history
causes Flyway validation errors. To remove seed, recreate the disposable development
volume using the documented reset procedure. Never enable this profile in production
and never copy a seeded volume into production. Re-running normal startup must not
duplicate fixtures. Production migrations remain in `db/migration` only.
