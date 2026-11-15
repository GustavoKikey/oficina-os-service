package br.com.fiap.techchallenge.oficina.os.suporte;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Cliente;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ItemOS;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Veiculo;
import br.com.fiap.techchallenge.oficina.os.saga.entities.PrazosSaga;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Dinheiro;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Documento;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Placa;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Dados de teste compartilhados — valores fixos para que as asserções sejam exatas. */
public final class Fixtures {

    public static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 11, 15, 14, 0, 0, 0, ZoneOffset.UTC);

    /** Prazos curtos e redondos: as contas de prazo nos testes ficam legíveis. */
    public static final PrazosSaga PRAZOS =
            new PrazosSaga(Duration.ofSeconds(30), Duration.ofHours(72), Duration.ofMinutes(35), 3);

    public static final UUID SERVICO_ALINHAMENTO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID PECA_PASTILHA = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private Fixtures() {}

    public static Cliente alice() {
        return Cliente.novo("Alice Souza", Documento.de("52998224725"), "alice@exemplo.com", "11999990000");
    }

    public static Cliente bruno() {
        return Cliente.novo("Bruno Lima", Documento.de("11144477735"), "bruno@exemplo.com", null);
    }

    public static Veiculo argoDe(Cliente dono) {
        return Veiculo.novo(Placa.de("ABC1D23"), "Fiat", "Argo", 2022, dono.id());
    }

    public static OrdemServico osAberta(Cliente cliente, Veiculo veiculo) {
        return OrdemServico.abrir(cliente.id(), veiculo.id(), "Barulho na suspensão", AGORA);
    }

    /** Alinhamento (R$ 120,00) + 2 pastilhas (R$ 95,00 cada) = R$ 310,00. */
    public static List<ItemOS> itensDiagnostico() {
        return List.of(
                new ItemOS(ItemOS.TipoItem.SERVICO, SERVICO_ALINHAMENTO, "Alinhamento", 1, Dinheiro.de("120.00")),
                new ItemOS(ItemOS.TipoItem.PECA, PECA_PASTILHA, "Pastilha de freio", 2, Dinheiro.de("95.00")));
    }
}
