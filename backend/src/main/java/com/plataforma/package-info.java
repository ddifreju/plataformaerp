/**
 * Plataforma de gestao para e-commerce brasileiro. Multi-tenant.
 *
 * <h2>Onde validar: em Java ou no CHECK do banco?</h2>
 *
 * Esta regra estava implicita, cada entidade explicando so o proprio
 * caso. Registrada aqui uma vez para valer para todas:
 *
 * <p><b>A entidade valida em Java quando o valor NASCE dentro do proprio
 * processo. Quando o valor vem de fonte externa, ela confia no CHECK do
 * banco.</b>
 *
 * <p>Exemplo dos dois lados:
 * <ul>
 *   <li>{@code EventoIngerido.hashPayload} e calculado pelo nosso proprio
 *       codigo segundos antes de gravar. Validar o formato em Java e um
 *       autoteste: pega um bug de geracao de hash antes da ida ao banco.
 *       Por isso ele valida.</li>
 *   <li>{@code Cliente.documentoHash}, {@code Pedido.cepEntrega} e afins
 *       vem do adaptador, que ja normaliza. Repetir a regex em Java
 *       criaria uma SEGUNDA fonte de verdade, que pode divergir do CHECK
 *       sem ninguem perceber. Uma regra em dois lugares e uma regra que
 *       vai divergir. Por isso eles nao validam.</li>
 * </ul>
 *
 * <p>O CHECK do banco e sempre a ultima camada, para os dois casos. A
 * validacao em Java nunca o substitui: ela so antecipa o erro quando
 * antecipar tem valor.
 *
 * <h2>As duas regras que nunca se negociam</h2>
 * <ul>
 *   <li><b>Tenant e identidade, nao filtro.</b> Ver
 *       {@code docs/decisoes/0007-propagacao-de-tenant.md} e
 *       {@code 0010-padrao-de-rls-por-tabela.md}. Nenhuma consulta
 *       escreve o predicado de tenant a mao: {@code @TenantId} injeta, e
 *       o Row Level Security barra no banco. SQL nativo e a excecao
 *       perigosa - ele NAO recebe o predicado, e precisa passar
 *       {@code tenant_id} explicitamente, sempre como bind parameter.</li>
 *   <li><b>Dinheiro nunca e float.</b> {@code BigDecimal} no Java,
 *       {@code NUMERIC(18,4)} no Postgres, escala e {@code RoundingMode}
 *       declarados em toda operacao. Ao ler JSON de fonte externa, o
 *       valor vai para {@code BigDecimal} pela representacao textual -
 *       ver {@code com.plataforma.integracao.SuporteJson}.</li>
 * </ul>
 */
package com.plataforma;
