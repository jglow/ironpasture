package com.broadcom.demo.ironpasture.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // HTTP Basic for development convenience.
    // For production, swap to OAuth2/OIDC (e.g., spring-boot-starter-oauth2-resource-server)
    // and remove the in-memory UserDetailsService.

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless API — no CSRF needed
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico").permitAll()
                        .requestMatchers("/", "/inspector").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/compliance/pre-fill").hasRole("PLANT_MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/compliance/report/**").hasAnyRole("PLANT_MANAGER", "INSPECTOR")
                        .requestMatchers(HttpMethod.GET, "/api/compliance/history/**").hasRole("INSPECTOR")
                        .requestMatchers(HttpMethod.POST, "/api/admin/**").hasRole("INSPECTOR")
                        .requestMatchers(HttpMethod.GET, "/api/info").permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        var manager = User.withUsername("manager")
                .password(encoder.encode("manager"))
                .roles("PLANT_MANAGER")
                .build();

        var inspector = User.withUsername("inspector")
                .password(encoder.encode("inspector"))
                .roles("INSPECTOR")
                .build();

        return new InMemoryUserDetailsManager(manager, inspector);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
