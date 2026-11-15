#!/usr/bin/env python3
"""
Percorre a saga de ponta a ponta no ambiente local (docker compose) — ensaio do vídeo.

  Cenário 1 — fluxo feliz:
    abertura → diagnóstico (peças reservadas) → orçamento → aprovação → pagamento
    → reparo → finalização → entrega                         (saga CONCLUIDA)

  Cenário 2 — compensação com estorno:
    OS B reserva a peça e a reserva vence; OS C reserva, paga e consome a peça;
    B paga depois → a Execução não libera o reparo → a saga estorna o pagamento
    no Billing e cancela a ordem na Execução                  (saga COMPENSADA)

Pré-requisito (na pasta oficina-os-service), com validade de reserva curta:
    RESERVA_VALIDADE=PT20S docker compose up -d --build

Uso:
    python scripts/fluxo-completo.py                 # os dois cenários, checkout simulado
    python scripts/fluxo-completo.py --mercadopago   # pagamentos de verdade no Mercado Pago (teste)
    python scripts/fluxo-completo.py --cenario feliz # só um dos cenários (feliz | estorno)

Com --mercadopago, o Billing precisa estar com PAGAMENTO_PROVEDOR=mercadopago (arquivo .env).
O roteiro mostra o link de cada cobrança e espera: abra numa janela anônima, entre com o
comprador de teste e pague com um cartão de teste com titular APRO.
"""
import argparse
import json
import random
import string
import sys
import time
import urllib.error
import urllib.request

OS = "http://localhost:8080"
EXECUCAO = "http://localhost:8081"
BILLING = "http://localhost:8082"
VALIDADE_RESERVA_S = 20
ESPERA_PAGAMENTO_S = 15 * 60

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
        with urllib.request.urlopen(req, timeout=15) as resp:
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


def esperar(descricao, condicao, limite=90):
    inicio = time.time()
    while time.time() - inicio < limite:
        try:
            valor = condicao()
            if valor:
                print(f"   ✓ {descricao} ({time.time() - inicio:.0f}s)", flush=True)
                return valor
        except RuntimeError:
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


class Oficina:
    def __init__(self, mercadopago=False):
        self.mercadopago = mercadopago
        self.token = chamar("POST", f"{OS}/auth/login", corpo={"username": "admin", "password": "admin123"})["accessToken"]

    def os(self, os_id):
        return chamar("GET", f"{OS}/ordens-servico/{os_id}", self.token)

    def status(self, os_id):
        return self.os(os_id)["status"]

    def saga(self, os_id):
        return chamar("GET", f"{OS}/admin/sagas/{os_id}", self.token)

    def abrir(self, nome):
        os_id = chamar("POST", f"{OS}/ordens-servico", self.token, {
            "cliente": {"documento": cpf_valido(), "nome": nome, "email": f"{nome.lower()}@exemplo.com"},
            "veiculo": {"placa": placa(), "marca": "Fiat", "modelo": "Argo", "ano": 2022},
            "relatoCliente": "Barulho na suspensão dianteira"})["id"]
        print(f"   OS {nome}: {os_id}")
        return os_id

    def diagnosticar(self, os_id, servico, peca, quantidade):
        esperar("OS chegou à fila da Execução",
                lambda: chamar("GET", f"{EXECUCAO}/ordens-execucao/{os_id}", self.token))
        chamar("POST", f"{EXECUCAO}/ordens-execucao/{os_id}/diagnostico/iniciar", self.token)
        chamar("POST", f"{EXECUCAO}/ordens-execucao/{os_id}/itens/servicos", self.token, {"servicoId": servico})
        chamar("POST", f"{EXECUCAO}/ordens-execucao/{os_id}/itens/pecas", self.token,
               {"pecaId": peca, "quantidade": quantidade})
        chamar("POST", f"{EXECUCAO}/ordens-execucao/{os_id}/diagnostico/concluir", self.token)
        print(f"   diagnóstico concluído: {quantidade} peça(s) reservada(s)")
        esperar("orçamento gerado no Billing → AGUARDANDO_APROVACAO",
                lambda: self.status(os_id) == "AGUARDANDO_APROVACAO")

    def aprovar_e_pagar(self, os_id):
        chamar("POST", f"{OS}/ordens-servico/{os_id}/orcamento/aprovar", self.token)
        link = esperar("link de pagamento disponível",
                       lambda: chamar("GET", f"{OS}/ordens-servico/{os_id}/status", self.token)["linkPagamento"])
        print(f"   link: {link}", flush=True)
        if self.mercadopago:
            print("   ➜ PAGUE AGORA, numa janela anônima, logado com o comprador de teste;")
            print("     cartão de teste com titular APRO (CPF 12345678909)", flush=True)
            esperar("Mercado Pago aprovou e o Billing confirmou o pagamento",
                    lambda: self.saga(os_id)["estado"] != "PAGAMENTO_SOLICITADO", limite=ESPERA_PAGAMENTO_S)
            return
        orcamento = self.os(os_id)["orcamento"]["orcamentoId"]
        chamar("POST", f"{BILLING}/simulador/pagamentos/{orcamento}?resultado=aprovado")
        print("   cliente pagou no checkout (simulado)")

    def reparo_liberado(self, os_id):
        # A OS já mostra EM_EXECUCAO enquanto a saga espera a Execução (REPARO_SOLICITADO);
        # o reparo só pode começar depois do ReparoLiberado.
        esperar("pagamento confirmado → Execução baixou as peças → saga EM_REPARO",
                lambda: self.saga(os_id)["estado"] == "EM_REPARO")

    def reparar(self, os_id):
        self.reparo_liberado(os_id)
        chamar("POST", f"{EXECUCAO}/ordens-execucao/{os_id}/reparo/iniciar", self.token)
        chamar("POST", f"{EXECUCAO}/ordens-execucao/{os_id}/reparo/finalizar", self.token)
        esperar("Execução comunicou a finalização → FINALIZADA", lambda: self.status(os_id) == "FINALIZADA")


def main():
    args = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    args.add_argument("--mercadopago", action="store_true", help="pagamentos reais no Mercado Pago (teste)")
    args.add_argument("--cenario", choices=["feliz", "estorno", "todos"], default="todos")
    opcoes = args.parse_args()

    of = Oficina(mercadopago=opcoes.mercadopago)
    peca = chamar("POST", f"{EXECUCAO}/pecas", of.token,
                  {"descricao": "Pastilha de freio", "valorUnitario": 95.00, "quantidadeInicial": 4})["id"]
    servico = chamar("POST", f"{EXECUCAO}/servicos", of.token, {"descricao": "Alinhamento", "valorBase": 120.00})["id"]
    print(f"Estoque: 4 pastilhas ({peca})\n")

    if opcoes.cenario in ("feliz", "todos"):
        cenario_feliz(of, servico, peca)
    if opcoes.cenario in ("estorno", "todos"):
        cenario_estorno(of, servico, peca)
    print("Tudo certo. Traces distribuídos: http://localhost:16686 (serviço oficina-os-service)")


def cenario_feliz(of, servico, peca):
    print("== Cenário 1 — fluxo feliz", flush=True)
    a = of.abrir("Alice")
    of.diagnosticar(a, servico, peca, 2)
    of.aprovar_e_pagar(a)
    of.reparar(a)
    chamar("POST", f"{OS}/ordens-servico/{a}/entregar", of.token)
    saga_a = of.saga(a)
    assert saga_a["estado"] == "CONCLUIDA", saga_a
    historico = [t["para"] for t in chamar("GET", f"{OS}/ordens-servico/{a}/historico", of.token)["transicoes"]]
    print(f"   histórico: {' → '.join(historico)}")
    print(f"   saga: {saga_a['estado']} — {len(saga_a['mensagens'])} mensagens trocadas\n")


def cenario_estorno(of, servico, peca):
    print("== Cenário 2 — reserva vencida, peça consumida por outra OS, estorno", flush=True)
    b = of.abrir("Bruno")
    of.diagnosticar(b, servico, peca, 2)
    print(f"   esperando a reserva de B vencer ({VALIDADE_RESERVA_S}s)...")
    time.sleep(VALIDADE_RESERVA_S + 3)
    c = of.abrir("Carla")
    of.diagnosticar(c, servico, peca, 2)
    of.aprovar_e_pagar(c)
    of.reparo_liberado(c)
    of.aprovar_e_pagar(b)
    esperar("Execução recusou liberar B → OS CANCELADA", lambda: of.status(b) == "CANCELADA")
    esperar("estorno confirmado e reservas liberadas → saga COMPENSADA",
            lambda: of.saga(b)["estado"] == "COMPENSADA")
    orc_b = chamar("GET", f"{BILLING}/orcamentos/os/{b}", of.token)
    assert orc_b["status"] == "ESTORNADO", orc_b
    print(f"   OS B: {of.os(b)['motivoCancelamento']} | orçamento no Billing: {orc_b['status']} "
          f"(R$ {orc_b['valorTotal']} devolvidos)")
    enviadas = [m["tipo"] for m in of.saga(b)["mensagens"] if m["direcao"] == "ENVIADA"]
    print(f"   comandos da saga de B: {' → '.join(enviadas)}\n")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, TimeoutError, AssertionError) as erro:
        print(f"\n✗ FALHOU: {erro}", file=sys.stderr)
        sys.exit(1)
