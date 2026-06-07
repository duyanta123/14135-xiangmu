package com.labcourse.config;

import com.labcourse.filter.JwtFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 登录接口允许匿名访问
                        .requestMatchers("/api/student/login", "/api/teacher/login", "/api/admin/login").permitAll()
                        .requestMatchers("/api/course/list", "/api/course/list/simple").permitAll()
                        // Token刷新接口需要认证（但不需要重新登录）
                        .requestMatchers("/api/auth/refresh", "/api/auth/validate").authenticated()
                        // 管理员可管理学生（增删改查）
                        .requestMatchers("/api/student/add", "/api/student/update", "/api/student/delete/**", "/api/student/list").hasRole("admin")
                        // 管理员可管理教师（增删改查）
                        .requestMatchers("/api/teacher/add", "/api/teacher/update", "/api/teacher/delete/**", "/api/teacher/list").hasRole("admin")
                        // 学生角色的接口
                        .requestMatchers("/api/selection/add", "/api/selection/my", "/api/selection/delete/**").hasRole("student")
                        // 学生可访问的考勤接口（签到、历史记录、服务器时间）
                        .requestMatchers("/api/attendance/check-in", "/api/attendance/history", "/api/attendance/server-time").hasRole("student")
                        // 教师角色的接口
                        .requestMatchers("/api/selection/studentList/**", "/api/score/**").hasRole("teacher")
                        // 教师可访问的考勤管理接口
                        .requestMatchers("/api/attendance/course", "/api/attendance/dates", "/api/attendance/update-status", "/api/attendance/export").hasRole("teacher")
                        // 管理员角色的接口
                        .requestMatchers("/api/admin/**").hasRole("admin")
                        .requestMatchers("/api/course/add", "/api/course/update", "/api/course/delete/**").hasRole("admin")
                        .requestMatchers("/api/lab/**").hasRole("admin")
                        // 其他需要认证
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
