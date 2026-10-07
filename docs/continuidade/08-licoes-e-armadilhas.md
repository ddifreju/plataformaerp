# Lições e armadilhas (já aconteceram)

1. **Nome de operação repetido:** `anuncios_lote` já existia ("Preparar 4
   canais"). Usar o mesmo nome para outra coisa deu "Identificador
   inválido". Antes de criar uma operação, procure por `case "nome"` no
   `RadarService`.
2. **Largura do prettier errada** reformata o arquivo inteiro e o diff fica
   gigante. Confira a largura de cada arquivo em `05-codigo-e-padroes.md`.
3. **Matar processo por texto** (`pkill -f`, `ps | grep java`) pega o
   próprio shell, porque o comando contém o mesmo texto, e derruba tudo
   (saída 144). Guarde o PID ao subir (`echo $! > arquivo.pid`) e mate por
   ele.
4. **Testcontainers sem Docker:** dezenas de testes falham com "Could not
   find a valid Docker environment". Não é defeito do código: suba o Docker.
5. **Banco local com checksum diferente no Flyway** (V021): aplique a
   migration nova direto com `psql`.
6. **Login no teste:** um cookie de uma tentativa que falhou (502 enquanto o
   Render acordava) estragava as tentativas seguintes. Refaça o login a cada
   tentativa. O endereço é `/api/login`, não `/api/sessao/login`.
7. **Operador `?` do jsonb dentro do JdbcTemplate** é confundido com
   parâmetro. Use `@> jsonb_build_array(?::text)` ou `jsonb_exists(...)`.
8. **Busca com negação:** "não enviado" casava com "enviado". O filtro
   genérico já trata (lookbehind "não/sem"); mantenha isso em filtros novos.
9. **CEP e números com separador:** a busca compara só os dígitos. Já está
   no componente.
10. **"Essa não é a plataforma":** a Juliana abriu `/login` (tela antiga) e
    achou que tinha perdido o sistema. A plataforma é **`/radar`**. Sempre
    mande o link com `/radar`.
11. **Oracle Cloud** recusou o cadastro 3 vezes. Não insista sem ela pedir;
    o plano B (Vercel + Render + despertador) está funcionando.
12. **Selo "habilitado" nos prints** de referência não significa que está
    habilitado no Radar. Mostre "não configurado" até ser verdade (regra 5).
13. **Fazer PR antes de testar no navegador** já deixou passar layout
    quebrado (a barra de lote de pedidos quebrava em 2 linhas por 2 px).
    Teste com Playwright em 1440 px e em 390 px quando mexer em layout.
