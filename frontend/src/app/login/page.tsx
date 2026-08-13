"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";

import { entrar, ErroApiCliente } from "@/lib/api/cliente";
import { NOME_PRODUTO } from "@/lib/marca";

const MENSAGEM_ERRO_PADRAO = "Não consegui entrar agora. Tente novamente em instantes.";

export default function PaginaLogin() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function aoEnviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErro(null);
    setEnviando(true);
    try {
      await entrar(email, senha);
      router.push("/resultado");
    } catch (erroCapturado) {
      // Mensagem sempre genérica (tarefa 17, armadilha 5 da V014): nunca
      // dizemos se foi o e-mail que não existe ou a senha que está errada.
      if (erroCapturado instanceof ErroApiCliente && erroCapturado.status === 401) {
        setErro("E-mail ou senha inválidos.");
      } else if (erroCapturado instanceof ErroApiCliente) {
        setErro(erroCapturado.message || MENSAGEM_ERRO_PADRAO);
      } else {
        setErro(MENSAGEM_ERRO_PADRAO);
      }
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="flex flex-1 items-center justify-center bg-superficie px-4 py-16">
      <div className="w-full max-w-sm rounded border border-borda bg-background p-6">
        <h1 className="text-lg text-foreground">{NOME_PRODUTO}</h1>
        <p className="mt-1 text-sm text-texto-secundario">Entre para continuar.</p>

        <form className="mt-6 flex flex-col gap-4" onSubmit={aoEnviar} noValidate>
          <div className="flex flex-col gap-1">
            <label htmlFor="email" className="text-sm font-medium text-foreground">
              E-mail
            </label>
            <input
              id="email"
              name="email"
              type="email"
              autoComplete="username"
              required
              value={email}
              onChange={(evento) => setEmail(evento.target.value)}
              className="rounded border border-borda px-3 py-2 text-base text-foreground"
            />
          </div>

          <div className="flex flex-col gap-1">
            <label htmlFor="senha" className="text-sm font-medium text-foreground">
              Senha
            </label>
            <input
              id="senha"
              name="senha"
              type="password"
              autoComplete="current-password"
              required
              value={senha}
              onChange={(evento) => setSenha(evento.target.value)}
              className="rounded border border-borda px-3 py-2 text-base text-foreground"
            />
          </div>

          {erro && (
            <p role="alert" className="text-sm text-valor-negativo">
              {erro}
            </p>
          )}

          <button
            type="submit"
            disabled={enviando}
            className="mt-2 rounded bg-acao px-4 py-2 text-sm font-medium text-white hover:bg-acao-hover disabled:opacity-60"
          >
            {enviando ? "Entrando…" : "Entrar"}
          </button>
        </form>
      </div>
    </main>
  );
}
