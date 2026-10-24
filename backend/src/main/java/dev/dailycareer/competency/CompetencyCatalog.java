package dev.dailycareer.competency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes the packaged competency map and sheets. A changed sheet becomes a new version; unused bank tasks
 * of older versions are retired so new tasks are generated from the current text (design §6.1).
 */
@Component
class CompetencyCatalog implements ApplicationRunner {
    static final String MAP_CODE = "financial-backend";
    private static final Logger log = LoggerFactory.getLogger(CompetencyCatalog.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    CompetencyCatalog(JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.json = json;
        this.tx = new TransactionTemplate(manager);
    }

    @Override public void run(ApplicationArguments args) throws IOException {
        JsonNode map = json.readTree(new ClassPathResource("competency/v1/map.json").getInputStream());
        long mapVersion = tx.execute(status -> publishMap(map));
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath:competency/v1/sheets/*.md")) {
            String text = resource.getContentAsString(StandardCharsets.UTF_8);
            try {
                SheetDocument sheet = SheetDocument.parse(text);
                tx.executeWithoutResult(status -> publishSheet(mapVersion, sheet, text));
            } catch (RuntimeException invalid) {
                log.error("Competency sheet {} was not published: {}", resource.getFilename(), invalid.getMessage());
            }
        }
    }

    private long publishMap(JsonNode map) {
        int versionNo = map.path("versionNo").asInt();
        jdbc.update("insert into daily_career.competency_map_version(code,version_no) values (?,?) on conflict (code,version_no) do nothing",
                MAP_CODE, versionNo);
        Long id = jdbc.queryForObject("select id from daily_career.competency_map_version where code=? and version_no=?", Long.class, MAP_CODE, versionNo);
        int order = 0;
        for (JsonNode item : map.path("competencies")) {
            jdbc.update("""
                    insert into daily_career.competency(map_version_id,code,area,area_title,title,priority,target_level,order_no)
                    values (?,?,?,?,?,?,?,?) on conflict (map_version_id,code) do update set area=excluded.area,
                        area_title=excluded.area_title,title=excluded.title,priority=excluded.priority,
                        target_level=excluded.target_level,order_no=excluded.order_no
                    """, id, item.path("code").asText(), item.path("area").asText(), item.path("areaTitle").asText(),
                    item.path("title").asText(), item.path("priority").asText(), item.path("targetLevel").asInt(), ++order);
        }
        return id;
    }

    private void publishSheet(long mapVersion, SheetDocument sheet, String text) {
        var competencies = jdbc.queryForList("select id from daily_career.competency where map_version_id=? and code=?",
                Long.class, mapVersion, sheet.code());
        if (competencies.isEmpty()) throw new IllegalArgumentException("Unknown competency " + sheet.code());
        long competency = competencies.getFirst();
        String digest = sha256(text);
        Boolean known = jdbc.queryForObject("select exists(select 1 from daily_career.competency_sheet_version where competency_id=? and content_digest=?)",
                Boolean.class, competency, digest);
        if (Boolean.TRUE.equals(known)) return;
        Integer next = jdbc.queryForObject("select coalesce(max(version_no),0)+1 from daily_career.competency_sheet_version where competency_id=?",
                Integer.class, competency);
        jdbc.update("update daily_career.competency_sheet_version set status='RETIRED' where competency_id=? and status='PUBLISHED'", competency);
        jdbc.update("""
                update daily_career.task_bank_item set status='RETIRED'
                where competency_id=? and status='ACTIVE' and owner_user_id is null
                """, competency);
        try {
            jdbc.update("""
                    insert into daily_career.competency_sheet_version
                        (competency_id,version_no,body_markdown,criteria_ids,source_references,content_digest,review_label)
                    values (?,?,?,?::jsonb,?::jsonb,?,?)
                    """, competency, next, text, json.writeValueAsString(sheet.criteriaIds()),
                    json.writeValueAsString(sheet.sources()), digest, sheet.review());
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        log.info("Published competency sheet {} version {}", sheet.code(), next);
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
