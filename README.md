# Backend — Data Centers Brasil

API REST em Java 25 e Spring Boot 4.1.1, baseada no esquema PostgreSQL do projeto.

Para integrar o frontend, consulte a [documentação completa dos endpoints, JSON e respostas](DOCUMENTACAO_API.md),
os [tipos TypeScript](api-types.ts) e a [especificação OpenAPI 3.1](openapi.json).

## Organização

O projeto Maven está em `brasil/`. Os pacotes em `src/main/java/data_centers/brasil/` são:

| Pacote | Responsabilidade |
|---|---|
| controller | Rotas HTTP e validação dos pedidos |
| dto | Contratos de entrada e saída |
| service | Operações transacionais e regras dos relacionamentos |
| repository | Persistência com Spring Data JPA e JDBC |
| model | Entidades e representação das linhas brutas de importação |
| mapper | Conversão de entidades para respostas, sem ciclos de serialização |
| exception | Erros HTTP no formato Problem Detail |
| config | Autenticação e serialização estável da paginação |

## Criar o banco pelo pgAdmin

1. Instale/inicie o PostgreSQL e registre a conexão local no pgAdmin.
2. Em **Databases → Create → Database**, crie `data_centers`.
3. Selecione esse banco, abra **Tools → Query Tool** e execute o arquivo
   [schema.sql](brasil/src/main/resources/db/schema.sql).
4. Execute esse arquivo uma vez em um banco vazio. Se já executou seu script original, use as tabelas existentes.

O SQL contém as sete tabelas, chaves estrangeiras e índices. A tabela de importação permanece sem chave primária, como no script original; ela usa JDBC. As demais usam JPA. Não há exclusão em cascata: apagar registros referenciados retorna HTTP 409.

A aplicação usa `ddl-auto=validate`: verifica o mapeamento ao iniciar e não cria nem altera tabelas automaticamente.

## Configurar e executar

Requisitos: JDK 25 e PostgreSQL com o esquema criado. O Maven Wrapper está incluído.
Os dados de conexão originais do Supabase estão em `brasil/src/main/resources/application.properties`.
DB_URL, DB_USERNAME e DB_PASSWORD não são usados pela configuração atual; para mudar o banco, edite as propriedades spring.datasource ou configure SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME e SPRING_DATASOURCE_PASSWORD.

No PowerShell, a partir desta pasta:

```powershell
cd brasil
.\mvnw.cmd spring-boot:run
```

A API estará em `http://localhost:8080/api`. Todas as rotas exigem **HTTP Basic** com usuário `user` e senha `dataCenters123!`, definidos em `spring.security.user.name` e `spring.security.user.password` no application.properties. Essas credenciais são da API e são independentes das credenciais PostgreSQL. Em produção, acesse a API por HTTPS.

Para sobrescrever a porta ou o pool sem editar o arquivo, use `SERVER_PORT` e `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE`.
A aplicação lê variáveis de ambiente; não carrega um arquivo `.env` automaticamente.

## Rotas

| Recurso | Caminho |
|---|---|
| Data centers | `/api/data-centers` |
| Fontes | `/api/fontes` |
| Indicadores ambientais | `/api/indicadores-ambientais` |
| Benefícios | `/api/beneficios` |
| Conteúdos de pesquisa | `/api/conteudos-pesquisa` |

Para cada recurso: `GET /` lista, `GET /{id}` consulta, `POST /` cria,
`PUT /{id}` substitui os campos editáveis e `DELETE /{id}` exclui.
Campos opcionais omitidos no PUT ficam nulos; `pais` omitido assume `Brasil`.

Criação retorna 201 e cabeçalho Location; exclusão retorna 204.
Erros de entrada retornam 400; registros/vínculos inexistentes, 404; conflitos de integridade, 409.
Datas usam `YYYY-MM-DD`; valores decimais usam números JSON com ponto.
Os campos JSON usam camelCase, enquanto as colunas SQL usam snake_case.
IDs são inteiros positivos; estado usa duas letras maiúsculas; coordenadas e precisão decimal são validadas.

Listas aceitam `page=0&size=20&sort=nome,asc`, com limite atual de 2000 registros por página.
O campo de ordenação precisa existir no modelo. A resposta tem `content` e
`page` (size, number, totalElements, totalPages).

### Exemplos de criação

`POST /api/data-centers`:

```json
{
  "nome": "Campus Campinas",
  "empresa": "Operador exemplo",
  "cidade": "Campinas",
  "estado": "SP",
  "latitude": -22.9056,
  "longitude": -47.0608,
  "potenciaMw": 10.25,
  "areaM2": 12000,
  "tipoInstalacao": "Campus"
}
```

`POST /api/fontes`:

```json
{
  "instituicao": "Instituto exemplo",
  "titulo": "Relatório de infraestrutura",
  "url": "https://example.com/relatorio",
  "dataConsulta": "2026-10-13"
}
```

`POST /api/indicadores-ambientais`:

```json
{"dataCenterId": 1, "tipo": "PUE", "valor": 1.25, "unidade": "índice", "periodo": "2026", "fonteId": 1}
```

`POST /api/beneficios`:

```json
{"dataCenterId": 1, "tipo": "Emprego", "descricao": "Geração de empregos locais", "fonteId": 1}
```

`POST /api/conteudos-pesquisa`:

```json
{"titulo": "Pesquisa regional", "conteudo": "Texto da pesquisa", "categoria": "Infraestrutura", "fonteId": 1}
```

Use os IDs efetivamente retornados nas criações. Indicadores e benefícios exigem data center e aceitam fonte opcional.
Conteúdo de pesquisa exige fonte e aceita data center opcional.

### Vínculos, mapa e dashboard

| Método | Rota | Resultado |
|---|---|---|
| GET | `/api/data-centers/{id}/fontes` | Fontes vinculadas, paginadas |
| PUT | `/api/data-centers/{id}/fontes/{fonteId}` | Cria vínculo; repetir não duplica |
| DELETE | `/api/data-centers/{id}/fontes/{fonteId}` | Remove vínculo |
| GET | `/api/data-centers/filtro?cidades=Campinas,Hortolândia&estado=SP` | Filtro regional; aceita também empresa |
| GET | `/api/mapa?estado=SP` | Registros com latitude e longitude, paginados |
| GET | `/api/dashboard` | Quantidade e soma da potência por cidade |

Todas as listagens aceitam filtros por todos os campos em camelCase. Texto usa busca por trecho;
use sufixos Eq (igualdade), Min/Max (faixas numéricas/datas), In (listas) e IsNull (nulos).
Exemplos: `/api/indicadores-ambientais?dataCenterId=1&tipoEq=PUE`,
`/api/data-centers?potenciaMwMin=10&estadoEq=SP` e
`/api/dashboard?estadoEq=SP&quantidadeDataCentersMin=2`.
Consulte a seção de filtros da [documentação da API](DOCUMENTACAO_API.md).

Vincular uma fonte pela rota acima é uma operação independente de referenciá-la em um indicador, benefício ou conteúdo.

## Popular o banco com o CSV incluído

O arquivo `resources/data/instituto-lovelace-data-centers-brasil-2026-09-22.csv` contém 352 registros.
A carga usa o arquivo no classpath, inclusive dentro do JAR, sem precisar enviar o arquivo pelo Postman.
Não é necessário alterar seu SQL nem a conexão PostgreSQL atual.

Com a aplicação reiniciada, no Postman use **Authorization → Basic Auth**:
usuário `user`, senha `dataCenters123!`.

1. **GET** `http://localhost:8080/api/importacoes/previa`: valida o CSV, mostra os totais,
   cinco exemplos e os avisos de conversão. Não grava no banco.
2. **POST** `http://localhost:8080/api/importacoes/popular`: sem corpo; executa a carga e retorna
   quantidades criadas, reutilizadas e ignoradas, além dos avisos.
3. Consulte `GET /api/data-centers`, `GET /api/mapa` e `GET /api/dashboard`.

### O que a carga grava

| Tabela | Dados |
|---|---|
| data_center_importacao | Linha original completa; não acrescenta outra se o id_original já existir |
| data_center | Unidade, operador, município, UF, coordenadas, potência de TI da unidade e área |
| fonte | Uma referência do levantamento por identificador CSV, com URL original e data de consulta |
| data_center_fonte | Vínculo da referência com o cadastro |
| indicador_ambiental | PUE/WUE declarados, quando presentes; texto original nas observações |
| conteudo_pesquisa | Todos os campos originais para pesquisa e rastreabilidade |

A carga não cria benefícios que não constam no CSV. Data centers novos ficam com validação
`PENDENTE` e sem data de validação. A data do levantamento não é tratada como data de publicação.
Vírgulas decimais são convertidas; campos `—` ficam nulos. Metas, intervalos e textos como
“Mais de 20” não viram medidas exatas: o texto fica preservado.
A potência do campus não é somada nem usada como potência de TI da unidade.

Todos os registros são validados antes de qualquer escrita. Qualquer erro reverte a carga inteira.
Cadastros existentes com os mesmos nome, empresa, cidade e estado são reutilizados sem sobrescrever edições;
mais de uma correspondência retorna 409 para que a ambiguidade seja corrigida.

A repetição é identificada pela fonte do levantamento (`instituicao` + `titulo` contendo o ID original),
vinculada ao data center. Não altere esses identificadores nas fontes geradas se quiser manter esse controle.
Repetir o POST ignora registros já carregados; ele não atualiza registros existentes.
Um lock transacional PostgreSQL impede duas cargas desse importador de rodarem simultaneamente em instâncias diferentes.
Linhas iguais importadas anteriormente pela rota de upload não são excluídas.

### Carga ao iniciar, opcional

Dentro de `brasil/`:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=popular"
```

O perfil `popular` executa a carga e mantém a API ativa. Sem esse perfil, a aplicação inicia normalmente,
sem carregar o CSV automaticamente.

Para executar apenas a carga e encerrar, depois de gerar o JAR:

```powershell
.\mvnw.cmd package
java -jar target/brasil-0.0.1-SNAPSHOT.jar --spring.profiles.active=popular --spring.main.web-application-type=none --app.carga.encerrar=true
```

O console mostra `CARGA CONCLUÍDA` com as contagens após a transação concluir.
Esses comandos usam a conexão do application.properties; mantenha as tabelas do seu script já criadas.

## Importação de linhas brutas por upload

`POST /api/importacoes` recebe multipart/form-data com o campo `arquivo`.
`GET /api/importacoes?page=0&size=20` lista as linhas brutas.

O formato esperado é o CSV presente em `src/main/resources/data/`: UTF-8, delimitador ponto e vírgula,
cabeçalho na mesma ordem, começando por `id;operador;unidade;municipio;uf`.
`id` do CSV é salvo em `id_original`. Aspas, campos com ponto e vírgula e BOM UTF-8 são suportados.
Limites atuais de upload: 1 MB por arquivo, 10 MB por requisição multipart e 50.000 linhas por envio.

```powershell
curl.exe -u user -F "arquivo=@src/main/resources/data/instituto-lovelace-data-centers-brasil-2026-09-22.csv" http://localhost:8080/api/importacoes
```

O curl pedirá a senha da API. A importação preserva textos como vírgulas decimais e valores não informados.
Ela apenas acrescenta linhas à tabela bruta, sem transformar os registros em data centers ou indicadores.
Importar novamente acrescenta duplicatas, pois a tabela original não possui chave única.
Se uma linha for inválida, a transação inteira é revertida.
A listagem usa ordenação fixa por id original, operador e unidade; como não há chave única, linhas iguais não possuem uma ordem individual garantida.

## Hospedar no Supabase e usar o pgAdmin

A configuração anterior apontava para o Supabase. Se esse projeto ainda está ativo, o PostgreSQL já está hospedado; falta criar as tabelas e configurar a conexão.

1. Crie/abra seu projeto Supabase e acesse o **SQL Editor**.
2. Em um banco vazio, execute [schema.sql](brasil/src/main/resources/db/schema.sql).
3. Em **Connect**, copie os parâmetros de conexão. Para pgAdmin e conexões IPv4 persistentes, use **Session pooler**.
4. No pgAdmin, selecione **Servers → Register → Server**, preencha host, porta, usuário, senha e banco fornecidos pelo painel. Configure SSL conforme as instruções do provedor.
5. Configure o backend com a URL JDBC e as credenciais, por exemplo:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://HOST_DO_SESSION_POOLER:5432/postgres?sslmode=require'
$env:SPRING_DATASOURCE_USERNAME = 'postgres.ID_DO_PROJETO'
$env:SPRING_DATASOURCE_PASSWORD = 'SUA_SENHA'
```

Copie o host, usuário e porta reais do painel. Uma URL `postgresql://...` precisa ser adaptada para `jdbc:postgresql://...`, com usuário e senha nas variáveis separadas.
As credenciais originais foram restauradas no application.properties conforme solicitado.

Referências oficiais:
[pgAdmin no Supabase](https://supabase.com/docs/guides/database/pgadmin) e
[conexões PostgreSQL](https://supabase.com/docs/guides/database/connecting-to-postgres).

Para hospedar também o backend, use um serviço que execute Java 25. Gere o artefato com
`.\mvnw.cmd package` dentro de `brasil` e execute
`java -jar target/brasil-0.0.1-SNAPSHOT.jar`.
Configure no serviço SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD, SPRING_SECURITY_USER_NAME e SPRING_SECURITY_USER_PASSWORD; SERVER_PORT é opcional.

## Verificação

```powershell
cd brasil
.\mvnw.cmd test
```

Os testes usam H2 em modo PostgreSQL, carregam o mesmo esquema SQL e não acessam seu banco remoto.
Cobrem inicialização, autenticação, CRUD, validação, relacionamentos opcionais/obrigatórios,
conflitos de exclusão, vínculos N:N, consultas regionais, mapa, dashboard, CSV e reversão de importação inválida.
A conexão e o comportamento no PostgreSQL hospedado precisam ser verificados após configurar as variáveis reais.

## Radar de concentração

Documentação do novo módulo REST: [RADAR_API.md](RADAR_API.md). Endpoints em `/api/radar`, com o mesmo Basic Auth da API.
