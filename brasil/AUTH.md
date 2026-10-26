# Autenticação no Render

A API usa HTTP Basic, sem sessão ou token. Configure no Environment do serviço:

```text
SPRING_SECURITY_USER_NAME=seu_usuario
SPRING_SECURITY_USER_PASSWORD=uma_senha_forte
APP_CORS_ALLOWED_ORIGINS=https://seu-frontend.onrender.com
```

Para vários frontends, separe as origens por vírgula, sem barra final. Faça um novo deploy depois de configurar.

Valide as credenciais com `GET /api/auth/me`, usando Basic Auth no Postman ou o botão Authorize do Swagger:

```sh
curl -u 'seu_usuario:uma_senha_forte' https://seu-backend.onrender.com/api/auth/me
```

Resposta válida: `200 {"username":"seu_usuario"}`. Credenciais ausentes ou incorretas retornam 401.
Envie `Authorization: Basic <base64(usuario:senha)>` em cada chamada protegida, sempre por HTTPS.
O frontend deve obter as credenciais no login; não coloque a senha em código público ou variáveis de build do frontend.

No Render, configure **Health Check Path** como `/health`: essa rota é pública e retorna `{"status":"UP"}`.
As rotas da API continuam protegidas. Acessá-las diretamente sem credenciais retorna 401, como esperado.
