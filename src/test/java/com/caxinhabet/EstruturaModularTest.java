package com.caxinhabet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Guardrail de arquitetura (AR-9): a estrutura física de monolito modular
 * package-by-feature precisa existir e estar versionada por
 * {@code package-info.java} (Java não versiona diretório vazio).
 *
 * <p>O teste lê a árvore de fontes ({@code src/main/java}) em vez de usar
 * reflexão, porque pacotes sem classes não são carregados pela JVM — só o
 * arquivo {@code package-info.java} garante que o pacote existe no VCS.
 */
class EstruturaModularTest {

	private static final Path BASE = Path.of("src/main/java/com/caxinhabet");

	private static final List<String> MODULOS_FEATURE =
			List.of("caixinha", "participante", "pagamento", "apuracao", "ledger", "auth");

	private static final List<String> SUBPACOTES_MODULO = List.of("domain", "app", "adapter");

	private static final List<String> SUBPACOTES_SHARED = List.of("money", "error", "config");

	static Stream<String> pacotesEsperados() {
		Stream<String> sharedRaiz = Stream.of("shared");
		Stream<String> sharedSub = SUBPACOTES_SHARED.stream().map(s -> "shared/" + s);
		Stream<String> modulosRaiz = MODULOS_FEATURE.stream();
		Stream<String> modulosSub =
				MODULOS_FEATURE.stream()
						.flatMap(m -> SUBPACOTES_MODULO.stream().map(s -> m + "/" + s));
		return Stream.of(sharedRaiz, sharedSub, modulosRaiz, modulosSub).flatMap(s -> s);
	}

	@ParameterizedTest(name = "pacote com.caxinhabet.{0} existe e tem package-info.java")
	@MethodSource("pacotesEsperados")
	@DisplayName("Todo módulo/subpacote da estrutura modular existe e está versionado")
	void pacoteExisteEVersionado(String pacoteRelativo) throws IOException {
		Path dir = BASE.resolve(pacoteRelativo.replace('/', java.io.File.separatorChar));
		assertThat(Files.isDirectory(dir))
				.as("diretório do pacote %s deve existir", pacoteRelativo)
				.isTrue();
		assertThat(Files.exists(dir.resolve("package-info.java")))
				.as("pacote %s deve ter package-info.java (versiona o pacote no VCS)", pacoteRelativo)
				.isTrue();
	}
}
