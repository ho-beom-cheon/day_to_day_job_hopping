package dev.dailycareer.coding;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.json.Ids;
import dev.dailycareer.common.json.JsonId;
import dev.dailycareer.user.UserAccountService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coding test track (design §4.7): problems are solved on the external site; the app recommends links, keeps the log
 * and schedules retries. Only names, links, numbers, levels and categories are stored — never problem text.
 */
@Service
public class CodingTrack {
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int RECENT_DAYS = 28;
    private final JdbcTemplate jdbc;
    private final UserAccountService users;

    CodingTrack(JdbcTemplate jdbc, UserAccountService users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    public record Settings(int weeklyTarget) {}
    public record Link(@JsonId Long linkId, String site, String siteProblemRef, String title, String url, Integer level, String category,
                       boolean own, boolean hidden, boolean solvedBefore, int attempts, String lastResult) {}
    public record LinkRequest(String title, String url, Integer level, String category) {}
    public record Log(@JsonId Long logId, @JsonId Long problemLinkId, LocalDate solvedOn, String site, String problemTitle, String problemUrl,
                      String category, String language, String result, Integer minutes, String stuckNote, String note, String source,
                      int retryStep, LocalDate nextRetryAt, int revision) {}
    public record LogRequest(String problemLinkId, String title, String url, String category, String language, String result,
                             Integer minutes, String stuckNote, String note, LocalDate solvedOn) {}
    public record LogPatch(String category, String language, Integer minutes, String stuckNote, String note) {}
    public record Recommendation(String reason, Link link, Log retryOf) {}
    public record CategoryStat(String category, int attempts, int successes) {}
    public record Overview(int weeklyTarget, LocalDate weekStart, int weekDone, List<Recommendation> recommendations,
                           List<Log> dueRetries, List<CategoryStat> categoryStats) {}

    @Transactional(readOnly = true)
    public Settings settings(long user) {
        users.current(user);
        return new Settings(target(user));
    }

    @Transactional
    public Settings updateSettings(long user, Settings request) {
        users.current(user);
        if (request.weeklyTarget() < 0 || request.weeklyTarget() > 7) throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
        jdbc.update("update daily_career.user_setting set weekly_coding_target=? where user_id=?", request.weeklyTarget(), user);
        return new Settings(target(user));
    }

    @Transactional(readOnly = true)
    public Overview overview(long user) {
        users.current(user);
        LocalDate today = LocalDate.now(SEOUL);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int target = target(user);
        Integer done = jdbc.queryForObject("select count(*)::int from daily_career.coding_practice_log where user_id=? and solved_on between ? and ?",
                Integer.class, user, monday, today);
        int weekDone = done == null ? 0 : done;
        List<CategoryStat> stats = jdbc.query("""
                select category,count(*)::int,count(*) filter (where result='SUCCESS')::int from daily_career.coding_practice_log
                where user_id=? and solved_on>? group by category
                """, (row, number) -> new CategoryStat(row.getString(1), row.getInt(2), row.getInt(3)), user, today.minusDays(RECENT_DAYS));
        List<Log> due = jdbc.query("select * from daily_career.coding_practice_log where user_id=? and next_retry_at<=? order by next_retry_at,id",
                this::log, user, today);
        return new Overview(target, monday, weekDone, target == 0 ? List.of() : recommend(user, Math.max(0, target - weekDone), due, stats),
                due, stats.stream().sorted(java.util.Comparator.comparingInt(stat -> CodingRules.CATEGORIES.indexOf(stat.category()))).toList());
    }

    private List<Recommendation> recommend(long user, int slots, List<Log> due, List<CategoryStat> stats) {
        List<Recommendation> result = new ArrayList<>();
        Map<Long, Link> links = visibleLinks(user).stream().collect(Collectors.toMap(Link::linkId, link -> link, (a, b) -> a, java.util.LinkedHashMap::new));
        for (Log log : due) {
            if (result.size() >= slots) return result;
            result.add(new Recommendation("RETRY_DUE", log.problemLinkId() == null ? null : links.get(log.problemLinkId()), log));
        }
        Set<String> everTried = new HashSet<>(jdbc.queryForList("select distinct category from daily_career.coding_practice_log where user_id=?", String.class, user));
        var order = CodingRules.categoryOrder(stats.stream().map(stat -> new CodingRules.Stat(stat.category(), stat.attempts(), stat.successes())).toList(), everTried);
        var unsolved = links.values().stream().filter(link -> !link.hidden() && !link.solvedBefore() && link.attempts() == 0)
                .map(link -> new CodingRules.Candidate(link.linkId(), link.category(), link.level())).toList();
        for (var pick : CodingRules.pick(slots - result.size(), order, unsolved, targets(user, links.values())))
            result.add(new Recommendation(pick.reason(), links.get(pick.linkId()), null));
        return result;
    }

    private Map<String, Integer> targets(long user, java.util.Collection<Link> links) {
        Map<String, Integer> lowest = new HashMap<>();
        for (Link link : links) if (link.level() != null) lowest.merge(link.category(), link.level(), Math::min);
        Map<String, List<String>> recent = new HashMap<>();
        Map<String, Integer> lastSuccess = new HashMap<>();
        jdbc.query("""
                select g.category,g.result,l.level from daily_career.coding_practice_log g
                left join daily_career.coding_problem_link l on l.id=g.problem_link_id where g.user_id=? order by g.solved_on desc,g.id desc
                """, row -> {
            String category = row.getString(1);
            List<String> results = recent.computeIfAbsent(category, key -> new ArrayList<>());
            if (results.size() < 2) results.add(row.getString(2));
            int level = row.getInt(3);
            if (!row.wasNull() && "SUCCESS".equals(row.getString(2))) lastSuccess.putIfAbsent(category, level);
        }, user);
        Map<String, Integer> targets = new HashMap<>();
        for (String category : CodingRules.CATEGORIES)
            targets.put(category, CodingRules.targetLevel(lastSuccess.get(category), recent.getOrDefault(category, List.of()), lowest.getOrDefault(category, 1)));
        return targets;
    }

    @Transactional(readOnly = true)
    public List<Link> links(long user) {
        users.current(user);
        return jdbc.query(LINKS + " order by l.owner_user_id nulls first,l.id", this::link, user, user, user, user, user);
    }

    private List<Link> visibleLinks(long user) {
        return jdbc.query(LINKS + " order by l.id", this::link, user, user, user, user, user);
    }

    private static final String LINKS = """
            select l.*,l.owner_user_id is not null as own,
                exists(select 1 from daily_career.coding_problem_mark m where m.user_id=? and m.link_id=l.id and m.kind='HIDDEN') as hidden,
                exists(select 1 from daily_career.coding_problem_mark m where m.user_id=? and m.link_id=l.id and m.kind='SOLVED_BEFORE') as solved_before,
                (select count(*)::int from daily_career.coding_practice_log g where g.user_id=? and g.problem_key='link:'||l.id) as attempts,
                (select g.result from daily_career.coding_practice_log g where g.user_id=? and g.problem_key='link:'||l.id order by g.solved_on desc,g.id desc limit 1) as last_result
            from daily_career.coding_problem_link l where (l.owner_user_id is null or l.owner_user_id=?)
            """;

    @Transactional
    public Link createLink(long user, LinkRequest request) {
        users.current(user);
        String title = required(request.title(), 200);
        String url = url(request.url());
        if (url == null || !CodingRules.CATEGORIES.contains(request.category()) || request.level() != null && (request.level() < 0 || request.level() > 5))
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
        var existing = jdbc.queryForList("select id from daily_career.coding_problem_link where url=? and (owner_user_id is null or owner_user_id=?)", Long.class, url, user);
        long id = existing.isEmpty() ? jdbc.queryForObject("""
                insert into daily_career.coding_problem_link(owner_user_id,site,title,url,level,category) values (?,?,?,?,?,?) returning id
                """, Long.class, user, site(url), title, url, request.level(), request.category()) : existing.getFirst();
        return link(user, id);
    }

    /**
     * Sets or clears a mark: HIDDEN, or SOLVED_BEFORE for problems solved before using the app. A link keeps one mark, so
     * setting one replaces the other. Marked links leave recommendations; neither mark counts toward the week or success rates.
     */
    @Transactional
    public Link mark(long user, long linkId, String kind, boolean set) {
        link(user, linkId);
        if (set) jdbc.update("""
                insert into daily_career.coding_problem_mark(user_id,link_id,kind) values (?,?,?)
                on conflict (user_id,link_id) do update set kind=excluded.kind,marked_at=now()
                """, user, linkId, kind);
        else jdbc.update("delete from daily_career.coding_problem_mark where user_id=? and link_id=? and kind=?", user, linkId, kind);
        return link(user, linkId);
    }

    @Transactional(readOnly = true)
    public List<Log> logs(long user) {
        users.current(user);
        return jdbc.query("select * from daily_career.coding_practice_log where user_id=? order by solved_on desc,id desc limit 200", this::log, user);
    }

    /** Records an attempt; the newest attempt of a problem carries its retry schedule and earlier ones lose theirs. */
    @Transactional
    public Log createLog(long user, LogRequest request) {
        users.current(user);
        LocalDate today = LocalDate.now(SEOUL);
        LocalDate solvedOn = request.solvedOn() == null ? today : request.solvedOn();
        if (solvedOn.isAfter(today) || solvedOn.isBefore(today.minusYears(1)) || !CodingRules.RESULTS.contains(request.result())
                || request.minutes() != null && (request.minutes() < 0 || request.minutes() > 600))
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
        Link link = null;
        if (request.problemLinkId() != null && !request.problemLinkId().isBlank()) link = link(user, Ids.parse(request.problemLinkId()));
        String url = link != null ? link.url() : url(request.url());
        if (link == null && url != null) {
            var match = jdbc.queryForList("select id from daily_career.coding_problem_link where url=? and (owner_user_id is null or owner_user_id=?)", Long.class, url, user);
            if (!match.isEmpty()) link = link(user, match.getFirst());
        }
        if (link == null && request.url() != null && !request.url().isBlank() && url == null) throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
        String title = link != null ? link.title() : required(request.title(), 200);
        String category = link != null && (request.category() == null || request.category().isBlank()) ? link.category() : request.category();
        if (!CodingRules.CATEGORIES.contains(category)) throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
        String key = CodingRules.problemKey(link == null ? null : link.linkId(), url, title);
        jdbc.queryForList("select id from daily_career.app_user where id=? for update", Long.class, user);
        var previous = jdbc.queryForList("select retry_step::int from daily_career.coding_practice_log where user_id=? and problem_key=? order by solved_on desc,id desc limit 1",
                Integer.class, user, key);
        var schedule = CodingRules.schedule(request.result(), previous.isEmpty() ? 0 : previous.getFirst(), solvedOn);
        jdbc.update("update daily_career.coding_practice_log set retry_step=0,next_retry_at=null,updated_at=clock_timestamp() where user_id=? and problem_key=? and retry_step<>0",
                user, key);
        Long id = jdbc.queryForObject("""
                insert into daily_career.coding_practice_log(user_id,problem_link_id,problem_key,solved_on,site,problem_title,problem_url,category,language,
                    result,minutes,stuck_note,note,retry_step,next_retry_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) returning id
                """, Long.class, user, link == null ? null : link.linkId(), key, solvedOn, link != null ? link.site() : url == null ? "OTHER" : site(url), title, url,
                category, text(request.language(), 32), request.result(), request.minutes(), text(request.stuckNote(), 4000), text(request.note(), 4000),
                schedule.step(), schedule.nextRetryAt());
        return log(user, id);
    }

    @Transactional
    public Log patchLog(long user, long id, LogPatch patch, int expected) {
        users.current(user);
        if (patch.category() != null && !CodingRules.CATEGORIES.contains(patch.category())
                || patch.minutes() != null && (patch.minutes() < 0 || patch.minutes() > 600)) throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
        int changed = jdbc.update("""
                update daily_career.coding_practice_log set category=coalesce(?,category),language=coalesce(?,language),minutes=coalesce(?,minutes),
                    stuck_note=coalesce(?,stuck_note),note=coalesce(?,note),updated_at=clock_timestamp(),revision=revision+1
                where id=? and user_id=? and revision=?
                """, patch.category(), patch.language() == null ? null : text(patch.language(), 32), patch.minutes(),
                patch.stuckNote() == null ? null : text(patch.stuckNote(), 4000), patch.note() == null ? null : text(patch.note(), 4000), id, user, expected);
        if (changed != 1) throw notFoundOrStale(user, id);
        return log(user, id);
    }

    /** "다시 풀기 추가": only the newest attempt of a problem without a planned retry. */
    @Transactional
    public Log addRetry(long user, long id, int expected) {
        Log log = log(user, id);
        var newest = jdbc.queryForList("""
                select id from daily_career.coding_practice_log where user_id=? and problem_key=(select problem_key from daily_career.coding_practice_log where id=?)
                order by solved_on desc,id desc limit 1
                """, Long.class, user, id);
        if (log.retryStep() != 0 || newest.isEmpty() || newest.getFirst() != id) throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
        var schedule = CodingRules.manualRetry(LocalDate.now(SEOUL));
        int changed = jdbc.update("""
                update daily_career.coding_practice_log set retry_step=?,next_retry_at=?,updated_at=clock_timestamp(),revision=revision+1
                where id=? and user_id=? and revision=?
                """, schedule.step(), schedule.nextRetryAt(), id, user, expected);
        if (changed != 1) throw notFoundOrStale(user, id);
        return log(user, id);
    }

    private int target(long user) {
        Integer target = jdbc.queryForObject("select weekly_coding_target::int from daily_career.user_setting where user_id=?", Integer.class, user);
        return target == null ? 2 : target;
    }

    private Link link(long user, long id) {
        var rows = jdbc.query(LINKS + " and l.id=?", this::link, user, user, user, user, user, id);
        if (rows.isEmpty()) throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND);
        return rows.getFirst();
    }

    private Log log(long user, long id) {
        var rows = jdbc.query("select * from daily_career.coding_practice_log where id=? and user_id=?", this::log, id, user);
        if (rows.isEmpty()) throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND);
        return rows.getFirst();
    }

    private Link link(ResultSet row, int number) throws SQLException {
        return new Link(row.getLong("id"), row.getString("site"), row.getString("site_problem_ref"), row.getString("title"), row.getString("url"),
                row.getObject("level", Integer.class), row.getString("category"), row.getBoolean("own"), row.getBoolean("hidden"), row.getBoolean("solved_before"),
                row.getInt("attempts"), row.getString("last_result"));
    }

    private Log log(ResultSet row, int number) throws SQLException {
        long link = row.getLong("problem_link_id");
        boolean noLink = row.wasNull();
        return new Log(row.getLong("id"), noLink ? null : link, row.getObject("solved_on", LocalDate.class), row.getString("site"),
                row.getString("problem_title"), row.getString("problem_url"), row.getString("category"), row.getString("language"),
                row.getString("result"), row.getObject("minutes", Integer.class), row.getString("stuck_note"), row.getString("note"),
                row.getString("source"), row.getInt("retry_step"), row.getObject("next_retry_at", LocalDate.class), row.getInt("revision"));
    }

    private ApiException notFoundOrStale(long user, long id) {
        return new ApiException(Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from daily_career.coding_practice_log where id=? and user_id=?)", Boolean.class, id, user))
                ? ApiErrorCode.PRECONDITION_FAILED : ApiErrorCode.RESOURCE_NOT_FOUND);
    }

    private static String required(String value, int max) {
        if (value == null || value.isBlank() || value.strip().length() > max) throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
        return value.strip();
    }

    private static String text(String value, int max) {
        String text = value == null ? "" : value.strip();
        if (text.length() > max) throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
        return text;
    }

    /** https links only; anything else is rejected rather than stored. */
    private static String url(String value) {
        if (value == null || value.isBlank()) return null;
        String url = value.strip();
        if (url.length() > 500 || !url.matches("https://[^\\s]+")) return null;
        return url;
    }

    private static String site(String url) {
        if (url.contains("programmers.co.kr")) return "PROGRAMMERS";
        if (url.contains("acmicpc.net")) return "BAEKJOON";
        if (url.contains("leetcode.com")) return "LEETCODE";
        return "OTHER";
    }
}
