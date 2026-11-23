# DataTerra AI: busca geográfica, conversas, visualizações e observabilidade

Este documento complementa o [README do agente](README-AGENTE.md) e descreve o contrato com memória persistente.

Atualizado em 12/11/2026 conforme a implementação local. Inclui recuperação de conversas, datasets paginados e planos de tabelas/gráficos validados pelo backend.

## Implantação

Antes de utilizar conversas em um banco existente, aplique de maneira autorizada o script aditivo [20261105_agent_conversation.sql](src/main/resources/db/migrations/20261105_agent_conversation.sql).

O script cria apenas a tabela agent_conversation e seu índice de proprietário, com IF NOT EXISTS. Não altera registros das instalações, nomes dos municípios ou relacionamentos existentes. Não foi aplicado ao Supabase durante esta implementação e não roda automaticamente em produção. A definição também está em db/schema.sql para bancos novos e testes.

Não foi adicionada dependência de Flyway. Se a tabela não existir ou estiver indisponível, o endpoint retorna 503 controlado, sem substituir a persistência por memória volátil.

## Busca geográfica

A comparação exata normaliza ambos os lados: acentuação portuguesa, marcas Unicode combinantes, maiúsculas/minúsculas, espaços repetidos, espaços nas extremidades e espaços não separáveis. O nome armazenado permanece original.

Hortolândia, Hortolandia e HORTOLANDIA encontram o mesmo município. São Paulo e Sao Paulo são equivalentes. O argumento de estado aceita UF ou nome completo, com validação no Java. Essa conversão não depende do Gemini.

São utilizadas funções nativas LOWER, TRANSLATE, REGEXP_REPLACE, REPLACE e TRIM. Não é necessário instalar unaccent nem criar uma função no banco. A implementação não amplia automaticamente o filtro: Hortol não corresponde a Hortolandia.

Os filtros exatos Eq e In de cidade, estado, empresa e nome utilizam a normalização. Os filtros parciais já existentes em outros endpoints não foram indiscriminadamente alterados. Valores continuam vinculados; os nomes de colunas vêm da lista autorizada.

Arquivos envolvidos:

- [TextNormalization.java](src/main/java/data_centers/brasil/filter/TextNormalization.java): normalização Java, expressão Criteria/JPA, expressão SQL e conversão dos 27 estados.
- [JpaFilters.java](src/main/java/data_centers/brasil/filter/JpaFilters.java): comparação normalizada em filtros exatos JPA.
- [SqlFilters.java](src/main/java/data_centers/brasil/filter/SqlFilters.java): comparação equivalente nas consultas JDBC.
- [AgentDataBaseService.java](src/main/java/data_centers/brasil/agent/service/AgentDataBaseService.java): validação das UFs e filtros das ferramentas.
- [AgentQueryRepository.java](src/main/java/data_centers/brasil/agent/repository/AgentQueryRepository.java): operadores filtráveis por cidade/estado e municípios distintos com normalização.

A operação OPERADORES aceita estado, cidade, empresa e nome. Isso permite responder quais empresas possuem instalações em Hortolândia sem carregar todas as instalações. A agregação não unifica operadores diferentes por suposição de equivalência.

Funções sobre colunas podem exigir índices de expressão em bases maiores. Nenhum índice destrutivo ou alteração de dados foi executado. Referência: [funções de strings do PostgreSQL](https://www.postgresql.org/docs/current/functions-string.html).

## Contrato HTTP

O endpoint permanece POST /api/agent/chat, com a autenticação existente.

Primeira mensagem, sem identificador:

```json
{"message":"Quantos data centers existem em São Paulo?"}
```

Resposta ilustrativa, sem número fixo no código:

```json
{
  "response":"Texto baseado na contagem consultada.",
  "fonte":"banco_de_dados",
  "consulta":[],
  "conversationId":"6bffcecb-2fe0-4d25-b167-d4fa86d09130"
}
```

O UUID acima é somente um exemplo. Guarde o UUID realmente retornado pelo backend e use-o nas próximas mensagens:

```json
{
  "conversationId":"6bffcecb-2fe0-4d25-b167-d4fa86d09130",
  "message":"E no Rio de Janeiro?"
}
```

Continue com o mesmo ID para perguntar qual dos dois possui mais, qual a diferença entre eles e qual a porcentagem dessa diferença em relação ao Rio de Janeiro.

Sem conversationId, sempre é criada outra conversa. O frontend não deve gerar o ID, misturar IDs de chats distintos ou enviar mensagens simultâneas para a mesma conversa. Para iniciar uma conversa nova, omita o campo.

O campo consulta permanece uma lista para compatibilidade, com conteúdo SQL liberado somente em desenvolvimento explicitamente autorizado. Quando reutiliza contagens históricas, devolve o SQL original salvo com essas contagens, sem refazer a consulta. O campo adicional consultaOrigem informa atual, historico, mista, nenhuma ou oculta.

## Como a memória funciona

O PostgreSQL armazena UUID, usuário proprietário, histórico JSON em TEXT, versão e data de atualização. A identidade vem do Principal autenticado, não de um campo fornecido pelo frontend.

Pessoas que compartilham exatamente a mesma credencial compartilham a identidade do backend. Isolamento individual exige credenciais individuais.

A cada mensagem, o backend recupera a conversa pelo par UUID/usuário. IDs inexistentes, expirados ou pertencentes a outro usuário produzem a mesma resposta 404.

Cada turno persistido contém pergunta, resposta, origem, data e evidências estruturadas de ferramentas. Os resultados são preservados enquanto couberem nos limites da memória; não existe uma amostragem fixa de dez registros. Quando um turno isolado excede o limite, a resposta é reduzida a até 2000 caracteres e resultados tabulares são substituídos por referências com registros vazios, metadados e `_requiresRefresh=true`. Se ainda exceder o limite, os resultados são substituídos por uma mensagem que solicita nova consulta.

O SQL bruto é associado ao resultado em metadados internos persistidos. Enquanto a evidência integral é mantida, as strings SQL são preservadas sem truncagem. A redução para referências pode remover esses metadados, mesmo preservando a requisição necessária para consultar o dataset novamente. O SQL interno não é enviado ao Gemini nem aos logs. Não é necessária migração adicional da tabela, pois o histórico já é JSON em TEXT.

Os SQLs históricos são selecionados pelas evidências realmente reutilizadas, não por uma cópia de todas as queries da conversa. Atualizar a comparação devolve SQL das novas execuções com consultaOrigem atual. Comparar uma contagem histórica com outra obtida agora produz mista. As strings podem se repetir: duas contagens com parâmetros distintos podem ter o mesmo texto preparado com interrogações.

Conversas criadas antes dessa atualização podem não conter SQL salvo. Nesses casos o backend não inventa nem reconstrói a query; peça uma atualização dos dados para registrar novas consultas. Respostas conceituais não herdam SQL de mensagens anteriores.

O histórico enviado ao modelo identifica os resultados como dados históricos, não como instruções. Para resultados tabulares, envia esquema e metadados do dataset em vez de todas as linhas; a resposta anterior do assistente também é substituída por uma indicação de que os resultados estão preservados no backend. Não são reconstruídas chamadas de função fictícias ou assinaturas artificiais. No turno vivo, o protocolo continua preservando as assinaturas reais do Gemini.

A memória é lida do banco em cada requisição e sobrevive à recriação do serviço ou ao reinício da aplicação. Não depende de variáveis locais ou logs.

| Configuração | Padrão | Intervalo aplicado |
| --- | --- | --- |
| app.agent.memory.max-turns | 8 turnos | 1 a 20 |
| app.agent.memory.max-chars | 60000 caracteres serializados | 8000 a 200000 |
| app.agent.memory.ttl-days | 7 dias sem atualização | 1 a 90 |

Variáveis de ambiente: AGENT_MEMORY_MAX_TURNS, AGENT_MEMORY_MAX_CHARS e AGENT_MEMORY_TTL_DAYS. O limite de caracteres controla o tamanho do contexto; não é uma contagem exata de tokens.

Turnos antigos são removidos como unidades quando o limite é excedido. Um turno isolado muito grande tem resposta/resumos reduzidos com indicação de omissão. Referências removidas podem exigir nova consulta ou esclarecimento.

O TTL é uma expiração lógica: não há exclusão física automática das linhas. Políticas de retenção física devem ser aplicadas separadamente de maneira autorizada.

Não existe transação ou lock mantido durante a chamada ao Gemini. O salvamento usa versão otimista: duas mensagens simultâneas podem produzir 409 em vez de sobrescrever silenciosamente o histórico.

### Arquivos de memória e resposta

| Arquivo | Responsabilidade |
| --- | --- |
| [AgentConversationStore.java](src/main/java/data_centers/brasil/agent/memory/AgentConversationStore.java) | INSERT, leitura por ID/usuário e UPDATE condicionado à versão. |
| [AgentConversationService.java](src/main/java/data_centers/brasil/agent/memory/AgentConversationService.java) | Abrir sessão, aplicar TTL, resumir ferramentas, limitar tamanho e reconstruir histórico. |
| [AgentConversationException.java](src/main/java/data_centers/brasil/agent/memory/AgentConversationException.java) | Associar o ID a erros controlados após abertura da conversa. |
| [AgentExceptionHandler.java](src/main/java/data_centers/brasil/agent/controller/AgentExceptionHandler.java) | Retornar ProblemDetail com conversationId quando disponível. |
| [AgentController.java](src/main/java/data_centers/brasil/agent/controller/AgentController.java) | Receber ChatRequest com message/UUID e repassar a identidade autenticada. |
| [AgentChatResponse.java](src/main/java/data_centers/brasil/agent/dto/AgentChatResponse.java) | Preservar response/fonte/consulta e acrescentar conversationId. |
| [AgentService.java](src/main/java/data_centers/brasil/agent/service/AgentService.java) | Recuperar a memória, conduzir o turno, persistir o resultado e aplicar a política de exposição SQL. |

O endpoint utiliza perguntarComFonte(message, conversationId, owner). As entradas internas antigas sem argumentos de sessão permanecem para chamadas diretas/testes e não são utilizadas pelo controller HTTP.

## Comparações confiáveis

A ferramenta comparar_contagens aceita estadoA, estadoB, percentualSobre opcional e atualizar opcional. Não aceita números calculados pelo modelo.

O [AgentComparisonService.java](src/main/java/data_centers/brasil/agent/service/AgentComparisonService.java) recupera contagens documentadas dos estados no histórico. Se faltar uma contagem compatível, consulta o banco. Com atualizar=true, refaz as duas contagens.

O resultado inclui os totais, maior estado ou empate, diferença absoluta, datas e descrição da base temporal. Para percentual, aplica:

```text
abs(totalA - totalB) / totalDoEstadoDenominador * 100
```

O denominador deve ser um dos estados comparados. Zero produz limitação explícita, sem inventar percentual. O cálculo usa BigDecimal e quatro casas decimais.

Comparações históricas são identificadas como baseadas nas consultas anteriores, com suas datas. Perguntas que exigem atualização devem utilizar atualizar=true ou novas consultas. O histórico não é tratado como medição atual.

O [prompt inicial](src/main/resources/agent/system-prompt.txt) orienta a resolver referências pelo histórico e a pedir esclarecimento quando ele for insuficiente. A seleção das ferramentas continua sendo feita pelo Gemini; não foi adicionado um classificador geral por palavras-chave.

O [AgentToolDeclarations.java](src/main/java/data_centers/brasil/agent/tool/AgentToolDeclarations.java) declara `comparar_contagens` junto de `consultar_dataterra`, `resposta_conceitual` e `planejar_visualizacao`. O [AgentFallbackFormatter.java](src/main/java/data_centers/brasil/agent/formatter/AgentFallbackFormatter.java) também apresenta resultados de comparação quando o Gemini falha.

Chamadas idênticas à ferramenta de banco no mesmo turno reutilizam o resultado sem repetir a consulta. Novos turnos não reutilizam esse cache de requisição. A ferramenta de comparação tem sua política histórica explícita.

## Recuperação de conversas

Os endpoints usam a identidade autenticada, sem aceitar um proprietário informado pelo cliente:

| Endpoint GET | Resposta |
| --- | --- |
| `/api/agent/conversations` | Lista de até 100 conversas com versão maior que zero, em ordem de atualização decrescente: `id`, `title`, `updatedAt`, `expired`. |
| `/api/agent/conversations/{id}` | Objeto com `id`, `messages` e `expired`. Cada mensagem contém `role` (`user` ou `assistant`) e `content`. |

O título usa até 100 caracteres da primeira pergunta ainda retida. A leitura devolve as mensagens que permaneceram após os limites de memória, sem reconstruir turnos removidos ou devolver as evidências internas e especificações visuais. Conversas expiradas continuam disponíveis nesses GETs com `expired=true`; tentar continuá-las pelo chat ou acessar seus datasets retorna 404. A listagem não oferece paginação e não há endpoint de exclusão implementado nesses controllers.

Implementação: [AgentConversationsController.java](src/main/java/data_centers/brasil/agent/controller/AgentConversationsController.java) e [AgentConversationService.java](src/main/java/data_centers/brasil/agent/memory/AgentConversationService.java).

## Tabelas e gráficos

A resposta de chat acrescenta `chartData` e `visualization` ao contrato existente. Ambos carregam a mesma especificação declarativa e podem ser nulos. O frontend deve renderizar apenas uma das representações para evitar duplicação. Não é uma imagem pronta nem código executável.

A ferramenta `planejar_visualizacao` propõe `datasetId`, `type` (`table` ou `chart`), `chartType`, título, descrição e campos como `x`, `y`, `series` e `yFields`. Os valores vêm das consultas autorizadas preservadas pelo backend. Campos desconhecidos, tipos incompatíveis, unidades não confirmadas e configurações não autorizadas são rejeitados.

Tipos disponíveis: `bar`, `horizontalBar`, `pie`, `donut`, `line`, `area` e `scatter`. Um tipo explicitamente solicitado tem prioridade sobre a proposta do modelo e a recomendação automática. Pizza ou rosca com muitas categorias recebe aviso, preservando o tipo solicitado e os registros. Linha e área exigem dimensão temporal real; dispersão exige eixo X numérico. Pizza e rosca não aceitam múltiplas séries, valores negativos ou soma zero. Valores nulos devem permanecer nulos no frontend.

A especificação usa `schemaVersion: "1.0"`, `type`, `title`, `description`, `datasetId`, `data` e `metadata`. Tabelas incluem `columns` e `rows`; gráficos incluem `chartType`, `encoding` e `legend`. Os aliases `totalElements` e `complete` permanecem disponíveis. `sort` e `groupBy` descrevem a apresentação; não autorizam SQL nem agregações inventadas pelo cliente. As agregações já vêm das consultas autorizadas.

Os metadados incluem `totalRecords`, `returnedRecords`, `totalKnown`, `complete`, origem, consultas com filtros/datas, datasets disponíveis e `dataEndpoint`. Percentuais indicam denominador e escopo: `full_dataset` ou `returned_records_only`. Não interprete uma participação sobre registros parciais como participação sobre toda a base.

Em pedidos como “agora em tabela” e “transforme em rosca”, o agente pode reutilizar o dataset e o plano histórico sem consultar novamente. Se os registros tiverem sido removidos pelos limites de memória, a requisição preservada permite recuperá-los novamente; isso pode produzir dados atuais. Em incompatibilidades, o fallback pode retornar tabela com `metadata.status=unsupported` e uma explicação. Sem evidência disponível, informa `no_data`; limites excedidos usam `limit_exceeded` e orientam buscar páginas.

| Configuração | Padrão |
| --- | --- |
| `agent.visualization.max-records` | 2000 registros |
| `agent.visualization.max-bytes` | 1000000 bytes |
| `agent.visualization.max-series` | 20 séries |
| `agent.query.max-records` | 2000 registros na recuperação automática |
| `agent.query.max-pages` | 100 páginas na recuperação automática |

São propriedades Spring com defaults no código. O limite de bytes considera a duplicação dos aliases visuais e os metadados; não garante que todo dataset com até 2000 registros caiba na resposta. Quando necessário, o backend devolve os arrays de dados vazios com a indicação do limite, sem apresentar um recorte silencioso como completo.

Arquivos principais:

- [AgentDatasetService.java](src/main/java/data_centers/brasil/agent/visualization/AgentDatasetService.java): adapta evidências, identifica datasets, combina páginas e infere tipos dos campos, sem juntar consultas independentes.
- [AgentVisualizationService.java](src/main/java/data_centers/brasil/agent/visualization/AgentVisualizationService.java): valida planos, vincula valores reais, aplica limites e constrói fallback.
- [ChartType.java](src/main/java/data_centers/brasil/agent/visualization/ChartType.java) e [VisualizationIntent.java](src/main/java/data_centers/brasil/agent/visualization/VisualizationIntent.java): tipos, aliases e intenção de apresentação.
- [visualization.schema.json](src/main/resources/agent/visualization.schema.json): contrato declarativo de visualização.

## Datasets e paginação

Use `GET /api/agent/conversations/{conversationId}/datasets/{datasetId}?page=0&size=20` com a autenticação da conversa. Substitua `{conversationId}` no `metadata.dataEndpoint` pelo UUID retornado no chat. `page` aceita 0 a 1000; `size`, 1 a 50. Valores fora desses intervalos retornam 400.

A resposta contém `datasetId`, `data`, `page`, `size`, `totalElements`, `hasNext` e `refreshed`. Um dataset completo é paginado sobre os registros preservados, com `refreshed=false`. Um dataset incompleto usa a última requisição autorizada para consultar a página no banco, com `refreshed=true`; portanto, não constitui um snapshot imutável. O total pode ser desconhecido (`-1`).

Dataset ausente na conversa retorna 404; sem requisição preservada para recuperar dados incompletos, retorna 409 pedindo reexecução; página consultada acima de 1000000 bytes retorna 413 e pede redução de `size`. O identificador do dataset deve ser obtido da resposta, sem presumir acesso global ou sua disponibilidade após a remoção dos turnos.

Pedidos de todos os registros usam recuperação automática limitada. `MUNICIPIOS` aceita `todos=true` e `ranking=true`; para top 10, use `ranking=true`, `limite=10`, `todos=false`. Recuperação completa e ranking são escolhas distintas. Os metadados de completude indicam se os limites impediram recuperar tudo.

Implementação: [AgentDatasetController.java](src/main/java/data_centers/brasil/agent/controller/AgentDatasetController.java) e [AgentQueryPaginationService.java](src/main/java/data_centers/brasil/agent/service/AgentQueryPaginationService.java).

Exemplo de sequência de mensagens no mesmo `conversationId`:

```json
{"message":"Mostre os municípios de São Paulo por quantidade de instalações em barras horizontais."}
```

```json
{"conversationId":"6bffcecb-2fe0-4d25-b167-d4fa86d09130","message":"Agora em tabela."}
```

Use o ID realmente recebido na primeira resposta. Confira `metadata.complete`, `metadata.status` e as datas das evidências antes de apresentar o resultado como completo ou atualizado.

## Logs e SQL de desenvolvimento

O [AgentToolTelemetry.java](src/main/java/data_centers/brasil/agent/service/AgentToolTelemetry.java) emite eventos JSON com conversationId, ferramenta, operação, filtros selecionados, quantidade de resultados, tempo em ms, cache, status e tipo de erro.

Filtros geográficos são normalizados. Nome, empresa e termo aparecem como [redacted]. Não são registrados prompt completo, conteúdo integral dos resultados, SQL confidencial, credenciais ou mensagens internas de exceções. Logs não substituem a persistência da conversa.

Exemplo ilustrativo:

```json
{"event":"agent_tool","conversationId":"6bffcecb-2fe0-4d25-b167-d4fa86d09130","tool":"consultar_dataterra","operation":"CONTAR","filters":{"cidade":"hortolandia"},"resultCount":2,"durationMs":4.2,"cached":false,"status":"success"}
```

O [AgentDebugSettings.java](src/main/java/data_centers/brasil/agent/config/AgentDebugSettings.java) exige flag app.agent.debug-sql-enabled=true e perfil dev, local ou test. A flag pode vir de AGENT_DEBUG_SQL_ENABLED=true. Em prod ou production a exposição é bloqueada mesmo se outro perfil estiver ativo.

Os perfis dev e local possuem arquivos próprios que habilitam a flag por padrão quando esses perfis são selecionados explicitamente. AGENT_DEBUG_SQL_ENABLED=false continua desabilitando o recurso. Para executar localmente com SQL nas respostas:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

Na IDE, configure o perfil ativo dev ou local e reinicie a aplicação. O Dockerfile ativa prod; nesse ambiente a lista continuará vazia por proteção, mesmo que o banco utilizado seja o mesmo Supabase.

Fora dessa autorização, consulta retorna uma lista vazia. Quando liberado, o conteúdo continua sendo SQL bruto capturado no JDBC, com os placeholders originais e sem valores interpolados.

## Erros de conversa

| HTTP | Situação |
| --- | --- |
| 400 | Mensagem inválida ou UUID malformado. |
| 401 | Autenticação necessária. |
| 404 | Conversa inexistente, expirada ou não pertencente ao usuário. |
| 409 | Outra mensagem atualizou a mesma conversa. |
| 503 | Memória indisponível ou Gemini sem evidência utilizável para fallback. |

Após a abertura da conversa, uma falha controlada inclui conversationId no ProblemDetail para nova tentativa. Se o banco nem conseguiu criar a conversa, ainda não existe ID para devolver.

## Validação HTTP de geografia

Envie ao mesmo endpoint autenticado cada corpo abaixo. Sem ID, cada um cria uma conversa independente.

```json
{"message":"Liste os data centers de Hortolândia."}
```

```json
{"message":"Liste os data centers de Hortolandia."}
```

```json
{"message":"Quais instalações existem em HORTOLANDIA?"}
```

```json
{"message":"Quantos data centers estão cadastrados em Hortolândia?"}
```

```json
{"message":"Quais empresas possuem instalações em Hortolândia?"}
```

Os registros e contagens devem corresponder ao banco, não a números fixos do prompt. A listagem continua paginada; quando não reunir todas as páginas, deve indicar que o resultado é parcial.

## Testes relacionados

- [AgentConversationIntegrationTests.java](src/test/java/data_centers/brasil/agent/service/AgentConversationIntegrationTests.java): cinco mensagens, histórico após recriar o serviço, isolamento, TTL, conflito de versão, limites, atualização, denominador zero, ID no HTTP, debug e erros.
- [AgentGeographyIntegrationTests.java](src/test/java/data_centers/brasil/agent/service/AgentGeographyIntegrationTests.java): cinco perguntas de Hortolândia, SQL direto de referência, estados completos, filtros por empresa/nome, Unicode, espaços e ausência de correspondência parcial.
- [AgentObservabilityTests.java](src/test/java/data_centers/brasil/agent/service/AgentObservabilityTests.java): autorização de SQL e logs estruturados com campos protegidos redigidos.
- [AgentVisualizationTests.java](src/test/java/data_centers/brasil/agent/service/AgentVisualizationTests.java) e [AgentVisualizationIntegrationTests.java](src/test/java/data_centers/brasil/agent/service/AgentVisualizationIntegrationTests.java): planos visuais, datasets, conversões, validações e integração.
- [ChartTypeSelectionTests.java](src/test/java/data_centers/brasil/agent/service/ChartTypeSelectionTests.java): seleção de tipos de gráfico e aliases.
- [AgentMunicipiosIntegrationTests.java](src/test/java/data_centers/brasil/agent/service/AgentMunicipiosIntegrationTests.java): consultas de municípios e paginação.

Os testes usam Gemini simulado e consultas reais ao H2 em modo PostgreSQL. Não afirmam ter validado a interpretação de um modelo remoto nem acessam o Supabase de produção.

Execute a suíte completa com o Maven Wrapper:

```powershell
.\mvnw.cmd -B -ntp clean test
```
