package dev.dailycareer.learning;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Strict operator input. IDs in the manuscript are portable editorial keys, never DB numbers. */
public final class CatalogManuscript {
    private CatalogManuscript() {}
    public record Bundle(int schemaVersion,String code,String version,String title,String description,
                         int durationMonths,List<Week> weeks,List<Day> days,List<Assessment> assessments) {}
    public record Week(int number,String title,List<String> goals) {}
    public record Source(String title,String url) {}
    public record Question(String id,String title,String prompt,List<String> choices,int answer,
                           String explanation,int difficulty,String use) {}
    public record Day(int number,int week,String title,String objective,String body,String labTask,
                      List<String> acceptance,List<String> keyPoints,List<Source> sources,
                      List<Question> questions,int minutes) {}
    public record Assessment(String id,String type,int day,String title,int seconds,List<String> questions) {}
    public static Bundle parse(ObjectMapper mapper,String source) {
        require(source!=null && source.length()<=4_000_000,"manuscript size");
        try {
            var strict=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            Bundle bundle=strict.readValue(source,Bundle.class);
            validate(bundle); return bundle;
        } catch (IllegalArgumentException e) {throw e;}
        catch (Exception e) {throw new IllegalArgumentException("Invalid manuscript JSON",e);}
    }
    public static void validate(Bundle b) {
        require(b!=null && b.schemaVersion==1,"schemaVersion must be 1");
        require(b.code!=null && b.code.matches("[A-Z][A-Z0-9-]{0,59}"),"course code");
        require(b.version!=null && b.version.matches("[a-zA-Z0-9][a-zA-Z0-9.-]{0,39}"),"version");
        text(b.title,120,"title");text(b.description,1000,"description");
        // Schema 1 is deliberately a complete four-week course, not an arbitrary SQL loader.
        require(b.durationMonths==1,"four-week manuscript duration must be 1");
        size(b.weeks,4,4,"weeks");size(b.days,20,20,"days");size(b.assessments,26,26,"assessments");
        for(int i=0;i<4;i++) {var w=b.weeks.get(i);require(w.number==i+1,"week order");text(w.title,120,"week title");texts(w.goals,1,10,300,"goals");}
        Map<String,Integer> questionDays=new HashMap<>();Set<String> prompts=new HashSet<>();
        for(int i=0;i<20;i++) {
            var d=b.days.get(i);require(d.number==i+1 && d.week==i/5+1,"day order/week");
            text(d.title,120,"day title");text(d.objective,1000,"objective");markdown(d.body,80000,"body");markdown(d.labTask,4000,"lab task");
            require(d.body.contains("```java"),"executable Java example required");
            texts(d.acceptance,2,10,500,"acceptance");texts(d.keyPoints,2,10,1000,"key points");
            size(d.sources,1,10,"sources");
            for(var s:d.sources) {text(s.title,160,"source title");var uri=URI.create(s.url);require("https".equals(uri.getScheme()) && uri.getHost()!=null && uri.getUserInfo()==null,"HTTPS source URL");}
            require(d.minutes>=30 && d.minutes<=110,"lesson minutes");
            size(d.questions,12,30,"questions");int practice=0,assessment=0;
            for(var q:d.questions) {
                require(q.id!=null && q.id.matches("[a-z][a-z0-9-]{0,79}") && questionDays.put(q.id,d.number)==null,"unique external question ID");
                text(q.title,160,"question title");markdown(q.prompt,4000,"question prompt");
                require(prompts.add(q.prompt.strip()),"duplicate question prompt");
                texts(q.choices,3,6,1000,"choices");require(new HashSet<>(q.choices).size()==q.choices.size(),"duplicate choice");
                require(q.answer>=1 && q.answer<=q.choices.size(),"answer must name an existing choice");
                markdown(q.explanation,4000,"explanation");require(q.difficulty>=1 && q.difficulty<=5,"difficulty");
                require(Set.of("PRACTICE","ASSESSMENT").contains(q.use),"question use");
                if(q.use.equals("PRACTICE"))practice++;else assessment++;
            }
            require(practice==10 && assessment>=2,"10 practice and at least 2 assessment questions per day");
        }
        Set<String> assessmentIds=new HashSet<>();Set<Integer> dailyDays=new HashSet<>(),weeklyDays=new HashSet<>();
        int monthly=0,finals=0;int[] minutes=new int[20];Set<String> usedExamQuestions=new HashSet<>();
        Map<String,Question> bank=new HashMap<>();b.days.forEach(d->d.questions.forEach(q->bank.put(q.id,q)));
        for(var a:b.assessments) {
            require(a.id!=null && a.id.matches("[a-z][a-z0-9-]{0,79}") && assessmentIds.add(a.id),"unique assessment ID");
            text(a.title,120,"assessment title");require(a.day>=1 && a.day<=20,"assessment day");
            require(a.seconds>=60 && a.seconds<=3600 && a.seconds%60==0,"whole-minute assessment limit");
            size(a.questions,1,50,"assessment questions");require(new HashSet<>(a.questions).size()==a.questions.size(),"duplicate assessment question");
            int minimumDay=switch(a.type) {
                case "DAILY" -> {require(dailyDays.add(a.day),"duplicate daily assessment");yield a.day;}
                case "WEEKLY" -> {require(a.day%5==0 && weeklyDays.add(a.day),"weekly placement");yield a.day-4;}
                case "MONTHLY" -> {require(a.day==20 && ++monthly==1,"monthly placement");yield 1;}
                case "FINAL" -> {require(a.day==20 && ++finals==1,"final placement");yield 1;}
                default -> throw new IllegalArgumentException("assessment type");
            };
            for(String q:a.questions) {Integer day=questionDays.get(q);require(day!=null && day>=minimumDay && day<=a.day && "ASSESSMENT".equals(bank.get(q).use),"assessment reference/scope");usedExamQuestions.add(q);}
            minutes[a.day-1]+=a.seconds/60;
        }
        require(dailyDays.size()==20 && weeklyDays.size()==4 && monthly==1 && finals==1,"assessment coverage");
        for(var d:b.days) {require(d.minutes+minutes[d.number-1]>=90 && d.minutes+minutes[d.number-1]<=120,"daily 90-120 minute budget");
            d.questions.stream().filter(q->q.use.equals("ASSESSMENT")).forEach(q->require(usedExamQuestions.contains(q.id),"unused assessment question"));}
    }
    public static String body(Day d) {
        var s=new StringBuilder(d.body).append("\n\n## 직접 해보기\n\n").append(d.labTask).append("\n\n### 완료 기준\n\n");
        d.acceptance.forEach(a->s.append("- ").append(a).append('\n'));
        s.append("\n## 공식 참고 자료\n\n");d.sources.forEach(a->s.append("- [").append(a.title).append("](").append(a.url).append(")\n"));
        return s.toString();
    }
    public static String digest(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e) {throw new IllegalStateException(e);}
    }
    public static JsonNode canonical(ObjectMapper mapper,JsonNode node) {
        if(node.isObject()) {var out=mapper.createObjectNode();var names=new TreeSet<String>();node.fieldNames().forEachRemaining(names::add);names.forEach(n->out.set(n,canonical(mapper,node.get(n))));return out;}
        if(node.isArray()) {var out=mapper.createArrayNode();node.forEach(n->out.add(canonical(mapper,n)));return out;}
        return node;
    }
    static void require(boolean condition,String message) {if(!condition)throw new IllegalArgumentException(message);}
    private static void text(String s,int max,String field) {require(s!=null && !s.isBlank() && s.codePointCount(0,s.length())<=max,field);}
    private static void markdown(String s,int max,String field) {text(s,max,field);require(!s.matches("(?is).*(?:javascript|vbscript|data)\\s*:.*"),"unsafe markdown");}
    private static void size(List<?> s,int min,int max,String field) {require(s!=null && s.size()>=min && s.size()<=max && s.stream().noneMatch(Objects::isNull),field);}
    private static void texts(List<String> s,int min,int max,int length,String field) {size(s,min,max,field);s.forEach(v->text(v,length,field));}
}
