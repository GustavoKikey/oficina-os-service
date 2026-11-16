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

Um orçamento por OS, e uma cobrança por orçamento: o agregado inteiro (itens, aprovação,
pagamento com histórico) é **um documento num item**, chaveado pela OS.

| Item | `PK` | `SK` | Atributos principais |
| --- | --- | --- | --- |
| Orçamento | `OS#<osId>` | `ORCAMENTO` | `orcamentoId`, `status`, `versao`, `dados` (o agregado em JSON, com o pagamento aninhado) |
| Outbox | `OUTBOX` | `<epoch>#<messageId>` | `tipo`, `routingKey`, `envelope`, `headers` |

| Índice | Chave | Uso |
| --- | --- | --- |
| `GSI1` | `GSI1PK = ORC#<orcamentoId>` | Achar o orçamento pelo `external_reference` do Mercado Pago (webhook) |
| `GSI2` (esparso) | `GSI2PK = PAG#PENDENTE`, `GSI2SK = expiraEm` | Pagamentos a consultar, em ordem de vencimento — só os pendentes têm esses atributos |

- **Chave pela OS** dá leitura consistente aos comandos da saga (que sempre trazem o
  `osId`) e idempotência de graça: a criação usa `attribute_not_exists(PK)`.
- **Concorrência otimista**: toda gravação exige `versao = <lida>` e grava `versao + 1`;
  webhook, consulta periódica e comando não sobrescrevem um ao outro em silêncio.
- **Deduplicação de comandos** é pela chave de negócio (ADR 010), não por um item de
  inbox: comando repetido precisa ser respondido de novo, não descartado.

Estado e outbox são gravados juntos com `TransactWriteItems`: é o outbox transacional
do [ADR 010](adr-010-outbox-e-consumidor-idempotente.md), na versão DynamoDB.

## Consequências

- O código de persistência do Billing é diferente do dos outros serviços (SDK do
  DynamoDB, não Panache). A Clean Architecture isola isso no gateway; domínio e use
  cases não sabem qual é o banco.
- No Learner Lab não é possível criar IAM role para o pod (IRSA), e a role dos nós não
  garante permissão no DynamoDB. O deploy injeta a credencial temporária da sessão do lab
  num Secret do Billing (ADR 012 §4). Em produção real, seria IRSA com política restrita à
  tabela.
- A tabela é criada pelo Terraform do próprio Billing (`infra/main.tf`), idêntica à que o
  `TabelaBilling.java` cria no DynamoDB Local — inclusive o `GSI1SK`, que este ADR não
  listava.
- Consultas analíticas sobre pagamentos (faturamento do mês, por exemplo) não cabem
  nesse modelo. Se surgirem, o caminho é exportar para um banco analítico, não forçar o
  DynamoDB.
