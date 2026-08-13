import { parseJsonPreservandoNumeros } from "./jsonSeguro";
import type {
  ErroApi,
  RespostaCanal,
  RespostaFilaPendencias,
  RespostaGargalosProcesso,
  RespostaMargemPeriodo,
  RespostaSessao,
} from "./tipos";

/**
 * Erro de chamada à API — carrega o código estável (`erro`) e a mensagem
 * em português (`mensagem`) que o backend sempre devolve no formato
 * `ErroApi`, mais o status HTTP para quem precisar decidir por ele.
 */
export class ErroApiCliente extends Error {
  constructor(
    public readonly codigo: string,
    mensagem: string,
    public readonly status: number,
  ) {
    super(mensagem);
    this.name = "ErroApiCliente";
  }
}

const MENSAGEM_ERRO_GENERICA = "Não consegui falar com o servidor. Tente novamente em instantes.";

/**
 * Todas as chamadas passam por aqui. `credentials: 'include'` (decisão
 * 0022/README: "o navegador nunca escolhe o tenant" — o cookie de sessão
 * é quem carrega essa informação, resolvido no servidor). Nunca usa
 * `response.json()`: o corpo é lido como texto e reparseado por
 * `parseJsonPreservandoNumeros`, para que nenhum valor monetário passe
 * pelo `number` nativo do JavaScript nesse meio do caminho.
 */
async function requisitar<T>(caminho: string, opcoes: RequestInit = {}): Promise<T> {
  let resposta: Response;
  try {
    resposta = await fetch(caminho, {
      ...opcoes,
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        ...opcoes.headers,
      },
    });
  } catch {
    throw new ErroApiCliente("falha_de_rede", MENSAGEM_ERRO_GENERICA, 0);
  }

  if (resposta.status === 401) {
    if (typeof window !== "undefined") {
      // Redirecionamento "duro" (não `router.push`) de propósito: este
      // módulo não é um componente React (é chamado de qualquer lugar,
      // inclusive fora de uma árvore React) e uma sessão expirada deve
      // limpar todo estado em memória do cliente, não só trocar de rota.
      // eslint-disable-next-line @next/next/no-location-assign-relative-destination -- ver comentário acima
      window.location.href = "/login";
    }
    throw new ErroApiCliente("nao_autenticado", "Faça login para continuar.", 401);
  }

  const texto = await resposta.text();
  const corpo = texto.length > 0 ? (parseJsonPreservandoNumeros(texto) as unknown) : null;

  if (!resposta.ok) {
    const erroApi = corpo as Partial<ErroApi> | null;
    throw new ErroApiCliente(
      erroApi?.erro ?? "erro_desconhecido",
      erroApi?.mensagem ?? MENSAGEM_ERRO_GENERICA,
      resposta.status,
    );
  }

  return corpo as T;
}

/** GET /api/sessao — quem está logado, incluindo o tenant (nunca decidido pelo navegador). */
export function buscarSessao(): Promise<RespostaSessao> {
  return requisitar<RespostaSessao>("/api/sessao");
}

/** POST /api/login — 204 sem corpo em caso de sucesso. Mensagem de erro sempre genérica (tarefa 17). */
export async function entrar(email: string, senha: string): Promise<void> {
  await requisitar<null>("/api/login", {
    method: "POST",
    body: JSON.stringify({ email, senha }),
  });
}

/** POST /api/logout. */
export async function sair(): Promise<void> {
  await requisitar<null>("/api/logout", { method: "POST" });
}

/**
 * GET /api/margem/periodo — `canalId` é OBRIGATÓRIO (decisão 0021: nunca
 * "todos os canais"). `inicio`/`fim` são `OffsetDateTime` ISO-8601.
 */
export function buscarMargemPeriodo(parametros: {
  inicio: string;
  fim: string;
  canalId: string;
}): Promise<RespostaMargemPeriodo> {
  const query = new URLSearchParams(parametros);
  return requisitar<RespostaMargemPeriodo>(`/api/margem/periodo?${query.toString()}`);
}

/** GET /api/painel/gestor — visão "Operação": gargalos de processo, nunca de pessoa (decisão 0003). */
export function buscarPainelGestor(): Promise<RespostaGargalosProcesso> {
  return requisitar<RespostaGargalosProcesso>("/api/painel/gestor");
}

/** GET /api/painel/analista — visão "Pendências": fila de lacunas acionáveis. */
export function buscarPainelAnalista(): Promise<RespostaFilaPendencias> {
  return requisitar<RespostaFilaPendencias>("/api/painel/analista");
}

/**
 * Canais do tenant, para o seletor da tela de Resultado.
 *
 * A decisão 0021 exige UM canal explícito por consulta — nunca "todos".
 * Sem esta lista, a única saída seria pedir o UUID digitado à mão, o que
 * ninguém faz numa ferramenta de trabalho.
 */
export function listarCanais(): Promise<RespostaCanal[]> {
  return requisitar<RespostaCanal[]>("/api/canais");
}
