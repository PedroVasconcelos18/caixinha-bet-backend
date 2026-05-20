package com.caxinhabet.arquitetura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Guardrail arquitetural (AR-3/AR-9, Story 1.4): a fronteira do módulo
 * {@code pagamento} é a <b>resposta arquitetural a R-7/C-3</b> (PSP
 * trocável). Se essa fronteira furar, perdemos a única mitigação técnica
 * que podemos dar ao crítico C-3. Por isso este teste roda no build — não
 * é convenção, é regra dura no bytecode.
 *
 * <p>Regras hexagonais:
 * <ol>
 *   <li><b>Domínio não conhece adapter</b> — {@code pagamento.domain..}
 *       não pode depender de {@code pagamento.adapter..}.
 *   <li><b>Asaas só no adapter dedicado</b> — nenhum código fora de
 *       {@code pagamento.adapter.asaas..} pode tocar em tipos do Asaas
 *       (pacote {@code com.asaas..} se viermos a usar um SDK; por ora vale
 *       para qualquer classe interna do próprio módulo {@code asaas}).
 * </ol>
 *
 * <p>Excluímos classes de teste do scan ({@link ImportOption.DoNotIncludeTests})
 * — testes podem mockar/instanciar o que precisarem.
 */
@AnalyzeClasses(
		packages = "com.caxinhabet",
		importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

	@ArchTest
	static final ArchRule dominioDePagamentoNaoConheceAdapter =
			noClasses()
					.that()
					.resideInAPackage("com.caxinhabet.pagamento.domain..")
					.should()
					.dependOnClassesThat()
					.resideInAPackage("com.caxinhabet.pagamento.adapter..")
					.because(
							"AR-3/AR-9: domínio é o lado de dentro do hexágono — adapter (lado de"
									+ " fora) depende do domínio, nunca o contrário. Se essa regra"
									+ " falhar, a trocabilidade do PSP (R-7/C-3) está quebrada.");

	@ArchTest
	static final ArchRule asaasSoNoAdapterDedicado =
			noClasses()
					.that()
					.resideOutsideOfPackage("com.caxinhabet.pagamento.adapter.asaas..")
					.should()
					.dependOnClassesThat()
					.resideInAnyPackage(
							"com.caxinhabet.pagamento.adapter.asaas..",
							"com.asaas..",
							"br.com.asaas..")
					.because(
							"AR-3: somente o adapter Asaas (pagamento.adapter.asaas) pode conhecer"
									+ " o Asaas. Qualquer classe de fora que importe deste pacote"
									+ " quebra a resposta a R-7/C-3 (PSP trocável).");

	@ArchTest
	static final ArchRule dominioDeAuthNaoConheceAdapter =
			noClasses()
					.that()
					.resideInAPackage("com.caxinhabet.auth.domain..")
					.should()
					.dependOnClassesThat()
					.resideInAPackage("com.caxinhabet.auth.adapter..")
					.because(
							"AR-3/AR-9 (Story 2.1): domínio do auth é o lado de dentro do hexágono"
									+ " — adapter (persistence/web/notification/session) depende"
									+ " do domínio, nunca o contrário. Se essa regra falhar, a"
									+ " trocabilidade do MagicLinkSender (LogSender → SmtpSender"
									+ " na Story 2.4) está quebrada.");

	@ArchTest
	static final ArchRule adapterDeAuthSoConsumidoPorAuth =
			noClasses()
					.that()
					.resideOutsideOfPackage("com.caxinhabet.auth..")
					.and()
					.resideOutsideOfPackage("com.caxinhabet.shared.config..")
					.and()
					// Story 2.2: caixinha.app/caixinha.adapter.web consultam
					// UsuarioRepository / UsuarioEntity para resolver o organizador
					// pelo email do principal. Concessão narrow + documentada;
					// alternativa seria criar um port em auth.domain, custo > ganho
					// para o MVP.
					.resideOutsideOfPackage("com.caxinhabet.caixinha..")
					.should()
					.dependOnClassesThat()
					.resideInAPackage("com.caxinhabet.auth.adapter..")
					.because(
							"AR-3 (Story 2.1): só o próprio módulo auth (e o SecurityConfig em"
									+ " shared.config, que faz o wiring do filter, e caixinha que"
									+ " resolve o organizador pelo email) podem tocar classes de"
									+ " auth.adapter.");

	@ArchTest
	static final ArchRule dominioDeCaixinhaNaoConheceAdapter =
			noClasses()
					.that()
					.resideInAPackage("com.caxinhabet.caixinha.domain..")
					.should()
					.dependOnClassesThat()
					.resideInAPackage("com.caxinhabet.caixinha.adapter..")
					.because(
							"AR-3 (Story 2.2): domain do caixinha não conhece JPA/Web. Inverte"
									+ " seria quebrar a hexagonalidade do módulo.");

	@ArchTest
	static final ArchRule adapterDeCaixinhaSoConsumidoPorCaixinha =
			noClasses()
					.that()
					.resideOutsideOfPackage("com.caxinhabet.caixinha..")
					.should()
					.dependOnClassesThat()
					.resideInAPackage("com.caxinhabet.caixinha.adapter..")
					.because(
							"AR-3 (Story 2.2): outros módulos não podem importar entities/repos"
									+ " do caixinha. Se precisarem (ex.: Story 3.x para pagamento),"
									+ " expor port no caixinha.domain.");

	@ArchTest
	static final ArchRule dominioDeParticipanteNaoConheceAdapter =
			noClasses()
					.that()
					.resideInAPackage("com.caxinhabet.participante.domain..")
					.should()
					.dependOnClassesThat()
					.resideInAPackage("com.caxinhabet.participante.adapter..")
					.because(
							"AR-3 (Story 2.2): domain do participante não conhece JPA. Stories"
									+ " 2.4/2.5 expandem o módulo seguindo a mesma regra.");
}
