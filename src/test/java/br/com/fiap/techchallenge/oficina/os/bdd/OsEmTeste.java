package br.com.fiap.techchallenge.oficina.os.bdd;

import io.quarkiverse.cucumber.ScenarioScope;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.math.BigDecimal;
import java.util.List;

/**
 * O que um cenário sabe sobre a sua OS: cliente, veículo, ids devolvidos pela API e o
 * que o mecânico diagnosticou. Um objeto novo por cenário ({@code @ScenarioScope}).
 *
 * <p>Acesso só por métodos: o bean é um proxy, e campo lido direto no proxy não é o
 * campo da instância do cenário.
 */
@ScenarioScope
public class OsEmTeste {

    private String nomeCliente;
    private String documento;
    private String placa;
    private String marca;
    private String modelo;
    private int ano;
    private String osId;
    private String clienteId;
    private JsonArray itens = new JsonArray();
    private List<String> compensacoes = List.of();

    public void registrarCliente(String nome, String documento) {
        this.nomeCliente = nome;
        this.documento = documento;
    }

    public void registrarVeiculo(String placa, String marca, String modelo, int ano) {
        this.placa = placa;
        this.marca = marca;
        this.modelo = modelo;
        this.ano = ano;
    }

    public JsonObject corpoDaAbertura(String relato) {
        return new JsonObject()
                .put("cliente", new JsonObject()
                        .put("documento", documento)
                        .put("nome", nomeCliente)
                        .put("email", "cliente@exemplo.com"))
                .put("veiculo", new JsonObject()
                        .put("placa", placa)
                        .put("marca", marca)
                        .put("modelo", modelo)
                        .put("ano", ano))
                .put("relatoCliente", relato);
    }

    public BigDecimal valorTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < itens.size(); i++) {
            JsonObject item = itens.getJsonObject(i);
            total = total.add(new BigDecimal(item.getString("valorUnitario"))
                    .multiply(BigDecimal.valueOf(item.getInteger("quantidade"))));
        }
        return total;
    }

    public String placa() {
        return placa;
    }

    public String osId() {
        return osId;
    }

    public void osAberta(String osId, String clienteId) {
        this.osId = osId;
        this.clienteId = clienteId;
    }

    public String clienteId() {
        return clienteId;
    }

    public JsonArray itens() {
        return itens;
    }

    public void diagnosticada(JsonArray itens) {
        this.itens = itens;
    }

    public List<String> compensacoes() {
        return compensacoes;
    }

    public void compensacoesDisparadas(List<String> compensacoes) {
        this.compensacoes = List.copyOf(compensacoes);
    }
}
