package dev.dailycareer.competency;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Read access to the published competency map and sheets. Parsed sheets are cached by immutable version id. */
@Component
class CompetencyStore {
    private final JdbcTemplate jdbc;
    private final Map<Long, SheetDocument> parsed = new ConcurrentHashMap<>();

    CompetencyStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    record Competency(long id, String code, String area, String areaTitle, String title, String priority, int targetLevel) {}
    record Sheet(long versionId, int versionNo, String reviewLabel, SheetDocument document) {}

    Competency competency(long id) {
        return jdbc.queryForObject("select id,code,area,area_title,title,priority,target_level from daily_career.competency where id=?",
                (row, number) -> new Competency(row.getLong(1), row.getString(2), row.getString(3), row.getString(4),
                        row.getString(5), row.getString(6), row.getInt(7)), id);
    }

    Competency byCode(String code) {
        var rows = jdbc.query("""
                select c.id,c.code,c.area,c.area_title,c.title,c.priority,c.target_level from daily_career.competency c
                join daily_career.competency_map_version m on m.id=c.map_version_id
                where m.code=? and m.status='PUBLISHED' and c.code=? order by m.version_no desc limit 1
                """, (row, number) -> new Competency(row.getLong(1), row.getString(2), row.getString(3), row.getString(4),
                row.getString(5), row.getString(6), row.getInt(7)), CompetencyCatalog.MAP_CODE, code);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** Latest published sheet, or null while the competency is still "준비 중". */
    Sheet currentSheet(long competencyId) {
        var rows = jdbc.queryForList("select id from daily_career.competency_sheet_version where competency_id=? and status='PUBLISHED' order by version_no desc limit 1",
                Long.class, competencyId);
        return rows.isEmpty() ? null : sheet(rows.getFirst());
    }

    Sheet sheet(long versionId) {
        var row = jdbc.queryForMap("select version_no,review_label,body_markdown from daily_career.competency_sheet_version where id=?", versionId);
        SheetDocument document = parsed.computeIfAbsent(versionId, id -> SheetDocument.parse(row.get("body_markdown").toString()));
        return new Sheet(versionId, ((Number) row.get("version_no")).intValue(), row.get("review_label").toString(), document);
    }

    List<Long> readyCompetencies() {
        return jdbc.queryForList("""
                select c.id from daily_career.competency c join daily_career.competency_map_version m on m.id=c.map_version_id
                where m.code=? and m.status='PUBLISHED'
                  and exists (select 1 from daily_career.competency_sheet_version s where s.competency_id=c.id and s.status='PUBLISHED')
                order by c.order_no
                """, Long.class, CompetencyCatalog.MAP_CODE);
    }
}
