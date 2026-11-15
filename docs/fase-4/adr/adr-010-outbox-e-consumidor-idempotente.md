# ADR 010 — Outbox transacional e consumidor idempotente

- **Status:** aceito
- **Data:** 2026-11-15

---

## Contexto

Uma saga só é tão confiável quanto a entrega das suas mensagens. Dois problemas
clássicos aparecem assim que existe banco e fila no mesmo fluxo:

1. **Escrita dupla.** O serviço grava o novo estado no banco e depois publica a
   mensagem. Se cair entre as duas operações, o estado mudou e ninguém ficou sabendo: a
   saga trava para sempre. Publicar antes de gravar tem o problema inverso.
2. **Entrega pelo menos uma vez.** O RabbitMQ (como qualquer broker) pode entregar a
   mesma mensagem duas vezes: depois de uma reconexão, de um `ack` perdido ou de um
   reenvio por timeout. Um consumidor ingênuo cobraria duas vezes ou baixaria a peça
   duas vezes.

## Decisão

### 1. Outbox transacional nos três serviços

A mensagem a publicar é gravada **na mesma transação** do estado, numa tabela (ou
item) de outbox. Um **relay** separado lê o outbox e publica.

| Serviço | Outbox | Relay |
| --- | --- | --- |
| OS, Execução (PostgreSQL) | Tabela `outbox` (`id`, `type`, `payload jsonb`, `headers jsonb`, `criado_em`, `publicado_em`) | `@Scheduled` a cada 500 ms: `SELECT … WHERE publicado_em IS NULL ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED`, publica com *publisher confirms* e marca como publicado |
| Billing (DynamoDB) | Item `PK=OUTBOX` gravado com `TransactWriteItems` junto do estado | Mesmo agendamento: lê a partição `OUTBOX`, publica e apaga |

- `FOR UPDATE SKIP LOCKED` permite várias réplicas rodando o relay sem publicar a mesma
  linha duas vezes.
- O **`traceparent` é gravado no outbox** no momento da transação. O relay roda em
  outra thread, sem o contexto da requisição original; sem isso, o trace se partiria
  em dois justamente na fila.
- Registros publicados são apagados depois de 7 dias.

### 2. Consumidor idempotente

| Tipo de mensagem | Proteção |
| --- | --- |
| **Evento** recebido pelo orquestrador | **Inbox**: `mensagens_processadas(message_id PK)`, gravado na mesma transação do efeito. `messageId` repetido → `ack` sem efeito |
| **Comando** recebido por participante | **Idempotência pela chave de negócio** (`osId` + tipo do comando). Comando já executado → **reenvia a resposta** com base no estado atual |

Por que comandos precisam de mais que o inbox: quando o orquestrador reenvia um
comando por timeout, é porque **a resposta não chegou**. Se o participante apenas
ignorasse a duplicata, a resposta nunca chegaria e a saga ficaria presa. Responder de
novo é o que destrava.

### 3. Retentativa e DLQ

Falha técnica (banco fora do ar, Mercado Pago com erro 5xx) leva a 3 retentativas com
backoff no próprio consumidor e, depois, a `nack` sem requeue, que manda a mensagem
para a DLQ. Erro de **negócio** não é exceção, é evento de resposta
(`OrcamentoNaoGerado`, `ReparoNaoLiberado`), e nunca vai para a DLQ.

## Alternativas consideradas

- **Publicar depois do commit, sem outbox.** É o mais simples, e perde mensagem na
  primeira queda no momento errado. Inaceitável num fluxo com dinheiro.
- **Debezium (CDC) lendo o log do PostgreSQL.** Elimina o relay por polling, mas exige
  Kafka Connect no cluster. Acrescenta infraestrutura demais para o volume da oficina.
- **Transação distribuída (XA, 2PC).** O RabbitMQ não participa de XA, e o DynamoDB
  também não. Mesmo que participassem, o acoplamento de disponibilidade é exatamente o
  que a separação em serviços quer evitar.

## Consequências

- Latência extra de até ~500 ms por passo, por causa do polling do relay. É irrelevante
  num fluxo que dura dias.
- Cada serviço carrega tabelas de infraestrutura (`outbox`, `mensagens_processadas`)
  no próprio banco, criadas pelas migrations como qualquer outra tabela.
- O comportamento é testável: os testes de integração derrubam a publicação no meio da
  transação e verificam que a mensagem sai depois.
