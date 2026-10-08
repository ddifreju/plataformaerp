"""Controle remoto do Radar local para os agentes de teste (só biblioteca padrão do Python).

Uso, dentro de um script de teste:

    import sys; sys.path.insert(0, r"C:\\Navega\\plataformaerp\\docs\\testes")
    from radar_api import Sessao

    dono = Sessao("dono@demo.plataforma")
    dados = dono.dados()                      # tudo o que a tela carrega (produtos, lojas...)
    ok, r = dono.comando("loja_salvar", marketplace="Shopee", nome="TESTE Shopee 1")
    print(ok, r)                              # ok=True/False; r = resposta (ou o erro)

Fala com http://localhost:3000 (o mesmo caminho da tela). Só para o ambiente LOCAL.
"""

import http.cookiejar
import json
import urllib.error
import urllib.request
import uuid

BASE = "http://localhost:3000"
SENHA = "demo1234"  # senha pública dos logins de demonstração locais


class Sessao:
    def __init__(self, email: str):
        self.email = email
        self._abridor = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
        )
        ok, r = self._pedir("POST", "/api/login", {"email": email, "senha": SENHA})
        if not ok:
            raise RuntimeError(f"Login de {email} falhou: {r}")

    def _pedir(self, metodo, caminho, corpo=None, cabecalhos=None):
        dados = None if corpo is None else json.dumps(corpo).encode("utf-8")
        req = urllib.request.Request(BASE + caminho, data=dados, method=metodo)
        req.add_header("Content-Type", "application/json")
        for k, v in (cabecalhos or {}).items():
            req.add_header(k, v)
        try:
            with self._abridor.open(req, timeout=120) as resp:
                texto = resp.read().decode("utf-8")
                return True, (json.loads(texto) if texto else {})
        except urllib.error.HTTPError as e:
            texto = e.read().decode("utf-8", "replace")
            try:
                return False, {"status": e.code, **json.loads(texto)}
            except ValueError:
                return False, {"status": e.code, "mensagem": texto[:300]}

    def dados(self) -> dict:
        ok, r = self._pedir("GET", "/api/radar")
        if not ok:
            raise RuntimeError(f"Falha ao carregar dados: {r}")
        return r

    def comando(self, op: str, **campos):
        """Executa uma operação como a tela faz. Devolve (ok, resposta)."""
        return self._pedir(
            "POST",
            "/api/radar/comandos",
            {"op": op, **campos},
            {"Idempotency-Key": str(uuid.uuid4()), "X-Radar-Request": "1"},
        )


if __name__ == "__main__":
    s = Sessao("dono@demo.plataforma")
    d = s.dados()
    print("login ok;", len(d.get("produtos", [])), "produtos;", len(d.get("lojas", [])), "lojas")
