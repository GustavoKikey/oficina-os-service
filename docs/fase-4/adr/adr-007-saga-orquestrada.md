# ADR 007 — Saga orquestrada pelo OS Service

- **Status:** aceito
- **Data:** 2026-11-15

---

## Contexto

Abrir uma OS, gerar o orçamento, cobrar o cliente e liberar o reparo envolve três
serviços com bancos separados. O enunciado exige o **Saga Pattern** com rollback e
compensação, e permite as duas formas: **orquestrada** (coordenador central) ou
**coreografada** (serviços reagindo a eventos uns dos outros). A escolha precisa ser
justificada.

## Decisão

**Saga orquestrada, com o OS Service como orquestrador.**

O OS Service mantém uma tabela `sagas` (uma linha por OS) com o estado atual, o prazo
do passo, as tentativas e as compensações pendentes. A cada resposta recebida, ele
decide o próximo comando. Execução e Billing não sabem que existe uma saga: recebem
comandos e respondem. O desenho completo está em [saga.md](../saga.md).

### Por que orquestração

| Critério | Orquestrada | Coreografada |
| --- | --- | --- |
| Onde está o fluxo | Em **um lugar** (`SagaOrdemServico`), legível de cima a baixo | Espalhado pelos handlers de três serviços |
| Compensação | O orquestrador sabe quais passos terminaram e dispara só o necessário | Cada serviço precisa deduzir, pelos eventos que viu, o que desfazer |
| "Em que pé está a OS 123?" | Uma consulta: `GET /admin/sagas/123` | Reconstruir a partir dos logs dos três serviços |
| Prazos (cliente não decide, pagamento não chega) | Um verificador, num lugar só | Cada serviço com seus timers, sem visão do todo |
| Acoplamento | Os participantes não conhecem uns aos outros; só o orquestrador conhece todos | Cada serviço conhece os eventos dos outros |
| Risco | Orquestrador vira ponto central de lógica | Ciclos de eventos difíceis de enxergar |

O fluxo da OS é **longo** (dias), **tem pessoas no meio** (mecânico, cliente) e **tem
dinheiro** (estorno). Nessas condições, poder enxergar e operar cada saga
individualmente vale mais que o desacoplamento extra da coreografia.

### Por que o OS Service, e não um serviço orquestrador separado

O ciclo de vida da saga **é** o ciclo de vida da OS: o status da OS muda a cada passo
da saga. Se o orquestrador ficasse em outro serviço, cada passo seria um evento a mais
só para manter a OS em dia. Manter os dois no mesmo serviço permite atualizar o estado
da saga e o status da OS **na mesma transação local**.

## Alternativas consideradas

- **Coreografia.** Descartada pelos critérios acima. Ela continua sendo a escolha
  certa para notificações e projeções, que não precisam de compensação.
- **Motor de workflow (Temporal, Camunda).** Resolveria persistência e prazos, mas
  acrescenta infraestrutura pesada ao cluster e esconde dentro de uma ferramenta o que
  o enunciado quer ver implementado.

## Consequências

- O OS Service concentra a lógica do fluxo. O risco é mitigado mantendo a saga como uma
  **máquina de estados pura**: a classe de domínio não importa framework e é testada
  sem banco nem fila.
- Execução e Billing ficam mais simples: handlers de comando idempotentes, sem estado
  de fluxo.
- Uma falha do OS Service **pausa** as sagas, mas não as perde: o estado está no banco,
  e as mensagens ficam na fila até ele voltar.
