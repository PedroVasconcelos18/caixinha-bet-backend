package com.caxinhabet.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Encoder de senha do projeto (auth por senha, 2026-05).
 *
 * <p>BCrypt com força padrão (10). Vem do {@code spring-boot-starter-security}
 * já presente no pom — sem dependência externa nova. Os use cases de
 * cadastro/login/redefinição recebem o {@link PasswordEncoder} por injeção;
 * nunca chamam BCrypt diretamente.
 */
@Configuration
class PasswordEncoderConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
