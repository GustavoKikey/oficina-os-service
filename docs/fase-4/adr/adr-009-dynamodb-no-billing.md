# ADR 009 — DynamoDB no Billing Service

- **Status:** aceito
- **Data:** 2026-11-15

---

## Contexto

O enunciado exige pelo menos um banco relacional e pelo menos um não relacional. OS e
Execução ficam no PostgreSQL por motivos fortes: o schema da fase 3 já existe na OS, e o
estoque da Execução precisa de lock pessimista. Resta o Billing.

## Decisão

**Amazon DynamoDB** no Billing Service, com **tabela única** (single-table design).

### Por que o Billing é o lugar certo para NoSQL

- **Acesso sempre pela chave.** "Orçamento X", "orçamento da OS Y", "pagamentos
  pendentes para consultar". Não há relatório ad hoc nem junção.
- **Documento com histórico aninhado.** Um pagamento acumula tentativas, notificações
  do Mercado Pago e estornos. Em relacional seriam três tabelas com FK; aqui é um item
  com uma lista, ou itens sob a mesma partição.
- **Escrita condicional resolve a idempotência.** `ConditionExpression` garante que um
  pagamento só sai de `PENDENTE` uma vez, mesmo com webhook e consulta periódica
  chegando ao mesmo tempo.

### Por que DynamoDB e não MongoDB

| Critério | DynamoDB | MongoDB |
| --- | --- | --- |
| Gerenciado na AWS | Sim, serverless, sem instância | DocumentDB é caro; Atlas fica fora da AWS do lab |
| Custo no Learner Lab | Por requisição (centavos) | Pod no cluster ou instância paga |
| Provisionamento | Uma tabela em Terraform, segundos | StatefulSet com volume ou cluster gerenciado |
| Ambiente local | `amazon/dynamodb-local` | `mongo` |
| Já usado no projeto | Sim (lock do state do Terraform) | Não |

### Modelo da tabela `oficina-billing-{ambiente}`

| Item | `PK` | `SK` | Atributos principais |
| --- | --- | --- | --- |
| Orçamento | `ORC#<orcamentoId>` | `ORCAMENTO` | `osId`, `cliente`, `itens[]`, `valorTotal`, `status`, `versao` |
| Pagamento | `ORC#<orcamentoId>` | `PAG#<pagamentoId>` | `mpPreferenceId`, `mpPaymentId`, `status`, `valor`, `link`, `expiraEm`, `eventos[]` |
| Outbox | `OUTBOX` | `<timestamp>#<messageId>` | `type`, `payload`, `headers` |
| Mensagem processada | `MSG#<messageId>` | `MSG` | `ttl` (expira em 7 dias) |

| Índice | Chave | Uso |
| --- | --- | --- |
| `GSI1` | `GSI1PK = OS#<osId>` | Orçamento de uma OS (idempotência de `GerarOrcamento`) |
| `GSI2` (esparso) | `GSI2PK = PAG#PENDENTE`, `GSI2SK = expiraEm` | Pagamentos a consultar no Mercado Pago, em ordem de vencimento |

Estado e outbox são gravados juntos com `TransactWriteItems`: é o outbox transacional
do [ADR 010](adr-010-outbox-e-consumidor-idempotente.md), na versão DynamoDB.

## Consequências

- O código de persistência do Billing é diferente do dos outros serviços (SDK do
  DynamoDB, não Panache). A Clean Architecture isola isso no gateway; domínio e use
  cases não sabem qual é o banco.
- No Learner Lab não é possível criar IAM role para o pod (IRSA), então o acesso vem
  da role do nó (`LabRole`). Em produção real, seria IRSA com política restrita à tabela.
- Consultas analíticas sobre pagamentos (faturamento do mês, por exemplo) não cabem
  nesse modelo. Se surgirem, o caminho é exportar para um banco analítico, não forçar o
  DynamoDB.
