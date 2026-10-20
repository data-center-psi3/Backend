# Documentação da API — Data Centers Brasil

Contrato do backend para integração do frontend. Referência: controllers, DTOs, serviços e configuração atuais do projeto, revisados em 13/10/2026.

Os exemplos de IDs e cadastros são ilustrativos. As contagens do CSV incluído são uma fotografia do arquivo atual; o resultado da carga depende do banco existente. Exemplos de listas podem mostrar menos elementos para facilitar a leitura.

## Índice

- [Conexão e autenticação](#conexão-e-autenticação)
- [Convenções, paginação e atualização](#convenções-paginação-e-atualização)
- [Todas as rotas](#todas-as-rotas)
- [Data centers](#data-centers)
- [Fontes](#fontes)
- [Indicadores ambientais](#indicadores-ambientais)
- [Benefícios](#benefícios)
- [Conteúdos de pesquisa](#conteúdos-de-pesquisa)
- [Filtros regionais](#filtros-regionais)
- [Mapa](#mapa)
- [Dashboard](#dashboard)
- [Vínculos entre data center e fonte](#vínculos-entre-data-center-e-fonte)
- [Importações e população do banco](#importações-e-população-do-banco)
- [Erros e códigos HTTP](#erros-e-códigos-http)
- [Integração com fetch e TypeScript](#integração-com-fetch-e-typescript)
- [Fluxos do frontend](#fluxos-do-frontend)
- [Limitações atuais](#limitações-atuais)

## Conexão e autenticação

| Item | Valor atual |
|---|---|
| Servidor local | `http://localhost:8080` |
| Prefixo das rotas | `/api` |
| Autenticação | HTTP Basic em todas as rotas |
| Usuário local | `user` |
| Senha local | `dataCenters123!` |
| Token JWT | Não implementado |
| Login/logout via endpoint | Não implementados como contrato da API |
| Sessão | Stateless; envie Authorization em cada chamada |

As credenciais acima são da API, não do PostgreSQL. Estão definidas em
`brasil/src/main/resources/application.properties`. Se elas mudarem, o frontend deve usar os novos valores.
Em um acesso remoto, use a URL HTTPS da API publicada.

Cabeçalhos de uma chamada JSON:


```http
Authorization: Basic <base64 de usuario:senha>
Accept: application/json, application/problem+json
Content-Type: application/json
```


`Content-Type: application/json` é necessário para POST/PUT com JSON. GET, DELETE e operações sem corpo não precisam desse cabeçalho.
Para upload, envie FormData e deixe o navegador gerar o Content-Type com o boundary.

No Postman, abra **Authorization → Basic Auth**. Não selecione Bearer Token.
Abrir uma URL na barra do navegador executa GET; a rota de carga exige POST.

### CORS e proxy do frontend

O backend permite chamadas diretas para `/api/**` a partir de `http://localhost:5173` e `http://127.0.0.1:5173`.
O preflight OPTIONS dessas origens é processado antes da autenticação; a requisição efetiva continua exigindo Basic Auth.
São permitidos GET, POST, PUT, DELETE e OPTIONS; cabeçalhos Authorization, Content-Type, Accept e X-Requested-With.
Credenciais são permitidas, e Location/WWW-Authenticate ficam disponíveis para leitura pelo frontend.
Outras origens precisam ser adicionadas à configuração CORS no backend.

Para desenvolver, você também pode usar um proxy do servidor do frontend e fazer chamadas relativas a `/api`.
Exemplo de configuração Vite:


```ts
import { defineConfig } from "vite";

export default defineConfig({
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
```


Com esse proxy, use `fetch("/api/data-centers", ...)`. Sem proxy, use `http://localhost:8080/api` como base da API.
Para produção, sirva frontend e API na mesma origem com proxy reverso, ou adicione a origem publicada à configuração CORS.

## Convenções, paginação e atualização

### Tipos e campos

- Os nomes JSON usam **camelCase**: `dataCenterId`, `potenciaMw`, `dataConsulta`.
- IDs de cadastros e vínculos são inteiros positivos. `idOriginal` da importação é texto e não equivale ao ID do cadastro.
- Valores decimais são números JSON com ponto: `1.25`; não envie `"1,25"` nos CRUDs.
- Datas são strings `YYYY-MM-DD`, sem hora. Trate-as como datas de calendário para não deslocar o dia por fuso horário.
- Campos opcionais podem ser omitidos ou enviados como `null`. Strings vazias não equivalem a null; campos obrigatórios não aceitam somente espaços.
- Respostas representam relacionamentos por IDs, sem objetos aninhados. `GET /data-centers/{id}` não inclui automaticamente fontes, benefícios ou indicadores.
- Os exemplos incluem campos opcionais nulos; o frontend deve aceitar null nos campos opcionais.
- `statusValidacao`, `tipo`, `categoria` e `tipoInstalacao` são textos livres, não enums fixos do backend.
- Campo `id` é gerado pelo banco e não faz parte dos DTOs de criação/atualização.

### Filtros em todos os campos

As listagens aceitam filtros por **todos os campos dos objetos retornados**, incluindo id e IDs de relacionamentos.
Use nomes em camelCase na query string. Os filtros são combinados com AND e aplicados no banco antes da paginação.

| Sintaxe | Exemplo | Regra |
|---|---|---|
| campo=valor | `nome=campus` | Texto: contém trecho sem diferenciar maiúsculas; números/datas: igualdade |
| campoEq=valor | `cidadeEq=Campinas` | Igualdade exata; textos sem diferenciar maiúsculas |
| campoMin=valor | `potenciaMwMin=10` | Maior ou igual; apenas números, IDs e datas |
| campoMax=valor | `dataConsultaMax=2026-10-13` | Menor ou igual; apenas números, IDs e datas |
| campoIn=a,b | `idIn=1,2,3` | Igualdade a qualquer item; até 100 itens; texto sem diferenciar maiúsculas |
| campoIsNull=true | `fonteIdIsNull=true` | Campo nulo |
| campoIsNull=false | `valorIsNull=false` | Campo preenchido |

Faixas são inclusivas. Datas usam YYYY-MM-DD; decimais na query usam ponto.
Textos mantêm a diferença entre acentos: “Sao” não equivale a “São”.
Caracteres % e _ são literais, não curingas. Para texto com vírgula, use campo/campoEq; In usa vírgula como separador.
Filtros vazios são ignorados. Filtro desconhecido, tipo inválido ou operador incompatível retorna 400 com detail específico.
Exceto In, envie cada filtro uma única vez; listas In também aceitam repetir a chave.
`page`, `size` e `sort` continuam sendo controles, não filtros de coluna.

| Coleção | Campos filtráveis |
|---|---|
| `/api/data-centers` | `id`, `nome`, `empresa`, `cidade`, `estado`, `pais`, `latitude`, `longitude`, `descricao`, `potenciaMw`, `areaM2`, `tipoInstalacao`, `statusValidacao`, `dataValidacao` |
| `/api/fontes` | `id`, `instituicao`, `titulo`, `url`, `dataPublicacao`, `dataConsulta` |
| `/api/indicadores-ambientais` | `id`, `dataCenterId`, `tipo`, `valor`, `unidade`, `periodo`, `fonteId`, `observacoes` |
| `/api/beneficios` | `id`, `dataCenterId`, `tipo`, `descricao`, `fonteId` |
| `/api/conteudos-pesquisa` | `id`, `dataCenterId`, `titulo`, `conteudo`, `categoria`, `fonteId`, `dataPublicacao` |
| `/api/importacoes` | `idOriginal`, `operador`, `unidade`, `municipio`, `uf`, `codigoIbge`, `endereco`, `latitude`, `longitude`, `precisaoGeo`, `tipoInstalacao`, `statusOperacional`, `inicioOperacao`, `potenciaTiMwUnidade`, `potenciaCampusMw`, `areaM2Unidade`, `redesConectadas`, `ixConectados`, `carriers`, `voltadaIa`, `resfriamento`, `fonteEnergia`, `pueDeclarado`, `wueDeclarado`, `certificacoes`, `robustezEvidencia`, `fonteUrl`, `dataLevantamento` |

Regras das consultas especiais:

- `/api/data-centers/filtro` e `/api/mapa` aceitam todos os filtros de DataCenter.
- `cidades=Campinas,Hortolândia` permanece como alias de `cidadeIn` nas consultas de data centers, mapa e dashboard.
- `/api/data-centers/{dataCenterId}/fontes` aceita todos os filtros de Fonte, mantendo a restrição ao data center da URL.
- `/api/dashboard` aceita todos os filtros de DataCenter antes de agregar e filtros de `quantidadeDataCenters` e `potenciaTotalMw` depois de agregar. O resultado continua sendo um array.
- Campos brutos de importação são strings, inclusive datas e números; neles não há Min/Max. Use comparação textual, Eq, In ou IsNull.
- Prévia do CSV, carga, GET por ID e operações de escrita não recebem esses filtros.

Exemplos:

```text
GET /api/data-centers?nome=campus&estadoEq=SP&potenciaMwMin=10&potenciaMwMax=50
GET /api/fontes?instituicao=instituto&dataConsultaMin=2026-01-01&urlIsNull=false
GET /api/indicadores-ambientais?dataCenterId=1&tipoEq=PUE&valorMax=1.5
GET /api/beneficios?dataCenterIdIn=1,2&descricao=emprego&fonteIdIsNull=false
GET /api/conteudos-pesquisa?categoria=infraestrutura&dataPublicacaoMin=2026-01-01
GET /api/importacoes?municipio=Campinas&ufEq=SP&operador=Ascenty
GET /api/mapa?empresa=Ascenty&potenciaMwMin=10&statusValidacaoEq=PENDENTE
GET /api/data-centers/1/fontes?titulo=levantamento&dataConsultaMin=2026-01-01
GET /api/dashboard?estadoEq=SP&quantidadeDataCentersMin=2&potenciaTotalMwMin=20
```

O JSON das respostas permanece igual: a mudança está nos parâmetros e nos elementos selecionados.

### Paginação

Aplica-se às listagens CRUD, filtro regional, mapa, fontes vinculadas e linhas brutas.
Dashboard, prévia e resultado de carga não são paginados.

| Parâmetro | Exemplo | Regra |
|---|---|---|
| page | `page=0` | Índice inicia em zero; padrão 0 |
| size | `size=20` | Padrão 20; limite atual do resolver 2000 |
| sort | `sort=nome,asc` | Campo do modelo em camelCase e direção asc/desc |

Peça tamanhos positivos e páginas não negativas. O resolver pode ajustar valores fora da faixa, em vez de retornar erro.
Não há ordenação padrão para os CRUDs; para navegar de forma previsível, use um campo com desempate por ID:
`?page=0&size=20&sort=nome,asc&sort=id,asc`.

Exemplos:


```text
GET /api/data-centers?page=0&size=20&sort=nome,asc&sort=id,asc
GET /api/fontes?page=1&size=20&sort=titulo,asc
GET /api/indicadores-ambientais?sort=tipo,asc&sort=id,asc
GET /api/beneficios?sort=id,desc
GET /api/conteudos-pesquisa?sort=titulo,asc
```


Formato de qualquer lista paginada — exemplo com data center:


```json
{
  "content": [
    {
      "id": 1,
      "nome": "Campus Campinas",
      "empresa": "Operador Exemplo",
      "cidade": "Campinas",
      "estado": "SP",
      "pais": "Brasil",
      "latitude": -22.9056,
      "longitude": -47.0608,
      "descricao": "Campus de infraestrutura digital.",
      "potenciaMw": 10.25,
      "areaM2": 12000,
      "tipoInstalacao": "Campus",
      "statusValidacao": "PENDENTE",
      "dataValidacao": null
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```


| Campo | Uso no frontend |
|---|---|
| content | Array de elementos; pode ser vazio |
| page.size | Tamanho efetivo da página |
| page.number | Página retornada, iniciando em 0 |
| page.totalElements | Total de elementos encontrados |
| page.totalPages | Total de páginas; 0 quando não há elementos |

A próxima página existe quando `page.number + 1 < page.totalPages`.
Um GET paginado não devolve toda a base. Para carregar todos os pontos do mapa, percorra as páginas.
Página vazia responde 200, não 404.

### POST, PUT e DELETE

| Operação CRUD | Resposta de sucesso |
|---|---|
| POST na coleção | 201, objeto criado com id e cabeçalho Location |
| GET na coleção | 200, objeto paginado |
| GET por ID | 200, objeto do recurso |
| PUT por ID | 200, objeto atualizado |
| DELETE por ID | 204, corpo vazio |

**PUT substitui todos os campos editáveis**, não é atualização parcial.
Reenvie os campos obrigatórios e todos os opcionais que quiser manter.
Opcionais omitidos ficam nulos; a exceção é `pais`, que assume `Brasil`.
Não há endpoint PATCH. PUT com ID inexistente retorna 404 e não cria um novo registro.

Não execute `response.json()` em uma resposta 204.
DELETE com ID inexistente retorna 404.
Data centers e fontes com registros vinculados não podem ser excluídos: recebem 409.
Não há exclusão em cascata.

## Todas as rotas

Os caminhos abaixo já incluem `/api`. Todas as operações exigem Basic Auth.

| Método | Endpoint | Sucesso | Grupo |
|---|---|---:|---|
| GET | `/api/data-centers` | 200 | Data centers |
| GET | `/api/data-centers/{id}` | 200 | Data centers |
| POST | `/api/data-centers` | 201 | Data centers |
| PUT | `/api/data-centers/{id}` | 200 | Data centers |
| DELETE | `/api/data-centers/{id}` | 204 | Data centers |
| GET | `/api/fontes` | 200 | Fontes |
| GET | `/api/fontes/{id}` | 200 | Fontes |
| POST | `/api/fontes` | 201 | Fontes |
| PUT | `/api/fontes/{id}` | 200 | Fontes |
| DELETE | `/api/fontes/{id}` | 204 | Fontes |
| GET | `/api/indicadores-ambientais` | 200 | Indicadores ambientais |
| GET | `/api/indicadores-ambientais/{id}` | 200 | Indicadores ambientais |
| POST | `/api/indicadores-ambientais` | 201 | Indicadores ambientais |
| PUT | `/api/indicadores-ambientais/{id}` | 200 | Indicadores ambientais |
| DELETE | `/api/indicadores-ambientais/{id}` | 204 | Indicadores ambientais |
| GET | `/api/beneficios` | 200 | Benefícios |
| GET | `/api/beneficios/{id}` | 200 | Benefícios |
| POST | `/api/beneficios` | 201 | Benefícios |
| PUT | `/api/beneficios/{id}` | 200 | Benefícios |
| DELETE | `/api/beneficios/{id}` | 204 | Benefícios |
| GET | `/api/conteudos-pesquisa` | 200 | Conteúdos de pesquisa |
| GET | `/api/conteudos-pesquisa/{id}` | 200 | Conteúdos de pesquisa |
| POST | `/api/conteudos-pesquisa` | 201 | Conteúdos de pesquisa |
| PUT | `/api/conteudos-pesquisa/{id}` | 200 | Conteúdos de pesquisa |
| DELETE | `/api/conteudos-pesquisa/{id}` | 204 | Conteúdos de pesquisa |
| GET | `/api/data-centers/filtro` | 200 | Consultas |
| GET | `/api/mapa` | 200 | Consultas |
| GET | `/api/dashboard` | 200 | Consultas |
| GET | `/api/data-centers/{dataCenterId}/fontes` | 200 | Vínculos |
| PUT | `/api/data-centers/{dataCenterId}/fontes/{fonteId}` | 204 | Vínculos |
| DELETE | `/api/data-centers/{dataCenterId}/fontes/{fonteId}` | 204 | Vínculos |
| GET | `/api/importacoes` | 200 | Importações |
| POST | `/api/importacoes` | 200 | Importações |
| GET | `/api/importacoes/previa` | 200 | Importações |
| POST | `/api/importacoes/popular` | 200 | Importações |

Os segmentos fixos `/data-centers/filtro`, `/importacoes/previa` e `/importacoes/popular` não são IDs.


## Data centers

### Endpoints

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| GET | `/api/data-centers` | Sem corpo; page, size, sort | 200, página de DataCenter |
| GET | `/api/data-centers/{id}` | Sem corpo | 200, objeto abaixo |
| POST | `/api/data-centers` | DataCenterRequest | 201, objeto abaixo + Location |
| PUT | `/api/data-centers/{id}` | DataCenterRequest completo | 200, objeto abaixo |
| DELETE | `/api/data-centers/{id}` | Sem corpo | 204, sem JSON |

### Campos de entrada

| Campo | Tipo JSON | Obrigatório | Validação / observação |
|---|---|---|---|
| `nome` | string | Sim | Obrigatório, não vazio; até 150 caracteres. |
| `empresa` | string | Não | Até 150 caracteres. |
| `cidade` | string | Sim | Obrigatório, não vazio; até 100 caracteres. |
| `estado` | string | Sim | Duas letras maiúsculas: SP, RJ etc.; sem enum de UFs. |
| `pais` | string | Não | Até 50 caracteres; omitido ou null vira Brasil. |
| `latitude` | number | Não | De -90 a 90; até 7 casas decimais. |
| `longitude` | number | Não | De -180 a 180; até 7 casas decimais. |
| `descricao` | string | Não | Texto livre. |
| `potenciaMw` | number | Não | Não negativo; até 8 dígitos inteiros e 2 casas decimais. |
| `areaM2` | number | Não | Não negativo; até 10 dígitos inteiros e 2 casas decimais. |
| `tipoInstalacao` | string | Não | Até 100 caracteres; texto livre. |
| `statusValidacao` | string | Não | Até 30 caracteres; sem enum obrigatório. |
| `dataValidacao` | string (data) | Não | YYYY-MM-DD; sem restrição passada/futura. |

### Exemplo de POST e PUT

```json
{
  "nome": "Campus Campinas",
  "empresa": "Operador Exemplo",
  "cidade": "Campinas",
  "estado": "SP",
  "pais": "Brasil",
  "latitude": -22.9056,
  "longitude": -47.0608,
  "descricao": "Campus de infraestrutura digital.",
  "potenciaMw": 10.25,
  "areaM2": 12000,
  "tipoInstalacao": "Campus",
  "statusValidacao": "PENDENTE",
  "dataValidacao": null
}
```

### Resposta de GET por ID, POST e PUT

```json
{
  "id": 1,
  "nome": "Campus Campinas",
  "empresa": "Operador Exemplo",
  "cidade": "Campinas",
  "estado": "SP",
  "pais": "Brasil",
  "latitude": -22.9056,
  "longitude": -47.0608,
  "descricao": "Campus de infraestrutura digital.",
  "potenciaMw": 10.25,
  "areaM2": 12000,
  "tipoInstalacao": "Campus",
  "statusValidacao": "PENDENTE",
  "dataValidacao": null
}
```

Na listagem, cada elemento de `content` tem esse mesmo formato.


### Regras de data centers

- CRUD manual não atribui `PENDENTE` automaticamente; esse valor é aplicado pela carga do CSV.
- `statusValidacao` e `dataValidacao` são independentes; não há regra de consistência entre eles.
- Latitude e longitude são opcionais individualmente. O mapa exige ambas.
- Ao excluir, o serviço verifica indicadores, benefícios, conteúdos e fontes vinculadas.
- A listagem básica e `/api/data-centers/filtro` aceitam os mesmos filtros de todos os campos; a rota de filtro regional continua disponível.


## Fontes

### Endpoints

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| GET | `/api/fontes` | Sem corpo; page, size, sort | 200, página de Fonte |
| GET | `/api/fontes/{id}` | Sem corpo | 200, objeto abaixo |
| POST | `/api/fontes` | FonteRequest | 201, objeto abaixo + Location |
| PUT | `/api/fontes/{id}` | FonteRequest completo | 200, objeto abaixo |
| DELETE | `/api/fontes/{id}` | Sem corpo | 204, sem JSON |

### Campos de entrada

| Campo | Tipo JSON | Obrigatório | Validação / observação |
|---|---|---|---|
| `instituicao` | string | Sim | Não vazio; até 150 caracteres. |
| `titulo` | string | Sim | Não vazio; até 200 caracteres. |
| `url` | string | Não | Texto livre; sem validação de URL no DTO. |
| `dataPublicacao` | string (data) | Não | YYYY-MM-DD. |
| `dataConsulta` | string (data) | Sim | Obrigatório; YYYY-MM-DD. |

### Exemplo de POST e PUT

```json
{
  "instituicao": "Instituto Exemplo",
  "titulo": "Relatório de infraestrutura",
  "url": "https://example.com/relatorio",
  "dataPublicacao": "2026-09-22",
  "dataConsulta": "2026-10-13"
}
```

### Resposta de GET por ID, POST e PUT

```json
{
  "id": 1,
  "instituicao": "Instituto Exemplo",
  "titulo": "Relatório de infraestrutura",
  "url": "https://example.com/relatorio",
  "dataPublicacao": "2026-09-22",
  "dataConsulta": "2026-10-13"
}
```

Na listagem, cada elemento de `content` tem esse mesmo formato.


### Regras de fontes

- `dataConsulta` é obrigatória mesmo quando `dataPublicacao` é desconhecida.
- A existência da URL não é verificada por requisição externa.
- A fonte pode ser compartilhada por vários data centers.
- Fontes referenciadas por indicadores, benefícios, conteúdos ou vínculos N:N retornam 409 ao excluir.
- Fontes geradas pela população usam instituição `Instituto Lovelace (levantamento CSV)` e título `Levantamento CSV: <idOriginal>`. Esses campos participam do controle de repetição da carga.


## Indicadores ambientais

### Endpoints

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| GET | `/api/indicadores-ambientais` | Sem corpo; page, size, sort | 200, página de IndicadorAmbiental |
| GET | `/api/indicadores-ambientais/{id}` | Sem corpo | 200, objeto abaixo |
| POST | `/api/indicadores-ambientais` | IndicadorAmbientalRequest | 201, objeto abaixo + Location |
| PUT | `/api/indicadores-ambientais/{id}` | IndicadorAmbientalRequest completo | 200, objeto abaixo |
| DELETE | `/api/indicadores-ambientais/{id}` | Sem corpo | 204, sem JSON |

### Campos de entrada

| Campo | Tipo JSON | Obrigatório | Validação / observação |
|---|---|---|---|
| `dataCenterId` | integer | Sim | ID positivo de um data center existente. |
| `tipo` | string | Sim | Não vazio; até 100 caracteres. Ex.: PUE, WUE. |
| `valor` | number | Não | Até 11 dígitos inteiros e 4 casas decimais; o CRUD aceita negativos. |
| `unidade` | string | Não | Até 50 caracteres. |
| `periodo` | string | Não | Até 30 caracteres; texto livre, não date. |
| `fonteId` | integer | Não | ID positivo de fonte existente, ou null. |
| `observacoes` | string | Não | Texto livre. |

### Exemplo de POST e PUT

```json
{
  "dataCenterId": 1,
  "tipo": "PUE",
  "valor": 1.25,
  "unidade": "índice",
  "periodo": "2026",
  "fonteId": 1,
  "observacoes": "Valor declarado pelo operador."
}
```

### Resposta de GET por ID, POST e PUT

```json
{
  "id": 1,
  "dataCenterId": 1,
  "tipo": "PUE",
  "valor": 1.25,
  "unidade": "índice",
  "periodo": "2026",
  "fonteId": 1,
  "observacoes": "Valor declarado pelo operador."
}
```

Na listagem, cada elemento de `content` tem esse mesmo formato.


### Regras de indicadores

- Data center inexistente ou fonte inexistente retorna 404; campo obrigatório ausente retorna 400.
- Um indicador pode ter `valor: null` e uma explicação em `observacoes`.
- PUE/WUE qualitativos importados ficam sem valor numérico; não apresente null como zero.
- A unidade e o período não são inferidos pelo CRUD; o frontend deve enviá-los.
- O GET da coleção aceita filtros por todos os campos, incluindo `dataCenterId`, `fonteId` e `tipo`.


## Benefícios

### Endpoints

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| GET | `/api/beneficios` | Sem corpo; page, size, sort | 200, página de Beneficio |
| GET | `/api/beneficios/{id}` | Sem corpo | 200, objeto abaixo |
| POST | `/api/beneficios` | BeneficioRequest | 201, objeto abaixo + Location |
| PUT | `/api/beneficios/{id}` | BeneficioRequest completo | 200, objeto abaixo |
| DELETE | `/api/beneficios/{id}` | Sem corpo | 204, sem JSON |

### Campos de entrada

| Campo | Tipo JSON | Obrigatório | Validação / observação |
|---|---|---|---|
| `dataCenterId` | integer | Sim | ID positivo de um data center existente. |
| `tipo` | string | Sim | Não vazio; até 50 caracteres. |
| `descricao` | string | Sim | Obrigatório, não vazio. |
| `fonteId` | integer | Não | ID positivo de fonte existente, ou null. |

### Exemplo de POST e PUT

```json
{
  "dataCenterId": 1,
  "tipo": "Emprego",
  "descricao": "Geração de empregos locais.",
  "fonteId": 1
}
```

### Resposta de GET por ID, POST e PUT

```json
{
  "id": 1,
  "dataCenterId": 1,
  "tipo": "Emprego",
  "descricao": "Geração de empregos locais.",
  "fonteId": 1
}
```

Na listagem, cada elemento de `content` tem esse mesmo formato.


### Regras de benefícios

A fonte é opcional; o data center é obrigatório.
A população do CSV não cria benefícios, porque eles não constam de forma explícita no arquivo.
O GET da coleção aceita filtros por todos os campos, incluindo `dataCenterId`, `tipo` e `descricao`.


## Conteúdos de pesquisa

### Endpoints

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| GET | `/api/conteudos-pesquisa` | Sem corpo; page, size, sort | 200, página de ConteudoPesquisa |
| GET | `/api/conteudos-pesquisa/{id}` | Sem corpo | 200, objeto abaixo |
| POST | `/api/conteudos-pesquisa` | ConteudoPesquisaRequest | 201, objeto abaixo + Location |
| PUT | `/api/conteudos-pesquisa/{id}` | ConteudoPesquisaRequest completo | 200, objeto abaixo |
| DELETE | `/api/conteudos-pesquisa/{id}` | Sem corpo | 204, sem JSON |

### Campos de entrada

| Campo | Tipo JSON | Obrigatório | Validação / observação |
|---|---|---|---|
| `dataCenterId` | integer | Não | ID positivo existente, ou null para pesquisa geral. |
| `titulo` | string | Sim | Não vazio; até 200 caracteres. |
| `conteudo` | string | Sim | Obrigatório, não vazio. |
| `categoria` | string | Não | Até 50 caracteres; sem enum. |
| `fonteId` | integer | Sim | ID positivo de fonte existente; obrigatório. |
| `dataPublicacao` | string (data) | Não | YYYY-MM-DD. |

### Exemplo de POST e PUT

```json
{
  "dataCenterId": 1,
  "titulo": "Infraestrutura regional",
  "conteudo": "Texto do levantamento de infraestrutura regional.",
  "categoria": "Infraestrutura",
  "fonteId": 1,
  "dataPublicacao": "2026-09-22"
}
```

### Resposta de GET por ID, POST e PUT

```json
{
  "id": 1,
  "dataCenterId": 1,
  "titulo": "Infraestrutura regional",
  "conteudo": "Texto do levantamento de infraestrutura regional.",
  "categoria": "Infraestrutura",
  "fonteId": 1,
  "dataPublicacao": "2026-09-22"
}
```

Na listagem, cada elemento de `content` tem esse mesmo formato.


### Regras de conteúdos

A fonte é obrigatória. Para pesquisa geral, envie `dataCenterId: null` ou omita o campo.
A categoria é texto livre. O GET da coleção aceita filtros por todos os campos, incluindo `categoria`, `dataCenterId` e `fonteId`.


### Exemplo TypeScript de filtros tipados

```ts
import { toQueryString, type IndicadorAmbientalFilters } from "./api-types";

const filters: IndicadorAmbientalFilters = {
  dataCenterId: 1,
  tipoEq: "PUE",
  valorMax: 1.5,
  fonteIdIsNull: false,
  page: 0,
  size: 20,
};
const url = "/api/indicadores-ambientais?" + toQueryString(filters);
```

## Filtros regionais

**GET** `/api/data-centers/filtro`

Sem corpo. Aceita page, size, sort e todos os filtros de DataCenter. Os filtros regionais são:

| Query | Exemplo | Comportamento |
|---|---|---|
| cidades | `Campinas,Hortolândia` | Alias de cidadeIn; igualdade sem diferenciar maiúsculas, preservando acentos |
| estado | `sp` | Contém o texto, sem diferenciar maiúsculas; use estadoEq para igualdade exata |
| empresa | `Operador` | Contém trecho, sem diferenciar maiúsculas; use empresaEq para igualdade exata |

Os filtros são combinados com AND; as cidades da lista são alternativas entre si.
Parâmetro opcional omitido não restringe o resultado.
Também há busca por nome e faixa de potência. Raio geográfico não foi implementado.

Exemplo de chamada:


```text
GET /api/data-centers/filtro?cidades=Campinas,Hortol%C3%A2ndia&estado=SP&empresa=Operador%20Exemplo&page=0&size=20&sort=nome,asc
```


**200**: o mesmo formato paginado de DataCenter mostrado acima. **400** para parâmetros inválidos;
**401** para credenciais ausentes ou incorretas.

Use URLSearchParams no frontend para preservar espaços e acentos.

## Mapa

**GET** `/api/mapa`

Sem corpo. Aceita todos os filtros de DataCenter, além de `cidades`, `page`, `size` e `sort`.
A semântica dos filtros é a mesma da listagem de data centers, incluindo empresa e faixas numéricas.
Retorna apenas cadastros com latitude e longitude não nulas.

**200**, exemplo:


```json
{
  "content": [
    {
      "id": 1,
      "nome": "Campus Campinas",
      "empresa": "Operador Exemplo",
      "cidade": "Campinas",
      "estado": "SP",
      "latitude": -22.9056,
      "longitude": -47.0608,
      "potenciaMw": 10.25,
      "tipoInstalacao": "Campus"
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```


Os números de latitude e longitude não vêm como texto. A ordem típica do Leaflet é `[latitude, longitude]`;
GeoJSON usa `[longitude, latitude]`. A resposta não é GeoJSON.
Percorra as páginas para mostrar toda a base; não trate a primeira página como todos os pontos.

## Dashboard

**GET** `/api/dashboard`

Sem corpo. Aceita filtros de DataCenter e dos agregados quantidadeDataCenters/potenciaTotalMw. Retorna um **array simples**, não um objeto paginado.
Ordena pela quantidade de data centers decrescente e, em empate, pela cidade crescente.

**200**, exemplo:


```json
[
  {
    "cidade": "Campinas",
    "quantidadeDataCenters": 3,
    "potenciaTotalMw": 28.25
  },
  {
    "cidade": "Hortolândia",
    "quantidadeDataCenters": 1,
    "potenciaTotalMw": 0
  }
]
```


| Campo | Tipo | Significado |
|---|---|---|
| cidade | string | Cidade agregada |
| quantidadeDataCenters | integer | Quantidade de cadastros |
| potenciaTotalMw | number | Soma de potenciaMw dos cadastros, ignorando valores null |

Se todos os valores de potência de uma cidade forem nulos, a soma retorna 0.
Isso não comprova potência instalada igual a zero: pode representar falta de dados.
O agrupamento é apenas por cidade, não por estado e cidade.
Aplica os filtros de cadastro antes do agrupamento. Filtros sobre quantidadeDataCenters e potenciaTotalMw são aplicados ao resultado agregado.

## Vínculos entre data center e fonte

### Listar fontes vinculadas

**GET** `/api/data-centers/{dataCenterId}/fontes`

Sem corpo. Aceita page, size e todos os filtros de Fonte. Retorna uma página de FonteResponse:


```json
{
  "content": [
    {
      "id": 1,
      "instituicao": "Instituto Exemplo",
      "titulo": "Relatório de infraestrutura",
      "url": "https://example.com/relatorio",
      "dataPublicacao": "2026-09-22",
      "dataConsulta": "2026-10-13"
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```


Use `sort=id.fonteId,asc` para ordenar o vínculo. A ordenação é sobre a entidade do vínculo,
não diretamente sobre a fonte; `sort=titulo,asc` não é um contrato válido nessa rota.
Data center inexistente retorna 404; data center existente sem vínculos retorna 200 com content vazio.

### Vincular fonte

**PUT** `/api/data-centers/{dataCenterId}/fontes/{fonteId}`

Exemplo: `PUT /api/data-centers/1/fontes/1`.

Sem corpo. **204**, sem JSON. Repetir o PUT não duplica o vínculo.
IDs ausentes no banco retornam 404; IDs não positivos retornam 400.

### Remover vínculo

**DELETE** `/api/data-centers/{dataCenterId}/fontes/{fonteId}`

Sem corpo. **204**, sem JSON. Remove somente a associação, preservando ambos os cadastros.
Vínculo inexistente retorna 404, inclusive ao repetir uma remoção.

Referenciar uma fonte em um indicador, benefício ou conteúdo não cria automaticamente este vínculo N:N.
Remover este vínculo não apaga as referências daqueles recursos.

## Importações e população do banco

Há duas operações diferentes: upload acrescenta **linhas brutas**; popular transforma o **CSV incluído no backend**
em cadastros, fontes, vínculos, indicadores e conteúdo.

### Listar linhas brutas

**GET** `/api/importacoes?page=0&size=20`

**200**, página. Todos os campos da linha são `string | null`, inclusive coordenadas e números.
A ordenação é fixa por id_original, operador e unidade; `sort` enviado pelo cliente não é aplicado.
A tabela não possui ID numérico nem chave única. Não existem GET/PUT/DELETE de linha por ID.

Exemplo:


```json
{
  "content": [
    {
      "idOriginal": "BR-PDB-4821",
      "operador": "DigitalNet MS",
      "unidade": "DigitalNet",
      "municipio": "Campo Grande",
      "uf": "MS",
      "codigoIbge": "5002704",
      "endereco": "Rua Salomão Abdalla, 989",
      "latitude": "-20,516021",
      "longitude": "-54,573625",
      "precisaoGeo": "Sítio (operador)",
      "tipoInstalacao": "—",
      "statusOperacional": "Em operação",
      "inicioOperacao": "Nao informado na base",
      "potenciaTiMwUnidade": "—",
      "potenciaCampusMw": "—",
      "areaM2Unidade": "—",
      "redesConectadas": "3",
      "ixConectados": "0",
      "carriers": "0",
      "voltadaIa": "Não declarada (sem verificação individual)",
      "resfriamento": "—",
      "fonteEnergia": "—",
      "pueDeclarado": "—",
      "wueDeclarado": "—",
      "certificacoes": "—",
      "robustezEvidencia": "Média (IA: baixa)",
      "fonteUrl": "https://www.peeringdb.com/fac/4821",
      "dataLevantamento": "2026-09-05"
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```


### Upload de CSV bruto

**POST** `/api/importacoes`

Content-Type: `multipart/form-data`.

| Campo do formulário | Tipo | Obrigatório |
|---|---|---|
| arquivo | File (conteúdo CSV) | Sim |

No Postman: Body → form-data → chave arquivo → tipo File → selecione o CSV.
Não envie um JSON com nome/caminho do arquivo.

**200**, exemplo:


```json
{
  "linhasImportadas": 352
}
```


O arquivo é inserido apenas em `data_center_importacao`, sem criar data centers, indicadores ou fontes.
Repetir esse upload pode duplicar linhas. Um erro de cabeçalho, linha, leitura ou limite reverte a transação.

Formato esperado: UTF-8, delimitador `;`, aspas CSV e BOM UTF-8 suportados.
Cabeçalho obrigatório, com as 28 colunas na ordem:


```csv
id;operador;unidade;municipio;uf;codigo_ibge;endereco;latitude;longitude;precisao_geo;tipo_instalacao;status_operacional;inicio_operacao;potencia_ti_mw_unidade;potencia_campus_mw;area_m2_unidade;redes_conectadas;ix_conectados;carriers;voltada_ia;resfriamento;fonte_energia;pue_declarado;wue_declarado;certificacoes;robustez_evidencia;fonte_url;data_levantamento
```


`id` do CSV vira `idOriginal` no JSON e `id_original` na tabela bruta.

**Limites da configuração atual:** até 50.000 linhas por upload;
o limite multipart padrão é **1 MB por arquivo e 10 MB por requisição**.
Esses limites não são o antigo limite de 10 MB por arquivo do README inicial.
O handler de 413 ainda contém a mensagem “Arquivo excede o limite de 10 MB.”;
o frontend deve usar o status 413 e o limite efetivo de 1 MB, sem inferir o limite apenas pelo texto.

### Prévia do CSV incluído

**GET** `/api/importacoes/previa`

Sem corpo. Lê:
`resources/data/instituto-lovelace-data-centers-brasil-2026-09-22.csv`.

Não grava e não verifica se a base já recebeu a carga.
Retorna até cinco amostras de DataCenterRequest, sem campo id.

**200**, exemplo com amostra reduzida:


```json
{
  "arquivo": "data/instituto-lovelace-data-centers-brasil-2026-09-22.csv",
  "totalLinhas": 352,
  "registrosComCoordenadas": 348,
  "registrosComPotenciaNumerica": 9,
  "amostra": [
    {
      "nome": "Campus Campinas",
      "empresa": "Operador Exemplo",
      "cidade": "Campinas",
      "estado": "SP",
      "pais": "Brasil",
      "latitude": -22.9056,
      "longitude": -47.0608,
      "descricao": "Campus de infraestrutura digital.",
      "potenciaMw": 10.25,
      "areaM2": 12000,
      "tipoInstalacao": "Campus",
      "statusValidacao": "PENDENTE",
      "dataValidacao": null
    }
  ],
  "avisos": [
    {
      "idOriginal": "EXEMPLO-1",
      "campo": "pue_declarado",
      "valorOriginal": "Meta declarada inferior a 1,5",
      "mensagem": "Texto preservado; não convertido para número."
    }
  ]
}
```


| Campo | Significado |
|---|---|
| arquivo | Caminho lógico do CSV no classpath |
| totalLinhas | Registros do arquivo, excluindo o cabeçalho |
| registrosComCoordenadas | Registros normalizados com latitude e longitude |
| registrosComPotenciaNumerica | Registros com potência exata de TI da unidade |
| amostra | Até cinco objetos sem id |
| avisos | Array de CargaAviso; pode ser vazio |

Todo aviso possui quatro strings: idOriginal, campo, valorOriginal e mensagem.
Avisos de texto qualitativo ou arredondamento não significam falha da carga.
Erros impeditivos retornam 400 com detail.

### Popular o banco com o CSV incluído

**POST** `/api/importacoes/popular`

**Sem corpo**. Não exige envio de arquivo, JSON, caminho ou parâmetros.
GET nessa mesma URL retorna **405**.

A operação é **síncrona**: 200 é devolvido depois de concluir a transação.
Não retorna 202, ID de job, progresso parcial ou endpoint de polling.
Durante a chamada, mostre um estado de carregamento e evite cliques duplicados.

**200**, exemplo de primeira carga em banco vazio:


```json
{
  "arquivo": "data/instituto-lovelace-data-centers-brasil-2026-09-22.csv",
  "totalLinhas": 352,
  "dataCentersCriados": 352,
  "dataCentersReutilizados": 0,
  "fontesCriadas": 352,
  "indicadoresCriados": 40,
  "conteudosCriados": 352,
  "linhasBrutasCriadas": 352,
  "registrosIgnorados": 0,
  "avisos": [
    {
      "idOriginal": "EXEMPLO-1",
      "campo": "pue_declarado",
      "valorOriginal": "Meta declarada inferior a 1,5",
      "mensagem": "Texto preservado; não convertido para número."
    }
  ]
}
```


Contagens são relativas à execução, não totais atuais das tabelas.
`dataCentersReutilizados` indica cadastros preexistentes encontrados pelos campos naturais.
`registrosIgnorados` indica registros já rastreados como carregados.

**200**, exemplo ao repetir uma carga concluída:


```json
{
  "arquivo": "data/instituto-lovelace-data-centers-brasil-2026-09-22.csv",
  "totalLinhas": 352,
  "dataCentersCriados": 0,
  "dataCentersReutilizados": 0,
  "fontesCriadas": 0,
  "indicadoresCriados": 0,
  "conteudosCriados": 0,
  "linhasBrutasCriadas": 0,
  "registrosIgnorados": 352,
  "avisos": [
    {
      "idOriginal": "EXEMPLO-1",
      "campo": "pue_declarado",
      "valorOriginal": "Meta declarada inferior a 1,5",
      "mensagem": "Texto preservado; não convertido para número."
    }
  ]
}
```


Regras da carga:

- Valida o CSV completo antes de gravar; qualquer erro impede ou reverte a carga inteira.
- Novo cadastro recebe statusValidacao PENDENTE e dataValidacao null.
- Conversão numérica aceita ponto ou vírgula; textos qualitativos ficam preservados.
- Potência vem de potencia_ti_mw_unidade; potência do campus não substitui esse valor.
- Cria fontes do levantamento e vínculos; preserva a URL original.
- Cria indicadores PUE/WUE quando há declaração; valor pode ser null.
- Cria conteúdo de pesquisa com todos os campos originais.
- Não cria benefícios nem afirma validação das evidências.
- Reutiliza cadastro com mesmos nome, empresa, cidade e estado sem sobrescrever edições.
- A repetição é identificada pelas fontes da carga vinculadas ao cadastro, não apenas por contagem de linhas brutas.
- Fontes/cadastros ambíguos retornam 409; o frontend deve mostrar detail.
- O CSV é aquele empacotado no backend; trocar um arquivo só no frontend não muda a carga.

Após receber 200, recarregue data centers, mapa e dashboard.
Se a conexão do cliente cair, o resultado pode ter sido confirmado no servidor;
uma nova carga normalmente ignora os registros já carregados, enquanto os marcadores de fonte/vínculo permanecerem intactos.

## Erros e códigos HTTP

| Status | Significado / reação sugerida |
|---:|---|
| 200 | Consulta, atualização, upload ou carga concluídos |
| 201 | Recurso CRUD criado; ler objeto e Location |
| 204 | Operação concluída, corpo vazio |
| 400 | Corrigir JSON, campos, IDs, datas, ordenação ou CSV |
| 401 | Enviar ou corrigir Basic Auth; não é erro do PostgreSQL |
| 403 | Acesso negado; revisar autenticação, autorização e origem da chamada |
| 404 | Cadastro, referência ou vínculo inexistente |
| 405 | Método HTTP errado; conferir GET/POST/PUT/DELETE |
| 413 | Upload acima do limite multipart |
| 415 | Content-Type incompatível com a rota |
| 500 | Erro inesperado ou falha de infraestrutura; não tratar como validação de campo |

### Problem Detail

Os erros tratados pelo ApiExceptionHandler usam `application/problem+json`.
Exemplo de validação de DTO:


```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Dados inválidos.",
  "instance": "/api/data-centers",
  "errors": [
    {
      "field": "nome",
      "message": "não deve estar em branco"
    },
    {
      "field": "estado",
      "message": "deve corresponder a \"[A-Z]{2}\""
    }
  ]
}
```


As mensagens dos validadores dependem da localização/configuração; não compare o texto para decidir o comportamento.
Use status e errors[].field. A ordem dos erros não é garantida.

Exemplo de referência inexistente:


```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "DataCenter não encontrado: 999",
  "instance": "/api/indicadores-ambientais"
}
```


Exemplo de exclusão bloqueada:


```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Data center possui registros vinculados. Remova os vínculos antes de excluir.",
  "instance": "/api/data-centers/1"
}
```


CSV normalizado inválido retorna 400 com detail específico da linha/campo.
O upload bruto retorna 400 com a mensagem genérica de parâmetros/corpo inválidos.

### Erros padrão do Spring e da segurança

Nem todos os erros passam pelo mesmo handler. Por exemplo, 405/415, parte dos 404 de rota
e falhas de segurança podem vir do Spring ou da cadeia de filtros.
O corpo de 401 não é garantido; pode ser vazio. O desafio pode incluir `WWW-Authenticate: Basic realm="Realm"`.

Exemplo de erro padrão 405:


```json
{
  "timestamp": "2026-10-13T17:32:19.397Z",
  "status": 405,
  "error": "Method Not Allowed",
  "message": "Method 'GET' is not supported.",
  "path": "/api/importacoes/popular"
}
```


Em desenvolvimento podem aparecer campos adicionais, inclusive trace.
O frontend deve aceitar ambos os formatos, mostrar detail ou message e usar o status HTTP quando não houver corpo.
Não use trace como mensagem da interface.


## Integração com fetch e TypeScript

O arquivo [api-types.ts](api-types.ts), na raiz, contém interfaces para entradas, saídas, paginação,
importações e erros. Copie-o para o frontend e ajuste o caminho do import nos exemplos abaixo.
Os tipos descrevem o contrato; não executam validação dos dados recebidos em tempo de execução.

O arquivo [openapi.json](openapi.json) descreve as 35 operações em OpenAPI 3.1 e pode ser importado em ferramentas
compatíveis para explorar a API ou gerar um cliente. A especificação é um arquivo estático; não há rota Swagger/OpenAPI
servida automaticamente pelo backend.

### Cliente HTTP

Exemplo usando o proxy local, com API_BASE igual a /api. As credenciais são recebidas em tempo de execução.
A autenticação Basic envia Base64 de usuário:senha em cada requisição; Base64 não é criptografia.
Não distribua credenciais administrativas de produção como constantes do bundle.



```ts
import type { ApiErrorBody } from "./api-types";

const API_BASE = "/api";
let authorization: string | undefined;

export function configureBasicAuth(username: string, password: string): void {
  const bytes = new TextEncoder().encode(username + ":" + password);
  const encoded = btoa(Array.from(bytes, byte => String.fromCharCode(byte)).join(""));
  authorization = "Basic " + encoded;
}

export function clearBasicAuth(): void {
  authorization = undefined;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly body?: ApiErrorBody,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  if (!authorization) throw new Error("Configure usuário e senha da API.");

  const headers = new Headers(init.headers);
  headers.set("Authorization", authorization);
  headers.set("Accept", "application/json, application/problem+json");
  // FormData precisa do boundary gerado pelo navegador.
  if (typeof init.body === "string" && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(API_BASE + path, {
    ...init,
    headers,
    credentials: "omit",
  });

  if (response.status === 204) return undefined as T;

  const text = await response.text();
  let payload: unknown;
  if (text) {
    try { payload = JSON.parse(text); }
    catch { payload = text; }
  }

  if (!response.ok) {
    const body = typeof payload === "object" && payload !== null
      ? payload as ApiErrorBody
      : undefined;
    const message = body?.detail || body?.message || body?.error
      || "Falha HTTP " + response.status;
    throw new ApiError(response.status, message, body);
  }

  return payload as T;
}

```


Use configureBasicAuth com os dados informados pelo usuário ou pela configuração local.
Após uma falha 401, peça as credenciais novamente; não há refresh token.

### Listar e filtrar data centers


```ts
import type {
  PageResponse, DataCenterResponse, DataCenterRequest,
  FonteResponse, IndicadorAmbientalResponse, BeneficioResponse,
  ConteudoPesquisaResponse, MapaResponse, DashboardResponse,
  DataCenterImportacao, ImportacaoResponse, CargaPreviaResponse, CargaResponse,
} from "./api-types";

// Exemplo local; em produção receba as credenciais no fluxo de acesso.
configureBasicAuth("user", "dataCenters123!");

const params = new URLSearchParams({
  cidades: ["Campinas", "Hortolândia"].join(","),
  estado: "SP",
  page: "0",
  size: "20",
});
params.append("sort", "nome,asc");
params.append("sort", "id,asc");

const page = await apiFetch<PageResponse<DataCenterResponse>>(
  "/data-centers/filtro?" + params.toString(),
);
const cards = page.content;
const hasNextPage = page.page.number + 1 < page.page.totalPages;

```


### Consultar, criar, atualizar e excluir


```ts
const input: DataCenterRequest = {
  nome: "Campus Campinas",
  cidade: "Campinas",
  estado: "SP",
  potenciaMw: 10.25,
};

const created = await apiFetch<DataCenterResponse>("/data-centers", {
  method: "POST",
  body: JSON.stringify(input),
});

const current = await apiFetch<DataCenterResponse>("/data-centers/" + created.id);

// PUT completo: preserva os outros valores do cadastro.
const { id, ...editable } = current;
const updated = await apiFetch<DataCenterResponse>("/data-centers/" + id, {
  method: "PUT",
  body: JSON.stringify({ ...editable, descricao: "Descrição atualizada." }),
});

await apiFetch<void>("/data-centers/" + id, { method: "DELETE" });
// Se houver vínculos, a exclusão lança ApiError com status 409.

```


O mesmo cliente funciona para /fontes, /indicadores-ambientais, /beneficios e /conteudos-pesquisa,
usando os tipos Request/Response correspondentes.

### Consumir fontes, indicadores, benefícios e conteúdos


```ts
const sources = await apiFetch<PageResponse<FonteResponse>>("/fontes?page=0&size=20");
const indicators = await apiFetch<PageResponse<IndicadorAmbientalResponse>>(
  "/indicadores-ambientais?page=0&size=20",
);
const benefits = await apiFetch<PageResponse<BeneficioResponse>>("/beneficios?page=0&size=20");
const research = await apiFetch<PageResponse<ConteudoPesquisaResponse>>(
  "/conteudos-pesquisa?page=0&size=20",
);

```


Envie dataCenterId na query para filtrar indicadores, benefícios e conteúdos no banco:
`/indicadores-ambientais?dataCenterId=1`, `/beneficios?dataCenterId=1` e `/conteudos-pesquisa?dataCenterId=1`.
Percorra as páginas dos resultados filtrados quando precisar de todos os recursos do cadastro.

### Vincular fonte


```ts
await apiFetch<void>("/data-centers/1/fontes/2", { method: "PUT" });

const linked = await apiFetch<PageResponse<FonteResponse>>(
  "/data-centers/1/fontes?page=0&size=20&sort=id.fonteId,asc",
);

await apiFetch<void>("/data-centers/1/fontes/2", { method: "DELETE" });

```


### Buscar todas as páginas de pontos do mapa e o dashboard


```ts
async function fetchAllMapPoints(estado?: string): Promise<MapaResponse[]> {
  const result: MapaResponse[] = [];
  for (let number = 0; ; number++) {
    const params = new URLSearchParams({
      page: String(number),
      size: "100",
      sort: "id,asc",
    });
    if (estado) params.set("estado", estado);

    const response = await apiFetch<PageResponse<MapaResponse>>(
      "/mapa?" + params.toString(),
    );
    result.push(...response.content);
    if (response.page.number + 1 >= response.page.totalPages) return result;
  }
}

const points = await fetchAllMapPoints("SP");
const dashboard = await apiFetch<DashboardResponse[]>("/dashboard");

```


### Upload de CSV e carga do arquivo incluído


```ts
async function uploadRawCsv(file: File): Promise<ImportacaoResponse> {
  const form = new FormData();
  form.append("arquivo", file);

  return apiFetch<ImportacaoResponse>("/importacoes", {
    method: "POST",
    body: form,
  });
}

const rawRows = await apiFetch<PageResponse<DataCenterImportacao>>(
  "/importacoes?page=0&size=20",
);
const preview = await apiFetch<CargaPreviaResponse>("/importacoes/previa");
const population = await apiFetch<CargaResponse>("/importacoes/popular", {
  method: "POST",
});

```


O upload recebe um File escolhido pelo usuário. A carga do arquivo incluído não recebe File nem FormData.
Durante a carga, mantenha o estado de loading até a resposta.
Cancelar a chamada pelo navegador não garante cancelamento da transação no servidor.

### Tratar erros e validação de formulário


```ts
try {
  await apiFetch<DataCenterResponse>("/data-centers", {
    method: "POST",
    body: JSON.stringify({ nome: "", cidade: "Campinas", estado: "sp" }),
  });
} catch (error) {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      clearBasicAuth();
      // Solicite novamente as credenciais.
    }

    const fieldErrors: Record<string, string[]> = {};
    for (const item of error.body?.errors ?? []) {
      (fieldErrors[item.field] ??= []).push(item.message);
    }
    // Use fieldErrors junto ao formulário e error.message para o aviso geral.
    console.error(error.status, error.message, fieldErrors);
  } else {
    // Falha de rede, configuração ou CORS pode ocorrer sem status HTTP acessível.
    console.error(error);
  }
}

```


## Fluxos do frontend

### Tela de mapa e painel

1. Consulte /mapa, percorra as páginas e desenhe os pontos.
2. Consulte /dashboard para totais por cidade.
3. Ao selecionar um ponto, consulte /data-centers/{id}.
4. Consulte /data-centers/{id}/fontes para as referências.
5. Não exiba potenciaMw null como zero nem trate PUE/WUE null como ausência de evidência qualitativa.

### Cadastro com referências

1. Crie ou consulte uma fonte.
2. Crie ou consulte um data center.
3. Vincule ambos com PUT /data-centers/{dataCenterId}/fontes/{fonteId}.
4. Crie indicadores, benefícios ou conteúdos com os IDs efetivos retornados.
5. Não presuma que o ID do primeiro registro será 1; sequências podem ter lacunas após reversões/exclusões.

### Tela de importação

1. Consulte GET /importacoes/previa e apresente contagens e avisos.
2. Ao executar a ação de carga, faça POST /importacoes/popular.
3. Mostre contagens criadas/reutilizadas/ignoradas; avisos não equivalem a falha.
4. Após sucesso, recarregue as coleções, o mapa e o dashboard.
5. Para guardar apenas outro CSV bruto, use POST /importacoes com FormData.

### Exclusão de cadastro

1. Tente DELETE na rota do recurso.
2. Em 409, mostre detail e permita revisar/remover as referências.
3. Remover vínculo N:N não remove indicadores, benefícios ou conteúdo.
4. Em 204, retire o item da interface sem tentar ler JSON.

## Limitações atuais

- Não há JWT, refresh token, gestão de usuários, perfis de acesso ou endpoint de login customizado.
- Todos os usuários autenticados da configuração atual acessam as mesmas rotas.
- CORS permite apenas as origens locais localhost:5173 e 127.0.0.1:5173; outras origens exigem configuração no backend.
- Não há documentação Swagger servida como rota; openapi.json é um artefato estático.
- Não há PATCH, pesquisa textual global entre recursos ou endpoint agregado de detalhes; as listagens têm filtros por todos os seus campos.
- Dashboard aceita filtros, mas ainda agrega somente pelo texto cidade.
- A carga usa um arquivo fixo incluído no backend; não é uma importação normalizada de qualquer upload.
- A carga não oferece processamento em segundo plano, progresso ou polling.
- Dados brutos não têm chave única; uploads repetidos podem duplicar registros.
- Não há garantia de que a prévia/carga reflita uma base já populada até chamar a operação ou consultar as coleções.
- Os exemplos e interfaces devem ser revisados quando controllers, DTOs ou configuração mudarem.

## Arquivos de referência

- [Tipos TypeScript](api-types.ts)
- [Especificação OpenAPI 3.1](openapi.json)
- [Configuração e execução do backend](README.md)
- [SQL das tabelas](brasil/src/main/resources/db/schema.sql)
- [Controllers](brasil/src/main/java/data_centers/brasil/controller)
- [DTOs](brasil/src/main/java/data_centers/brasil/dto)

Esta documentação não modifica as rotas, a autenticação nem as credenciais PostgreSQL.
