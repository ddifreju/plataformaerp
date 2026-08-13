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
