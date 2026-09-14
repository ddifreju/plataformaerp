import type { NextConfig } from "next";

// Endereço do backend Spring Boot. Em dev ele roda em outra porta
// (localhost:8080) — o rewrite abaixo faz o navegador conversar só com a
// própria origem do Next.js. Isso evita dois problemas ao mesmo tempo:
// CORS (não precisa configurar) e o cookie de sessão SameSite=Lax
// (application.yml do backend) ser descartado por ser "cross-site" numa
// chamada fetch direta a localhost:8080. O Next.js repassa a requisição
// por trás; o Set-Cookie da resposta chega ao navegador como se tivesse
// vindo da própria origem do frontend.
const ENDERECO_BACKEND = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // Empacota só o necessário para rodar em produção (server.js +
  // node_modules mínimos), usado pelo estágio "runtime" de
  // frontend/Dockerfile (tarefa 28). Não muda nada em `next dev`.
  output: "standalone",
  // O rewrite abaixo só existe para `next dev` local (localhost:3000 ->
  // localhost:8080). EM STAGING/PRODUÇÃO (infra/docker-compose.staging.yml)
  // quem decide se uma requisição é `/api/*` (vai pro backend) ou não
  // (vai pro frontend) é o CADDY (infra/Caddyfile, decisão 0032) — o Next
  // roda atrás dele na mesma origem e nunca recebe uma chamada de
  // `/api/*` para reescrever. BACKEND_URL não é lido em produção.
  async rewrites() {
    return [
      {
        source: "/api/:caminho*",
        destination: `${ENDERECO_BACKEND}/api/:caminho*`,
      },
    ];
  },
};

export default nextConfig;
