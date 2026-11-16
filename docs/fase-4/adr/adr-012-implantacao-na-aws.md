# ADR 012 — Implantação na AWS (fase 4)

- **Status:** aceito
- **Data:** 2026-11-16

---

## Contexto

O enunciado pede, para cada microsserviço, repositório, infraestrutura e banco próprios,
pipeline independente com **deploy automatizado em Kubernetes**, e mensageria no ambiente.
O ambiente é o **AWS Academy Learner Lab** da fase 3: credenciais que vencem a cada 4 horas,
sem criar IAM roles (nem IRSA), US$ 50 de crédito para a fase, e a regra de destruir tudo ao
fim de cada sessão.

## Decisões

### 1. Cada serviço provisiona o que é só dele; a plataforma, o que é compartilhado

| Repositório | Terraform | Publica no SSM (`/oficina/{env}/...`) |
| --- | --- | --- |
| `oficina-infra-k8s` | Rede (SGs sobre a VPC default), EKS (3 × t3.medium), credenciais do RabbitMQ | `rede/*`, `eks/*`, `mensageria/*` |
| `oficina-os-service` | RDS PostgreSQL, par de chaves do JWT, token da rota interna | `os/db/*`, `jwt/*`, `os/token-interno` |
| `oficina-execucao-service` | RDS PostgreSQL | `execucao/db/*` |
| `oficina-billing-service` | Tabela DynamoDB (a mesma do `TabelaBilling.java`) | `billing/tabela` |
| `oficina-auth-lambda` | Lambda + API Gateway | `apigw/*` |

O contrato entre repositórios continua sendo o **SSM Parameter Store**, como na fase 3
(ADR 004 da fase 3): nenhum repositório lê o state de outro. Cada serviço só conhece a URL e a
senha do próprio banco.

### 2. Infra sob comando, deploy contínuo

- **Terraform** valida em todo PR (fmt, validate, tflint) e **aplica sob comando** (Run
  workflow ou `scripts/aplicar.sh`). Um apply por merge, com três serviços e credenciais de
  4 horas, gastaria crédito à toa e falharia sempre que a sessão estivesse fechada.
- **Deploy (CD)** é contínuo: quando o CI termina **verde** num commit da `main` (build,
  testes unitários, de integração e BDD, gate de cobertura, quality gate do Sonar), o
  `cd.yml` constrói a imagem, envia ao ECR e implanta no ambiente `hom`. `prod` é promoção
  manual.
- Com a sessão do lab fechada ou o ambiente desprovisionado, o deploy é **pulado com aviso**,
  não falha. Ambiente no ar e deploy quebrado continua sendo falha. (Mesmo critério do CD da
  fase 3.)

### 3. Exposição

| Service | Tipo | Quem acessa |
| --- | --- | --- |
| `oficina-<serviço>-publico` (os três) | LoadBalancer público | Nasce fechado (`127.0.0.1/32`); `scripts/liberar-meu-ip.sh` abre para o IP de quem vai usar. O deploy preserva a lista. |
| `oficina-os-interno` | LoadBalancer **interno** | Só a VPC: a Lambda (ADR 011), com `X-Internal-Token` |
| `rabbitmq` | ClusterIP | Só os pods; o painel abre por `port-forward` |

O webhook do Mercado Pago continua sem rota pública: a consulta periódica cobre (RFC 004 §4).

### 4. Identidade

- **Chaves do JWT geradas pelo Terraform** (`tls_private_key`) a cada ambiente. A privada vai
  para o Secrets Manager e só o OS Service (tokens de funcionário) e a Lambda (tokens de
  cliente) a leem; a pública vai para o SSM e os três serviços validam com ela. Substitui a
  chave que a fase 3 deixou versionada no repositório, considerada comprometida.
- **Um usuário do RabbitMQ por serviço**, com senha gerada pelo Terraform da plataforma.
- **Billing → DynamoDB:** no Learner Lab não há IRSA, e a role dos nós não garante permissão
  no DynamoDB. O deploy injeta a credencial temporária da sessão do lab num Secret
  (`oficina-billing-aws`), que vence com a sessão e é renovado no próximo deploy. Numa conta
  AWS comum, seria IRSA com política restrita à tabela (ADR 009).

### 5. RabbitMQ sem volume persistente

O ADR 008 previa um StatefulSet com volume persistente. No EKS, um PVC exige o driver EBS CSI
com uma permissão de IAM que a role dos nós do lab não garante — e o ambiente é recriado a
cada sessão. O RabbitMQ roda com `emptyDir`: num reinício do broker, perde-se o que estava na
fila. O lado de quem publica está protegido pelo outbox (ADR 010), e a saga reenvia comandos
sem resposta (saga.md §6). Em produção: três nós com volume EBS, ou Amazon MQ.

### 6. Operação

Os scripts da fase 3 foram estendidos para os cinco repositórios e vivem no
`oficina-infra-k8s/scripts/`: `diagnostico-lab`, `bootstrap`, `renovar-secrets`, `aplicar`
(plataforma → bancos → CD dos serviços → Lambda), `liberar-meu-ip`, `observabilidade`
(agente do New Relic), `newrelic-saga.py` (painel e alertas da saga) e `destruir` (ordem
inversa, apagando antes os load balancers do Kubernetes, que senão ficariam órfãos travando a
destruição da rede).

## Custo estimado por hora de lab

| Recurso | US$/h |
| --- | --- |
| EKS (control plane) | 0,10 |
| 3 × t3.medium | 0,125 |
| 2 × RDS db.t3.micro | 0,036 |
| 4 load balancers (3 públicos, 1 interno) | ~0,10 |
| DynamoDB, Lambda, API Gateway, SSM | ~0 (por requisição) |
| **Total** | **~0,36** — cerca de US$ 1,50 por sessão de 4 horas |

## Consequências

- O ambiente sobe com um comando (`scripts/aplicar.sh hom`) e é destruído com outro.
- O deploy depende de secrets renovados a cada sessão (`scripts/renovar-secrets.sh`): é o
  preço de não ter OIDC no Learner Lab.
- Quatro load balancers são o maior custo variável. Um Ingress único economizaria três, mas
  acoplaria a exposição dos serviços num recurso compartilhado; com o ambiente vivo só durante
  a sessão, a independência vale mais.
