# ADR 011 — A Lambda de autenticação consulta o OS Service, não o banco

- **Status:** aceito
- **Data:** 2026-11-15
- **Altera:** o fluxo de autenticação da fase 3 ([RFC 003](https://github.com/GustavoKikey/tech-challenge-15SOAT/blob/main/docs/fase-3/rfc/rfc-003-estrategia-de-autenticacao.md))

---

## Contexto

Na fase 3, a Lambda de autenticação por CPF lê a tabela `clientes` **direto do
PostgreSQL** (`oficina-auth-lambda/src/cliente-repo.js`), com a credencial do banco da
aplicação.

A fase 4 diz, em destaque: **"Nenhum serviço pode acessar diretamente o banco de outro
serviço."** A tabela `clientes` passa a pertencer ao OS Service. A Lambda é um
componente com deploy próprio; lendo o banco do OS Service, ela fere a regra e prende o
schema de `clientes` a dois donos.

## Decisão

O OS Service expõe um endpoint **interno** de consulta, e a Lambda passa a chamá-lo:

```
GET /internal/clientes?documento=52998224725
→ 200 { "id": "…", "nome": "Alice Souza", "ativo": true }
→ 404
```

| Aspecto | Como |
| --- | --- |
| Rede | A Lambda **já roda dentro da VPC** (`vpc_config` em `infra/main.tf`). O OS Service ganha um Service `LoadBalancer` **interno** (`aws-load-balancer-internal: "true"`, `k8s/46-service-interno.yaml`). O security group da Lambda troca a saída 5432 (banco) pela 80 dentro do CIDR da VPC. Não há NAT |
| Autenticação | Header `X-Internal-Token` com segredo compartilhado (SSM → env da Lambda e Secret no cluster). Defesa em profundidade: a rota já é inalcançável de fora da VPC |
| Exposição | A rota fica fora do Swagger. Um Service do Kubernetes não filtra caminho: o LB público também a serve, mas ele é restrito por IP e a rota exige o token — bloquear o caminho no LB pediria um Ingress, sem ganho real aqui |
| Contrato | Mesmos campos que a Lambda lia da tabela (`id`, `nome`, `ativo`), então a regra de emissão do token não muda |

A Lambda perde a credencial do banco e o driver `pg`.

## Alternativas consideradas

| Alternativa | Por que não |
| --- | --- |
| Manter o acesso ao banco e justificar que a Lambda "faz parte" do OS Service | É uma leitura possível, mas contraria o destaque do enunciado e mantém dois donos para o schema |
| Réplica de clientes num banco da Lambda (DynamoDB), alimentada por evento | Resolve o acoplamento, mas cria mais um banco e consistência eventual no login (cliente recém-cadastrado não conseguiria entrar) |
| A Lambda chamar o OS Service pelo load balancer público | O LB público é restrito por IP; abri-lo para a Lambda exigiria NAT (pago) ou abrir para a internet |

## Consequências

- O login por CPF passa a depender de o OS Service estar no ar. É aceitável: sem o OS
  Service, o token não teria uso, porque é o OS Service que serve a área do cliente.
- A latência do login sobe alguns milissegundos (HTTP interno no lugar de uma query).
- O OS Service ganha o primeiro endpoint que não é de usuário final; o padrão
  `/internal/*` fica definido para os próximos.
