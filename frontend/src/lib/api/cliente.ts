import type {
  ErroApi,
  RequisicaoMargemPeriodo,
  RequisicaoPergunta,
  RespostaCanal,
  RespostaFilaPendencias,
  RespostaGargalosProcesso,
  RespostaMargemPeriodo,
  RespostaPergunta,
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
 * Caminhos onde um 401 NÃO significa "sessão expirada" e por isso não
 * deve disparar o redirecionamento duro para `/login` (achado de
 * segurança da revisão da Fase 3): `entrar()` chama `/api/login` e senha
 * errada também devolve 401 — sem esta exclusão, cada tentativa malsucedida
 * recarregava a página inteira, e a pessoa nunca chegava a ver o
 * `setErro("E-mail ou senha inválidos.")` da tela de login. `/api/logout`
 * entra pelo mesmo motivo: uma sessão já encerrada não deveria "redirecionar
 * para o login" como se fosse uma sessão que expirou no meio do uso.
 */
const CAMINHOS_SEM_REDIRECIONAMENTO_NO_401 = ["/api/login", "/api/logout"];

/**
 * Todas as chamadas passam por aqui. `credentials: 'include'` (decisão
 * 0022/README: "o navegador nunca escolhe o tenant" — o cookie de sessão
 * é quem carrega essa informação, resolvido no servidor).
 *
 * Usa `response.json()` direto: o backend garante, na origem, que todo
 * `BigDecimal` (dinheiro) sai como STRING via `toPlainString()`
 * (`ConfiguracaoJackson`, `backend/.../comum/web/ConfiguracaoJackson.java`)
 * — nunca como número JSON. Não existe mais um valor monetário que o
 * `JSON.parse` nativo possa converter em `double` pelo caminho. Um parser
 * escrito à mão (`jsonSeguro.ts`) existiu aqui para se defender do mesmo
 * risco quando essa garantia só existia do lado do frontend; com o
 * contrato do backend fechado, mantê-lo era dívida (~190 linhas sem guarda
 * de profundidade, sem tratar literais malformados) para proteger algo que
 * já está protegido na origem. NÃO reintroduza esse parser sem antes
 * verificar se `ConfiguracaoJackson` ainda está no lugar — ver
 * `docs/decisoes/0026-dinheiro-como-texto-vem-do-backend.md`.
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

  if (resposta.status === 401 && !CAMINHOS_SEM_REDIRECIONAMENTO_NO_401.some((c) => caminho.startsWith(c))) {
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
  const corpo = texto.length > 0 ? (JSON.parse(texto) as unknown) : null;

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
 * POST /api/margem/periodo — `canalId` é OBRIGATÓRIO (decisão 0021: nunca
 * "todos os canais"). `inicio`/`fim` são `OffsetDateTime` ISO-8601.
 *
 * É POST, não GET: `ServicoMargemPeriodo.calcular` grava uma linha de
 * auditoria a cada chamada, então a operação altera estado (decisão 0034;
 * ver o comentário equivalente em `MargemController.java`).
 */
export function buscarMargemPeriodo(parametros: RequisicaoMargemPeriodo): Promise<RespostaMargemPeriodo> {
  return requisitar<RespostaMargemPeriodo>("/api/margem/periodo", {
    method: "POST",
    body: JSON.stringify(parametros),
  });
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

/**
 * POST /api/pergunta — camada de IA da decisão 0030. É POST, não GET:
 * {@code ServicoPergunta.responder} grava uma {@code ConsultaAuditada} a
 * cada chamada, inclusive em RECUSA e ESCLARECIMENTO (ver o Javadoc de
 * `PerguntaController.java`) — a rota altera estado mesmo parecendo uma
 * leitura.
 */
export function perguntar(texto: string): Promise<RespostaPergunta> {
  const corpo: RequisicaoPergunta = { texto };
  return requisitar<RespostaPergunta>("/api/pergunta", {
    method: "POST",
    body: JSON.stringify(corpo),
  });
}
