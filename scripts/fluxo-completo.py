#!/usr/bin/env python3
"""
Percorre a saga de ponta a ponta — ensaio e roteiro do vídeo.

  Cenário feliz:
    abertura → diagnóstico (peças reservadas) → orçamento → aprovação → pagamento
    → reparo → finalização → entrega                         (saga CONCLUIDA)

  Cenário recusa (falha antes da cobrança):
    abertura → diagnóstico → orçamento → aprovação → pagamento RECUSADO
    → Execução devolve as peças e o Billing cancela o orçamento (saga COMPENSADA)

  Cenário estorno (falha depois da cobrança):
    OS B reserva a peça e a reserva vence; OS C reserva, paga e consome a peça;
    B paga depois → a Execução não libera o reparo → a saga estorna o pagamento
    no Billing e cancela a ordem na Execução                  (saga COMPENSADA)

Onde roda:
  local (padrão)  docker compose do oficina-os-service (localhost:8080/8081/8082);
                  o estorno precisa de RESERVA_VALIDADE=PT20S no compose.
  --nuvem [env]   ambiente na AWS: descobre os load balancers públicos (kubectl) e
                  o API Gateway (SSM). A cliente entra com o CPF pela Lambda e
                  aprova o orçamento pela área do cliente. Abra antes os LBs para o
                  seu IP (scripts/liberar-meu-ip.sh do oficina-infra-k8s).

Opções:
  --mercadopago   pagamento de verdade no Mercado Pago (contas de teste): o roteiro
                  mostra o link e espera você pagar (titular APRO aprova, OTHE recusa).
                  Sem a opção, o checkout é o simulado do Billing.
  --pausar        para nos momentos importantes (Enter continua) — para gravar.

Uso:
  python scripts/fluxo-completo.py                                   # local, feliz + estorno
  python scripts/fluxo-completo.py --nuvem --cenario feliz --pausar --mercadopago
  python scripts/fluxo-completo.py --nuvem --cenario recusa --pausar --mercadopago
"""
import argparse
import json
import random
import string
import subprocess
import sys
import time
import urllib.error
import urllib.request

ESPERA_PAGAMENTO_S = 15 * 60
VALIDADE_RESERVA_S = 20

for fluxo in (sys.stdout, sys.stderr):
    try:
        fluxo.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass


def chamar(metodo, url, token=None, corpo=None, esperado=(200, 201)):
    dados = None if corpo is None else json.dumps(corpo).encode()
    req = urllib.request.Request(url, data=dados, method=metodo)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            texto = resp.read().decode()
            status = resp.status
    except urllib.error.HTTPError as e:
        texto, status = e.read().decode(), e.code
    if status not in esperado:
        raise RuntimeError(f"{metodo} {url} → {status}: {texto[:300]}")
    try:
        return json.loads(texto) if texto else None
    except json.JSONDecodeError:
        return texto


def esperar(descricao, condicao, limite=120):
    inicio = time.time()
    while time.time() - inicio < limite:
        try:
            valor = condicao()
            if valor:
                print(f"   ✓ {descricao} ({time.time() - inicio:.0f}s)", flush=True)
                return valor
        except (RuntimeError, urllib.error.URLError, TimeoutError):
            pass
        time.sleep(1)
    raise TimeoutError(f"não aconteceu em {limite}s: {descricao}")


def cpf_valido():
    base = [random.randint(0, 9) for _ in range(9)]
    for peso in (10, 11):
        soma = sum(d * (peso - i) for i, d in enumerate(base))
        resto = soma * 10 % 11
        base.append(0 if resto == 10 else resto)
    return "".join(map(str, base))


def placa():
    return ("".join(random.choices(string.ascii_uppercase, k=3)) + str(random.randint(0, 9))
            + random.choice(string.ascii_uppercase) + f"{random.randint(0, 99):02d}")


def comando(*args):
    return subprocess.run(args, capture_output=True, text=True, check=True).stdout.strip()


def enderecos_na_nuvem(ambiente):
    def lb(servico):
        host = comando("kubectl", "-n", "oficina", "get", "svc", servico,
                       "-o", "jsonpath={.status.loadBalancer.ingress[0].hostname}")
        if not host:
            raise RuntimeError(f"o LB {servico} ainda não tem endereço")
        return f"http://{host}"
    gateway = comando("aws", "ssm", "get-parameter", "--name", f"/oficina/{ambiente}/apigw/invoke-url",
                      "--query", "Parameter.Value", "--output", "text")
    return lb("oficina-os-publico"), lb("oficina-execucao-publico"), lb("oficina-billing-publico"), gateway


class Oficina:
    def __init__(self, os_url, execucao_url, billing_url, gateway=None, mercadopago=False, pausar=False):
        self.os_url, self.execucao_url, self.billing_url = os_url, execucao_url, billing_url
        self.gateway = gateway
        self.mercadopago = mercadopago
        self.pausar = pausar
        self.cpfs = {}
        self.token = chamar("POST", f"{os_url}/auth/login",
                            corpo={"username": "admin", "password": "admin123"})["accessToken"]

    def pausa(self, o_que_mostrar):
        if self.pausar:
            input(f"\n   ⏸  {o_que_mostrar}\n      (Enter para continuar) ")

    def os(self, os_id):
        return chamar("GET", f"{self.os_url}/ordens-servico/{os_id}", self.token)

    def status(self, os_id):
        return self.os(os_id)["status"]

    def saga(self, os_id):
        return chamar("GET", f"{self.os_url}/admin/sagas/{os_id}", self.token)

    def abrir(self, nome):
        cpf = cpf_valido()
        os_id = chamar("POST", f"{self.os_url}/ordens-servico", self.token, {
            "cliente": {"documento": cpf, "nome": nome, "email": f"{nome.lower()}@exemplo.com"},
            "veiculo": {"placa": placa(), "marca": "Fiat", "modelo": "Argo", "ano": 2022},
            "relatoCliente": "Barulho na suspensão dianteira"})["id"]
        self.cpfs[os_id] = cpf
        print(f"   OS de {nome}: {os_id}", flush=True)
        self.pausa(f"OS ABERTA — no Swagger do OS: GET /ordens-servico/{os_id}  (status RECEBIDA)")
        return os_id

    def diagnosticar(self, os_id, servico, peca, quantidade):
        esperar("OS chegou à fila da Execução",
                lambda: chamar("GET", f"{self.execucao_url}/ordens-execucao/{os_id}", self.token))
        chamar("POST", f"{self.execucao_url}/ordens-execucao/{os_id}/diagnostico/iniciar", self.token)
        chamar("POST", f"{self.execucao_url}/ordens-execucao/{os_id}/itens/servicos", self.token, {"servicoId": servico})
        chamar("POST", f"{self.execucao_url}/ordens-execucao/{os_id}/itens/pecas", self.token,
               {"pecaId": peca, "quantidade": quantidade})
        chamar("POST", f"{self.execucao_url}/ordens-execucao/{os_id}/diagnostico/concluir", self.token)
        print(f"   diagnóstico concluído pelo mecânico: {quantidade} peça(s) reservada(s)", flush=True)
        self.pausa("DIAGNÓSTICO — a Execução avisou o OS, que pediu o orçamento ao Billing")
        esperar("orçamento gerado no Billing → AGUARDANDO_APROVACAO",
                lambda: self.status(os_id) == "AGUARDANDO_APROVACAO")
        orcamento = self.os(os_id)["orcamento"]
        print(f"   orçamento: R$ {orcamento['valorTotal']:.2f}", flush=True)
        self.pausa(f"ORÇAMENTO — GET /ordens-servico/{os_id}  (AGUARDANDO_APROVACAO, valor do orçamento)")

    def aprovar(self, os_id):
        if self.gateway:
            # A cliente entra com o CPF: API Gateway → Lambda → OS Service (rota interna).
            sessao = chamar("POST", f"{self.gateway}/auth/cliente", corpo={"cpf": self.cpfs[os_id]})
            print(f"   cliente {sessao['cliente']['nome']} autenticada pela Lambda (JWT de "
                  f"{sessao['expiresIn'] // 60} min)", flush=True)
            chamar("POST", f"{self.os_url}/cliente/ordens-servico/{os_id}/orcamento/decisao",
                   sessao["accessToken"], {"aprovado": True})
            print("   orçamento aprovado pela própria cliente, na área do cliente", flush=True)
        else:
            chamar("POST", f"{self.os_url}/ordens-servico/{os_id}/orcamento/aprovar", self.token)
            print("   orçamento aprovado pelo atendente", flush=True)
        self.pausa("APROVAÇÃO — a saga pediu a cobrança ao Billing")

    def pagar(self, os_id, aprovar=True):
        link = esperar("link de pagamento disponível",
                       lambda: chamar("GET", f"{self.os_url}/ordens-servico/{os_id}/status", self.token)["linkPagamento"])
        print(f"\n   LINK DE PAGAMENTO: {link}\n", flush=True)
        if self.mercadopago:
            titular = "APRO" if aprovar else "OTHE"
            print("   ➜ PAGUE AGORA numa janela anônima, logado com o comprador de teste:")
            print(f"     cartão de teste com titular {titular} (CPF 12345678909)", flush=True)
            esperar("o Billing recebeu a resposta do Mercado Pago",
                    lambda: self.saga(os_id)["estado"] != "PAGAMENTO_SOLICITADO", limite=ESPERA_PAGAMENTO_S)
            return
        orcamento = self.os(os_id)["orcamento"]["orcamentoId"]
        resultado = "aprovado" if aprovar else "recusado"
        chamar("POST", f"{self.billing_url}/simulador/pagamentos/{orcamento}?resultado={resultado}")
        print(f"   pagamento {resultado} no checkout (simulado)", flush=True)

    def reparo_liberado(self, os_id):
        # A OS já mostra EM_EXECUCAO enquanto a saga espera a Execução (REPARO_SOLICITADO);
        # o reparo só pode começar depois do ReparoLiberado.
        esperar("pagamento confirmado → Execução baixou as peças → saga EM_REPARO",
                lambda: self.saga(os_id)["estado"] == "EM_REPARO")

    def reparar(self, os_id):
        self.reparo_liberado(os_id)
        chamar("POST", f"{self.execucao_url}/ordens-execucao/{os_id}/reparo/iniciar", self.token)
        chamar("POST", f"{self.execucao_url}/ordens-execucao/{os_id}/reparo/finalizar", self.token)
        esperar("Execução comunicou a finalização → FINALIZADA", lambda: self.status(os_id) == "FINALIZADA")
        self.pausa("REPARO — o mecânico iniciou e finalizou; a Execução avisou o OS")

    def mensagens(self, os_id):
        return [f"{'→' if m['direcao'] == 'ENVIADA' else '←'} {m['tipo']}" for m in self.saga(os_id)["mensagens"]]


def preparar_estoque(of, quantidade, descricao="Pastilha de freio"):
    peca = chamar("POST", f"{of.execucao_url}/pecas", of.token,
                  {"descricao": descricao, "valorUnitario": 95.00, "quantidadeInicial": quantidade})["id"]
    servico = chamar("POST", f"{of.execucao_url}/servicos", of.token,
                     {"descricao": "Alinhamento", "valorBase": 120.00})["id"]
    return peca, servico


def cenario_feliz(of):
    print("\n== Cenário feliz — do balcão à entrega", flush=True)
    peca, servico = preparar_estoque(of, 4)
    a = of.abrir("Alice")
    of.diagnosticar(a, servico, peca, 2)
    of.aprovar(a)
    of.pagar(a)
    of.reparar(a)
    chamar("POST", f"{of.os_url}/ordens-servico/{a}/entregar", of.token)
    saga = of.saga(a)
    assert saga["estado"] == "CONCLUIDA", saga
    historico = [t["para"] for t in chamar("GET", f"{of.os_url}/ordens-servico/{a}/historico", of.token)["transicoes"]]
    print(f"   histórico: {' → '.join(historico)}")
    print(f"   saga: {saga['estado']} — {len(saga['mensagens'])} mensagens trocadas", flush=True)
    of.pausa(f"ENTREGA — GET /ordens-servico/{a}/historico  e  GET /admin/sagas/{a}")


def cenario_recusa(of):
    print("\n== Cenário recusa — pagamento recusado, a saga desfaz", flush=True)
    peca, servico = preparar_estoque(of, 4)
    b = of.abrir("Bruna")
    of.diagnosticar(b, servico, peca, 2)
    of.aprovar(b)
    of.pagar(b, aprovar=False)
    esperar("pagamento recusado → OS CANCELADA", lambda: of.status(b) == "CANCELADA")
    esperar("Execução devolveu as peças e Billing cancelou o orçamento → saga COMPENSADA",
            lambda: of.saga(b)["estado"] == "COMPENSADA")
    print(f"   motivo: {of.os(b)['motivoCancelamento']}")
    print(f"   mensagens da saga: {'  '.join(of.mensagens(b))}", flush=True)
    of.pausa(f"COMPENSAÇÃO — GET /admin/sagas/{b}  (estado COMPENSADA e as mensagens)")


def cenario_estorno(of, nuvem):
    print("\n== Cenário estorno — reserva vencida, peça consumida por outra OS", flush=True)
    if nuvem:
        print("   (na nuvem, precisa de reserva curta: "
              "kubectl -n oficina set env deployment/oficina-execucao RESERVA_VALIDADE=PT20S)", flush=True)
    # Saldo para uma OS só: quem pagar primeiro leva; o outro pagamento é estornado.
    peca, servico = preparar_estoque(of, 2, "Amortecedor dianteiro")
    b = of.abrir("Bruno")
    of.diagnosticar(b, servico, peca, 2)
    print(f"   esperando a reserva de Bruno vencer ({VALIDADE_RESERVA_S}s)...", flush=True)
    time.sleep(VALIDADE_RESERVA_S + 3)
    c = of.abrir("Carla")
    of.diagnosticar(c, servico, peca, 2)
    of.aprovar(c)
    of.pagar(c)
    of.reparo_liberado(c)
    of.aprovar(b)
    of.pagar(b)
    esperar("Execução recusou liberar o reparo de Bruno → OS CANCELADA", lambda: of.status(b) == "CANCELADA")
    esperar("estorno confirmado e reservas liberadas → saga COMPENSADA",
            lambda: of.saga(b)["estado"] == "COMPENSADA")
    orc_b = chamar("GET", f"{of.billing_url}/orcamentos/os/{b}", of.token)
    print(f"   OS de Bruno: {of.os(b)['motivoCancelamento']} | orçamento no Billing: {orc_b['status']} "
          f"(R$ {orc_b['valorTotal']} devolvidos)")
    print(f"   mensagens da saga: {'  '.join(of.mensagens(b))}", flush=True)


def main():
    args = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    args.add_argument("--nuvem", nargs="?", const="hom", metavar="AMBIENTE", help="ambiente na AWS (padrão: hom)")
    args.add_argument("--mercadopago", action="store_true", help="pagamentos reais no Mercado Pago (teste)")
    args.add_argument("--cenario", choices=["feliz", "recusa", "estorno", "todos"], default="todos")
    args.add_argument("--pausar", action="store_true", help="para nos momentos importantes (Enter continua)")
    opcoes = args.parse_args()

    if opcoes.nuvem:
        os_url, execucao_url, billing_url, gateway = enderecos_na_nuvem(opcoes.nuvem)
    else:
        os_url, execucao_url, billing_url, gateway = (
            "http://localhost:8080", "http://localhost:8081", "http://localhost:8082", None)
    print(f"OS {os_url}\nExecução {execucao_url}\nBilling {billing_url}"
          + (f"\nAPI Gateway {gateway}" if gateway else ""), flush=True)

    of = Oficina(os_url, execucao_url, billing_url, gateway, opcoes.mercadopago, opcoes.pausar)
    if opcoes.cenario in ("feliz", "todos"):
        cenario_feliz(of)
    if opcoes.cenario == "recusa":
        cenario_recusa(of)
    if opcoes.cenario in ("estorno", "todos"):
        cenario_estorno(of, bool(opcoes.nuvem))
    print(f"\nTudo certo. Swagger: {os_url}/swagger", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, TimeoutError, AssertionError, subprocess.CalledProcessError) as erro:
        print(f"\n✗ FALHOU: {erro}", file=sys.stderr)
        sys.exit(1)
