package com.plataforma.integracao;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.plataforma.canal.TipoCanal;
import com.plataforma.cliente.Cliente;
import com.plataforma.custo.Custo;
import com.plataforma.custo.NaturezaCusto;
import com.plataforma.pedido.FormaPagamento;
import com.plataforma.pedido.ItemPedido;
import com.plataforma.pedido.Pedido;
import com.plataforma.pedido.StatusPedido;

/**
 * Traduz o payload de {@code GET /orders/{id}} do Mercado Livre para o
 * modelo canonico. Ver docs/integracoes/mapeamento-mercadolivre.md
 * (versao 12/08/2026) para a tabela campo a campo, o nivel de confianca
 * de cada um e a lista completa de gaps - este adaptador implementa
 * exatamente o que aquele documento descreve, nada além. Data da ultima
 * validacao contra a API real: NENHUMA (ver "Status de verificacao" no
 * topo daquele documento) - a estrutura usada aqui e reconstrucao do
 * formato publicamente conhecido, nao payload capturado.
 *
 * NAO CHAMA REDE. Recebe o payload ja obtido (fixture hoje, resposta
 * HTTP real quando a credencial existir) e so traduz - decisao 0014
 * (Camel adiado): isto e uma classe Spring comum, nao uma rota.
 *
 * PURO: nao consulta banco (RepositorioVariacao, RepositorioCliente
 * etc.). Por isso {@code item_pedido.variacaoId} sai sempre nulo daqui -
 * casar o item vendido com uma variacao ja sincronizada no catalogo (por
 * SKU/atributos) e trabalho de quem PERSISTE o resultado
 * (com.plataforma.ingestao.ServicoIngestao), nao deste tradutor. Isso e o
 * que permite testar a tradução com JUnit puro, sem Postgres (ver
 * requisito de teste da tarefa 10/11: "Testes de tradução são unitários
 * puros, sem banco").
 *
 * FORA DE ESCOPO DESTA RODADA, DE PROPOSITO: o recurso de envio
 * ({@code /shipments/{id}}, fixture envio-detalhe.json) e o de
 * reclamação/mediação ({@code /claims}, fixture pedido-com-devolucao.json)
 * NAO sao lidos por este metodo - {@link AdaptadorDeCanal#traduzirPedido}
 * recebe um unico payload, o do PEDIDO. Os campos que so existem
 * naqueles outros recursos (frete, endereco de entrega, devolucao) ficam
 * como {@link CampoAusente} aqui; ler e casar esses recursos e trabalho
 * de um metodo/adaptador futuro, fora do que a tarefa 10 pediu.
 */
@Component
public class AdaptadorMercadoLivre implements AdaptadorDeCanal {

    @Override
    public TipoCanal tipoSuportado() {
        return TipoCanal.MERCADO_LIVRE;
    }

    @Override
    public ResultadoTraducao traduzirPedido(String payloadJson, UUID canalId) {
        JsonNode raiz = SuporteJson.lerArvore(payloadJson);
        List<CampoAusente> ausentes = new ArrayList<>();

        String idExternoPedido = SuporteJson.texto(raiz, "id");
        if (idExternoPedido == null || idExternoPedido.isBlank()) {
            throw new PayloadInvalidoException("Pedido do Mercado Livre sem 'id' - sem id nao ha chave de idempotencia.");
        }

        OffsetDateTime feitoEm = SuporteJson.dataHora(raiz, "date_created");
        if (feitoEm == null) {
            throw new PayloadInvalidoException(
                    "Pedido " + idExternoPedido + " sem 'date_created' - pedido.feito_em e obrigatorio (NOT NULL, V008).");
        }

        Cliente cliente = traduzirCliente(raiz.get("buyer"), canalId, ausentes);

        String statusOrigem = SuporteJson.texto(raiz, "status");
        StatusPedido status = traduzirStatus(statusOrigem, idExternoPedido, ausentes);

        List<ItemBruto> itensBrutos = parseItens(raiz, idExternoPedido);

        BigDecimal valorBrutoItens = itensBrutos.stream()
                .map(ItemBruto::valorTotalLinha)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SuporteJson.ESCALA_MONETARIA, SuporteJson.ARREDONDAMENTO_MONETARIO);

        BigDecimal valorDesconto = SuporteJson.normalizar(SuporteJson.decimal(raiz.get("coupon"), "amount"));
        if (valorDesconto == null) {
            valorDesconto = SuporteJson.zero();
        }

        BigDecimal valorTotalPedido = SuporteJson.normalizar(SuporteJson.decimal(raiz, "total_amount"));
        if (valorTotalPedido == null) {
            throw new PayloadInvalidoException(
                    "Pedido " + idExternoPedido + " sem 'total_amount' - valor_total_pedido e obrigatorio (R1, V008).");
        }

        String moeda = SuporteJson.texto(raiz, "currency_id");
        if (moeda == null) {
            // Armadilha de dinheiro 6 do mapeamento-mercadolivre.md: nunca
            // fixar 'BRL' como default silencioso quando a fonte nao diz.
            ausentes.add(new CampoAusente("pedido.moeda",
                    "'currency_id' ausente no payload - o default do schema (BRL) sera aplicado pelo construtor de Pedido, "
                            + "mas isso NAO foi confirmado pela fonte (ver armadilha 6 do mapeamento-mercadolivre.md)."));
        }

        JsonNode payments = raiz.path("payments");
        FormaPagamento formaPagamento = null;
        Short quantidadeParcelas = null;
        if (payments.isArray() && !payments.isEmpty()) {
            JsonNode primeiroPagamento = payments.get(0);
            String paymentMethodId = SuporteJson.texto(primeiroPagamento, "payment_method_id");
            String paymentType = SuporteJson.texto(primeiroPagamento, "payment_type");
            formaPagamento = traduzirFormaPagamento(paymentMethodId, paymentType);
            if (formaPagamento == null) {
                ausentes.add(new CampoAusente("pedido.forma_pagamento",
                        "payment_method_id='" + paymentMethodId + "' / payment_type='" + paymentType + "' sem correspondencia "
                                + "confirmada na tabela de traducao (incompleta - ver mapeamento-mercadolivre.md)."));
            }
            Integer installments = SuporteJson.inteiro(primeiroPagamento, "installments");
            quantidadeParcelas = (installments == null) ? null : installments.shortValue();
        } else {
            ausentes.add(new CampoAusente("pedido.forma_pagamento", "payments[] vazio ou ausente no payload do pedido."));
        }

        // Frete e endereco de entrega: NAO vem em /orders/{id} (ver o
        // "canonico exige e a fonte NAO fornece" #1/#4 do mapeamento).
        // Gravar 0 aqui NAO significa frete gratis confirmado - significa
        // "nao obtido nesta chamada". A CampoAusente e o que distingue as
        // duas coisas para quem ler o relatorio depois.
        ausentes.add(new CampoAusente("pedido.valor_frete_cobrado",
                "shipping_option.cost so existe em /shipments/{id}, nao em /orders/{id} (fixture separada, fora do escopo "
                        + "deste metodo). Gravado como 0 (default da coluna) - NAO e frete gratis confirmado."));
        ausentes.add(new CampoAusente("pedido.valor_repasse_previsto",
                "recurso de pedido do Mercado Livre nao expoe o liquido a repassar; provavelmente mora em /finance "
                        + "(nao consultado por este adaptador). Fica NULL."));
        ausentes.add(new CampoAusente("pedido.cep_entrega/cidade_entrega/uf_entrega",
                "receiver_address so existe em /shipments/{id}, nao em /orders/{id} - mesma limitacao do frete."));

        String dadosOrigemPedido = extensaoPedido(raiz);

        Pedido pedido = new Pedido(canalId, cliente == null ? null : cliente.getId(), idExternoPedido, idExternoPedido,
                status, statusOrigem, feitoEm, valorBrutoItens, valorDesconto, null, valorTotalPedido, null,
                moeda, formaPagamento, quantidadeParcelas, null, null, null, dadosOrigemPedido);

        List<ItemPedido> itens = new ArrayList<>();
        List<Custo> custos = new ArrayList<>();
        for (ItemBruto bruto : itensBrutos) {
            ItemPedido item = new ItemPedido(pedido.getId(), null, bruto.skuOrigem(), bruto.tituloOrigem(),
                    bruto.quantidade(), bruto.valorUnitarioBruto(), bruto.valorDescontoLinha(), bruto.valorTotalLinha(),
                    bruto.idExterno(), extensaoItem(bruto));
            itens.add(item);

            if (bruto.saleFee() != null) {
                custos.add(new Custo(NaturezaCusto.COMISSAO_CANAL, pedido.getId(), item.getId(), null,
                        bruto.saleFee(), pedido.getMoeda(), pedido.getFeitoEm(), false, null, null, null, null,
                        // sale_fee vem so como total (sem base/aliquota) -
                        // ver "armadilha de dinheiro 3" do mapeamento.
                        // baseCalculo/aliquotaAplicada ficam NULL de
                        // proposito, nunca um chute de "sale_fee/unit_price".
                        "Comissao Mercado Livre (sale_fee) do item " + bruto.idExterno(), canalId, null, "{}"));
            }
        }

        return new ResultadoTraducao(pedido, itens, cliente, custos, ausentes);
    }

    // ------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------

    private static StatusPedido traduzirStatus(String statusOrigem, String idExternoPedido, List<CampoAusente> ausentes) {
        if (statusOrigem == null) {
            ausentes.add(new CampoAusente("pedido.status",
                    "campo 'status' ausente no payload do pedido " + idExternoPedido + " - AGUARDANDO_PAGAMENTO usado como neutro."));
            return StatusPedido.AGUARDANDO_PAGAMENTO;
        }
        return switch (statusOrigem) {
            case "paid" -> StatusPedido.PAGO;
            case "cancelled" -> StatusPedido.CANCELADO;
            default -> {
                // O status do ML NAO avança para EM_SEPARACAO/ENVIADO/
                // ENTREGUE (mora em /shipments/{id}.status, outro
                // recurso) - qualquer status_origem alem de paid/cancelled
                // que este adaptador ainda nao mapeou cai aqui, registrado,
                // nunca adivinhado (ver gap #1 do mapeamento-mercadolivre.md).
                ausentes.add(new CampoAusente("pedido.status",
                        "status_origem ML desconhecido ('" + statusOrigem + "') - sem tradutor confirmado "
                                + "(ver mapeamento-mercadolivre.md). AGUARDANDO_PAGAMENTO usado como neutro, nunca um chute."));
                yield StatusPedido.AGUARDANDO_PAGAMENTO;
            }
        };
    }

    private static FormaPagamento traduzirFormaPagamento(String paymentMethodId, String paymentType) {
        if ("pix".equalsIgnoreCase(paymentMethodId)) {
            return FormaPagamento.PIX;
        }
        if (paymentType == null) {
            return null;
        }
        return switch (paymentType) {
            case "credit_card" -> FormaPagamento.CARTAO_CREDITO;
            case "debit_card" -> FormaPagamento.CARTAO_DEBITO;
            case "ticket" -> FormaPagamento.BOLETO;
            case "account_money" -> FormaPagamento.SALDO_CANAL;
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // Cliente (de "buyer")
    // ------------------------------------------------------------------

    private Cliente traduzirCliente(JsonNode buyer, UUID canalId, List<CampoAusente> ausentes) {
        if (buyer == null || buyer.isNull()) {
            ausentes.add(new CampoAusente("cliente", "'buyer' ausente no payload do pedido - atipico, o ML normalmente sempre entrega."));
            return null;
        }

        String idExterno = SuporteJson.texto(buyer, "id");
        String apelido = SuporteJson.texto(buyer, "nickname");
        String primeiroNome = SuporteJson.texto(buyer, "first_name");
        String ultimoNome = SuporteJson.texto(buyer, "last_name");

        String nome = null;
        if (primeiroNome != null && ultimoNome != null) {
            nome = (primeiroNome + " " + ultimoNome).trim();
        } else {
            // Regra 5 do CLAUDE.md: NAO monta nome a partir do apelido
            // quando first_name/last_name vem nulo (caso comum no ML,
            // ver mapeamento-mercadolivre.md) - fica NULL, honesto.
            ausentes.add(new CampoAusente("cliente.nome",
                    "buyer.first_name/last_name vieram nulos - o ML so expoe nickname na maioria dos pedidos. "
                            + "cliente.nome fica NULL, nao e preenchido a partir do apelido."));
        }

        String email = SuporteJson.texto(buyer, "email");
        if (email == null) {
            ausentes.add(new CampoAusente("cliente.email",
                    "buyer.email veio nulo - o ML mascara/omite o e-mail do comprador na maior parte do ciclo do pedido."));
        }

        String telefone = telefoneMl(buyer.get("phone"));

        JsonNode billingInfo = buyer.get("billing_info");
        String docNumero = SuporteJson.texto(billingInfo, "doc_number");
        if (docNumero != null && !docNumero.isBlank()) {
            // documento_hash exige HMAC-SHA256 com chave de infraestrutura
            // (V007) que esta rodada nao provisiona - nunca gravamos em
            // claro, entao o documento fica de fora ate essa chave existir.
            ausentes.add(new CampoAusente("cliente.documento_hash",
                    "buyer.billing_info.doc_number presente, mas o HMAC (V007) precisa de uma chave de infraestrutura "
                            + "ainda nao provisionada nesta rodada - documento NAO gravado, nem em claro nem em hash."));
        } else {
            ausentes.add(new CampoAusente("cliente.documento_hash",
                    "buyer.billing_info sem doc_number - so aparece quando ha emissao de nota associada pelo proprio ML, "
                            + "caso raro na Fase 1 (ver gap #5 do mapeamento-mercadolivre.md)."));
        }

        // tipo/documentoTipo ficam nulos (deixando o default PESSOA_FISICA
        // do schema aplicar): sem doc_number gravado, manter um
        // documento_tipo sozinho seria dado incompleto sem propósito.
        return new Cliente(canalId, idExterno, null, nome, apelido, email, telefone, null, null, null, null);
    }

    private static String telefoneMl(JsonNode phone) {
        if (phone == null) {
            return null;
        }
        String ddd = SuporteJson.texto(phone, "area_code");
        String numero = SuporteJson.texto(phone, "number");
        if (ddd == null || numero == null || ddd.isBlank() || numero.isBlank()) {
            return null;
        }
        return "+55" + ddd + numero;
    }

    // ------------------------------------------------------------------
    // Itens (de "order_items[]")
    // ------------------------------------------------------------------

    private record ItemBruto(
            String idExterno,
            String skuOrigem,
            String sellerCustomField,
            String tituloOrigem,
            BigDecimal quantidade,
            BigDecimal valorUnitarioBruto,
            BigDecimal valorDescontoLinha,
            BigDecimal valorTotalLinha,
            BigDecimal saleFee,
            String condition,
            String categoryId,
            String warranty) {
    }

    private List<ItemBruto> parseItens(JsonNode raiz, String idExternoPedido) {
        List<ItemBruto> itens = new ArrayList<>();
        for (JsonNode itemNode : raiz.path("order_items")) {
            JsonNode item = itemNode.get("item");
            if (item == null || item.isNull()) {
                throw new PayloadInvalidoException("order_items[] do pedido " + idExternoPedido + " tem entrada sem 'item'.");
            }

            String itemId = SuporteJson.texto(item, "id");
            String variationId = SuporteJson.texto(item, "variation_id");
            // Concatenado (nao so item.id): o mesmo item.id pode aparecer
            // em duas linhas com variacoes diferentes no mesmo pedido - so
            // item.id quebraria uq_item_pedido_origem (ver mapeamento).
            String idExternoItem = (variationId != null && !variationId.isBlank()) ? itemId + "-" + variationId : itemId;

            String skuOrigem = SuporteJson.texto(item, "seller_sku");
            String sellerCustomField = SuporteJson.texto(item, "seller_custom_field");
            if (skuOrigem == null) {
                skuOrigem = sellerCustomField;
            }

            String titulo = SuporteJson.texto(item, "title");
            if (titulo == null || titulo.isBlank()) {
                throw new PayloadInvalidoException(
                        "Item " + idExternoItem + " do pedido " + idExternoPedido + " sem 'title' - titulo_origem e obrigatorio (NOT NULL, V008).");
            }

            BigDecimal quantidade = SuporteJson.decimal(itemNode, "quantity");
            BigDecimal valorUnitario = SuporteJson.normalizar(SuporteJson.decimal(itemNode, "unit_price"));
            BigDecimal precoCheio = SuporteJson.normalizar(SuporteJson.decimal(itemNode, "full_unit_price"));

            BigDecimal descontoLinha = SuporteJson.zero();
            if (precoCheio != null && valorUnitario != null && quantidade != null) {
                BigDecimal diferencaUnitaria = precoCheio.subtract(valorUnitario);
                if (diferencaUnitaria.signum() > 0) {
                    descontoLinha = diferencaUnitaria.multiply(quantidade)
                            .setScale(SuporteJson.ESCALA_MONETARIA, SuporteJson.ARREDONDAMENTO_MONETARIO);
                }
            }

            // O ML nao manda um total de linha pronto - calculado UMA VEZ
            // aqui, na ingestao (permitido pela regra 5 quando a fonte nao
            // manda; ver "armadilha de dinheiro 4" do mapeamento).
            BigDecimal totalLinha = (valorUnitario != null && quantidade != null)
                    ? valorUnitario.multiply(quantidade).setScale(SuporteJson.ESCALA_MONETARIA, SuporteJson.ARREDONDAMENTO_MONETARIO)
                    : null;

            BigDecimal saleFee = SuporteJson.normalizar(SuporteJson.decimal(itemNode, "sale_fee"));

            itens.add(new ItemBruto(idExternoItem, skuOrigem, sellerCustomField, titulo, quantidade, valorUnitario,
                    descontoLinha, totalLinha, saleFee,
                    SuporteJson.texto(item, "condition"), SuporteJson.texto(item, "category_id"), SuporteJson.texto(item, "warranty")));
        }
        return itens;
    }

    // ------------------------------------------------------------------
    // Extensao (dados_origem) - o que nao tem coluna canonica
    // ------------------------------------------------------------------

    private static String extensaoPedido(JsonNode raiz) {
        ObjectNode extensao = SuporteJson.objeto();
        extensao.put("pack_id", SuporteJson.texto(raiz, "pack_id"));
        extensao.put("pickup_id", SuporteJson.texto(raiz, "pickup_id"));
        JsonNode contexto = raiz.get("context");
        if (contexto != null) {
            extensao.set("context", contexto);
        }
        JsonNode tags = raiz.get("tags");
        if (tags != null) {
            extensao.set("tags", tags);
        }
        JsonNode shipping = raiz.get("shipping");
        if (shipping != null) {
            extensao.set("shipping", shipping);
        }
        JsonNode seller = raiz.get("seller");
        if (seller != null) {
            extensao.set("seller", seller);
        }
        return SuporteJson.textoJson(extensao);
    }

    private static String extensaoItem(ItemBruto bruto) {
        ObjectNode extensao = SuporteJson.objeto();
        extensao.put("condition", bruto.condition());
        extensao.put("category_id", bruto.categoryId());
        extensao.put("warranty", bruto.warranty());
        extensao.put("seller_custom_field", bruto.sellerCustomField());
        return SuporteJson.textoJson(extensao);
    }
}
