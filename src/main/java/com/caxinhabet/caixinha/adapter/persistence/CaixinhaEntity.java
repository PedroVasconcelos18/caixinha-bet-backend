package com.caxinhabet.caixinha.adapter.persistence;

import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entidade JPA da tabela {@code caixinha} (Story 2.2).
 *
 * <p>Dinheiro como {@code long valorIngressoCentavos} (NÃO {@code Money})
 * — conversão acontece na borda do use case (decisão da Story 2.2,
 * documentada em {@code package-info.java}).
 *
 * <p>{@code estado} como {@link EnumType#STRING} — mantém o bate-1:1 entre
 * o nome do enum em {@code snake_case} e o valor no SQL.
 */
@Entity
@Table(name = "caixinha")
public class CaixinhaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String titulo;

	// @Column(name=...) explícito: `CamelCaseToUnderscoresNamingStrategy` só
	// insere underscore antes de UMA letra maiúscula que esteja entre duas
	// MINÚSCULAS. Como o `A`/`B` é a última letra do field name, a regra
	// não dispara e o nome físico vira `ladoa`/`ladob` em vez de
	// `lado_a`/`lado_b`. Forçamos o nome correto aqui — uma das raras
	// exceções permitidas pelo AGENTS.md (linha "Reservar @Column(name=...)
	// para nome legado real").
	@Column(name = "lado_a", nullable = false, length = 80)
	private String ladoA;

	@Column(name = "lado_b", nullable = false, length = 80)
	private String ladoB;

	@Column(nullable = false)
	private long valorIngressoCentavos;

	@Column(nullable = false)
	private int minimoParticipantes;

	// FR-1 v5 (2026-05-21): Nº de Ganhadores (1, 2 ou 3) — entre quantos
	// o Prêmio é rateado. CHECK SQL em V6 garante 1..3 e ≤ minimoParticipantes.
	@Column(nullable = false)
	private int numeroGanhadores;

	@Column(nullable = false)
	private Instant prazoEntrada;

	@Column(nullable = false)
	private Instant dataApuracao;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private EstadoCaixinha estado;

	@Column(nullable = false)
	private Long organizadorUsuarioId;

	/**
	 * Resultado Final escolhido na apuração (Épico 4 v5, Story 4.2).
	 * NULLABLE — null até a apuração. Após `apurada` é IMUTÁVEL (o use case
	 * de apuração rejeita re-apuração). É o id de um {@code resultado_possivel}
	 * desta Caixinha.
	 */
	@Column(name = "resultado_final_id")
	private Long resultadoFinalId;

	@Column(nullable = false)
	private Instant criadoEm;

	protected CaixinhaEntity() {
		// JPA.
	}

	public CaixinhaEntity(
			String titulo,
			String ladoA,
			String ladoB,
			long valorIngressoCentavos,
			int minimoParticipantes,
			int numeroGanhadores,
			Instant prazoEntrada,
			Instant dataApuracao,
			EstadoCaixinha estado,
			Long organizadorUsuarioId) {
		this.titulo = titulo;
		this.ladoA = ladoA;
		this.ladoB = ladoB;
		this.valorIngressoCentavos = valorIngressoCentavos;
		this.minimoParticipantes = minimoParticipantes;
		this.numeroGanhadores = numeroGanhadores;
		this.prazoEntrada = prazoEntrada;
		this.dataApuracao = dataApuracao;
		this.estado = estado;
		this.organizadorUsuarioId = organizadorUsuarioId;
		this.criadoEm = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getTitulo() {
		return titulo;
	}

	public String getLadoA() {
		return ladoA;
	}

	public String getLadoB() {
		return ladoB;
	}

	public long getValorIngressoCentavos() {
		return valorIngressoCentavos;
	}

	public int getMinimoParticipantes() {
		return minimoParticipantes;
	}

	public int getNumeroGanhadores() {
		return numeroGanhadores;
	}

	public Instant getPrazoEntrada() {
		return prazoEntrada;
	}

	public Instant getDataApuracao() {
		return dataApuracao;
	}

	public EstadoCaixinha getEstado() {
		return estado;
	}

	/**
	 * Story 3.1 (FR-6): transiciona a Caixinha para um novo estado.
	 *
	 * <p>Setter cru NÃO é exposto. Este método é o único caminho de
	 * mutação do estado a partir da camada de aplicação — concentra a
	 * disciplina de "ninguém regride estado por acidente".
	 *
	 * <p><b>Transição redundante (estado-alvo == atual) é no-op silencioso</b>
	 * — não lança (fix code review Épico 3, 2026-05-21). Motivo: sob
	 * concorrência, dois eventos assíncronos (dois aceites no N-ésimo, duas
	 * confirmações de webhook) podem ambos chamar {@code transicionarPara}
	 * para o mesmo estado-alvo; lançar aqui derrubaria o webhook (500 →
	 * Asaas re-tenta em loop) ou faria rollback de um aceite legítimo. O
	 * resultado idempotente (estado já é o desejado) é exatamente o que se
	 * quer. Validações de transições legais (quais origens→destinos são
	 * válidas) ficam nos serviços de cada Story.
	 *
	 * @param novo estado-alvo (não pode ser {@code null})
	 */
	public void transicionarPara(EstadoCaixinha novo) {
		if (novo == null) {
			throw new IllegalArgumentException("estado-alvo não pode ser null");
		}
		this.estado = novo;
	}

	public Long getOrganizadorUsuarioId() {
		return organizadorUsuarioId;
	}

	/** Resultado Final da apuração (Épico 4). {@code null} se não apurada. */
	public Long getResultadoFinalId() {
		return resultadoFinalId;
	}

	/**
	 * Story 4.2 (FR-12): registra o Resultado Final escolhido na apuração.
	 * Chamado uma única vez, na transição para {@code apurada} — o use case
	 * de apuração garante que não há re-apuração.
	 */
	public void registrarResultadoFinal(long resultadoFinalId) {
		this.resultadoFinalId = resultadoFinalId;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}
}
