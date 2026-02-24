package com.medical.qc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Redis Session 配置。
 * 保持现有基于 Cookie 的会话行为与路径策略一致，避免前端受影响。
 */
@Configuration
public class RedisSessionConfig {

    /**
     * 配置会话 Cookie 序列化策略，保持使用 JSESSIONID 作为 Cookie 名称。
     *
     * @return CookieSerializer
     */
    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("JSESSIONID");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(false);
        serializer.setCookiePath("/");
        return serializer;
    }
}
