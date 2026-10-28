# Autenticação do backend e integração com o frontend

## Por que `/api/mapa` retorna 401

Todas as rotas `/api/**` exigem HTTP Basic em cada requisição. Isso inclui mapa, dashboard, data centers, Radar, notícias e importações. O endereço `127.0.0.1:5173` no Network é o endereço do proxy Vite; ele não indica, por si só, onde o 401 foi produzido. Pelo código de proxy apresentado, a resposta do backend é repassada ao navegador.

Se `API_USERNAME` ou `API_PASSWORD` estiverem vazios, o proxy apresentado não adiciona Authorization. Se estiverem diferentes dos valores do serviço no Render, o backend rejeita a autenticação. Não é necessário tornar as rotas públicas para integrar o frontend.

## Contrato de autenticação

- Mecanismo: HTTP Basic, cabeçalho `Authorization: Basic <base64(usuario:senha)>`.
- Não há JWT, refresh token, cadastro, endpoint de login ou endpoint de logout implementados.
- A configuração usa um usuário da aplicação, definido pelas variáveis abaixo. Não são as credenciais do PostgreSQL/Supabase.
- A API é stateless: validar `/api/auth/me` não autentica automaticamente chamadas futuras. Todas precisam do cabeçalho.
- Não é necessário cookie de sessão, `credentials: "include"` ou token CSRF para essa integração.
- As rotas protegidas exigem autenticação, sem separação de permissões por operação nessa configuração. O usuário autenticado também pode acessar operações de escrita.
- Base64 não criptografa a senha; use HTTPS entre proxy/cliente e Render.

São públicas `/health`, `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs`, `/v3/api-docs/**` e `/v3/api-docs.yaml`. A raiz `/` não foi liberada.

## 1. Configuração no Render

A API usa HTTP Basic, sem sessão ou token. Configure no Environment do serviço:

```text
SPRING_SECURITY_USER_NAME=seu_usuario
SPRING_SECURITY_USER_PASSWORD=uma_senha_forte
APP_CORS_ALLOWED_ORIGINS=https://seu-frontend.onrender.com
```

Para vários frontends, separe as origens por vírgula, sem barra final. Faça um novo deploy depois de configurar.

Para acesso direto pelo frontend local, use por exemplo:

```env
APP_CORS_ALLOWED_ORIGINS=http://127.0.0.1:5173,http://localhost:5173
```

Sem essa variável, essas duas origens locais já são permitidas por padrão. A configuração CORS é aplicada a `/api/**`, permite GET, POST, PUT, DELETE e OPTIONS, e aceita Authorization, Content-Type, Accept e X-Requested-With. CORS não substitui autenticação. Em chamadas pelo proxy local, o navegador chama a mesma origem do Vite.

## 2. Variáveis do frontend com proxy Vite

Crie `.env.local` na pasta do frontend em que estão `package.json` e `vite.config.ts`:

```env
API_PROXY_TARGET=https://backend-5lgu.onrender.com
API_USERNAME=seu_usuario
API_PASSWORD=sua_senha

RADAR_API_PROXY_TARGET=https://backend-5lgu.onrender.com
RADAR_DATA_SOURCE=radar
```

Substitua `seu_usuario` pelo valor de `SPRING_SECURITY_USER_NAME` e `sua_senha` pelo valor de `SPRING_SECURITY_USER_PASSWORD` do Render. Os nomes das variáveis do frontend são diferentes dos nomes usados pelo backend. Os nomes `API_*` são lidos pelo seu `vite.config.ts`; não são propriedades do Spring.

Não inclua `/api` em `API_PROXY_TARGET`: o proxy preserva o caminho recebido. Não versione `.env.local`. Se a senha contiver `#`, coloque o valor entre aspas no arquivo; valores com `$` exigem cuidado com a expansão de variáveis do carregador dotenv. Não imprima o conteúdo das credenciais em logs.

Pare e reinicie `npm run dev` depois de alterar variáveis ou o proxy. Confira também arquivos como `.env.development.local` e variáveis do processo, que podem sobrescrever valores de `.env.local`.

Se o Radar usar as mesmas credenciais, remova `RADAR_API_USERNAME` e `RADAR_API_PASSWORD` ou use o fallback com `||` mostrado abaixo. Com `??`, uma variável definida como string vazia não usa o fallback.

## 3. Proxy no `vite.config.ts`

Dentro do `defineConfig(({ mode }) => { ... })`, após `loadEnv`, use esta configuração para o proxy. Preserve os plugins e aliases já existentes:

```ts
// No topo do arquivo: import type { ProxyOptions } from "vite";
const primaryTarget = env.API_PROXY_TARGET || "http://localhost:8080";
const radarTarget = env.RADAR_API_PROXY_TARGET || primaryTarget;

const makeProxy = (target: string, username?: string, password?: string): ProxyOptions => {
  if (!username || !password) {
    throw new Error(`Credenciais da API ausentes para ${target}. Confira o .env.local.`);
  }

  return {
    target,
    changeOrigin: true,
    configure(proxyServer) {
      proxyServer.on("proxyReq", (proxyReq) => {
        proxyReq.setHeader(
          "Authorization",
          `Basic ${Buffer.from(`${username}:${password}`).toString("base64")}`,
        );
      });
    },
  };
};

const proxy = {
  "/api/radar": makeProxy(
    radarTarget,
    env.RADAR_API_USERNAME || env.API_USERNAME,
    env.RADAR_API_PASSWORD || env.API_PASSWORD,
  ),
  "/api": makeProxy(primaryTarget, env.API_USERNAME, env.API_PASSWORD),
};
```

Use `proxy` em `server.proxy` e, se necessário, `preview.proxy`, como no arquivo atual. Adicione `import type { ProxyOptions } from "vite"` no topo do arquivo para tipar o exemplo.

O proxy adiciona o cabeçalho na conexão com o Render. Por isso, é normal não ver Authorization nos Request Headers da chamada navegador → Vite. A senha fica no processo do proxy; não use prefixo `VITE_` para essas credenciais.

## 4. Chamadas no React

Com o proxy acima, o frontend usa caminhos relativos e não precisa montar Basic Auth:

```ts
async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(path, {
    headers: { Accept: "application/json" },
  });

  if (response.status === 401) {
    throw new Error("A API recusou as credenciais. Confira a configuração do proxy.");
  }
  if (!response.ok) {
    throw new Error(`Erro HTTP ${response.status}`);
  }
  return response.json() as Promise<T>;
}

// Valida o usuário configurado no proxy:
const user = await apiGet<{ username: string }>("/api/auth/me");

// O mesmo proxy autentica a consulta de mapa:
const response = await fetch("/api/mapa?page=0&size=100&sort=id,asc");
if (!response.ok) throw new Error(`Erro HTTP ${response.status}`);
const mapa = await response.json();
```

Nesse modelo, `/api/auth/me` identifica o usuário configurado no proxy, não um usuário individual que fez login no React. O proxy sobrescreve Authorization usando as credenciais do ambiente.

## 5. Testes para localizar o 401

Primeiro teste diretamente no Render. No PowerShell, informe somente o usuário no comando para o curl solicitar a senha sem gravá-la no histórico:

```powershell
curl.exe -i -u seu_usuario https://backend-5lgu.onrender.com/api/auth/me
```

Esperado: HTTP 200 e `{"username":"seu_usuario"}`. A resposta tem `Cache-Control: no-store`. Se retornar 401, confira as variáveis do backend e se o deploy já aplicou os novos valores. Não use credenciais antigas citadas em outros documentos como prova dos valores atuais.

Depois, com o Vite iniciado, teste o proxy sem fornecer credenciais no curl:

```powershell
curl.exe -i http://127.0.0.1:5173/api/auth/me
curl.exe -i "http://127.0.0.1:5173/api/mapa?page=0&size=100&sort=id,asc"
```

| Resultado | Próxima verificação |
|---|---|
| Render retorna 401 com Basic | Usuário e senha efetivamente configurados no serviço do backend |
| Render retorna 200, Vite retorna 401 | Carregamento do `.env`, credenciais do proxy, destino e reinício do Vite |
| `/api/auth/me` funciona, apenas Radar retorna 401 | Credenciais e destino específicos do Radar |
| Proxy retorna 502 | Erro no terminal do Vite e logs do Render: conexão, DNS, TLS ou timeout; o status sozinho não identifica a causa |
| Navegador mostra erro CORS em chamada direta | Origem exata configurada em `APP_CORS_ALLOWED_ORIGINS` |
| `/health` retorna UP, API retorna 401 | Serviço acessível, mas a chamada protegida não autenticou |

O corpo de uma resposta 401 não precisa ser JSON; cheque `response.ok` antes de chamar `response.json()`.

## 6. Produção e login de usuários

O proxy de desenvolvimento do Vite não é incluído no resultado de `npm run build`. Em uma hospedagem estática, configure um servidor, função ou BFF que encaminhe `/api/**` ao Render e adicione Authorization no lado servidor. `vite preview` serve para pré-visualização local, não substitui essa infraestrutura de produção.

Um proxy público que injeta credenciais compartilhadas permite aos seus visitantes executar as operações autorizadas por essas credenciais, inclusive escritas. Para restringir o acesso a usuários, o servidor do frontend precisa validar a sessão e autorizar as operações antes de encaminhá-las.

Se a intenção for login individual direto contra a API, a implementação atual não fornece cadastro ou gestão de usuários. Um cliente pode enviar Basic Auth em cada chamada usando credenciais fornecidas pelo usuário, com CORS correto, mas isso é um fluxo diferente do proxy com credenciais compartilhadas. Não salve senhas em localStorage nem as inclua no bundle. Encerrar a interface não revoga a senha Basic; a API atual não tem revogação de sessões ou logout remoto.

## Referência rápida

Valide as credenciais com `GET /api/auth/me`, usando Basic Auth no Postman ou o botão Authorize do Swagger:

```sh
curl -u 'seu_usuario:uma_senha_forte' https://seu-backend.onrender.com/api/auth/me
```

Resposta válida: `200 {"username":"seu_usuario"}`. Credenciais ausentes ou incorretas retornam 401.
Envie `Authorization: Basic <base64(usuario:senha)>` em cada chamada protegida, sempre por HTTPS.
O frontend deve obter as credenciais no login; não coloque a senha em código público ou variáveis de build do frontend.

No Render, configure **Health Check Path** como `/health`: essa rota é pública e retorna `{"status":"UP"}`.
As rotas da API continuam protegidas. Acessá-las diretamente sem credenciais retorna 401, como esperado.
