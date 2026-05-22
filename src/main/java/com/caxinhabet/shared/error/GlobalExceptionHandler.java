package com.caxinhabet.shared.error;

import com.caxinhabet.auth.domain.AcessoExpiradoException;
import com.caxinhabet.auth.domain.AcessoJaConsumidoException;
import com.caxinhabet.auth.domain.ChavePixObrigatoriaException;
import com.caxinhabet.auth.domain.TokenInvalidoException;
import com.caxinhabet.caixinha.domain.ApuracaoInvalidaException;
import com.caxinhabet.caixinha.domain.CriacaoCaixinhaInvalidaException;
import com.caxinhabet.caixinha.domain.OperacaoNaoAutorizadaException;
import com.caxinhabet.caixinha.domain.PagamentoIndisponivelException;
import com.caxinhabet.caixinha.domain.PrazoEncerradoException;
import com.caxinhabet.participante.domain.PalpiteInvalidoException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Handler global de exceções — contrato de erro RFC 9457 (Story 1.3 AC-3).
 *
 * <p>Combina dois mecanismos:
 * <ul>
 *   <li><b>Spring MVC built-in</b> ({@code spring.mvc.problemdetails.enabled=true}
 *       + extensão de {@link ResponseEntityExceptionHandler}): exceções
 *       padrão do MVC (404, 415, validação, etc.) já retornam
 *       {@link ProblemDetail}.
 *   <li><b>Catch-all 500</b>: qualquer {@link Exception} não tratada vira
 *       {@code Internal Server Error} com {@link ProblemDetail} — <b>nunca</b>
 *       expomos stack trace ao cliente. O log mantém o stack server-side
 *       para diagnóstico.
 * </ul>
 *
 * <p>Anti-padrão proibido: introduzir um wrapper de resposta tipo
 * {@code ApiResponse<T>} para sucesso. Sucesso = corpo direto (AR-8).
 * Erro = RFC 9457; SEMPRE.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static final URI TYPE_INTERNAL =
			URI.create("https://caixinha.bet/problems/erro-interno");

	private static final URI TYPE_TOKEN_INVALIDO =
			URI.create("https://caixinha.bet/problems/token-invalido");

	private static final URI TYPE_ACESSO_EXPIRADO =
			URI.create("https://caixinha.bet/problems/acesso-expirado");

	private static final URI TYPE_ACESSO_JA_CONSUMIDO =
			URI.create("https://caixinha.bet/problems/acesso-ja-consumido");

	private static final URI TYPE_CAIXINHA_INVALIDA =
			URI.create("https://caixinha.bet/problems/caixinha-invalida");

	private static final URI TYPE_OPERACAO_NAO_AUTORIZADA =
			URI.create("https://caixinha.bet/problems/operacao-nao-autorizada");

	private static final URI TYPE_PRAZO_ENCERRADO =
			URI.create("https://caixinha.bet/problems/prazo-encerrado");

	private static final URI TYPE_PALPITE_INVALIDO =
			URI.create("https://caixinha.bet/problems/palpite-invalido");

	private static final URI TYPE_CHAVE_PIX_OBRIGATORIA =
			URI.create("https://caixinha.bet/problems/chave-pix-obrigatoria");

	private static final URI TYPE_PAGAMENTO_INDISPONIVEL =
			URI.create("https://caixinha.bet/problems/pagamento-indisponivel");

	private static final URI TYPE_APURACAO_INVALIDA =
			URI.create("https://caixinha.bet/problems/apuracao-invalida");

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUncaught(Exception ex) {
		// Log com stack server-side; resposta sem stack (sem vazar interno).
		log.error("Exceção não tratada propagou até o handler global", ex);
		ProblemDetail problem =
				ProblemDetail.forStatusAndDetail(
						HttpStatus.INTERNAL_SERVER_ERROR,
						"Erro inesperado ao processar a requisição.");
		problem.setType(TYPE_INTERNAL);
		problem.setTitle("Erro interno");
		return problem;
	}

	@ExceptionHandler(TokenInvalidoException.class)
	public ProblemDetail handleTokenInvalido(TokenInvalidoException ex) {
		ProblemDetail problem =
				ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setType(TYPE_TOKEN_INVALIDO);
		problem.setTitle("Token de acesso inválido");
		return problem;
	}

	@ExceptionHandler(AcessoExpiradoException.class)
	public ProblemDetail handleAcessoExpirado(AcessoExpiradoException ex) {
		ProblemDetail problem =
				ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
		problem.setType(TYPE_ACESSO_EXPIRADO);
		problem.setTitle("Link expirado");
		return problem;
	}

	@ExceptionHandler(AcessoJaConsumidoException.class)
	public ProblemDetail handleAcessoJaConsumido(AcessoJaConsumidoException ex) {
		ProblemDetail problem =
				ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
		problem.setType(TYPE_ACESSO_JA_CONSUMIDO);
		problem.setTitle("Link já utilizado");
		return problem;
	}

	/**
	 * Caixinha inválida (Story 2.2): violações semânticas agregadas (cross-
	 * field, regras de negócio). 422 + extensão {@code violations} (RFC 9457
	 * §3.2.1). Distingue de 400 (Bean Validation, sintaxe/campo individual).
	 */
	@ExceptionHandler(CriacaoCaixinhaInvalidaException.class)
	public ProblemDetail handleCaixinhaInvalida(CriacaoCaixinhaInvalidaException ex) {
		ProblemDetail problem =
				ProblemDetail.forStatusAndDetail(
						HttpStatus.UNPROCESSABLE_ENTITY,
						"Caixinha inválida: corrija os itens em 'violations' e tente de novo.");
		problem.setType(TYPE_CAIXINHA_INVALIDA);
		problem.setTitle("Caixinha inválida");
		problem.setProperty("violations", ex.motivos());
		return problem;
	}

	/**
	 * Operação restrita ao Organizador (Story 2.4): convidar, encerrar prazo,
	 * apurar (Épico 4). 403 = autenticado MAS não autorizado para esta Caixinha.
	 */
	@ExceptionHandler(OperacaoNaoAutorizadaException.class)
	public ProblemDetail handleOperacaoNaoAutorizada(OperacaoNaoAutorizadaException ex) {
		ProblemDetail problem =
				ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
		problem.setType(TYPE_OPERACAO_NAO_AUTORIZADA);
		problem.setTitle("Operação não autorizada");
		return problem;
	}

	/**
	 * Prazo de entrada encerrado ou estado da Caixinha incompatível
	 * (Story 2.4). 422 = sintaxe OK, semântica inválida (tempo acabou).
	 */
	@ExceptionHandler(PrazoEncerradoException.class)
	public ProblemDetail handlePrazoEncerrado(PrazoEncerradoException ex) {
		ProblemDetail problem =
				ProblemDetail.forStatusAndDetail(
						HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
		problem.setType(TYPE_PRAZO_ENCERRADO);
		problem.setTitle("Prazo encerrado");
		return problem;
	}

	/**
	 * Palpite inválido (Story 2.5): tipicamente resultadoPossivelId que
	 * não pertence à Caixinha. 422 = sintaxe OK, semântica inválida.
	 */
	@ExceptionHandler(PalpiteInvalidoException.class)
	public ProblemDetail handlePalpiteInvalido(PalpiteInvalidoException ex) {
		ProblemDetail problem =
				ProblemDetail.forStatusAndDetail(
						HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
		problem.setType(TYPE_PALPITE_INVALIDO);
		problem.setTitle("Palpite inválido");
		return problem;
	}

	/**
	 * Chave PIX obrigatória (Story 2.5 v5, FR-5 v5): tentar palpitar sem
	 * cadastrar chave PIX no perfil. 422 com type próprio para o front
	 * abrir o form de cadastro sem inspecionar texto da mensagem.
	 */
	@ExceptionHandler(ChavePixObrigatoriaException.class)
	public ProblemDetail handleChavePixObrigatoria(ChavePixObrigatoriaException ex) {
		ProblemDetail problem =
				ProblemDetail.forStatusAndDetail(
						HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
		problem.setType(TYPE_CHAVE_PIX_OBRIGATORIA);
		problem.setTitle("Chave PIX obrigatória");
		return problem;
	}

	/**
	 * Pagamento indisponível (Story 3.1, FR-6): operação de pagamento em
	 * Caixinha cujo estado ainda não permite (ex.: {@code coletando_convites}
	 * — Mínimo de Aceites não atingido).
	 */
	@ExceptionHandler(PagamentoIndisponivelException.class)
	public ProblemDetail handlePagamentoIndisponivel(PagamentoIndisponivelException ex) {
		ProblemDetail problem =
				ProblemDetail.forStatusAndDetail(
						HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
		problem.setType(TYPE_PAGAMENTO_INDISPONIVEL);
		problem.setTitle("Pagamento indisponível");
		return problem;
	}

	/**
	 * Apuração inválida (Story 4.2, FR-12): estado da Caixinha incompatível
	 * (não está {@code formada}, ou já apurada — imutabilidade), Resultado
	 * Final que não pertence à Caixinha, ou seleção de Ganhadores ausente/
	 * incorreta quando há mais palpiteiros corretos que o Nº de Ganhadores.
	 */
	@ExceptionHandler(ApuracaoInvalidaException.class)
	public ProblemDetail handleApuracaoInvalida(ApuracaoInvalidaException ex) {
		ProblemDetail problem =
				ProblemDetail.forStatusAndDetail(
						HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
		problem.setType(TYPE_APURACAO_INVALIDA);
		problem.setTitle("Apuração inválida");
		// Caso "mais palpiteiros corretos que vagas": expõe os candidatos
		// (extensão RFC 9457 §3.2) para o front montar a tela de seleção.
		if (!ex.candidatos().isEmpty()) {
			problem.setProperty("candidatos", ex.candidatos());
		}
		return problem;
	}
}
