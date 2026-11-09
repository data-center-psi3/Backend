# Visualizações dinâmicas do DataTerra AI

O endpoint existente continua sendo `POST /api/agent/chat`, com `{ "message": "...", "conversationId": "..." }`. A autenticação e a propriedade da conversa continuam obrigatórias. Não há migração de banco nem alteração de dados de produção.

## Causa e implementação

Anteriormente, AgentService só preenchia chartData em uma condição específica de MUNICIPIOS, com barras fixas. ESTADOS e as demais ferramentas entregavam seus dados ao texto do Gemini, sem contrato visual. A compactação histórica de listas em dez itens também prejudicava as conversões.

O novo fluxo é: consulta autorizada → paginação de recuperação → datasets → esquema e metadados para Gemini → planejar_visualizacao → validação → associação aos valores reais → JSON. O modelo propõe configurações por Function Calling com schema de argumentos; não envia valores. AgentDatasetService adapta listas, DTOs, resultados paginados e contagens escalares. AgentVisualizationService valida campos, tipos, eixos, unidades, séries e limites. AgentQueryPaginationService recupera páginas independentemente da paginação visual. Repositories não conhecem gráficos.

Não há seleção de gráfico por operação SQL. A escolha considera tipos de campos, intenção, quantidade de categorias e proposta validada. Um pedido explícito prevalece quando compatível. Não se inventa uma série histórica de inaugurações a partir do cadastro atual ou de uma data de validação.

A prioridade é: tipo explicitamente solicitado na mensagem atual → tipo proposto pelo Gemini → recomendação automática. ChartType centraliza os identificadores e aliases em português/inglês. A proposta semântica do Gemini também pode selecionar um datasetId autorizado do histórico, sem depender de uma palavra-chave para converter o formato. O tipo anterior é uma preferência de continuidade, nunca uma autorização para substituir um novo pedido explícito.

Identificadores oficiais de saída: `bar`, `horizontalBar`, `pie`, `donut`, `line`, `area`, `scatter`. `pizza`/`pie chart` normalizam para `pie`; `rosca`/`donut chart`/`doughnut` para `donut`. O alias legado `doughnut` continua aceito em propostas e na leitura de planos persistidos. O frontend deve renderizar `donut` e o legado `doughnut` como rosca. Nenhum campo do envelope de chat foi removido.

## Contrato 1.0

Os campos `response`, `fonte`, `consulta`, `consultaOrigem`, `conversationId` e `chartData` continuam presentes. `visualization` é um alias aditivo de chartData para tabelas e gráficos. Ambos contêm a mesma especificação, ou null quando não foi solicitada uma visualização. As propriedades legadas `dimension`, `metric`, `data`, `totalElements` e `complete` permanecem disponíveis nos gráficos. Para selecionar o renderer, clientes novos devem usar `type` e `chartType`.

```typescript
type FieldType = 'nominal' | 'quantitative' | 'temporal' | 'unknown';
type ChartType = 'bar' | 'horizontalBar' | 'pie' | 'donut' | 'line' | 'area' | 'scatter';
type Row = Record<string, string | number | boolean | null>;
type Column = { field: string; title: string; type: FieldType };
type Axis = Column & {
  fields?: string[]; // várias métricas numéricas, não nomes de séries inventados
  format?: '' | 'integer' | 'decimal' | 'percent';
  unit?: string;
  normalization?: 'percent';
  denominator?: number;
};
type Visualization = {
  schemaVersion: '1.0';
  datasetId?: string;
  type: 'chart' | 'table';
  chartType?: ChartType;
  title?: string;
  description?: string;
  data: Row[];
  rows?: Row[]; // alias de data para tabelas
  columns?: Column[];
  encoding?: { x: Axis; y: Axis; color?: Axis; theta?: Axis; tooltip: Column[] };
  legend?: { visible: boolean; field: string };
  sort?: { field: string; direction: 'ascending' | 'descending' }[];
  groupBy?: string[]; // campos existentes; não autoriza agregar valores outra vez
  preferredPresentation?: Record<string, unknown>; // preferências preservadas na conversão
  dimension?: string;
  metric?: string;
  totalElements?: number;
  complete?: boolean;
  metadata: {
    status: 'ok' | 'empty' | 'unsupported' | 'no_data' | 'limit_exceeded';
    source?: 'PostgreSQL DataTerra';
    totalRecords?: number; // -1 quando o total ainda é desconhecido
    totalKnown?: boolean;
    returnedRecords: number;
    complete: boolean;
    message?: string;
    warning?: string;
    ordering?: 'source';
    aggregation?: 'precomputed_by_authorized_query';
    percentageDenominator?: number;
    percentageScope?: 'full_dataset' | 'returned_records_only';
    queries?: { operation: string; filters: Record<string, unknown>;
      queriedAt: string; historical: boolean; request: Record<string, unknown> }[];
    availableDatasets?: { datasetId: string; fields: Record<string, FieldType>;
      totalRecords: number; complete: boolean }[];
    dataEndpoint?: string;
  };
};
```

O schema de publicação está em `src/main/resources/agent/visualization.schema.json`. A validação semântica adicional é executada no backend; JSON válido sozinho não é suficiente. A gramática não aceita URLs de dados, transformações executáveis, expressões, SQL, HTML, JavaScript nem agregações arbitrárias. Agregações são realizadas nas consultas já autorizadas; o motor não soma novamente linhas agregadas. `groupBy`, séries e ordenação são configurações declarativas sobre campos existentes.

`metadata.complete` descreve a cobertura do conjunto consultado, não a realidade nacional. Um top cinco pode ser uma seleção parcial da distribuição total. A soma/denominador de um conjunto parcial nunca é apresentada como total nacional. Pizza e rosca exigem uma dimensão categórica/temporal, métrica numérica não negativa e soma positiva. Acima de doze categorias há somente um aviso de legibilidade: um pedido explícito ou uma proposta válida do Gemini mantém o gráfico e todos os registros. Sem tipo escolhido pelo usuário ou modelo, a recomendação automática pode preferir barras horizontais. Uma incompatibilidade real, como números negativos, recebe explicação e alternativa em tabela.

Pizza e rosca fornecem `encoding.color` para a categoria e `encoding.theta` para o peso, mantendo também x/y, dimension e metric por compatibilidade. A cor de uma fatia não é uma série adicional: ao converter novamente esse gráfico, previousPlan não transforma color em series.

Nulos são preservados. Não substitua null por zero. Períodos só são temporais quando os valores têm formato temporal reconhecido; um texto arbitrário em periodo não cria uma série válida. Unidades declaradas pelo modelo precisam ser confirmadas pelos dados, e séries com unidades misturadas são rejeitadas.

## Continuidade e limites

As evidências persistem operação, filtros, paginação solicitada, consulta original, completude e última especificação validada. Listas deixam de ser amostradas em dez itens. Se excederem o orçamento de memória, as linhas são omitidas com uma referência aos parâmetros originais e `_requiresRefresh`. Na próxima conversão o backend refaz a mesma consulta e informa que a visualização reflete dados atuais.

Para pedidos de mudança de formato, o backend seleciona o dataset anterior e preserva seu conteúdo. Uma chamada equivocada do modelo com limite=10 não substitui esse snapshot. Para pedir dados atuais ou alterar filtros, envie uma nova pergunta de consulta, por exemplo: "Atualize os dados de municípios de SP e mostre em tabela".

Configurações opcionais Spring, com seus padrões:

```properties
agent.query.max-records=2000
agent.query.max-pages=100
agent.municipios.max-records=1000
agent.municipios.max-pages=50
agent.visualization.max-records=2000
agent.visualization.max-bytes=1000000
agent.visualization.max-series=20
app.agent.memory.max-chars=60000
```

Os limites de bytes consideram os aliases para evitar duplicação excessiva da resposta. Ao atingir um limite, não há amostragem silenciosa. O cliente recebe completude falsa ou status limit_exceeded e uma referência de recuperação. O modelo recebe o esquema e os metadados dos resultados visuais, sem os milhares de valores completos. Resultados de ferramentas distintas ficam em datasets separados, selecionáveis por datasetId; não há junções implícitas nem alinhamento de linhas por coincidência de índices.

### Recuperação paginada

`GET /api/agent/conversations/{conversationId}/datasets/{datasetId}?page=0&size=20`

page começa em zero, máximo 1000; size entre 1 e 50. Retorna datasetId, data, page, size, totalElements, hasNext e refreshed. Usa o snapshot completo enquanto estiver disponível. Caso contrário, reexecuta os parâmetros autorizados da consulta com a página solicitada; refreshed=true informa que os dados são atuais e podem ter mudado. Não há um snapshot transacional permanente entre chamadas HTTP reexecutadas.

A consulta é restrita ao dono da conversa, inclusive em referências a datasets. Outra identidade recebe 404. Conversas expiradas também recebem 404. Parâmetros inválidos recebem 400. Se um histórico antigo não contiver parâmetros recuperáveis, recebe 409 para reexecutar a consulta. Sem autenticação, 401. Nunca aceite um datasetId como autorização suficiente.

## Integração React

1. Guarde conversationId da resposta e envie-o nas próximas mensagens. Use a autenticação já existente para chat e datasets.
2. Leia `const spec = result.visualization ?? result.chartData`. Se null, apresente response como texto. Não tente extrair um gráfico de Markdown.
3. Verifique schemaVersion e metadata.status. Exiba message/warning, completude e origem temporal. Para limit_exceeded, ofereça paginação pelo dataEndpoint substituindo `{conversationId}` pelo ID retornado.
4. Para table, renderize columns e rows ?? data. Use JSX/textContent, nunca HTML interpretado. A ordenação declarada deve ser aplicada a uma cópia dos dados.
5. Para chart, adapte chartType à biblioteca já usada pelo projeto. x/y indicam campos, color indica séries categóricas e y.fields indica várias métricas da mesma unidade. horizontalBar apenas muda a orientação visual; x continua sendo a dimensão lógica e y a métrica lógica. Scatter exige dois eixos numéricos; line/area exigem x temporal. Para pizza/rosca, x é a categoria e y o peso.
6. Percentuais são derivados dos valores reais: use `100 * row[y.field] / denominator`, onde denominator vem de encoding.y.denominator ou metadata.percentageDenominator. Preserve os números originais em tooltips e informe quando percentageScope for returned_records_only. Não interprete um COUNT bruto como percentual.
7. Para municípios, identifique a categoria pelo par município/UF. Para qualquer gráfico com categorias repetidas, use identidade de linha ou série e mantenha os campos de tooltip; não agregue categorias diferentes só porque têm o mesmo rótulo.
8. Para 98 categorias, use uma área rolável, altura proporcional ou paginação visual. Paginação visual não reduz o dataset recebido e não muda complete. Não aplique slice(0, 10) automaticamente.

Exemplo de seleção do renderer (ChartRenderer é o adaptador da biblioteca do seu frontend):

```tsx
function VisualizationPanel({ result }: { result: ChatResponse }) {
  const spec = result.visualization ?? result.chartData;
  return <section>
    <p>{result.response}</p>
    {spec?.metadata.message && <p role="status">{spec.metadata.message}</p>}
    {spec?.type === 'table' && <table>
      <thead><tr>{spec.columns?.map(c => <th key={c.field}>{c.title}</th>)}</tr></thead>
      <tbody>{(spec.rows ?? spec.data).map((row, i) => <tr key={i}>
        {spec.columns?.map(c => <td key={c.field}>
          {row[c.field] == null ? '—' : String(row[c.field])}
        </td>)}
      </tr>)}</tbody>
    </table>}
    {spec?.type === 'chart' && spec.metadata.status === 'ok' &&
      <ChartRenderer specification={spec} />}
  </section>;
}
```

## Exemplos e testes

Os testes de integração consultam H2 em modo PostgreSQL por meio dos services reais e simulam somente o transporte Gemini. O teste do controller produz `target/visualization-endpoint-example.json`: resposta completa do POST com 98 municípios em tabela. `target/visualization-bar-example.json` e `target/visualization-pie-example.json` mostram a mesma consulta e sua conversão. São dados isolados de teste, não dados do Supabase; os IDs não são utilizáveis em produção.

`target/chart-type-pie-regression.json` registra a resposta completa do endpoint para a mensagem exata "Em gráfico de pie." sobre um dataset de 25 UFs. Os testes verificam barras → pizza → rosca → barras horizontais → tabela na mesma conversa, datasetId/dados preservados, nenhuma nova consulta de recuperação, propostas conflitantes do Gemini e igualdade entre chartData e visualization. ChartTypeSelectionTests cobre aliases isolados, prioridade, identificador legado e incompatibilidade real.

Execute `./mvnw.cmd test`. AgentVisualizationTests cobre a gramática, tipos, percentuais, períodos, dispersão, séries, campos inexistentes, nulos, unidades, ordenação, limites e datasets de múltiplas ferramentas. AgentVisualizationIntegrationTests cobre chat, memória, conversões, 98 municípios, 27 UFs, limites explícitos, correção de propostas, indisponibilidade Gemini, recuperação de referências e isolamento por usuário. AgentMunicipiosIntegrationTests mantém os cenários de 0/20/21/98 registros e soma de instalações. As suítes anteriores verificam Function Calling, assinaturas, SQL autorizado, memória, controladores e serviços existentes.

As respostas em caso de incompatibilidade visual usam HTTP 200 com uma alternativa real em tabela e metadata.status=unsupported. Falta de dados/limites são estados explícitos no contrato. Indisponibilidade sem nenhuma evidência utilizável mantém o erro HTTP 503 existente, sem detalhes internos ou stack traces.
