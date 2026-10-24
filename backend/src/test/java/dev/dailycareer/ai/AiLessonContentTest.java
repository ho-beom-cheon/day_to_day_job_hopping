package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.learning.ExtraLearningService;
import dev.dailycareer.learning.LearningQueryService;
import dev.dailycareer.user.UserAccountService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiLessonContentTest {
    static final ObjectMapper JSON=new ObjectMapper();
    static final String URL="https://www.postgresql.org/docs/17/transaction-iso.html";
    static ObjectNode request(){return JSON.createObjectNode().put("topic","동시 거래와 격리 수준").put("subject","SQL").put("stage","실무").put("targetMinutes",30);}
    static ObjectNode candidate(){
        String concept="스냅샷의 읽기 시점을 구분하고 동시 변경이 발생한 순서를 비교한다. 문장 단위와 트랜잭션 단위의 관찰 범위가 다르다. ";
        String evidence="같은 요청을 두 번 처리하지 않으려면 요청 식별자와 유일 제약의 적용 범위를 함께 확인한다.";
        var c=JSON.createObjectNode().put("status","READY").put("reason","").put("title","동시 거래의 격리와 재시도")
                .put("objective","동시 처리 조건을 비교하고 오류 시 재시도 범위와 검증 근거를 설명한다.")
                .put("explanation",concept.repeat(8)).put("caseStudy","가상 상황에서 두 트랜잭션이 같은 잔액을 조회했다. ".repeat(4)+"\n```text\nT1 read=100\nT2 read=100\n```\n")
                .put("solution",evidence+concept.repeat(7)).put("exercise","아래 거래 순서가 바뀌는 경우를 비교하고 기대값과 실패 결과를 설명한다. ".repeat(4))
                .put("exerciseAnswer","요청 식별자와 커밋 여부를 확인한 다음 전체 트랜잭션의 재시도 가능 여부를 판단한다. ".repeat(7));
        c.putArray("keyPoints").add(evidence).add("읽기 시점과 잠금 범위를 구분하여 중복 재시도와 커밋 결과를 확인한다.");
        c.putArray("sources").addObject().put("title","PostgreSQL isolation documentation").put("url",URL);
        c.putArray("observedSourceUrls").add(URL);
        var qs=c.putArray("questions");
        for(int i=1;i<=2;i++)qs.addObject().put("prompt",i+"번 상황에서 요청 중복과 커밋 여부를 판단하는 가장 적절한 방법은?")
                .put("answer",2).put("explanation","같은 요청 식별자를 비교하여 중복 처리를 막는다. 단순히 응답 시간이 지났다는 이유만으로 커밋 실패라고 판단해서는 안 된다.")
                .put("evidencePoint",1).putArray("choices").add("응답 시간만 본다").add("요청 식별자와 유일 제약 범위를 확인한다").add("무조건 반복한다").add("항상 성공으로 간주한다");
        return c;
    }
    @Test void publishesStructuredMaterialWithHonestProvenanceAndClickableSources(){
        var result=AiLessonContent.validate(JSON,request(),candidate());
        assertThat(result.path("body").asText()).contains("AI 생성 학습자료","## 실습 해설","## 핵심 판단 근거",URL);
        String evidence=candidate().path("keyPoints").get(0).asText();
        assertThat(result.path("body").asText()).contains(evidence);
        assertThat(result.path("questions").get(0).path("explanation").asText()).contains("본문 근거: "+evidence);
        assertThat(result.path("questions")).hasSize(2);
        assertThat(result.path("digest").asText()).hasSize(64);
    }
    @Test void fabricatedCitationOrHostIsRejected(){
        var c=candidate();c.putArray("observedSourceUrls");
        assertThatThrownBy(()->AiLessonContent.validate(JSON,request(),c)).isInstanceOf(IllegalArgumentException.class);
        for(String url:List.of("https://postgresql.org.evil.test/manual","http://www.postgresql.org/docs","https://user@www.postgresql.org/docs","https://127.0.0.1/docs"))
            assertThatThrownBy(()->AiLessonContent.canonicalUrl(url)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void unsupportedShortLongSessionAndWrongEvidenceDoNotPass(){
        assertThatThrownBy(()->AiLessonContent.validate(JSON,request(),candidate().put("status","UNSUPPORTED"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->AiLessonContent.validate(JSON,request().put("targetMinutes",120),candidate())).isInstanceOf(IllegalArgumentException.class);
        for(int index:List.of(0,-1,3)) {
            var c=candidate();((ObjectNode)c.path("questions").get(0)).put("evidencePoint",index);
            assertThatThrownBy(()->AiLessonContent.validate(JSON,request(),c)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void inputProjectionExcludesIdentityHistoryAndSourceVersionId(){
        var source=request().put("curriculumId","private").put("baseContentVersionId","33").put("promptVersion","x").put("answers","private");
        assertThat(AiLessonContent.providerInput(JSON,source).fieldNames()).toIterable().containsExactlyInAnyOrder("topic","subject","stage","targetMinutes");
        assertThatThrownBy(()->AiLessonCatalog.validateRequest(source)).isInstanceOf(ApiException.class);
    }
    @Test void inaccessibleBaseCannotBeSentToProvider(){
        var jdbc=mock(JdbcTemplate.class);var extra=mock(ExtraLearningService.class);var learning=mock(LearningQueryService.class);
        when(learning.currentId(7)).thenReturn(1L);when(extra.topics(7,ExtraLearningService.Mode.EXPLORE)).thenReturn(List.of());
        var catalog=new AiLessonCatalog(jdbc,JSON,extra,learning,mock(UserAccountService.class));
        assertThatThrownBy(()->catalog.snapshot(7,request().put("baseContentVersionId","123"))).isInstanceOf(ApiException.class);
        verifyNoInteractions(jdbc);
    }
    @Test void onlyProviderAnnotationsAndSearchResultsEstablishObservedUrls() throws Exception {
        var response=JSON.readTree("{\"output\":[{\"type\":\"web_search_call\",\"action\":{\"sources\":[{\"url\":\""+URL+"\"}]}},{\"type\":\"message\",\"content\":[{\"text\":\"https://example.com\",\"annotations\":[]}]}]}");
        assertThat(AiLessonContent.observedSources(response)).containsExactly(URL);
    }
    @Test void completedPageVisitsAreSourcesButFailedOrMerelyRequestedUrlsAreNot() {
        var response=JSON.createObjectNode();var outputs=response.putArray("output");
        outputs.addObject().put("type","web_search_call").put("status","completed").putObject("action").put("type","open_page").put("url",URL);
        outputs.addObject().put("type","web_search_call").put("status","failed").putObject("action").put("type","open_page").put("url",URL+"?failed=true");
        outputs.addObject().put("type","web_search_call").put("status","completed").putObject("action").put("type","search").put("query",URL+"?query=true");
        assertThat(AiLessonContent.observedSources(response)).containsExactly(URL);
    }
}
