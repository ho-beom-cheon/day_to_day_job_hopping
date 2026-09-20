package dev.dailycareer.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration(proxyBeanMethods = false)
public class SessionCookieConfiguration {
    @Bean CookieSerializer cookieSerializer(@Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
        var cookie = new DefaultCookieSerializer();
        cookie.setCookieName(secure ? "__Host-CAREER_SESSION" : "CAREER_SESSION");
        cookie.setUseSecureCookie(secure);
        cookie.setUseHttpOnlyCookie(true);
        cookie.setSameSite("Lax");
        cookie.setCookiePath("/");
        return cookie;
    }
}
