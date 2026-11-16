#!/usr/bin/env bash
# =====================================================================
# Implanta o OS Service no EKS
# ---------------------------------------------------------------------
# Chamado pelo cd.yml depois que o CI passou na main, e também rodável à
# mão (credenciais do Learner Lab no ~/.aws). Lê tudo do contrato no SSM:
#
#   plataforma (oficina-infra-k8s) -> eks/cluster-name, RabbitMQ
#   este repo (infra/)             -> os/db/*, jwt/*, os/token-interno
#
# e publica o que só existe depois do deploy:
#
#   /oficina/{env}/os/url-interna  -> LB interno, lido pela Lambda (ADR 011)
#
# Uso:  bash scripts/implantar.sh hom 123456789012.dkr.ecr.us-east-1.amazonaws.com/oficina-os-service:abc1234
# =====================================================================
set -euo pipefail

AMBIENTE="${1:?informe o ambiente (hom|prod)}"
IMAGEM="${2:?informe a imagem (registry/repositorio:tag)}"
REGIAO="${AWS_REGION:-us-east-1}"
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PY="$(command -v python3 || command -v python)"
RESUMO="${GITHUB_STEP_SUMMARY:-/dev/null}"
NS=oficina
APP=oficina-os
CONFIGMAP=oficina-os-config

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"; [ -n "${PF:-}" ] && kill "$PF" 2>/dev/null || true' EXIT

ssm() { aws ssm get-parameter --name "/oficina/${AMBIENTE}/$1" --with-decryption --query Parameter.Value --output text; }
segredo() { aws secretsmanager get-secret-value --secret-id "$1" --query SecretString --output text; }
campo() { "$PY" -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]], end="")' "$1"; }

# secret <nome> <chave>=<arquivo>...  — arquivos, para a senha não ir para a linha de comando.
secret() {
  local nome="$1"; shift
  local args=()
  for par in "$@"; do args+=("--from-file=$par"); done
  kubectl -n "$NS" create secret generic "$nome" "${args[@]}" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  echo "   $nome"
}

# Endereço DNS de um Service LoadBalancer (a AWS leva de segundos a minutos para atribuir).
endereco_lb() {
  local h
  for _ in $(seq 1 60); do
    h=$(kubectl -n "$NS" get svc "$1" -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)
    if [ -n "$h" ]; then echo "$h"; return 0; fi
    sleep 5
  done
  return 1
}

echo "== Cluster"
aws eks update-kubeconfig --region "$REGIAO" --name "$(ssm eks/cluster-name)" >/dev/null
kubectl apply -f "$RAIZ/k8s/00-namespace.yaml" >/dev/null

echo "== Secrets"
segredo "$(ssm os/db/secret-arn)" >"$TMP/db.json"
campo username <"$TMP/db.json" >"$TMP/username"
campo password <"$TMP/db.json" >"$TMP/password"
secret oficina-os-db username="$TMP/username" password="$TMP/password"

segredo "$(ssm jwt/private-key-secret-arn)" | campo privateKey >"$TMP/privateKey.pem"
ssm jwt/public-key >"$TMP/publicKey.pem"
secret oficina-os-jwt publicKey.pem="$TMP/publicKey.pem" privateKey.pem="$TMP/privateKey.pem"

ssm os/token-interno | tr -d '\r\n' >"$TMP/token"
secret oficina-os-interno token="$TMP/token"

# O RabbitMQ (oficina-os-rabbitmq) é da plataforma: scripts/plataforma-k8s.sh.
if ! kubectl -n "$NS" get secret oficina-os-rabbitmq >/dev/null 2>&1; then
  echo "Secret oficina-os-rabbitmq ausente: aplique a plataforma antes (oficina-infra-k8s)." >&2
  exit 1
fi

otel_desligado=true
if [ -n "${NEW_RELIC_LICENSE_KEY:-}" ]; then
  printf '%s' "$NEW_RELIC_LICENSE_KEY" >"$TMP/nr"
  secret oficina-newrelic license-key="$TMP/nr"
  otel_desligado=false
fi

echo "== Rede e configuração"
# A lista de IPs do LB público é de quem está usando o ambiente
# (scripts/liberar-meu-ip.sh): o manifesto a declara fechada, e o deploy
# não deve trancar ninguém do lado de fora.
liberados=$(kubectl -n "$NS" get svc "$APP-publico" -o jsonpath='{.spec.loadBalancerSourceRanges}' 2>/dev/null || true)
kubectl apply -f "$RAIZ/k8s/10-configmap.yaml" -f "$RAIZ/k8s/40-service.yaml" \
              -f "$RAIZ/k8s/45-service-publico.yaml" -f "$RAIZ/k8s/46-service-interno.yaml"
if [ -n "$liberados" ] && [ "$liberados" != '["127.0.0.1/32"]' ]; then
  kubectl -n "$NS" patch svc "$APP-publico" --type merge -p "{\"spec\":{\"loadBalancerSourceRanges\":$liberados}}" >/dev/null
  echo "   IPs liberados no LB público preservados: $liberados"
fi
kubectl -n "$NS" patch configmap "$CONFIGMAP" --type merge -p "$(
  "$PY" -c 'import json,sys; print(json.dumps({"data": {"AMBIENTE": sys.argv[1], "DB_URL": sys.argv[2], "OTEL_SDK_DISABLED": sys.argv[3]}}))' \
    "$AMBIENTE" "$(ssm os/db/jdbc-url)" "$otel_desligado"
)" >/dev/null

echo "== Deployment"
# A imagem entra no manifesto antes do apply (um 'set image' depois faria um
# rollout com a imagem placeholder primeiro). A anotação com o hash da
# configuração reinicia os pods quando só o ConfigMap mudou.
hash_config=$(kubectl -n "$NS" get configmap "$CONFIGMAP" -o jsonpath='{.data}' | sha256sum | cut -c1-16)
sed -e "s|^\(\s*image:\s*\)oficina-os-service:latest|\1${IMAGEM}|" \
    -e "0,/^      annotations:$/s//      annotations:\n        oficina.fiap\/configuracao: \"${hash_config}\"/" \
    "$RAIZ/k8s/30-deployment.yaml" >"$TMP/30-deployment.yaml"
grep -q "image: ${IMAGEM}$" "$TMP/30-deployment.yaml" || { echo "A imagem não entrou no manifesto." >&2; exit 1; }
kubectl apply -f "$TMP/30-deployment.yaml" -f "$RAIZ/k8s/50-hpa.yaml" -f "$RAIZ/k8s/60-pdb.yaml"
kubectl -n "$NS" rollout status "deployment/$APP" --timeout=600s

echo "== Smoke test"
kubectl -n "$NS" port-forward "svc/$APP" 18080:80 >/dev/null 2>&1 &
PF=$!
curl -fsS --retry 10 --retry-delay 3 --retry-all-errors http://localhost:18080/q/health/ready
echo

echo "== Endereços"
interno=$(endereco_lb "$APP-interno") || { echo "LB interno sem endereço após 5 min." >&2; exit 1; }
aws ssm put-parameter --name "/oficina/${AMBIENTE}/os/url-interna" --type String --overwrite \
  --value "http://${interno}" >/dev/null
publico=$(endereco_lb "$APP-publico" || echo "(ainda provisionando)")
echo "   público: http://${publico}  (Swagger em /swagger)"
echo "   interno: http://${interno}  -> /oficina/${AMBIENTE}/os/url-interna"

{
  echo "## OS Service — \`${AMBIENTE}\`"
  echo
  echo "| | |"
  echo "| --- | --- |"
  echo "| Imagem | \`${IMAGEM}\` |"
  echo "| LB público (restrito por IP) | http://${publico}/swagger |"
  echo "| LB interno (Lambda) | http://${interno} |"
  echo
  echo '```'
  kubectl -n "$NS" get deploy,pods,svc,hpa -l app.kubernetes.io/name="$APP" 2>/dev/null || true
  echo '```'
} >>"$RESUMO"
