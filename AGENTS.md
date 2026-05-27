# Regras para agentes que mexem no caixinha-bet-backend

## ⚠️ Gate Asaas (R-7 / Caminho crítico)

O Épico 3 (núcleo financeiro) **NÃO pode iniciar** enquanto o gate Asaas
não estiver **VERDE**.

- **Estado atual (2026-05-19):**
  - ✅ AC-1 técnico (criar cobrança PIX sandbox real): VERDE em 23:47 BRT
    (`pay_rp3zox02ura58hfw`)
  - ⏳ AC-2 técnico (webhook ponta a ponta com ngrok): pendente
  - ⏳ AC-3 documental (aceite contratual): pendente decisão Pedro
- **Onde ver / atualizar:** `../docs/implementation-artifacts/gate-asaas-2026-05.md`
- **Epic 2** (sem dinheiro) pode prosseguir.
- **Epic 3+** bloqueado até gate VERDE COMPLETO (AC-1 + AC-2 + AC-3).

## Regras de negócio Asaas descobertas no smoke

- **Mínimo R$ 5,00 por cobrança PIX** (sandbox e produção). Tentativa com
  valor menor → HTTP 400 "valor da cobrança ... não pode ser menor que R$ 5,00".
  Story 1.5 ajustou `AsaasSandboxTest` para R$ 5,00; quando o Épico 3 expor
  o valor de ingresso, validar no domínio antes de chamar o adapter
  (rejeitar `< Money.of("5.00")` na criação da Caixinha — FR-1).
- **`customer` é obrigatório** no `POST /payments` — sem ele, 400. Implementado
  como `customerId` em `SolicitacaoCobranca` (Story 1.5 Task 0).
- **Base URL: `https://sandbox.asaas.com/api/v3`** (não `api-sandbox.asaas.com`).
  O `/api/v3` já está embutido na `base-url`; endpoints relativos são
  `/payments`, `/customers`, `/payments/{id}/pixQrCode`.

## Aviso de ambiente

- **JDK 21 obrigatório** (Spring Boot 4.0.6). Em Windows, se `java -version`
  mostrar Java 8/11, setar `JAVA_HOME` explicitamente para o JDK 21 antes do
  Maven. PowerShell: `$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.8"`.
  Sem isso, o build falha silenciosamente. O CI (GitHub Actions) não tem o
  quirk — usa `actions/setup-java`.
- **Docker em execução** para os testes de integração (Testcontainers Postgres).

## Aviso técnico — Spring Boot 4 ≠ Spring Boot 3

O Boot 4 **modularizou as autoconfigs** para pacotes por módulo. Se sua
memória/training data tem nomes do Boot 3, eles vão **falhar silenciosamente**
(autoconfig não exclui, BOM gerencia errado, etc.). Mapeamento já confirmado
neste projeto:

| Boot 3 (antigo)                                              | Boot 4 (atual)                                                   |
| ------------------------------------------------------------ | ---------------------------------------------------------------- |
| `org.springframework.boot.autoconfigure.jdbc.*`              | `org.springframework.boot.jdbc.autoconfigure.*`                  |
| `org.springframework.boot.autoconfigure.orm.jpa.*`           | `org.springframework.boot.hibernate.autoconfigure.*`             |
| `org.springframework.boot.autoconfigure.flyway.*`            | `org.springframework.boot.flyway.autoconfigure.*`                |
| `org.springframework.boot.autoconfigure.security.servlet.*`  | `org.springframework.boot.security.autoconfigure.*`              |
| `org.springframework.boot.autoconfigure.domain.EntityScan`   | `org.springframework.boot.persistence.autoconfigure.EntityScan`  |
| `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` |
| `flyway-core` sozinho ativa Flyway no boot                   | Precisa de `spring-boot-flyway` (autoconfig modularizada)        |
| `spring-boot-starter-parent:X.Y.Z.RELEASE`                   | `spring-boot-starter-parent:X.Y.Z` (sem `.RELEASE`)              |

**Testcontainers 2.x** (gerenciado pelo Boot 4): artefatos renomeados de
`postgresql`/`junit-jupiter` → `testcontainers-postgresql`/`testcontainers-junit-jupiter`.
BOM import não é transitivo em Maven — reimportar o `testcontainers-bom` no
`pom.xml` deste projeto (já feito na Story 1.1).

## Regras duras do projeto Caixinha Bet (fundação — Stories 1.1/1.3)

Estas regras são **load-bearing** (NFR-1, AR-8). Violar = retrabalho ou bug
financeiro.

### 1. Dinheiro

- **NUNCA** `Number`/`float`/`double` em nenhuma camada (Java/JSON/JPA).
- Use `com.caxinhabet.shared.money.Money` — escala fixa 2 centavos, `BigDecimal`
  por dentro, `RoundingMode.UNNECESSARY` (qualquer operação que exigiria
  arredondamento implícito falha).
- Factories: `Money.of(String)`, `Money.of(BigDecimal)`, `Money.ofCentavos(long)`.
  **Não existe** `Money.of(double)` — não compila.
- Aritmética: `plus`/`minus`/`times(int >=0)`/`divideWithRemainder(int >0)`.
  O split com resíduo (FR-13/OQ-2) usa `SplitResult(quotient, remainder)` —
  o destino do resíduo é decisão de negócio, mas nenhum centavo se perde.
- Igualdade semântica: `Money.of("10")` == `Money.of("10.00")` (compareTo == 0).
- **Anti-padrões proibidos:**
  - Expor `BigDecimal` cru em DTO/API (perde a garantia de string).
  - `Money.toBigDecimal()` em ponto que vai para o JSON.
  - Habilitar `WRITE_BIGDECIMAL_AS_PLAIN` pensando que "vira string" (não vira).
  - Aceitar número JSON na desserialização de campo monetário — o
    `MoneyDeserializer` já rejeita.

### 2. API: contrato fixo

- **Sucesso = corpo direto** (sem envelope `ApiResponse`/`data`/`payload`/`result`).
  O tipo da resposta é o recurso.
- **Erro = RFC 9457** `application/problem+json` via `ProblemDetail`. Habilitado
  por `spring.mvc.problemdetails.enabled=true` + `GlobalExceptionHandler`
  (`@RestControllerAdvice` em `shared.error`). **NÃO** introduzir formato de
  erro próprio. **NÃO** expor stack trace, `timestamp` legacy, `path` ou `trace`.
- **camelCase 1:1** entre Java/API. Sem conversão de naming na borda HTTP.
- Endpoints REST plural (`/caixinhas`, `/caixinhas/{id}/participantes`).

### 3. Naming Java ↔ Postgres

- Java/API: `camelCase` (classes `PascalCase`).
- Postgres: `snake_case` — gerado automaticamente pela
  `CamelCaseToUnderscoresNamingStrategy` (config no `application.yml`).
- Conversão **só** na borda do JPA. **Não usar** `@Column(name="...")` para
  forçar snake_case (a naming strategy já faz). Reservar `@Column(name=...)`
  para nome legado **real** que não pode mudar.

### 4. Schema do banco

- **Flyway é o único dono.** `spring.jpa.hibernate.ddl-auto: validate` —
  Hibernate NUNCA cria/altera tabela.
- Toda mudança é uma migration versionada `Vn__descricao.sql` em
  `src/main/resources/db/migration/`, escrita em `snake_case`.

### 5. Estrutura modular (package-by-feature)

- `com.caxinhabet.{caixinha,participante,pagamento,apuracao,ledger,auth}` —
  cada módulo vertical com `domain`/`app`/`adapter`.
- **Módulo `pagamento` é a única fronteira que conhece o Asaas.** O núcleo
  **NUNCA** importa o adapter (porta `ProvedorPagamento` chega na Story 1.4 —
  resposta arquitetural ao R-7/C-3).
- `shared/` é cross-cutting (`money`, `error`, `config`) — sem regra de negócio
  de feature.

### 6. Eventos de pagamento (Story 1.4+)

- Toda mutação financeira sai de um evento Asaas **persistido primeiro** com
  `event_id` (idempotência) + `provider_timestamp` (ordenação). Estado =
  projeção do evento mais recente por timestamp, NÃO ordem de chegada.
- Anti-padrão proibido: aplicar efeito de webhook antes de gravar o evento.

### 7. Segredos

- `.env` real NUNCA versionado — só `.env.example`. `.gitignore` cobre.

## Auth (Story 2.1)

Autenticação por **e-mail via magic link** (FR-16, OQ-5 fechada). Resumo
operacional para quem mexe no módulo `auth`:

- **Endpoints:** `POST /auth/solicitar-acesso` (gera link, sempre 204),
  `GET /auth/callback?token=...` (consome, abre sessão, 302 + cookie),
  `GET /auth/me` (quem sou eu, 200 ou 401), `POST /auth/sair`
  (limpa cookie). Whitelist no Spring Security para os 3 primeiros +
  `/webhooks/asaas` + `/actuator/health`.
- **Cookie:** `caixinhabet_sessao` opaco (43 chars Base64URL), HttpOnly,
  SameSite=Lax, Secure quando HTTPS, Path=/, Max-Age = 7 dias.
- **Token no e-mail:** o token cru SÓ existe na URL do link. No banco
  guardamos apenas o SHA-256 hex (`solicitacao_acesso.token_hash`). Roubo
  do banco não compromete tokens vivos.
- **Sessão = in-memory** (`SessaoStore` com `ConcurrentHashMap`). Restart
  do backend invalida todas as sessões. Trade-off explícito do MVP —
  retrospectiva do Épico 2 reavalia (Redis/tabela). Adapter trocável: a
  Story que migrar não toca em domain/use case.
- **Envio do link:** porta `MagicLinkSender` com 2 adapters:
  `LogMagicLinkSender` (default, dev — loga URL no console) e
  `SmtpMagicLinkSender` (Story 2.4 destravou — produção via `JavaMailSender`).
  `BootGuard` aceita `auth.magic-link.sender=log` ou `smtp`; qualquer
  outro valor → boot falha. **PRODUÇÃO DEVE USAR `smtp`** (token cru no
  log de aplicação seria leak).
- **`redirectTo`:** sanitizado no use case — precisa começar com `/`, sem
  esquema, sem `\\`, sem `//`. Inválido vira `null` (default `/`).
- **CORS:** habilitado para `http://localhost:3000` em dev,
  `allowCredentials=true` (sem isso o navegador não envia o cookie).
  Endurecer com domínio público em produção.
- **Anti-enumeração:** `POST /auth/solicitar-acesso` SEMPRE responde 204,
  não distingue e-mail novo de existente.

**Smoke local:**
```powershell
docker compose up -d
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.8"
.\mvnw.cmd spring-boot:run

# Em outro terminal:
curl -X POST http://localhost:8080/auth/solicitar-acesso `
  -H 'Content-Type: application/json' `
  -d '{"email":"teste@local"}'
# Procurar no log do backend a linha [MAGIC-LINK] ... e abrir a URL.
```

## Caixinha (Story 2.2)

Criação da Caixinha pelo Organizador (FR-1, FR-3). Resumo operacional:

- **Endpoints:** `POST /caixinhas` (cria, autenticado, 201 + Location);
  `GET /caixinhas/{id}` (detalhe; só Participantes vêem, **não-participante
  → 404** — anti-enumeração). **NÃO** existem `PATCH/PUT/DELETE` —
  imutabilidade por construção (FR-3) = ausência do endpoint.
- **Tabelas (V4):** `caixinha`, `resultado_possivel`, `participante`.
  Tabela `participante` criada aqui porque o Organizador é registrado
  como Participante na mesma transação da criação (PRD §3).
- **Dinheiro no DB = BIGINT centavos**. `valorIngressoCentavos` na entity;
  conversão `Money.centavos()` ↔ `Money.ofCentavos(long)` na borda do use
  case. CHECK SQL `>= 500` codifica o mínimo R$ 5,00 do Asaas.
- **Estado inicial:** `coletando_convites`. Demais 6 estados do PRD §3
  existem no enum (e no CHECK do DB) mas são populados em Stories
  futuras. NÃO é breaking change ADICIONAR estados; REMOVER seria.
- **Validação:** Bean Validation (`@NotBlank`, `@Min`, `@Email`, etc.)
  → 400 + RFC 9457 default. Validações cross-field/regra de negócio
  (`dataApuracao > prazoEntrada`, `valorIngresso >= R$ 5`) → 422 +
  `properties.violations` (extensão RFC 9457, §3.2.1). Agregadas: TODOS
  os motivos retornam de uma vez. NUNCA inventar formato próprio.
- **Authentication.principal = email** (Story 2.1) — o controller faz
  `usuarios.findByEmail(auth.getName())` para resolver o `usuarioId`.
  Concessão registrada no ArchUnit (caixinha pode importar
  `auth.adapter.persistence` — namespace narrow).
- **`@Column(name=...) raro mas necessário** em `ladoA`/`ladoB`: o
  `CamelCaseToUnderscoresNamingStrategy` só insere `_` antes de letra
  maiúscula que esteja entre duas minúsculas. Como `A`/`B` são a última
  letra do field, viraria `ladoa`/`ladob` sem o `@Column`.

**Smoke local:**
```powershell
# 1. Autenticar (precisa do cookie de sessão da Story 2.1)
curl -X POST http://localhost:8080/auth/solicitar-acesso `
  -H 'Content-Type: application/json' `
  -d '{"email":"rafael@local"}'
# pega o link [MAGIC-LINK] no log e abre no browser para gerar cookie

# 2. Criar caixinha (com cookie capturado em cookie.txt)
curl -b cookie.txt -X POST http://localhost:8080/caixinhas `
  -H 'Content-Type: application/json' `
  -d '{"titulo":"Brasil x Marrocos","ladoA":"Brasil","ladoB":"Marrocos","valorIngresso":"40.00","minimoParticipantes":5,"prazoEntrada":"2026-06-01T12:00:00Z","dataApuracao":"2026-06-01T14:00:00Z","rotulosResultados":["Vitória do Brasil","Empate","Vitória do Marrocos"],"emailsConvidados":[]}'
```

## Convites (Story 2.4)

Convidar Participantes por e-mail (FR-4). Resumo:

- **Endpoints:** `POST /caixinhas/{id}/convites` (autenticado, só dono).
  Resposta 200 com `{convidados:[...], jaPresentes:[...]}` (duplicata não
  é erro — informativo). 403 se não-dono; 422 se prazo encerrado; 400 se
  payload inválido (Bean Validation `@Email`, `@Size(min=1, max=50)`).
- **Convite na criação:** o wizard manda `emailsConvidados[]` no
  `POST /caixinhas`. `CriarCaixinhaUseCase` (Story 2.2) agora chama
  `EnviarConvitesUseCase` na MESMA transação — composição limpa.
- **Porta `ConviteEmailSender`** (domain) + 2 adapters:
  - `LogConviteEmailSender` (default, `caixinha.convite.sender=log`) —
    loga `[CONVITE] ...` no console. Dev/test.
  - `SmtpConviteEmailSender` (`caixinha.convite.sender=smtp`) — usa
    `JavaMailSender` + `spring.mail.*`. **Story 2.4 também ATIVOU SMTP
    para o magic link** (`SmtpMagicLinkSender`, `auth.magic-link.sender=smtp`).
- **`CaixinhaBootGuard`** (em `caixinha.app`) e `auth.app.BootGuard`
  ampliado: ambos aceitam `log` ou `smtp` (qualquer outro valor → boot falha).
- **Envio = pós-commit, best-effort.** `TransactionSynchronizationManager.
  registerSynchronization(afterCommit)` dispara o sender APÓS a tx
  commitar. Falha SMTP NÃO reverte a criação/convite — log error e segue.
- **Limite:** `@Size(max=50)` por chamada. Bolão típico é 5-20; 50 dá folga.
- **Provedor SMTP em produção:** **Resend** (decisão 2026-05-26). Config
  no `.env` real:
  - `SMTP_HOST=smtp.resend.com`
  - `SMTP_PORT=587` (STARTTLS — casa com o `spring.mail.properties` do
    `application.yml`)
  - `SMTP_USER=resend` (literal, igual para todo mundo)
  - `SMTP_PASSWORD=re_xxx` (API key gerada em `resend.com/api-keys`)
  - `EMAIL_FROM=<conta>@<domínio>` — o **domínio precisa estar verificado**
    em `resend.com/domains` (registros DNS de SPF/DKIM publicados); sem
    isso o provedor rejeita o envio.

  Nenhum adapter dedicado a Resend existe — `JavaMailSender` + os
  `Smtp*EmailSender` já cobrem (Resend é SMTP padrão). Trocar de
  provedor no futuro = trocar as 4 vars acima, zero código.
- **Health do mail desabilitado**: `management.health.mail.enabled=false`
  — SMTP é dependência soft, não deve derrubar `/actuator/health`.

**Smoke local:** depois de autenticar e criar uma Caixinha,
```powershell
curl -b cookie.txt -X POST http://localhost:8080/caixinhas/1/convites `
  -H 'Content-Type: application/json' `
  -d '{"emails":["alice@local","bob@local"]}'
# 200 + corpo {convidados:[...], jaPresentes:[]}
# Procurar no log: [CONVITE] Para alice@local: http://localhost:3000/convites/1
```

## Convite e Palpite (Story 2.5)

Aceitar convite e escolher Palpite (FR-5). Resumo operacional:

- **Endpoints:**
  - `GET /caixinhas/{id}/convite` — visão restrita do convidado
    (dados da Caixinha + visão do próprio Participante). NÃO inclui lista
    completa de Participantes (privacidade).
  - `POST /caixinhas/{id}/aceitar` — transição `convidado → aceito`.
    **Idempotente** — chamar 2x não erra.
  - `PUT /caixinhas/{id}/palpite` body `{resultadoPossivelId}` — grava/
    atualiza palpite. Aceite implícito se status é `convidado`.
- **Anti-enumeração:** não-convidado → 404 (não 403); mesma estratégia
  do `GET /caixinhas/{id}` da Story 2.2.
- **Vínculo `usuario_id`:** Participante criado por convite (Story 2.4)
  começa com `usuario_id=NULL`. No PRIMEIRO acesso autenticado a
  qualquer endpoint da Story 2.5, o `usuario_id` é vinculado pelo email
  do principal. Sem isso, FR-17 (dashboard) não funcionaria depois.
- **Prazo congelado:** `PUT /palpite` após `prazoEntrada` → 422
  `PrazoEncerradoException` (mesma exceção da Story 2.4).
- **Validação de palpite cross-Caixinha:** `resultadoPossivelId` precisa
  pertencer à própria Caixinha — id de outra → 422 `PalpiteInvalidoException`
  (anti-enumeração de IDs).
- **`ResultadoResponse` ganhou `id`** (era `{ordem, rotulo}`, agora
  `{id, ordem, rotulo}`). Não-breaking: TS aceita campo extra.
- **`ParticipanteResumoResponse` ganhou `palpiteResultadoPossivelId`** —
  exposto em `GET /caixinhas/{id}` para dashboard saber quem palpitou.

**Smoke local (alice convidada por rafael):**
```powershell
# 1. Como alice, abrir o convite (vincula usuario_id automaticamente)
curl -b cookie_alice.txt http://localhost:8080/caixinhas/1/convite
# 200 com dados restritos

# 2. Aceitar
curl -b cookie_alice.txt -X POST http://localhost:8080/caixinhas/1/aceitar
# 200 com status=aceito

# 3. Palpitar (aceite implícito se status=convidado)
curl -b cookie_alice.txt -X PUT http://localhost:8080/caixinhas/1/palpite `
  -H 'Content-Type: application/json' `
  -d '{"resultadoPossivelId":1}'
# 200 com palpiteResultadoPossivelId=1
```

## Comandos úteis

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.8"
.\mvnw.cmd -B clean verify       # build + todos os testes
.\mvnw.cmd spring-boot:run       # app local (precisa de Postgres do docker-compose)
docker compose up -d             # sobe o Postgres local
```

## Onde estão os contratos

- `src/main/java/com/caxinhabet/shared/money/Money.java` — tipo monetário (Story 1.3).
- `src/main/java/com/caxinhabet/shared/error/GlobalExceptionHandler.java` — RFC 9457 (Story 1.3).
- `src/main/resources/application.yml` — datasource, JPA validate, naming
  snake_case, problemdetails enabled, Actuator health.
- `src/main/resources/db/migration/` — Flyway migrations.
- `src/test/java/com/caxinhabet/shared/money/Money*Test.java` — guardrails de dinheiro.
- `src/test/java/com/caxinhabet/shared/naming/NamingStrategyIT.java` — guardrail snake_case.

## Coerência com o frontend

O `caixinha-bet-frontend/AGENTS.md` (Story 1.2) espelha estas regras do lado
TS: `decimal.js` em vez de `Number`, `ProblemDetails` no cliente, camelCase
1:1, mobile-first. Os contratos devem casar 1:1 — qualquer mudança em um
lado pede o reflexo no outro.
