package dev.dailycareer.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

/** Validate real responses against a source OpenAPI excerpt, independently of Java DTO definitions. */
final class LearningContractAssertions {
    static final JsonNode SCHEMAS;
    static {try(var in=LearningContractAssertions.class.getResourceAsStream("/api/learning-contract.json")) {
        SCHEMAS=new ObjectMapper().readTree(in).get("schemas");
    }catch(Exception e){throw new ExceptionInInitializerError(e);}}
    static void contract(String name,JsonNode value) {check(SCHEMAS.get(name),value,name);}
    static void check(JsonNode schema,JsonNode v,String path) {
        assertThat(v).as(path).isNotNull();
        if(schema.has("$ref")){contract(schema.get("$ref").asText().replace("#/components/schemas/",""),v);return;}
        if(schema.has("anyOf")) {
            if(v.isNull()) {assertThat(schema.get("anyOf").toString()).contains("\"null\"");return;}
            for(var option:schema.get("anyOf"))if(!option.path("type").asText().equals("null")){check(option,v,path);return;}
        }
        switch(schema.path("type").asText()) {
            case "object" -> {
                assertThat(v.isObject()).as(path).isTrue();
                for(var k:schema.path("required"))assertThat(v.has(k.asText())).as(path+"."+k).isTrue();
                var fields=v.fields();while(fields.hasNext()) {var f=fields.next();assertThat(schema.path("properties").has(f.getKey())).as(path+" unexpected "+f.getKey()).isTrue();check(schema.get("properties").get(f.getKey()),f.getValue(),path+"."+f.getKey());}
            }
            case "array" -> {
                assertThat(v.isArray()).as(path).isTrue();
                assertThat(v.size()).isBetween(schema.path("minItems").asInt(),schema.path("maxItems").asInt(Integer.MAX_VALUE));
                Set<JsonNode> unique=new HashSet<>();
                for(var item:v){check(schema.get("items"),item,path+"[]");if(schema.path("uniqueItems").asBoolean())assertThat(unique.add(item)).isTrue();}
            }
            case "string" -> {
                assertThat(v.isTextual()).as(path).isTrue();String s=v.asText();
                assertThat(s.codePointCount(0,s.length())).isBetween(schema.path("minLength").asInt(),schema.path("maxLength").asInt(Integer.MAX_VALUE));
                if(schema.has("pattern"))assertThat(s).matches(schema.get("pattern").asText());
                if(schema.path("format").asText().equals("date"))assertThat(LocalDate.parse(s).toString()).isEqualTo(s);
                if(schema.path("format").asText().equals("date-time"))assertThat(OffsetDateTime.parse(s).getOffset().getTotalSeconds()).isEqualTo(32400);
            }
            case "integer","number" -> {
                assertThat(v.isNumber()).as(path).isTrue();if(schema.path("type").asText().equals("integer"))assertThat(v.isIntegralNumber()).isTrue();
                if(schema.has("minimum"))assertThat(v.asDouble()).isGreaterThanOrEqualTo(schema.get("minimum").asDouble());
                if(schema.has("maximum"))assertThat(v.asDouble()).isLessThanOrEqualTo(schema.get("maximum").asDouble());
            }
            case "boolean" -> assertThat(v.isBoolean()).as(path).isTrue();
            default -> throw new AssertionError("Unsupported contract "+schema);
        }
        if(schema.has("enum")){boolean found=false;for(var e:schema.get("enum"))if(e.equals(v))found=true;assertThat(found).as(path+" enum").isTrue();}
    }
}
