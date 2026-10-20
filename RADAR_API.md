# Radar de Concentração de Data Centers

API de leitura em `/api/radar`. Reutiliza Basic Auth, CORS e erros `ProblemDetail` do backend. Não há migração, escrita, carga automática ou alteração dos endpoints anteriores. O contrato OpenAPI existente referencia `radar.openapi.json` (manter os dois arquivos juntos).

## Esquema inspecionado e fonte dos indicadores

O projeto usa Spring Boot 4.1.1, Java 25, JPA para `DataCenter`, `Fonte`, `IndicadorAmbiental`, `Beneficio`, `ConteudoPesquisa` e o vínculo `DataCenterFonte`; JDBC para importação e consultas. PostgreSQL/Supabase é configurado externamente, com Hibernate `ddl-auto=validate`. Os testes usam H2 em modo PostgreSQL. Não foi necessário acessar o banco remoto.

`data_center` possui nome, empresa, cidade, estado, coordenadas decimais e tipo de instalação, mas não possui IBGE, conectividade nem precisão geográfica. Esses campos estão persistidos em **`data_center_importacao`**, todos como TEXT. O Radar consulta exclusivamente essa tabela, que representa o dataset importado, sem juntar nem somar registros do cadastro JPA. Cadastros criados apenas em `/api/data-centers` não entram no Radar. Alterações na tabela bruta são refletidas na próxima consulta. Se a importação estiver vazia, não se utiliza o CSV como fallback.

| Dataset | Coluna usada pelo Radar |
| --- | --- |
| id | id_original (texto, não é PK) |
| operador, unidade, municipio, uf | mesmas colunas |
| codigo_ibge, precisao_geo, tipo_instalacao | mesmas colunas |
| latitude, longitude | mesmas colunas TEXT; conversão estrita na leitura |
| redes_conectadas, ix_conectados, carriers | mesmas colunas TEXT; contagens inteiras não negativas |

Demais campos do dataset não participam destes indicadores. Não há deduplicação física nem unicidade garantida de `id_original`; o frontend não deve presumir que `id` do mapa é o ID de `/api/data-centers/{id}`. Linhas repetidas continuam contando como instalações registradas.

## Filtros e validação

Todos os oito endpoints aceitam filtros combináveis (AND): `uf`, `region`, `operator`, `installationType`. UF aceita caixa baixa; região aceita variações de caixa e acentuação entre Norte, Nordeste, Centro-Oeste, Sudeste e Sul. Operador e tipo usam igualdade após normalização de espaços e caixa (sem busca parcial). Filtros vazios ou placeholders são inválidos. UF e região incompatíveis produzem conjunto vazio. Em rotas de estado, UF de query diferente da UF do caminho é erro 400.

`sortBy` usa a lista permitida indicada abaixo; `direction=asc|desc`, padrão `desc`. Valores não informados ficam no final em ambas as direções; empates usam UF ou identidade do município. `limit` é inteiro entre 1 e 1000, padrão 10. Estado inválido retorna 400; estado válido sem registros no conjunto filtrado retorna 404 em detalhe e municípios. Listas e overview vazios retornam 200. Autenticação ausente retorna 401.

## Endpoints e consumo pelo frontend

| GET | Uso e resposta |
| --- | --- |
| `/overview` | Cards: objeto Metrics com totais, somas, médias e cobertura de dados. |
| `/states` | Coroplético e gráficos: lista State, padrão por instalações decrescente. `sortBy=installations|operators|municipalities|networks|averageNetworks|ix|carriers`. |
| `/states/{uf}` | Painel de estado: campos State mais `operadores` e `municipios`, rankings de até 10 por instalações. Inclui `nome`. |
| `/states/{uf}/cities` | Exploração municipal: lista City completa, `sortBy=installations|operators|networks|averageNetworks|ix|carriers`. |
| `/operators/ranking` | Ranking nacional/regional: lista Operator por instalações decrescente e nome no desempate. `limit`. `totalEstados` e `totalMunicipios` indicam presença geográfica. |
| `/map` | Marcadores, clusters e calor: lista MapPoint com coordenadas válidas; `precisaoGeo` preserva o valor original. Pesos do calor podem usar contagem de pontos ou conectividade válida. |
| `/cities/ranking` | Ranking nacional/regional: lista City; `limit`, `sortBy` e `direction` iguais aos municípios por estado. |
| `/filters` | Selects: `states`, `operators`, `installationTypes`, `regions`. Apenas valores presentes no conjunto filtrado; regiões derivadas de UFs válidas. |

Exemplos (enviar o cabeçalho Authorization Basic configurado na aplicação):

```http
GET /api/radar/overview
GET /api/radar/states?region=Sudeste&sortBy=operators&direction=desc
GET /api/radar/states/SP?installationType=Campus
GET /api/radar/states/SP/cities?sortBy=networks
GET /api/radar/operators/ranking?uf=SP&limit=10
GET /api/radar/map?uf=SP&operator=Operador%20Exemplo
GET /api/radar/cities/ranking?region=Sul&sortBy=networks&limit=20
GET /api/radar/filters
```

Resposta real esperada para base vazia (não representa o banco de produção):

```json
{
  "totalInstalacoes": 0, "totalOperadores": 0, "totalMunicipios": 0,
  "totalEstados": 0, "instalacoesComCoordenadas": 0,
  "totalRedesRegistradas": null, "mediaRedesPorInstalacao": null,
  "totalIxRegistrados": null, "mediaIxPorInstalacao": null,
  "totalCarriersRegistrados": null, "mediaCarriersPorInstalacao": null,
  "instalacoesComConectividade": 0, "instalacoesComRedes": 0,
  "instalacoesComIx": 0, "instalacoesComCarriers": 0
}
```

Listas vazias retornam `[]`; filtros vazios retornam `{"states":[],"operators":[],"installationTypes":[],"regions":[]}`. Os schemas e exemplos de cada resposta constam em `radar.openapi.json`.

## Fórmulas e qualidade dos dados

- Instalações: número de linhas, incluindo registros com UF ou município desconhecido. Nenhum total é fixo.
- Operadores: distintos com espaços colapsados e caixa normalizada; exibição mantém uma grafia original escolhida em ordem lexical determinística.
- Municípios: código de sete dígitos com prefixo compatível com a UF; na ausência, UF válida + nome com espaços, caixa e acentos normalizados. Código e fallback têm identidades distintas: não se infere um código para linhas sem IBGE. Códigos não são certificados contra um catálogo municipal oficial; apenas formato e prefixo são verificados. Grafias diferentes além de caixa, acentos e espaços não são reconciliadas. Linhas sem identidade municipal válida não entram no total nem no ranking municipal.
- Estados: apenas as 27 UFs oficiais. UF inválida fica fora da agregação estadual e dos filtros; a linha permanece no total nacional e pode aparecer no mapa com `uf=null` se as coordenadas forem válidas.
- `participacaoNacional = 100 * instalações do estado no conjunto filtrado / total de linhas nacional sem filtros`, arredondada a duas casas. Representa participação no dataset. Não há uma participação dentro do filtro neste contrato; as porcentagens dos estados filtrados podem não somar 100%.
- Conectividade: aceita somente número inteiro não negativo, com ponto ou vírgula decimal se a parte fracionária for zero. `10+`, intervalos, textos, negativos e frações são desconhecidos. Zero explícito é válido. Não interpreta separador de milhar. Somas representam conexões registradas, nunca redes únicas.
- Somas retornam `null` se não existir qualquer valor válido; médias dividem a soma apenas pela quantidade de instalações com aquele campo válido, com duas casas decimais. Ausentes nunca viram zero. `instalacoesComConectividade` conta linhas com ao menos um dos três campos válido; os contadores por campo permitem informar a cobertura.
- Coordenadas: número decimal exato com ponto ou vírgula, latitude entre -90 e 90 e longitude entre -180 e 180, inclusive. Ambos obrigatórios no mapa; nenhuma coordenada é inferida. Precisão geográfica é devolvida como registrada, sem pressupor precisão de endereço.
- Nulos, vazios, traços, `n/a`, `na`, `null`, `Não informado/a` e `Não disponível` são ausentes. Normalização serve apenas para cálculo; nenhum UPDATE ou correção silenciosa é executado.

## Execução e limites

```powershell
cd brasil
.\mvnw.cmd test
.\mvnw.cmd package
```

Testes de integração transacionais criam dados de cenário exclusivamente no H2 e cobrem operadores com múltiplas instalações, identidades municipais, nulos, números inválidos, coordenadas nos limites, filtros combinados, participação, ordenação, validação e autenticação.

Validação desta implementação: 38 testes aprovados, incluindo oito novos testes do Radar. O empacotamento padrão encontrou bloqueio de renomeação do JAR preexistente no Windows. O empacotamento executável foi validado com sucesso com um POM temporário idêntico, alterando somente `finalName` para `brasil-radar-validation`; o POM temporário foi removido e o `pom.xml` original foi preservado. Artefato: `brasil/target/brasil-radar-validation.jar`.

Para manter a mesma normalização de TEXT no PostgreSQL e no H2, a agregação usa uma projeção JDBC de 13 colunas e regras Java, em uma consulta por requisição, sem N+1 nem carregamento de entidades completas. A memória e o processamento crescem com o número de linhas importadas; `limit` limita a resposta, não a leitura. Para bases significativamente maiores, o próximo passo é uma migração incremental com colunas tipadas normalizadas e índices, com validação e backfill auditável, preservando os campos brutos. O mapa não é paginado. Não foram executadas migrações nem consultas no Supabase nesta implementação.
