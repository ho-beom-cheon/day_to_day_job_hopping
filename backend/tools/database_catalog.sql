-- Read-only summary; the full source comparison runs in BusinessSchemaIT.
SELECT jsonb_pretty(jsonb_build_object(
    'postgresVersion', current_setting('server_version'),
    'clusterId', (SELECT system_identifier::text FROM pg_control_system()),
    'businessSchema', 'daily_career',
    'businessTables', (SELECT count(*) FROM pg_tables WHERE schemaname = 'daily_career'),
    'businessColumns', (SELECT count(*) FROM information_schema.columns WHERE table_schema = 'daily_career'),
    'columnComments', (SELECT count(*) FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid
        WHERE c.relnamespace = 'daily_career'::regnamespace AND c.relkind = 'r' AND a.attnum > 0
          AND NOT a.attisdropped AND col_description(a.attrelid, a.attnum) IS NOT NULL),
    'constraints', (SELECT jsonb_object_agg(kind, total) FROM
        (SELECT contype::text AS kind, count(*) AS total FROM pg_constraint
         WHERE connamespace = 'daily_career'::regnamespace GROUP BY contype) counts),
    'businessIndexes', (SELECT count(*) FROM pg_indexes WHERE schemaname = 'daily_career'),
    'publicInfrastructureTables', (SELECT jsonb_agg(tablename ORDER BY tablename) FROM pg_tables WHERE schemaname = 'public'),
    'migrations', (SELECT jsonb_agg(jsonb_build_object('version', version, 'description', description,
        'checksum', checksum, 'success', success) ORDER BY installed_rank) FROM public.flyway_schema_history)
));
