# Oficina — OS Service

Microsserviço de **ordens de serviço** da oficina mecânica (Tech Challenge FIAP SOAT,
**fase 4**). Cuida de clientes, veículos, login de funcionários, ciclo de vida e
histórico da OS, área do cliente (JWT por CPF) e **orquestra a saga** que coordena
Execução e Billing.

> **Situação: esqueleto (etapa 2 de 5).** Projeto, configuração, mensageria, CI,
> manifestos e ambiente local estão prontos e testados. O domínio (migrado do monolito
> da fase 3) e a saga entram na etapa 3.

---

## Documentação da arquitetura

A documentação do sistema inteiro vive neste repositório, em [`docs/fase-4/`](docs/fase-4/):

| Documento | Conteúdo |
| --- | --- |
| [arquitetura.md](docs/fase-4/arquitetura.md) | **Ponto de entrada**: divisão em serviços, bancos, comunicação, mapa requisito → onde |
| [saga.md](docs/fase-4/saga.md) | Saga orquestrada: fluxo, máquina de estados, compensações, prazos |
| [contratos-mensagens.md](docs/fase-4/contratos-mensagens.md) | Filas, exchanges, envelope e cada comando/evento |
| [ADRs 006–011](docs/fase-4/adr/) | Decisões: divisão, orquestração, RabbitMQ, DynamoDB, outbox, Lambda |
| [RFC 004](docs/fase-4/rfc/rfc-004-integracao-mercado-pago.md) | Integração com o Mercado Pago |

## Os repositórios da fase 4

| Repositório | Papel |
| --- | --- |
| **oficina-os-service** (este) | OS, clientes, veículos, orquestrador da saga |
| [oficina-execucao-service](https://github.com/GustavoKikey/oficina-execucao-service) | Fila de execução, diagnóstico, reparo, estoque |
| [oficina-billing-service](https://github.com/GustavoKikey/oficina-billing-service) | Orçamento e pagamento (Mercado Pago) |
| [oficina-infra-k8s](https://github.com/GustavoKikey/oficina-infra-k8s) | Cluster EKS e RabbitMQ |
| [oficina-auth-lambda](https://github.com/GustavoKikey/oficina-auth-lambda) | Autenticação do cliente por CPF |

## Stack

Java 21 · Quarkus 3.40 (LTS) · PostgreSQL 16 + Flyway · RabbitMQ (SmallRye Reactive
Messaging) · JWT RS256 · OpenTelemetry + Micrometer + logs JSON (New Relic) · JUnit 5,
REST-Assured, Testcontainers · JaCoCo (gate de 80%) · SonarCloud · Docker · Kubernetes (EKS)

## Executar localmente

Pré-requisitos: Docker e JDK 21. O Maven vem pelo wrapper (`./mvnw`).

**Só este serviço, em modo dev:** o Quarkus sobe PostgreSQL e RabbitMQ sozinho (Dev Services).

```bash
./mvnw quarkus:dev
```

**O sistema inteiro:** com os três repositórios clonados lado a lado na mesma pasta:

```bash
docker compose up -d --build
```

| Endereço | O quê |
| --- | --- |
| http://localhost:8080/swagger | OS Service |
| http://localhost:8081/swagger | Execução Service |
| http://localhost:8082/swagger | Billing Service |
| http://localhost:15672 | Painel do RabbitMQ (`oficina` / `oficina`) |
| http://localhost:16686 | Jaeger: traces distribuídos |

## Testes

```bash
./mvnw verify
```

Roda os testes unitários e os que sobem a aplicação contra PostgreSQL e RabbitMQ reais
(Testcontainers), e **falha o build se a cobertura do serviço ficar abaixo de 80%**.
O relatório fica em `target/site/jacoco/index.html`.

## Mensageria

| Direção | Exchange / fila | Conteúdo |
| --- | --- | --- |
| Publica | `oficina.comandos` (direct) | Comandos da saga para Execução (`rk: execucao`) e Billing (`rk: billing`) |
| Consome | `os.eventos` ← `oficina.eventos` (`execucao.#`, `billing.#`) | Respostas e eventos dos participantes |
| DLQ | `os.eventos.dlq` | Mensagens rejeitadas após as retentativas |

## CI/CD

| Gatilho | O que roda |
| --- | --- |
| Push fora da `main` e PR para a `main` | [`ci.yml`](.github/workflows/ci.yml): build, testes, gate de cobertura, SonarCloud, imagem Docker, kubeconform |
| `homolog` → `hom`, `main` → `prod` | Deploy no EKS (etapa 5) |

`main` protegida: merge só por Pull Request, com as checagens do CI obrigatórias.
