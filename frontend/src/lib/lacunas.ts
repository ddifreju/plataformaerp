import type { Lacuna } from "./api/tipos";

/**
 * Texto de "próximo passo" para cada lacuna, seguindo a regra de forma da
 * seção 3.3 do guia de interface: "frase 1 diz o que falta, frase 2 diz o
 * que fazer". A frase 1 já vem pronta do backend (`Lacuna.descricao`,
 * escrita no mesmo tom — nunca inventamos uma segunda versão dela, regra
 * 5 do CLAUDE.md: "nunca invente dado"). Esta função só decide a frase 2
 * (ação), mapeando pelo `codigo` estável que o catálogo do motor de
 * margem usa (`backend/.../margem/CatalogoLacunas.java`).
 *
 * IMPORTANTE (relatado no fim da tarefa): nenhuma tela de cadastro existe
 * ainda neste frontend (Fase 3, tarefas 18-20 não incluem "Cadastrar
 * taxa", "Configurar regime" etc.). O rótulo de ação é mostrado como
 * texto — não como link, para não apontar para uma rota que não existe.
 */
export interface AcaoLacuna {
  texto: string;
  rotulo: string;
}

export function acaoParaLacuna(lacuna: Lacuna): AcaoLacuna {
  const { codigo } = lacuna;

  if (codigo === "custo_mercadoria_nao_cadastrado" || codigo === "item_sem_variacao") {
    return {
      texto: "Cadastre o custo do produto e os pedidos afetados são recalculados.",
      rotulo: "Cadastrar custo",
    };
  }

  if (codigo.startsWith("taxa_canal_nao_cadastrada")) {
    if (codigo.includes("ANTECIPACAO")) {
      return {
        texto:
          "Isto é diferente das outras lacunas: se você antecipa recebíveis e eu não sei, a margem aparece maior " +
          "do que é de verdade. Responda em Configurações → Financeiro, mesmo que a resposta seja \"não antecipo\".",
        rotulo: "Configurar antecipação",
      };
    }
    return {
      texto: "Cadastre essa taxa e os pedidos que dependem dela são recalculados.",
      rotulo: "Cadastrar taxa",
    };
  }

  if (codigo === "regime_tributario_nao_configurado") {
    return {
      texto: "Informe o regime (Simples Nacional, Lucro Presumido...) em Configurações → Fiscal.",
      rotulo: "Configurar regime",
    };
  }

  if (codigo === "canais_sobrepostos") {
    return {
      texto: "Esta tela já evita a soma indevida: o seletor de canal acima exige um único canal por consulta.",
      rotulo: "Nenhuma ação necessária aqui",
    };
  }

  if (codigo === "repasse_diverge_quantificavel" || codigo === "repasse_previsto_ausente") {
    return {
      texto: "Confirme o valor de repasse previsto do canal para este período com o time técnico.",
      rotulo: "Revisar repasse",
    };
  }

  // Padrão geral (seção 3.3 do guia): quando o código não é um dos
  // catalogados acima, ainda assim nunca deixamos a lacuna sem próximo
  // passo — só que genérico, porque não sabemos mais que o backend nos
  // contou.
  return {
    texto: "Revise esta pendência — ela ainda não tem um cadastro dedicado nesta tela.",
    rotulo: "Sem ação disponível",
  };
}
