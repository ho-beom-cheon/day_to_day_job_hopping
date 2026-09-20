package dev.dailycareer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// API errors use the canonical advice/security writers; do not expose Boot's generic /error payload.
@SpringBootApplication(exclude = {ErrorMvcAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
public class DailyCareerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DailyCareerApplication.class, args);
    }
}
