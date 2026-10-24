package dev.dailycareer.common.code;

import dev.dailycareer.user.UserAccountService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static dev.dailycareer.common.code.CommonCodeViews.*;

@Service
public class CommonCodeService {
    private static final Map<String,String> LABELS=labels();
    private final JdbcTemplate jdbc; private final UserAccountService users;
    public CommonCodeService(JdbcTemplate jdbc,UserAccountService users){this.jdbc=jdbc;this.users=users;}

    @Transactional(readOnly=true)
    public CommonCodes codes(long user){users.current(user);Map<String,Boolean> active=new java.util.HashMap<>();jdbc.queryForList("select code,enabled from daily_career.subject").forEach(row->active.put((String)row.get("code"),Boolean.TRUE.equals(row.get("enabled"))));int[] order={0};List<CodeItem> categories=LABELS.entrySet().stream().map(entry->new CodeItem(entry.getKey(),entry.getValue(),++order[0],active.getOrDefault(entry.getKey(),false))).toList();return new CommonCodes(categories,"Asia/Seoul");}

    private static Map<String,String> labels(){Map<String,String> values=new LinkedHashMap<>();values.put("JAVA","Java");values.put("SPRING","Spring");values.put("DATABASE","데이터베이스");values.put("SQL","SQL");values.put("ORACLE","Oracle");values.put("ALGORITHM","알고리즘");values.put("SYSTEM_DESIGN","시스템 설계");values.put("NETWORK","네트워크");values.put("SECURITY","보안");values.put("AUTH","인증");values.put("LEGACY","레거시");values.put("AI","AI 활용");values.put("INTERVIEW","면접");values.put("CAREER","경력 관리");return java.util.Collections.unmodifiableMap(values);}
}
