package dev.dailycareer.learning;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LearningConfiguration {
    @Bean Clock learningClock() { return Clock.system(ZoneId.of("Asia/Seoul")); }
}
