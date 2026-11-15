# ADR 006 — Divisão em três microsserviços

- **Status:** aceito
- **Data:** 2026-11-15
- **Substitui:** a seção 2 do [ADR 004 da fase 3](https://github.com/GustavoKikey/tech-challenge-15SOAT/blob/main/docs/fase-3/adr/adr-004-padrao-de-comunicacao.md) ("chamada direta entre bounded contexts, na mesma transação")

---

## Contexto

O enunciado da fase 4 exige ao menos três microsserviços independentes, cada um com
repositório, infraestrutura e banco próprios, e sugere a divisão OS / Billing /
Execução.

O monolito da fase 3 tem quatro bounded contexts: `atendimento` (cliente, veículo,
catálogo de serviços, OS com itens e orçamento), `estoque` (peças e reservas),
`relatorio` e `seguranca`. Nenhum deles corresponde exatamente a um serviço sugerido. O
orçamento e os itens estão dentro do agregado OS, e não existe pagamento.

## Decisão

Três serviços, alinhados aos **três momentos do negócio** (atender, executar e cobrar),
e não às tabelas do banco:

| Serviço | Fica com | Vem de |
| --- | --- | --- |
| **OS Service** | Cliente, veículo, usuários e login, OS (status, histórico, resumo do orçamento), área do cliente, relatório e **orquestração da saga** | `atendimento` (sem itens nem catálogo), `seguranca`, `relatorio` |
| **Execução Service** | Fila de execução, diagnóstico com itens, reparo, **catálogo de serviços** e **estoque** | `estoque` + itens e catálogo de `atendimento`; a fila é nova |
| **Billing Service** | Orçamento e pagamento (Mercado Pago), estorno | Cálculo do orçamento de `atendimento`; o pagamento é novo |

### Onde cada conceito disputado ficou, e por quê

**Itens da OS → Execução.** Quem registra os itens é o mecânico, durante o diagnóstico.
Se os itens ficassem na OS, cada peça adicionada exigiria consultar o estoque, que está
em outro serviço: uma chamada síncrona por clique, com a Execução fora do ar travando o
atendimento. Na Execução, a validação de peça e preço é local. A OS recebe a lista
pronta no evento `DiagnosticoConcluido` e guarda uma cópia para exibir ao cliente.

**Catálogo de serviços → Execução.** Ele acompanha os itens pelo mesmo motivo: o
mecânico escolhe o serviço do catálogo ao diagnosticar.

**Estoque → Execução, e não um quarto serviço.** Quem consome peça é o reparo. Um
serviço de estoque separado acrescentaria repositório, banco, pipeline e pod para
manter, e transformaria a reserva (hoje transação local, com lock) em mais uma etapa
distribuída. O enunciado pede no mínimo três; o quarto não se paga.

**Orçamento → Billing.** O enunciado põe "geração e envio de orçamentos para aprovação"
no Billing, e é o orçamento que define o valor cobrado. A OS guarda só `orcamentoId`,
`valorTotal` e `linkPagamento`, recebidos por evento.

**Decisão do cliente → OS Service.** A área do cliente (JWT por CPF, conferência de que
a OS é dele) já existe no OS Service. Mover a aprovação para o Billing obrigaria o
Billing a conhecer o dono de cada OS.

## Alternativas consideradas

| Alternativa | Por que não |
| --- | --- |
| Quatro serviços (Estoque separado) | Custo operacional sem ganho para o enunciado; reserva de peça vira passo distribuído |
| Estoque no OS Service | Esvazia a Execução, que ficaria só com uma fila; a saga perde a etapa de peças, a que mais justifica compensação |
| Itens e catálogo no OS Service | Chamada síncrona da OS para a Execução a cada item; acoplamento de disponibilidade |

## Consequências

- A abertura da OS **deixa de receber itens**. O `POST /ordens-servico` da fase 3
  aceitava serviços e peças já na abertura; na fase 4 os itens nascem no diagnóstico.
  O campo novo `relatoCliente` registra o que o cliente descreveu no balcão.
- As rotas de itens, diagnóstico e finalização saem do OS Service e vão para a Execução
  (`/ordens-execucao/{osId}/…`).
- As rotas `/publico/ordens-servico/*`, marcadas como depreciadas no [ADR 002 da fase 3](https://github.com/GustavoKikey/tech-challenge-15SOAT/blob/main/docs/fase-3/adr/adr-002-descontinuar-rotas-publicas.md),
  **são removidas**.
- O status da OS ganha `AGUARDANDO_PAGAMENTO`.
- O código do monolito é reaproveitado, não reescrito: entidades, use cases e testes de
  `estoque` mudam de repositório quase intactos.
