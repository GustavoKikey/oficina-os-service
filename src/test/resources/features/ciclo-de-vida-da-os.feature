# language: pt
@bdd @saga
Funcionalidade: Ciclo de vida da ordem de serviço, coordenado pela saga
  Como oficina mecânica
  Quero que abertura, diagnóstico, orçamento, pagamento e reparo de uma OS sejam
  coordenados entre os serviços de Execução e de Billing
  Para que o cliente só pague pelo que aprovou e nunca fique com dinheiro preso
  quando o reparo não puder ser feito

  O OS Service roda de verdade nestes cenários: API, PostgreSQL, outbox, inbox e a saga.
  Execução e Billing aparecem pelas mensagens que publicariam no RabbitMQ, e os comandos
  que a saga envia a eles são conferidos na saída.

  Contexto:
    Dado que a cliente "Ana Souza" chegou à oficina com o veículo "Fiat Argo 2022"

  Cenário: Do balcão à entrega, com aprovação e pagamento pela própria cliente
    Quando o atendente abre a ordem de serviço com o relato "Barulho na suspensão dianteira"
    Então a OS fica "RECEBIDA"
    E a Execução recebe o comando "AbrirOrdemExecucao" com a placa do veículo
    Quando a Execução coloca a OS na fila e o mecânico inicia o diagnóstico
    Então a OS fica "EM_DIAGNOSTICO"
    Quando o mecânico conclui o diagnóstico com os itens:
      | tipo    | descrição         | quantidade | valor unitário |
      | SERVICO | Alinhamento       | 1          | 120,00         |
      | PECA    | Pastilha de freio | 2          | 95,00          |
    Então o Billing recebe o comando "GerarOrcamento" com 2 itens
    Quando o Billing gera o orçamento
    Então a OS fica "AGUARDANDO_APROVACAO" com orçamento de R$ 310,00
    Quando a cliente aprova o orçamento pela área do cliente
    Então a OS fica "AGUARDANDO_PAGAMENTO"
    E o Billing recebe o comando "SolicitarPagamento" aprovado por "CLIENTE"
    Quando o Billing cria a cobrança no Mercado Pago
    Então a cliente vê o link de pagamento ao consultar a OS
    Quando o Mercado Pago confirma o pagamento
    Então a OS fica "EM_EXECUCAO"
    E a Execução recebe o comando "LiberarParaReparo"
    Quando a Execução libera o reparo e o mecânico o executa até o fim
    Então a OS fica "FINALIZADA"
    Quando o atendente entrega o veículo
    Então a OS fica "ENTREGUE"
    E a saga termina "CONCLUIDA"
    E o histórico da OS mostra "RECEBIDA, EM_DIAGNOSTICO, AGUARDANDO_APROVACAO, AGUARDANDO_PAGAMENTO, EM_EXECUCAO, FINALIZADA, ENTREGUE"

  Esquema do Cenário: Falha durante <etapa> (<motivo>): a saga desfaz só o que já tinha sido feito
    Dado que a OS avançou até <etapa>
    Quando <falha>
    Então a OS fica "CANCELADA" com o motivo "<motivo>"
    E a saga dispara somente as compensações "<compensações>"
    Quando os serviços confirmam as compensações
    Então a saga termina "COMPENSADA"

    Exemplos:
      | etapa                  | falha                                       | motivo               | compensações                             |
      | o pedido de orçamento  | o Billing não consegue gerar o orçamento    | ORCAMENTO_NAO_GERADO | CancelarOrdemExecucao                    |
      | a aprovação            | a cliente recusa o orçamento                | ORCAMENTO_RECUSADO   | CancelarOrdemExecucao, CancelarOrcamento |
      | o pagamento            | o Mercado Pago recusa o pagamento           | PAGAMENTO_RECUSADO   | CancelarOrdemExecucao, CancelarOrcamento |
      | o pagamento            | o link de pagamento vence sem ser pago      | PAGAMENTO_EXPIRADO   | CancelarOrdemExecucao, CancelarOrcamento |
      | a liberação do reparo  | a Execução não consegue liberar o reparo    | REPARO_NAO_LIBERADO  | CancelarOrdemExecucao, EstornarPagamento |
