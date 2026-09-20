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
    public CatalogOperationsRunner(LearningCatalog catalog,Environment env,ConfigurableApplicationContext context) {
        this.catalog=catalog;this.env=env;this.context=context;
    }
    @Override public void run(ApplicationArguments args) {
        String operation=env.getRequiredProperty("catalog.operation");
        if(!operation.equals("validate") && !operation.equals("publish"))throw new IllegalArgumentException("catalog.operation must be validate or publish");
        long id=Ids.parse(env.getRequiredProperty("catalog.template-id"));
        if(operation.equals("publish"))catalog.publish(id);else catalog.plan(id,false);
        LoggerFactory.getLogger(CatalogOperationsRunner.class).info("CATALOG_{} templateId={}",operation.toUpperCase(java.util.Locale.ROOT),id);
        context.close();
    }
}
