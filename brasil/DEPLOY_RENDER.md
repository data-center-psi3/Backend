# Deploy no Render

## Projeto

O backend fica em `brasil/`, relativo à raiz Git (`Backend`). O `pom.xml`
declara Java 25 e Spring Boot 4.1.1. O Maven Wrapper usa Maven 3.9.16.
O plugin Spring Boot gera `target/brasil-0.0.1-SNAPSHOT.jar`, executável com
`java -jar`. Se a versão ou artifactId do projeto mudar, ajuste o COPY do JAR
no estágio de build do Dockerfile.

Dependências principais: Web MVC, Security (HTTP Basic), Validation, Data JPA,
PostgreSQL, Lombok, springdoc 3.0.2, Commons CSV 1.14.1, Rome 2.1.0 e jsoup
1.21.2. H2 é utilizado apenas nos testes. Devtools é excluído do JAR pelo
empacotamento padrão do Spring Boot.

## Serviço

Crie um Web Service conectado ao repositório:

- Runtime: Docker.
- Root Directory: `brasil`.
- Dockerfile Path: `./Dockerfile`.
- Docker Build Context: `.` (relativo ao Root Directory).
- Docker Command: deixe vazio; o ENTRYPOINT inicia o JAR.
- Health Check Path: `/v3/api-docs` (rota pública existente).

O servidor já escuta em `0.0.0.0` e usa `${PORT:8080}`. O Render fornece PORT;
EXPOSE é apenas documentação e não limita a porta real.
O container ativa o perfil `prod`, sem modificar os padrões locais.

## Variáveis no Render

| Variável | Valor |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<host-session-pooler>:5432/postgres?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | Usuário do Session pooler, normalmente `postgres.<project-ref>` |
| `SPRING_DATASOURCE_PASSWORD` | Senha do banco Supabase |
| `SPRING_SECURITY_USER_NAME` | Usuário HTTP Basic da API |
| `SPRING_SECURITY_USER_PASSWORD` | Senha forte da API |
| `DB_POOL_MAX_SIZE` | Opcional, padrão `5` |
| `DB_POOL_MIN_IDLE` | Opcional, padrão `1` |
| `JAVA_TOOL_OPTIONS` | Opcional, flags JVM adequadas à memória contratada |

Copie host e usuário do painel Connect do Supabase, opção Session pooler.
Esse modo oferece IPv4 e mantém sessões compatíveis com JPA. A URL deve começar
com `jdbc:postgresql://`, sem embutir usuário ou senha. Não use a chave anon ou
service_role como senha PostgreSQL. `sslmode=require` exige criptografia;
para validar também a identidade do servidor, use `sslmode=verify-full` com
o certificado CA disponibilizado pelo Supabase e configure `sslrootcert`.

Não envie `.env` ao Git nem à imagem. O arquivo local continua sendo carregado
opcionalmente para desenvolvimento; o `.dockerignore` o exclui do build.

## Antes do primeiro deploy

- `spring.jpa.hibernate.ddl-auto=validate` foi preservado: o banco deve possuir
  as tabelas e colunas esperadas antes de iniciar. Confira
  `src/main/resources/db/schema.sql`; aplique-o somente a um banco novo após
  revisão. Não execute esse script indiscriminadamente sobre um banco existente.
  O script foi completado com a tabela `noticia`, exigida pela entidade News.
  Se o banco existente não tiver essa tabela, aplique apenas o bloco
  `CREATE TABLE noticia` após conferir o schema atual.
- O CORS em `SecurityConfig` aceita somente `localhost:5173` e
  `127.0.0.1:5173`. Chamadas de um frontend publicado precisarão ter sua origem
  HTTPS adicionada nessa configuração. Essa alteração depende da URL do frontend.
- Não ative o perfil `popular` ou `app.carga.encerrar=true` no serviço web:
  são opções da carga de dados e podem encerrar o processo.
- O build precisa acessar Maven Central e o registro das imagens Temurin.
- A imagem usa Java 25 JRE, usuário sem privilégios e build multi-stage.
  O cache Maven usa BuildKit, sem copiar dependências de build à imagem final.

## Teste local

Na pasta `brasil`, com Docker instalado e em execução:

```sh
docker build --progress=plain -t brasil-backend .
docker run --rm --name brasil-backend --env-file .env -e PORT=8080 -p 8080:8080 brasil-backend
```

O `.env` deve usar formato `CHAVE=valor`, sem aspas envolvendo valores, para
compatibilidade com `docker --env-file`. Use um banco de teste remoto ou
`host.docker.internal` para PostgreSQL no host Windows; `localhost` dentro do
container aponta para o próprio container.

```sh
curl -f http://localhost:8080/v3/api-docs
docker logs brasil-backend
```

Validação Maven independente de Docker, no PowerShell:

```powershell
.\mvnw.cmd -B -ntp verify
```

O Dockerfile pula a execução de testes durante a construção; execute a validação
Maven antes de publicar. Nenhuma credencial é necessária para os testes H2.

Referências: https://render.com/docs/docker,
https://render.com/docs/monorepo-support,
https://render.com/docs/web-services,
https://supabase.com/docs/guides/database/connecting-to-postgres,
https://jdbc.postgresql.org/documentation/ssl/
