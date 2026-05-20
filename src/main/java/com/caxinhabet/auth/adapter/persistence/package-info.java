/**
 * Persistence adapter do módulo auth (Story 2.1).
 *
 * <p>Entidades JPA + Spring Data repositories para {@code usuario} e
 * {@code solicitacao_acesso}. Mapeamento camelCase ↔ snake_case via
 * {@code CamelCaseToUnderscoresNamingStrategy} (Story 1.1).
 *
 * <p>Padrão da Story 1.4: o repositório é usado direto pelo use case;
 * camada de "DAO/Service de repositório" não é introduzida — simplicidade
 * antes de generalização.
 */
package com.caxinhabet.auth.adapter.persistence;
