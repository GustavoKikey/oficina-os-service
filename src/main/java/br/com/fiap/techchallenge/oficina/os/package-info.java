/**
 * <b>OS Service</b> — clientes, veículos, ordens de serviço e orquestração da saga.
 *
 * <p>Clean Architecture, como nas fases anteriores — a dependência aponta sempre para
 * dentro:
 * <pre>
 *   external   (api · persistence · messaging · security · config · observabilidade)
 *      ↓                                          Frameworks &amp; Drivers
 *   controllers · presenters · gateways · dtos    Interface Adapters
 *      ↓
 *   usecases                                      Application
 *      ↓
 *   entities                                      Enterprise (agregados, VOs, saga)
 * </pre>
 *
 * <p>Desenho: {@code docs/fase-4/arquitetura.md} e {@code docs/fase-4/saga.md}.
 */
package br.com.fiap.techchallenge.oficina.os;
