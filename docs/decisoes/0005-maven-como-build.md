# 0005 — Maven como build do backend

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

## Contexto

O backend é Java 21 + Spring Boot 3. Precisa de uma ferramenta de build.
A fundadora vem de Java/Spring corporativo.

## Decisão

Maven, com `pom.xml` único no `backend/`. Sem multi-módulo por enquanto.

O `pom.xml` fixa a versão exata de cada coisa relevante (Java 21, Spring Boot,
Camel) — nada de versão flutuante.

## Alternativas consideradas

- **Gradle (Kotlin DSL).** Build mais rápido e configuração mais expressiva.
  Descartada: a vantagem aparece em projetos grandes e multi-módulo. Aqui o
  custo é ter que depurar build script em Kotlin quando algo quebra, num
  projeto onde a pessoa tem 20h/semana. Maven é declarativo e chato — que é
  exatamente a qualidade desejada num build para quem trabalha sozinho.
- **Maven multi-módulo desde já** (core, integracoes, api). Descartada: dividir
  antes de saber onde ficam as costuras cria fronteiras erradas. Enquanto o
  isolamento entre domínios estiver garantido por pacote, um módulo só basta.
  Dividir depois é mecânico.

## Consequências

- Build previsível, muita documentação disponível
- Sem cache incremental sofisticado: build completo é mais lento (irrelevante
  no tamanho atual)
- Migrar para Gradle depois é possível e localizado (um arquivo), por isso
  reversível
- **Pendência:** o Maven Wrapper (`mvnw`) não foi incluído porque exige o jar
  binário do wrapper, que não pode ser gerado sem Maven instalado. Ao instalar
  o Maven pela primeira vez, rode `mvn wrapper:wrapper` no `backend/` para que
  o build passe a ser autossuficiente. Registrado em `docs/PENDENCIAS.md`.
