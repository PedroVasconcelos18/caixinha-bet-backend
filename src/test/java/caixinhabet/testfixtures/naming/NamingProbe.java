package caixinhabet.testfixtures.naming;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade de teste — existe SÓ para o {@code NamingStrategyIT} validar
 * que {@code CamelCaseToUnderscoresNamingStrategy} (Story 1.1) traduz
 * {@code nomeUsuarioCompleto} → {@code nome_usuario_completo} no Postgres.
 *
 * <p>Vive em <b>pacote fora de {@code com.caxinhabet.*}</b> de propósito:
 * o {@code @SpringBootApplication} faz component-scan a partir da raiz
 * {@code com.caxinhabet} e auto-descobriria esta {@code @Entity} (forçando
 * todos os outros testes JPA a esperar uma tabela {@code naming_probe}).
 * Em {@code caixinhabet.testfixtures.naming} ela só entra no contexto via
 * {@code @EntityScan} explícito do {@link NamingStrategyIT}.
 */
@Entity
@Table(name = "naming_probe")
public class NamingProbe {

	@Id Long id;
	String nomeUsuarioCompleto;
}
