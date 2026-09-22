package com.grupomariposa.orders.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupomariposa.orders.infrastructure.observability.TraceContext;
import com.grupomariposa.orders.infrastructure.web.OrderRequestParser;
import com.grupomariposa.orders.infrastructure.web.OrderResponseMapper;
import com.grupomariposa.orders.infrastructure.web.OrdersApiProperties;
import com.grupomariposa.orders.infrastructure.web.OrdersController;
import com.grupomariposa.orders.infrastructure.web.ProblemProperties;
import com.grupomariposa.orders.infrastructure.web.ProblemFactory;
import com.grupomariposa.orders.infrastructure.web.security.ProblemSecurityHandler;
import com.grupomariposa.orders.infrastructure.web.security.RealmRoleConverter;
import com.grupomariposa.orders.infrastructure.web.security.SecurityModeGuard;
import com.grupomariposa.orders.infrastructure.web.security.WebSecurityProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
public class WebConfiguration {

    private static final String[] API_DOC_PATHS = {
        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"
    };
    private static final String ACTUATOR_PATHS = "/actuator/**";
    private static final String ORDERS_PATHS = OrdersController.BASE_PATH + "/**";
    private static final String ALL_PATHS = "/**";
    private static final String BEARER = "bearer";
    private static final String JWT = "JWT";
    private static final String API_TITLE = "Orders query API (order-processor)";
    private static final String API_VERSION = "1.0.0";

    @Bean
    public OrderRequestParser orderRequestParser(final OrdersApiProperties limits) {
        return new OrderRequestParser(limits);
    }

    @Bean
    public OrderResponseMapper orderResponseMapper() {
        return new OrderResponseMapper();
    }

    @Bean
    public ProblemFactory problemFactory(final Clock clock, final TraceContext traceContext,
                                         final ProblemProperties problems) {
        return new ProblemFactory(clock, traceContext, problems.typeBase());
    }

    @Bean
    public ProblemSecurityHandler problemSecurityHandler(final ProblemFactory problems,
                                                         final ObjectMapper objectMapper) {
        return new ProblemSecurityHandler(problems, objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http,
                                                   final WebSecurityProperties properties,
                                                   final ProblemSecurityHandler problems,
                                                   final Environment environment)
            throws Exception {
        SecurityModeGuard.requireLocalWhenDisabled(properties.enabled(), environment);
        statelessApi(http, problems);
        if (!properties.enabled()) {
            return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }
        return http.authorizeHttpRequests(auth -> authorize(auth, properties))
                .oauth2ResourceServer(server -> server
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new RealmRoleConverter()))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .build();
    }

    private static void statelessApi(final HttpSecurity http,
                                     final ProblemSecurityHandler problems) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems));
    }

    private static void authorize(
            final AuthorizeHttpRequestsConfigurer<HttpSecurity>
                    .AuthorizationManagerRequestMatcherRegistry auth,
            final WebSecurityProperties properties) {
        auth.requestMatchers(properties.publicPaths().toArray(String[]::new)).permitAll()
                .requestMatchers(API_DOC_PATHS).access((authentication, context) ->
                        new AuthorizationDecision(properties.apiDocsEnabled()))
                .requestMatchers(HttpMethod.OPTIONS, ALL_PATHS).permitAll()
                .requestMatchers(ACTUATOR_PATHS).hasRole(properties.adminRole())
                .requestMatchers(HttpMethod.GET, OrdersController.BASE_PATH, ORDERS_PATHS)
                .hasAnyRole(properties.readerRole(), properties.adminRole())
                .anyRequest().authenticated();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            final WebSecurityProperties properties) {
        final CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(properties.corsAllowedMethods());
        cors.setAllowedHeaders(properties.corsAllowedHeaders());
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(ALL_PATHS, cors);
        return source;
    }

    @Bean
    public OpenAPI ordersOpenApi() {
        return new OpenAPI()
                .info(new Info().title(API_TITLE).version(API_VERSION))
                .components(new Components().addSecuritySchemes(OrdersController.SECURITY_SCHEME,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme(BEARER)
                                .bearerFormat(JWT)));
    }
}
