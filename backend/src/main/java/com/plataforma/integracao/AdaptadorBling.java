package com.plataforma.integracao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.plataforma.canal.TipoCanal;
import com.plataforma.cliente.Cliente;
import com.plataforma.cliente.TipoCliente;
import com.plataforma.cliente.TipoDocumento;
import com.plataforma.custo.Custo;
import com.plataforma.custo.NaturezaCusto;
import com.plataforma.pedido.ItemPedido;
import com.plataforma.pedido.Pedido;
import com.plataforma.pedido.StatusPedido;

/**
 * Traduz o payload de {@code GET /pedidos/vendas/{id}} do Bling API v3
 * para o modelo canonico. Ver docs/integracoes/mapeamento-bling.md
 * (versao 12/08/2026) para a tabela campo a campo - a confianca
 * ESTRUTURAL aqui e MENOR que a do Mercado Livre: a propria documentacao
 * oficial nao pode ser confirmada nesta rodada (decisao
 * 0013-bling-como-primeiro-erp.md ja previa esse risco). Data da ultima
 * validacao contra a API real: NENHUMA.
 *
 * NAO CHAMA REDE (decisao 0014) e e PURO (nao consulta banco) pelo mesmo
 * motivo do {@link AdaptadorMercadoLivre} - ver o javadoc de la.
 *
 * REGRA DE ORQUESTRACAO IMPORTANTE (nao e deste adaptador sozinho, mas
 * registrada aqui porque e a armadilha mais provavel de quem ler este
 * arquivo depois): este adaptador NUNCA gera
 * {@code Custo(natureza=COMISSAO_CANAL)}. Isso e taxa de marketplace; o
 * Bling e ERP e nao cobra isso - so o adaptador de Mercado Livre grava
 * essa natureza (ver mapeamento-bling.md, "o que o canonico exige e a
 * fonte NAO fornece" #1). Como a decisao 0017 NAO deduplica pedido entre
 * canais, o pedido do Bling e o pedido do ML para a mesma venda real sao
 * DUAS linhas de {@code pedido}, com dois {@code canal_id} diferentes -
 * ainda assim nunca faria sentido inventar aqui uma comissao que o Bling
 * simplesmente nao sabe informar.
 */
@Component
public class AdaptadorBling implements AdaptadorDeCanal {

    // Bling nao manda fuso (ver gap 3 do mapeamento-bling.md) - meio-dia
    // em America/Sao_Paulo e a aproximacao assumida, documentada em cada
    // CampoAusente correspondente, nunca apresentada como horario real.
    private static final ZoneId FUSO_PADRAO = ZoneId.of("America/Sao_Paulo");

    @Override
    public TipoCanal tipoSuportado() {
        return TipoCanal.ERP_BLING;
    }

    @Override
    public ResultadoTraducao traduzirPedido(String payloadJson, UUID canalId) {
        JsonNode raiz = SuporteJson.lerArvore(payloadJson);
        List<CampoAusente> ausentes = new ArrayList<>();

        String idExternoPedido = SuporteJson.texto(raiz, "id");
        if (idExternoPedido == null || idExternoPedido.isBlank()) {
            throw new PayloadInvalidoException("Pedido do Bling sem 'id' - sem id nao ha chave de idempotencia.");
        }

        Cliente cliente = traduzirCliente(raiz.get("contato"), canalId, ausentes);

        OffsetDateTime feitoEm = dataComHoraAssumida(SuporteJson.texto(raiz, "data"), ausentes, "pedido.feito_em", "data");
        if (feitoEm == null) {
            throw new PayloadInvalidoException(
                    "Pedido " + idExternoPedido + " sem 'data' - pedido.feito_em e obrigatorio (NOT NULL, V008).");
        }

        String dataSaida = SuporteJson.texto(raiz, "dataSaida");
        if (dataSaida != null) {
            // pedido.enviado_em EXISTE na fonte, mas o construtor de
            // Pedido (V008) deliberadamente nao aceita datas de ciclo de
            // vida na CRIACAO - sao preenchidas por um fluxo de transicao
            // de status que e outra tarefa (ver javadoc de Pedido). O
            // valor cru fica preservado em dados_origem para nao se perder.
            ausentes.add(new CampoAusente("pedido.enviado_em",
                    "Bling informa dataSaida='" + dataSaida + "', mas esta rodada nao grava datas de ciclo de vida na criacao "
                            + "do pedido (fora do escopo do construtor de Pedido). Valor bruto preservado em dados_origem."));
        }

        String situacaoOrigem = SuporteJson.texto(raiz.get("situacao"), "valor");
        StatusPedido status = traduzirStatus(situacaoOrigem, idExternoPedido, ausentes);

        BigDecimal valorBrutoItens = SuporteJson.normalizar(SuporteJson.decimal(raiz, "totalProdutos"));
        if (valorBrutoItens == null) {
            valorBrutoItens = SuporteJson.zero();
        }

        BigDecimal valorTotalPedido = SuporteJson.normalizar(SuporteJson.decimal(raiz, "total"));
        if (valorTotalPedido == null) {
            throw new PayloadInvalidoException(
                    "Pedido " + idExternoPedido + " sem 'total' - valor_total_pedido e obrigatorio (R1, V008). "
                            + "NUNCA recalculado como soma das parcelas (armadilha 5 do mapeamento-bling.md).");
        }

        BigDecimal valorDesconto = traduzirDesconto(raiz.get("desconto"), valorBrutoItens, ausentes);

        JsonNode transporte = raiz.get("transporte");
        FreteBling frete = traduzirFrete(transporte, ausentes);

        JsonNode etiqueta = (transporte == null) ? null : transporte.get("etiqueta");
        String cepEntrega = normalizarCep(SuporteJson.texto(etiqueta, "cep"));
        String cidadeEntrega = SuporteJson.texto(etiqueta, "municipio");
        String ufEntrega = normalizarUf(SuporteJson.texto(etiqueta, "uf"));
        if (etiqueta == null) {
            // Regra explicita do mapeamento-bling.md: o endereco de
            // entrega vem SEMPRE de transporte.etiqueta, NUNCA de
            // contato.endereco (usar o do cadastro corromperia a analise
            // de regiao quando o destino real for outro).
            ausentes.add(new CampoAusente("pedido.cep_entrega/cidade_entrega/uf_entrega",
                    "transporte.etiqueta ausente - a regra do adaptador e nunca usar contato.endereco como substituto."));
        }

        Short quantidadeParcelas = null;
        JsonNode parcelasNode = raiz.path("parcelas");
        if (parcelasNode.isArray() && !parcelasNode.isEmpty()) {
            quantidadeParcelas = (short) parcelasNode.size();
        }
        // forma_pagamento: parcelas[].formaPagamento.id e configuravel por
        // conta no Bling (nao e enum fixo da API) - sem tabela de
        // traducao por tenant (ainda nao existe na Fase 1), o adaptador
        // NAO adivinha (gap 4 do mapeamento-bling.md). Sempre NULL.
        ausentes.add(new CampoAusente("pedido.forma_pagamento",
                "parcelas[].formaPagamento.id e configuravel por tenant no Bling - sem tabela de traducao por tenant "
                        + "ainda, o adaptador nao adivinha e deixa NULL (ver gap 4 do mapeamento-bling.md)."));

        String codigoExibicao = SuporteJson.texto(raiz, "numero");
        String dadosOrigemPedido = extensaoPedido(raiz);

        Pedido pedido = new Pedido(canalId, cliente == null ? null : cliente.getId(), idExternoPedido, codigoExibicao,
                status, situacaoOrigem, feitoEm, valorBrutoItens, valorDesconto, frete.valorCobrado(), valorTotalPedido,
                // valor_repasse_previsto: sempre NULL para origem ERP -
                // NAO e lacuna a resolver (Bling nao e marketplace, nao
                // ha repasse de canal). Por isso NAO entra em `ausentes`.
                null, null, null, quantidadeParcelas, cepEntrega, cidadeEntrega, ufEntrega, dadosOrigemPedido);

        List<ItemPedido> itens = parseItens(raiz, pedido.getId(), idExternoPedido, ausentes);

        List<Custo> custos = new ArrayList<>();
        if (frete.ehCustoDoLojista()) {
            custos.add(new Custo(NaturezaCusto.FRETE, pedido.getId(), null, null, frete.valor(), pedido.getMoeda(),
                    pedido.getFeitoEm(), true, null, null, null, null,
                    "Frete pago pelo lojista (transporte.frete do Bling) - classificacao CIF/FOB nao confirmada, "
                            + "ver armadilha 3 do mapeamento-bling.md", canalId, null, "{}"));
        }
        JsonNode tributacao = raiz.get("tributacao");
        adicionarCustoImposto(custos, tributacao, "totalICMS", pedido, canalId);
        adicionarCustoImposto(custos, tributacao, "totalIPI", pedido, canalId);
        adicionarCustoImposto(custos, tributacao, "totalICMSST", pedido, canalId);

        return new ResultadoTraducao(pedido, itens, cliente, custos, ausentes);
    }

    // ------------------------------------------------------------------
    // Itens (de "itens[]") - extraido do corpo de traduzirPedido (dívida 4
    // do docs/ESTADO.md: o metodo tinha ~140 linhas com o laço de itens
    // embutido, enquanto o AdaptadorMercadoLivre ja isolava isto em
    // parseItens. So estrutura - nenhum comportamento mudou.
    // ------------------------------------------------------------------

    private List<ItemPedido> parseItens(JsonNode raiz, UUID pedidoId, String idExternoPedido, List<CampoAusente> ausentes) {
        List<ItemPedido> itens = new ArrayList<>();
        boolean primeiroItem = true;
        for (JsonNode itemNode : raiz.path("itens")) {
            String idExternoItem = SuporteJson.texto(itemNode, "id");
            String skuOrigem = SuporteJson.texto(itemNode, "codigo");
            String titulo = SuporteJson.texto(itemNode, "descricao");
            if (titulo == null || titulo.isBlank()) {
                throw new PayloadInvalidoException(
                        "Item " + idExternoItem + " do pedido " + idExternoPedido + " sem 'descricao' - titulo_origem e obrigatorio (NOT NULL, V008).");
            }

            BigDecimal quantidade = SuporteJson.decimal(itemNode, "quantidade");
            BigDecimal valorUnitario = SuporteJson.normalizar(SuporteJson.decimal(itemNode, "valor"));
            BigDecimal descontoLinha = SuporteJson.normalizar(SuporteJson.decimal(itemNode, "desconto"));
            if (descontoLinha == null) {
                descontoLinha = SuporteJson.zero();
            } else if (primeiroItem) {
                // "REAL vs. PERCENTUAL" so e confirmado no nivel do
                // PEDIDO (desconto.unidade); no nivel do ITEM o
                // mapeamento-bling.md registra que nao ha indicacao de
                // unidade - tratado como valor em reais, hipotese mais
                // simples e NAO CONFIRMADA. Uma nota basta para o pedido
                // inteiro, nao por item.
                ausentes.add(new CampoAusente("item_pedido.valor_desconto_linha (interpretacao)",
                        "itens[].desconto tratado como valor em reais - o Bling nao expoe unidade (REAL/PERCENTUAL) neste "
                                + "nivel (armadilha 2 do mapeamento-bling.md so confirma a ambiguidade no nivel do pedido)."));
            }
            primeiroItem = false;

            // O Bling nao confirma um total de linha pronto (ver
            // mapeamento) - calculado UMA VEZ aqui, na ingestao.
            BigDecimal totalLinha = (quantidade != null && valorUnitario != null)
                    ? valorUnitario.multiply(quantidade).subtract(descontoLinha)
                            .setScale(SuporteJson.ESCALA_MONETARIA, SuporteJson.ARREDONDAMENTO_MONETARIO)
                    : null;

            itens.add(new ItemPedido(pedidoId, null, skuOrigem, titulo, quantidade, valorUnitario, descontoLinha,
                    totalLinha, idExternoItem, extensaoItem(itemNode)));
        }
        return itens;
    }

    // ------------------------------------------------------------------
    // Status - dominio configuravel por tenant, nao adivinhamos (gap 4)
    // ------------------------------------------------------------------

    private static StatusPedido traduzirStatus(String situacaoOrigem, String idExternoPedido, List<CampoAusente> ausentes) {
        if (situacaoOrigem == null) {
            ausentes.add(new CampoAusente("pedido.status",
                    "situacao.valor ausente no pedido " + idExternoPedido + " - AGUARDANDO_PAGAMENTO usado como neutro."));
            return StatusPedido.AGUARDANDO_PAGAMENTO;
        }
        if ("Cancelado".equalsIgnoreCase(situacaoOrigem)) {
            return StatusPedido.CANCELADO;
        }
        // Qualquer outra situacao (inclusive "Atendido", que PARECE
        // "pago"): o dominio de situacao no Bling e configuravel por
        // tenant (gap 4 do mapeamento-bling.md). Sem tabela de traducao
        // por tenant, o adaptador NAO adivinha - usa o status mais
        // neutro do dominio canonico e registra a divergencia, SEMPRE.
        ausentes.add(new CampoAusente("pedido.status",
                "situacao.valor='" + situacaoOrigem + "' - dominio configuravel por tenant no Bling, sem tabela de "
                        + "traducao por tenant ainda (gap 4 do mapeamento-bling.md). AGUARDANDO_PAGAMENTO usado como "
                        + "neutro, nunca um chute de que a situacao significa 'pago'."));
        return StatusPedido.AGUARDANDO_PAGAMENTO;
    }

    // ------------------------------------------------------------------
    // Desconto - REAL vs. PERCENTUAL (armadilha 2 do mapeamento)
    // ------------------------------------------------------------------

    private static BigDecimal traduzirDesconto(JsonNode desconto, BigDecimal totalProdutos, List<CampoAusente> ausentes) {
        if (desconto == null) {
            ausentes.add(new CampoAusente("pedido.valor_desconto", "campo 'desconto' ausente no payload do pedido - tratado como 0."));
            return SuporteJson.zero();
        }
        BigDecimal valor = SuporteJson.decimal(desconto, "valor");
        if (valor == null) {
            return SuporteJson.zero();
        }
        String unidade = SuporteJson.texto(desconto, "unidade");
        if ("PERCENTUAL".equalsIgnoreCase(unidade)) {
            // Calculo DERIVADO, nao copia direta da fonte - documentado
            // porque desconto.valor aqui e uma aliquota (ex.: 10), nao reais.
            BigDecimal fracao = valor.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            BigDecimal emReais = totalProdutos.multiply(fracao).setScale(SuporteJson.ESCALA_MONETARIA, SuporteJson.ARREDONDAMENTO_MONETARIO);
            ausentes.add(new CampoAusente("pedido.valor_desconto (calculado)",
                    "desconto.unidade=PERCENTUAL - valor em reais calculado como totalProdutos * (" + valor + "/100). "
                            + "Nao e copia direta da fonte."));
            return emReais;
        }
        // REAL (ou unidade desconhecida - tratada como REAL, a hipotese
        // mais simples segundo o mapeamento-bling.md).
        return valor.setScale(SuporteJson.ESCALA_MONETARIA, SuporteJson.ARREDONDAMENTO_MONETARIO);
    }

    // ------------------------------------------------------------------
    // Frete - CIF vs. FOB (armadilha 3 do mapeamento)
    // ------------------------------------------------------------------

    private record FreteBling(BigDecimal valor, BigDecimal valorCobrado, boolean ehCustoDoLojista) {
    }

    private static FreteBling traduzirFrete(JsonNode transporte, List<CampoAusente> ausentes) {
        BigDecimal valorFrete = SuporteJson.normalizar(SuporteJson.decimal(transporte, "frete"));
        if (valorFrete == null || valorFrete.signum() == 0) {
            return new FreteBling(valorFrete, SuporteJson.zero(), false);
        }
        Integer fretePorConta = SuporteJson.inteiro(transporte, "fretePorConta");
        // fretePorConta=1 e a hipotese mais comum para "destinatario paga"
        // (CIF) em ERPs brasileiros, mas o mapeamento-bling.md marca isso
        // como NAO CONFIRMADO. Tratado como receita SO neste caso;
        // qualquer outro codigo (inclusive o 0 desta fixture) vira custo
        // de frete do lojista.
        if (fretePorConta != null && fretePorConta == 1) {
            return new FreteBling(valorFrete, valorFrete, false);
        }
        ausentes.add(new CampoAusente("custo.natureza=FRETE (classificacao)",
                "transporte.frete=" + valorFrete + " com fretePorConta=" + fretePorConta + " - semantica CIF/FOB do "
                        + "Bling nao confirmada (armadilha 3 do mapeamento-bling.md). Classificado como custo do lojista; "
                        + "eh_estimativa=true porque a CLASSIFICACAO (nao o valor) e incerta."));
        return new FreteBling(valorFrete, SuporteJson.zero(), true);
    }

    // ------------------------------------------------------------------
    // Imposto (de "tributacao")
    // ------------------------------------------------------------------

    private static void adicionarCustoImposto(List<Custo> custos, JsonNode tributacao, String campo, Pedido pedido, UUID canalId) {
        BigDecimal valor = SuporteJson.normalizar(SuporteJson.decimal(tributacao, campo));
        if (valor == null) {
            // Fonte nao informou este imposto - nao inventamos zero (regra 5).
            return;
        }
        custos.add(new Custo(NaturezaCusto.IMPOSTO, pedido.getId(), null, null, valor, pedido.getMoeda(),
                pedido.getFeitoEm(), false, null, null, null, null,
                campo + " apurado pelo Bling (tributacao." + campo + ")", canalId, null, "{}"));
    }

    // ------------------------------------------------------------------
    // Cliente (de "contato")
    // ------------------------------------------------------------------

    private Cliente traduzirCliente(JsonNode contato, UUID canalId, List<CampoAusente> ausentes) {
        if (contato == null) {
            ausentes.add(new CampoAusente("cliente", "'contato' ausente no pedido do Bling."));
            return null;
        }

        String idExterno = SuporteJson.texto(contato, "id");
        String nome = SuporteJson.texto(contato, "nome");
        String email = SuporteJson.texto(contato, "email");
        String telefone = normalizarTelefoneBr(SuporteJson.texto(contato, "telefone"));
        String tipoPessoa = SuporteJson.texto(contato, "tipoPessoa");

        TipoCliente tipo;
        TipoDocumento documentoTipo;
        if ("F".equalsIgnoreCase(tipoPessoa)) {
            tipo = TipoCliente.PESSOA_FISICA;
            documentoTipo = TipoDocumento.CPF;
        } else if ("J".equalsIgnoreCase(tipoPessoa)) {
            tipo = TipoCliente.PESSOA_JURIDICA;
            documentoTipo = TipoDocumento.CNPJ;
        } else {
            tipo = null; // Cliente() aplica o default PESSOA_FISICA do schema.
            documentoTipo = null;
            ausentes.add(new CampoAusente("cliente.tipo",
                    "contato.tipoPessoa='" + tipoPessoa + "' nao e 'F' nem 'J' (valores esperados nao confirmados - "
                            + "ver mapeamento-bling.md)."));
        }

        String numeroDocumento = SuporteJson.texto(contato, "numeroDocumento");
        if (numeroDocumento != null && !numeroDocumento.isBlank()) {
            // Mesma limitacao do AdaptadorMercadoLivre: HMAC (V007) exige
            // chave de infraestrutura ainda nao provisionada nesta
            // rodada - documento NAO gravado, nem em claro nem em hash.
            ausentes.add(new CampoAusente("cliente.documento_hash",
                    "contato.numeroDocumento presente, mas o HMAC (V007) precisa de chave de infraestrutura ainda nao "
                            + "provisionada nesta rodada - documento NAO gravado, nem em claro nem em hash."));
            documentoTipo = null; // idem: sem hash, documento_tipo sozinho nao tem propósito.
        }

        // contato.endereco e DESCARTADO de proposito (minimizacao de LGPD
        // - ver V007): rua/numero/complemento nao respondem nenhuma
        // pergunta de negocio e so aumentam o dano de um vazamento.
        // cep/cidade/uf de ENTREGA vem de transporte.etiqueta (traduzirPedido).
        return new Cliente(canalId, idExterno, tipo, nome, null, email, telefone, documentoTipo, null, null, null);
    }

    private static String normalizarTelefoneBr(String telefone) {
        if (telefone == null) {
            return null;
        }
        String digitos = telefone.replaceAll("[^0-9]", "");
        if (digitos.isBlank()) {
            return null;
        }
        if (digitos.length() == 10 || digitos.length() == 11) {
            return "+55" + digitos;
        }
        if (digitos.startsWith("55") && (digitos.length() == 12 || digitos.length() == 13)) {
            return "+" + digitos;
        }
        // Formato nao reconhecido: guardamos o que der, sem rejeitar
        // (mesma regra do telefone na V007 - perder o dado e pior que
        // guardar torto).
        return digitos;
    }

    private static String normalizarCep(String cep) {
        if (cep == null) {
            return null;
        }
        String digitos = cep.replaceAll("[^0-9]", "");
        return digitos.isBlank() ? null : digitos;
    }

    private static String normalizarUf(String uf) {
        return (uf == null) ? null : uf.trim().toUpperCase();
    }

    // ------------------------------------------------------------------
    // Datas - Bling manda so a data, sem hora nem fuso (gap 3)
    // ------------------------------------------------------------------

    private static OffsetDateTime dataComHoraAssumida(String dataTexto, List<CampoAusente> ausentes, String campoCanonico,
            String campoOrigem) {
        if (dataTexto == null || dataTexto.isBlank()) {
            return null;
        }
        LocalDate data;
        try {
            data = LocalDate.parse(dataTexto);
        } catch (DateTimeParseException erro) {
            throw new PayloadInvalidoException(
                    "Campo '" + campoOrigem + "' com formato de data inesperado: '" + dataTexto + "'", erro);
        }
        ausentes.add(new CampoAusente(campoCanonico,
                "Bling manda so a data ('" + dataTexto + "'), sem hora nem fuso. Meio-dia em America/Sao_Paulo foi "
                        + "assumido como aproximacao (gap 3 do mapeamento-bling.md) - NAO e o horario real do pedido."));
        return data.atTime(12, 0).atZone(FUSO_PADRAO).toOffsetDateTime();
    }

    // ------------------------------------------------------------------
    // Extensao (dados_origem) - o que nao tem coluna canonica
    // ------------------------------------------------------------------

    private static String extensaoPedido(JsonNode raiz) {
        ObjectNode extensao = SuporteJson.objeto();
        extensao.put("numeroLoja", SuporteJson.texto(raiz, "numeroLoja"));
        extensao.put("numeroPedidoCompra", SuporteJson.texto(raiz, "numeroPedidoCompra"));
        extensao.put("dataSaida", SuporteJson.texto(raiz, "dataSaida"));
        extensao.put("dataPrevista", SuporteJson.texto(raiz, "dataPrevista"));
        extensao.put("observacoes", SuporteJson.texto(raiz, "observacoes"));
        extensao.put("observacoesInternas", SuporteJson.texto(raiz, "observacoesInternas"));
        extensao.put("totalDesconto", SuporteJson.texto(raiz, "totalDesconto"));

        JsonNode loja = raiz.get("loja");
        if (loja != null) {
            extensao.set("loja", loja);
        }
        JsonNode categoria = raiz.get("categoria");
        if (categoria != null) {
            extensao.set("categoria", categoria);
        }
        JsonNode intermediador = raiz.get("intermediador");
        if (intermediador != null) {
            extensao.set("intermediador", intermediador);
        }
        JsonNode vendedor = raiz.get("vendedor");
        if (vendedor != null) {
            extensao.set("vendedor", vendedor);
        }

        JsonNode transporte = raiz.get("transporte");
        if (transporte != null) {
            ObjectNode transporteExtensao = SuporteJson.objeto();
            transporteExtensao.put("fretePorConta", SuporteJson.texto(transporte, "fretePorConta"));
            transporteExtensao.put("quantidadeVolumes", SuporteJson.texto(transporte, "quantidadeVolumes"));
            transporteExtensao.put("pesoBruto", SuporteJson.texto(transporte, "pesoBruto"));
            JsonNode transportadora = transporte.get("transportadora");
            if (transportadora != null) {
                transporteExtensao.set("transportadora", transportadora);
            }
            JsonNode volumes = transporte.get("volumes");
            if (volumes != null) {
                transporteExtensao.set("volumes", volumes);
            }
            extensao.set("transporte", transporteExtensao);
        }

        return SuporteJson.textoJson(extensao);
    }

    private static String extensaoItem(JsonNode itemNode) {
        ObjectNode extensao = SuporteJson.objeto();
        extensao.put("aliquotaIPI", SuporteJson.texto(itemNode, "aliquotaIPI"));
        extensao.put("unidade", SuporteJson.texto(itemNode, "unidade"));
        JsonNode produto = itemNode.get("produto");
        if (produto != null) {
            extensao.set("produto", produto);
        }
        return SuporteJson.textoJson(extensao);
    }
}
