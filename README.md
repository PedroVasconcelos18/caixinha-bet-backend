# caixinha-bet-backend

Backend do **Caixinha Bet** — orquestrador de máquina de estados financeira
sobre um PSP externo (Asaas). Spring Boot 4.0.6 / Java 21, monolito modular
package-by-feature.

> ⚠️ Este produto custodia dinheiro de terceiros. **Corretude monetária tem
> precedência sobre prazo e performance** (NFR-1). Nunca representar dinheiro
> como `float`/`double`/`Number` em nenhuma camada.

## Pré-requisitos

- **JDK 21** (LTS). O build NÃO funciona com Java 8/11/17.
- **Docker** (Docker Desktop no Windows/Mac) — para o Postgres local e para
  os testes de integração (Testcontainers).
- O Maven não precisa ser instalado: use o wrapper `./mvnw` (`mvnw.cmd` no
  Windows), que é versionado.

### Nota de ambiente (Windows — máquina do dev)

Se `java -version` mostrar Java 8/11, o JDK 21 pode estar instalado mas fora
do `PATH`. Aponte `JAVA_HOME` para o JDK 21 antes de rodar o Maven:

- **PowerShell:** `$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.8"`
- **bash:** `export JAVA_HOME=/c/Program\ Files/Java/jdk-21.0.8`

O CI (GitHub Actions) não tem esse problema — usa `actions/setup-java` com
JDK 21.

## Decisão de build: Maven

O projeto usa **Maven** (wrapper `./mvnw`). A Architecture deixou
"Maven vs. Gradle" deferido como não-bloqueante; a decisão foi por Maven:
default do Spring Initializr, menor superfície de configuração para um time
pequeno sob prazo, CI mais simples. Há um único build tool no repositório
(`pom.xml`).

## Subir o ambiente local

```bash
# 1. Configurar variáveis (uma vez)
cp .env.example .env        # Windows: copy .env.example .env

# 2. Subir o Postgres
docker compose up -d        # aguarda o healthcheck ficar 'healthy'

# 3. Rodar a aplicação
./mvnw spring-boot:run      # Windows: .\mvnw.cmd spring-boot:run

# 4. Conferir saúde
curl http://localhost:8080/actuator/health   # -> {"status":"UP", ...}

# Derrubar o Postgres
docker compose down
```

## Smoke do auth (magic link, Story 2.1)

```bash
# Com app rodando, dispara o envio do magic link:
curl -X POST http://localhost:8080/auth/solicitar-acesso \
  -H 'Content-Type: application/json' \
  -d '{"email":"teste@local"}'
# -> 204 No Content. Procurar no log do backend a linha "[MAGIC-LINK] ..."
#    e abrir a URL no browser. Vai redirecionar para / com cookie de sessão.
```

Em dev, o envio é um log no console (adapter `LogMagicLinkSender`). Envio
real por SMTP chega na Story 2.4.

## Smoke de Caixinha (Story 2.2)

Depois de autenticar (smoke do auth), criar uma Caixinha:

```bash
curl -b cookie.txt -X POST http://localhost:8080/caixinhas \
  -H 'Content-Type: application/json' \
  -d '{
    "titulo": "Brasil x Marrocos",
    "ladoA": "Brasil",
    "ladoB": "Marrocos",
    "valorIngresso": "40.00",
    "minimoParticipantes": 5,
    "prazoEntrada": "2026-06-01T12:00:00Z",
    "dataApuracao": "2026-06-01T14:00:00Z",
    "rotulosResultados": ["Vitória do Brasil", "Empate", "Vitória do Marrocos"],
    "emailsConvidados": []
  }'
# -> 201 Created + Location: /caixinhas/{id} + corpo com a Caixinha.
```

## Rodar os testes

```bash
./mvnw clean verify
```

Inclui:

- `EstruturaModularTest` — guardrail da estrutura modular package-by-feature.
- `CaixinhaBetApplicationContextTest` — o contexto Spring sobe (sem Postgres).
- `PostgresIntegracaoTest` — Testcontainers sobe um Postgres 17 real, o
  Flyway aplica a baseline e o Actuator health responde `UP`. **Requer Docker
  em execução.**

## Estrutura (monolito modular package-by-feature)

```
com.caxinhabet
├── CaixinhaBetApplication        # ponto de entrada (raiz do component scan)
├── shared/ {money,error,config}  # cross-cutting (preenchido na Story 1.3)
├── caixinha/     {domain,app,adapter}   # FR-1,2,3,17
├── participante/ {domain,app,adapter}   # FR-4,5
├── pagamento/    {domain,app,adapter}   # NÚCLEO DE RISCO — FR-6,7,8,10,11
├── apuracao/     {domain,app,adapter}   # FR-9,12,13,14,15
├── ledger/       {domain,app,adapter}   # ledger duplo + reconciliação
└── auth/         {domain,app,adapter}   # FR-16
```

**Regra dura de fronteira:** o módulo `pagamento` é a única fronteira que
conhece o Asaas. O núcleo de domínio **nunca** importa o adapter do Asaas —
sempre via a porta `ProvedorPagamento` (a ser criada na Story 1.4).

## Schema do banco

O **Flyway** é o único dono do schema. O Hibernate roda com
`ddl-auto: validate` e **nunca** cria/altera tabela. Toda mudança de schema é
uma migration versionada `Vn__descricao.sql` em
`src/main/resources/db/migration/`, escrita em `snake_case`.

## Anti-padrões proibidos

- ❌ Dinheiro como `Number`/`float`/`double` em qualquer camada. Use o tipo
  `com.caxinhabet.shared.money.Money` (Story 1.3). NÃO expor `BigDecimal`
  cru em DTO/API — perde a garantia de string na borda JSON.
- ❌ Construir `Money` a partir de `double`/`float` (não compila — não há
  sobrecarga). Factories: `Money.of(String)`, `Money.of(BigDecimal)`,
  `Money.ofCentavos(long)`.
- ❌ Habilitar `spring.jackson.serialization.write-bigdecimal-as-plain`
  pensando que "serializa como string": só desliga notação científica. A
  garantia de string vem do `@JsonValue` no `Money`.
- ❌ Aceitar dinheiro como número JSON na desserialização. O `MoneyDeserializer`
  rejeita explicitamente — `{"valor": 40.00}` (número) falha; só `{"valor": "40.00"}`
  (string) é aceito.
- ❌ Hibernate criando schema (`ddl-auto` ≠ `validate`). Flyway é o único
  dono do schema.
- ❌ Qualquer classe fora de `com.caxinhabet.pagamento.adapter` conhecer o
  Asaas.
- ❌ `@Column(name="...")` manual para "forçar" snake_case. A naming strategy
  (`CamelCaseToUnderscoresNamingStrategy`) já gera `snake_case` automaticamente.
  Só usar `@Column(name=...)` para nome legado **real** que não pode mudar.
- ❌ Wrapper de resposta de sucesso (`ApiResponse<T>`, `Envelope`, `data: {...}`).
  Sucesso = corpo direto. Erro = `application/problem+json` (RFC 9457) via
  `GlobalExceptionHandler`.
- ❌ Versionar o `.env` real (só `.env.example`).
