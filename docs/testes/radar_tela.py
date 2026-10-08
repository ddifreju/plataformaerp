"""Navegador automático do Radar local para os agentes de teste (Playwright + Edge desta máquina).

Uso, dentro de um script de teste:

    import sys; sys.path.insert(0, r"C:\\Navega\\plataformaerp\\docs\\testes")
    from radar_tela import abrir

    with abrir("dono@demo.plataforma") as (pg, erros):
        pg.get_by_role("button", name="mais ações").click()
        pg.get_by_role("menuitem", name="imprimir relatório").click()
        pg.screenshot(path=r"C:\\Navega\\Interno\\navega\\_tmp_testes_radar\\menu.png")
        print(erros)   # erros do console e respostas 4xx/5xx do servidor, com o endereço

`abrir` entra com o login de demonstração e já deixa a página em Cadastros → Produtos.
`largura=390` simula celular. `visivel=True` mostra a janela (para acompanhar).
Só para o ambiente LOCAL (http://localhost:3000).
"""

from contextlib import contextmanager

from playwright.sync_api import sync_playwright

BASE = "http://localhost:3000"
SENHA = "demo1234"  # senha pública dos logins de demonstração locais


@contextmanager
def abrir(email: str, largura: int = 1440, visivel: bool = False, produtos: bool = True):
    with sync_playwright() as p:
        nav = p.chromium.launch(channel="msedge", headless=not visivel)
        ctx = nav.new_context(
            viewport={"width": largura, "height": 900 if largura > 500 else 844},
            accept_downloads=True,
        )
        pg = ctx.new_page()
        pg.set_default_timeout(15000)
        erros: list[str] = []
        pg.on("console", lambda m: m.type == "error" and erros.append(f"console: {m.text}"))
        pg.on("pageerror", lambda e: erros.append(f"erro na página: {e}"))
        pg.on(
            "response",
            lambda r: r.status >= 400
            and "/api/" in r.url
            and erros.append(f"servidor {r.status}: {r.request.method} {r.url}"),
        )
        pg.goto(BASE + "/radar")
        pg.get_by_label("E-mail").fill(email)
        pg.get_by_label("Senha").fill(SENHA)
        pg.get_by_role("button", name="Entrar no Radar").click()
        if produtos:
            pg.get_by_role("button", name="Cadastros").click()
            pg.get_by_role("button", name="Produtos").first.click()
            pg.get_by_role("heading", name="Produtos", exact=True).wait_for()
        # Antes do login a tela pergunta a sessão e recebe 401: isso é normal, não é erro.
        erros.clear()
        try:
            yield pg, erros
        finally:
            nav.close()


if __name__ == "__main__":
    with abrir("dono@demo.plataforma") as (pg, erros):
        print("ok:", pg.locator(".rd-abas-tipo").inner_text().replace("\n", " "))
        print("erros:", erros)
