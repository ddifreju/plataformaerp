# Radar — documentação e continuidade

Esta pasta reúne o manual mestre e o planejamento completo do Radar. A versão atual é uma avaliação local funcional; integrações reais, NF-e e agentes externos ainda estão em desenvolvimento.

## Comece aqui

- [Como rodar no seu computador](COMO-RODAR-LOCAL.md): passo a passo com Docker, acessos de demonstração e o aviso de `localhost`.
- [Manual mestre — 24 capítulos](Manual-mestre-Radar.txt): negócio, operação, engenharia e continuidade com outra IA.
- [Livro completo com anexos e código de referência](Manual-mestre-Radar-completo.txt): arquivo único para compartilhar com outra IA ou desenvolvedor.
- [Livro navegável em HTML](Entrega-local-Radar.html): baixe somente este arquivo e abra no navegador; não é necessário baixar um ZIP. Os links para anexos separados exigem os respectivos arquivos.
- [Plano completo de implementação](Plano-de-acao-Radar.md).
- [Estado atual e limitações](Estado-da-implementacao-Radar.txt).
- [Manifesto técnico](Manifesto-tecnico-Radar.json) e [evidência histórica dos testes locais](Verificacao-local-Radar.json).

O GitHub apresenta o código-fonte de HTML; esta publicação no repositório não hospeda um site. O endereço `127.0.0.1` mencionado nos documentos atende apenas no computador que executa o Radar.

## Para desenvolver

Clone o repositório normalmente e leia o manual, especialmente os capítulos 12 a 19 e 23. Código novo: `backend/src/main/java/com/plataforma/radar`, `frontend/src/app/radar` e migration V017. Os modelos analíticos anteriores foram preservados e ainda precisam de contrato de unificação com as tabelas `radar_*`.

O launcher em `infra/local-native` documenta a instalação **sem Docker** feita na estação original. Os caminhos absolutos são referências daquela estação, não um instalador pronto para qualquer máquina. Em outra estação, prepare Java 21, Node compatível e PostgreSQL 16, configure caminhos/segredos e ensaie o bootstrap em base vazia de desenvolvimento. Não use credenciais fictícias em produção.

O histórico nativo usa `radar_native_schema`, não `flyway_schema_history`. Não reaplique migrations indiscriminadamente. Leia `infra/local-native/LOCAL-RADAR.txt` antes de configurar banco ou executar fixtures. O atalho específico da estação, binários, banco e credenciais não são distribuídos aqui.

## O que não foi publicado

Banco local, `native-config.json`, arquivos `.env` reais, logs, binários do runtime, `node_modules`, builds e caches. O código contém contas fictícias de teste identificadas como demonstração; elas não são credenciais de produção.

Os resultados de testes documentados pertencem à execução local registrada em 30/09/2026; o envio ao GitHub não é uma homologação de produção. O manual foi escrito antes deste envio, portanto menções históricas a “nenhum commit/push” descrevem aquele momento.

Este material é interno. Antes de usar trechos comercialmente, remova caminhos pessoais e detalhes de operação, e preserve a distinção entre recurso disponível, simulação e planejamento.