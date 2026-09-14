package com.plataforma.canal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.plataforma.autenticacao.UsuarioAutenticado;

/**
 * Lista os canais do tenant, para a interface montar o seletor, e recebe
 * a declaracao de escopo de canal (tarefa 31, decisao 0033).
 *
 * PORQUE {@code GET /api/canais} EXISTE: a decisao 0021 obriga a tela de
 * Resultado a informar UM canal explicito - nunca "todos", porque somar
 * canais sobrepostos contaria a mesma venda duas vezes. Sem uma lista, a
 * unica saida do frontend era pedir o UUID do canal digitado a mao, o que
 * ninguem faz numa ferramenta de trabalho.
 *
 * PORQUE {@code POST /api/canais/{id}/escopo} EXISTE: a 0033 tirou a
 * consequencia da 0017/0021 (nunca somar canais potencialmente
 * sobrepostos) e perguntou "quem sabe se sao sobrepostos?" - resposta: so
 * a lojista. Este endpoint e onde ela declara isso. E POST porque ALTERA
 * ESTADO (o oposto do GET acima) - nao ha tensao com a decisao 0025 aqui,
 * so os dois verbos corretos convivendo no mesmo recurso.
 *
 * NAO ha tenantId na assinatura de nenhum metodo: as consultas filtram
 * por tenant sozinhas via @TenantId (decisao 0007, camada 3), a partir do
 * ContextoTenant que o FiltroTenant preencheu com o tenant do usuario
 * AUTENTICADO. Aceitar tenant por parametro aqui seria reabrir o buraco
 * que a decisao 0023 fechou.
 *
 * Sem logica de negocio no controller (CLAUDE.md): so recebe, delega para
 * {@link ServicoEscopoDeCanal} (que faz toda a validacao de cadeia/ciclo)
 * e devolve o resultado ja mapeado.
 */
@RestController
@RequestMapping("/api/canais")
public class CanalController {

    private final RepositorioCanal repositorioCanal;
    private final ServicoEscopoDeCanal servicoEscopoDeCanal;

    public CanalController(RepositorioCanal repositorioCanal, ServicoEscopoDeCanal servicoEscopoDeCanal) {
        this.repositorioCanal = repositorioCanal;
        this.servicoEscopoDeCanal = servicoEscopoDeCanal;
    }

    @GetMapping
    public List<RespostaCanal> listar() {
        return repositorioCanal.findAllByOrderByNomeAsc()
                .stream()
                .map(RespostaCanal::de)
                .toList();
    }

    @PostMapping("/{id}/escopo")
    public RespostaCanal declararEscopo(@PathVariable UUID id, @RequestBody @Valid RequisicaoEscopoCanal requisicao,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        Canal canal = servicoEscopoDeCanal.declarar(id, requisicao.escopo(), requisicao.espelhaCanalId(),
                usuario.usuarioId());
        return RespostaCanal.de(canal);
    }
}
