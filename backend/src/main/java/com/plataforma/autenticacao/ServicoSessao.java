package com.plataforma.autenticacao;

import org.springframework.stereotype.Service;

import com.plataforma.comum.tenant.RepositorioTenant;
import com.plataforma.comum.tenant.Tenant;

/**
 * Monta a resposta de {@code GET /api/sessao} - a unica logica aqui e
 * buscar o nome de exibicao do tenant para acompanhar o restante dos
 * dados que ja vem prontos em {@link UsuarioAutenticado}.
 *
 * {@link RepositorioTenant} nao tem RLS (a tabela tenant e catalogo
 * administrativo - V002) e nao precisa: estamos buscando o nome da
 * PROPRIA loja do usuario ja autenticado, o mesmo tenantId que o
 * FiltroTenant ja validou como ativo para esta requisicao.
 */
@Service
public class ServicoSessao {

    private final RepositorioTenant repositorioTenant;

    public ServicoSessao(RepositorioTenant repositorioTenant) {
        this.repositorioTenant = repositorioTenant;
    }

    public RespostaSessao sessaoAtual(UsuarioAutenticado usuario) {
        String tenantNome = repositorioTenant.findById(usuario.tenantId())
                .map(Tenant::getNome)
                .orElse(null);

        return new RespostaSessao(
                usuario.getUsername(), usuario.nome(), usuario.papel(), usuario.tenantId(), tenantNome);
    }
}
