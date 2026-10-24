package dev.dailycareer.learning;

import dev.dailycareer.common.json.Ids;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Explicit one-shot operator process. This profile is never enabled on the serving application. */
@Component
@Profile("catalog-ops")
public class CatalogOperationsRunner implements ApplicationRunner {
    private final LearningCatalog catalog;
    private final Environment env;
    private final ConfigurableApplicationContext context;
    private final CatalogImportService imports;
    public CatalogOperationsRunner(LearningCatalog catalog,CatalogImportService imports,Environment env,ConfigurableApplicationContext context) {
        this.catalog=catalog;this.imports=imports;this.env=env;this.context=context;
    }
    @Override public void run(ApplicationArguments args) throws Exception {
        String operation=env.getRequiredProperty("catalog.operation");
        Object result;
        if(operation.equals("import")) {
            var file=java.nio.file.Path.of(env.getRequiredProperty("catalog.file"));
            if(java.nio.file.Files.size(file)>4_000_000)throw new IllegalArgumentException("Manuscript exceeds 4 MB");
            result=imports.importDraft(java.nio.file.Files.readString(file));
        } else {
            long id=Ids.parse(env.getRequiredProperty("catalog.template-id"));
            switch(operation) {
                case "validate-import" -> result=imports.validateImported(id,env.getRequiredProperty("catalog.expected-digest"));
                case "publish-import" -> result=imports.publishReviewed(id,env.getRequiredProperty("catalog.expected-digest"),env.getRequiredProperty("catalog.review-note"));
                case "validate" -> {catalog.plan(id,false);result=Long.toString(id);}
                case "publish" -> {catalog.publish(id);result=Long.toString(id);}
                default -> throw new IllegalArgumentException("catalog.operation must be import, validate-import, publish-import, validate or publish");
            }
        }
        LoggerFactory.getLogger(CatalogOperationsRunner.class).info("CATALOG_{} {}",operation.toUpperCase(java.util.Locale.ROOT),result);
        context.close();
    }
}
