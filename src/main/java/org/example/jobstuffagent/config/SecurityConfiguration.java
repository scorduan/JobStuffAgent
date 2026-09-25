package org.example.jobstuffagent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * A small local security baseline. An OAuth/OIDC resource-server configuration can replace
 * the local user source later without changing the application's authorization vocabulary.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(LocalSecurityProperties.class)
public class SecurityConfiguration {

    public static final String APPLICATIONS_READ_ONLY = "APPLICATIONS_READ_ONLY";
    public static final String APPLICATIONS_MANAGER = "APPLICATIONS_MANAGER";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/applications/**")
                        .hasAnyRole(APPLICATIONS_READ_ONLY, APPLICATIONS_MANAGER)
                        .requestMatchers(HttpMethod.POST, "/applications/**").hasRole(APPLICATIONS_MANAGER)
                        .requestMatchers(HttpMethod.PUT, "/applications/**").hasRole(APPLICATIONS_MANAGER)
                        .requestMatchers(HttpMethod.DELETE, "/applications/**").hasRole(APPLICATIONS_MANAGER)
                        .requestMatchers(HttpMethod.GET, "/workflows/**")
                        .hasAnyRole(APPLICATIONS_READ_ONLY, APPLICATIONS_MANAGER)
                        .requestMatchers(HttpMethod.POST, "/workflows/**")
                        .hasAnyRole(APPLICATIONS_READ_ONLY, APPLICATIONS_MANAGER)
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    UserDetailsService userDetailsService(LocalSecurityProperties properties, PasswordEncoder passwordEncoder) {
        UserDetails readOnlyUser = User.withUsername(properties.readOnlyUsername())
                .password(passwordEncoder.encode(properties.readOnlyPassword()))
                .roles(APPLICATIONS_READ_ONLY)
                .build();
        UserDetails managerUser = User.withUsername(properties.managerUsername())
                .password(passwordEncoder.encode(properties.managerPassword()))
                .roles(APPLICATIONS_MANAGER)
                .build();
        return new InMemoryUserDetailsManager(readOnlyUser, managerUser);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
