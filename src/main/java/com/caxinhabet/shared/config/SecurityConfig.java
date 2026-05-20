package com.caxinhabet.shared.config;

import com.caxinhabet.auth.adapter.session.SessaoStore;
import com.caxinhabet.auth.adapter.web.SessaoCookieAuthenticationFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security do projeto (Story 1.4 → endurecido pela Story 2.1).
 *
 * <p><b>Estado atual (Story 2.1):</b> autenticação por sessão de cookie.
 * O filtro {@link SessaoCookieAuthenticationFilter} lê o cookie
 * {@code caixinhabet_sessao}, busca no {@link SessaoStore} e popula o
 * {@code SecurityContext}.
 *
 * <p><b>Whitelist (rotas públicas):</b>
 * <ul>
 *   <li>{@code POST /auth/solicitar-acesso} — entrada do fluxo, ninguém
 *       está autenticado ainda
 *   <li>{@code GET  /auth/callback} — consome o token e abre sessão
 *   <li>{@code POST /webhooks/asaas} — autentica-se por
 *       {@code asaas-access-token} (Story 1.4); cookie de sessão não se
 *       aplica
 *   <li>{@code GET  /actuator/health} — health public para
 *       infra/observabilidade
 *   <li>{@code POST /auth/sair} — limpar cookie sem exigir autenticação
 *       (cliente já pode estar com cookie inválido; o handler é tolerante)
 * </ul>
 *
 * <p>Demais rotas exigem {@code authenticated()}.
 *
 * <p><b>CSRF desligado</b> porque o projeto é stateless do ponto de vista
 * do Spring Security (não há servlet session). A defesa CSRF prática vem
 * de cookie {@code SameSite=Lax} + nenhum endpoint mutante aceitando
 * GET — alinhado com OWASP "Session Management Cheat Sheet".
 *
 * <p><b>CORS</b> habilitado para o front (Next em http://localhost:3000
 * em dev). {@code allowCredentials=true} é essencial — sem ele, o
 * navegador não enviaria o cookie de sessão. Em produção, a origem é
 * substituída pelo domínio público; nunca usar {@code "*"} com
 * credenciais (proibido pela spec do CORS, e o Spring Security falharia
 * silenciosamente).
 */
@Configuration
class SecurityConfig {

	@Bean
	SecurityFilterChain caixinhaBetSecurity(HttpSecurity http, SessaoStore sessaoStore)
			throws Exception {
		SessaoCookieAuthenticationFilter sessaoFilter =
				new SessaoCookieAuthenticationFilter(sessaoStore);

		http.cors(cors -> cors.configurationSource(corsConfig()))
				.csrf(csrf -> csrf.disable())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(
						// API sem servlet session — sem credencial = 401 (não 403, que é
						// o default do Spring Security para anônimo em rota authenticated).
						e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.authorizeHttpRequests(
						a ->
								a.requestMatchers("/auth/solicitar-acesso", "/auth/callback", "/auth/sair")
										.permitAll()
										.requestMatchers("/webhooks/asaas")
										.permitAll()
										.requestMatchers("/actuator/health", "/actuator/health/**")
										.permitAll()
										.anyRequest()
										.authenticated())
				.addFilterBefore(sessaoFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	private static UrlBasedCorsConfigurationSource corsConfig() {
		CorsConfiguration cors = new CorsConfiguration();
		// Em dev: front em http://localhost:3000. Para produção, ler de
		// app.public-base-url (Story 2.1) — TODO endurecer quando subir.
		cors.setAllowedOrigins(List.of("http://localhost:3000"));
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Content-Type", "Accept"));
		cors.setAllowCredentials(true);
		cors.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", cors);
		return source;
	}
}
