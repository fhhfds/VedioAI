package com.vedioai.server.identity.config;

import com.password4j.Argon2Function;
import com.password4j.types.Argon2;
import com.vedioai.server.identity.security.PasswordPolicy;
import com.vedioai.server.identity.security.PasswordService;
import com.vedioai.server.identity.web.ApiErrorWriter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password4j.Argon2Password4jPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(VedioaiAuthProperties.class)
public class SecurityConfig {

    @Bean
    public CookieSerializer cookieSerializer(VedioaiAuthProperties properties) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(properties.getCookie().getName());
        serializer.setUseHttpOnlyCookie(properties.getCookie().isHttpOnly());
        serializer.setSameSite(properties.getCookie().getSameSite());
        serializer.setCookiePath(properties.getCookie().getPath());
        serializer.setUseSecureCookie(properties.getCookie().isSecure());
        return serializer;
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        repository.setParameterName("_csrf");
        return repository;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("账号登录尚未接入");
        };
    }

    @Bean
    public PasswordPolicy passwordPolicy() {
        return new PasswordPolicy();
    }

    @Bean
    public PasswordService passwordService(VedioaiAuthProperties properties, PasswordPolicy policy) {
        VedioaiAuthProperties.Password.Argon2 argon2 = properties.getPassword().getArgon2();
        Argon2Function function = Argon2Function.getInstance(
                argon2.getMemoryKib(),
                argon2.getIterations(),
                argon2.getParallelism(),
                32,
                Argon2.ID);
        return new PasswordService(new Argon2Password4jPasswordEncoder(function), policy);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CsrfTokenRepository csrfTokenRepository) throws Exception {

        http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/health",
                                "/actuator/health",
                                "/api/auth/policy",
                                "/api/auth/csrf",
                                "/api/auth/registration-challenges",
                                "/api/auth/registrations",
                                "/error")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                ApiErrorWriter.write(
                                        request,
                                        response,
                                        HttpStatus.UNAUTHORIZED.value(),
                                        "UNAUTHORIZED",
                                        "需要登录"))
                        .accessDeniedHandler((request, response, exception) -> {
                            if (exception instanceof CsrfException) {
                                ApiErrorWriter.write(
                                        request,
                                        response,
                                        HttpStatus.FORBIDDEN.value(),
                                        "CSRF_INVALID",
                                        "CSRF token 无效或缺失");
                            } else {
                                ApiErrorWriter.write(
                                        request,
                                        response,
                                        HttpStatus.FORBIDDEN.value(),
                                        "FORBIDDEN",
                                        "无权访问");
                            }
                        }));

        return http.build();
    }
}