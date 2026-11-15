# ADR 008 — RabbitMQ como mensageria

- **Status:** aceito
- **Data:** 2026-11-15

---

## Contexto

A saga troca comandos e eventos entre três serviços. O enunciado aceita RabbitMQ,
Kafka, SQS "etc.". O ambiente é o AWS Academy Learner Lab: crédito limitado,
credenciais de 4 h, e o ambiente é criado e destruído a cada sessão. A demonstração
precisa mostrar o **rastreamento do fluxo distribuído**.

## Decisão

**RabbitMQ**, rodando **dentro do cluster EKS** (StatefulSet com a imagem oficial
`rabbitmq:management` e volume persistente), consumido pelos serviços com **SmallRye
Reactive Messaging** (`quarkus-messaging-rabbitmq`).

| Critério | RabbitMQ (no cluster) | SQS + SNS | Kafka |
| --- | --- | --- | --- |
| Modelo | Exchanges e roteamento, ideal para comando (1 destino) e evento (N destinos) | SNS para eventos, SQS para comandos: duas peças | Log particionado; feito para streaming, não para comando |
| Rastreamento | O conector propaga `traceparent` **automaticamente** | Atributos de mensagem propagados manualmente | Automático, mas com custo de operação alto |
| Ambiente local | Um container no compose | LocalStack | Kafka + controlador; pesado para a máquina |
| Custo no Learner Lab | Um pod no cluster que já existe | Por requisição (baixo) | MSK é caro; no cluster, pesado |
| Visibilidade na demo | Painel de gerenciamento mostra filas, taxa e DLQ | Console AWS | Precisa de ferramenta extra |
| Operação | Nós operamos (versão, volume) | Gerenciado | Nós operamos, e é complexo |

O ponto decisivo é o **trace distribuído**. Com o SmallRye RabbitMQ, a mesma OS aparece
como um único trace atravessando os três serviços no New Relic, sem código de
propagação. Com SQS, essa costura seria manual e frágil.

## Alternativas consideradas

- **Amazon MQ (RabbitMQ gerenciado).** É a mesma tecnologia sem operar, mas é cobrado
  por hora e leva cerca de 20 minutos para provisionar. Isso pesa muito num ambiente que
  nasce e morre a cada sessão de 4 h.
- **SQS/SNS.** É a melhor opção gerenciada, e fica como plano B documentado: as portas
  de mensageria dos serviços isolam o conector, e trocar o broker muda a borda, não o
  domínio.

## Consequências

- Somos responsáveis pelo RabbitMQ no cluster. Para o escopo, uma réplica com volume
  persistente basta. Em produção real, seria um cluster de três nós com quorum queues
  (já usadas aqui, para que a mudança seja só de réplicas).
- As credenciais do broker são um Secret por serviço, com usuário e permissões
  (`configure/write/read`) restritos às filas e exchanges que aquele serviço usa.
- O painel de gerenciamento **não** é exposto publicamente; é acessado por
  `kubectl port-forward`.
