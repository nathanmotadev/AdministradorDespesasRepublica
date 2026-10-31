# Deploy do República (Render + PostgreSQL)

O app roda em um container Docker (`Dockerfile`) e usa o perfil `prod` (`application-prod.properties`).
Tudo que é segredo vem de variáveis de ambiente.

## 1. Banco PostgreSQL
Crie um banco PostgreSQL gratuito (por exemplo no Neon ou no próprio Render; confira os limites atuais de cada plano).
Anote: host, nome do banco, usuário e senha.

A URL do Spring (JDBC) tem este formato:

    jdbc:postgresql://HOST/NOME_DO_BANCO?sslmode=require

## 2. Web Service no Render
1. Em render.com, **New → Web Service** e conecte o repositório do GitHub (branch `main`).
2. **Language/Runtime:** Docker (ele usa o `Dockerfile` da raiz).
3. Em **Environment**, crie as variáveis:

| Variável | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://HOST/NOME_DO_BANCO?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | usuário do banco |
| `SPRING_DATASOURCE_PASSWORD` | senha do banco |
| `JWT_SEGREDO` | uma frase aleatória com 32+ caracteres |

4. Clique em **Deploy**. Quando terminar, abra a URL `https://SEU-APP.onrender.com`.

## 3. Primeiro acesso
- Em "Abrir uma casa nova", crie a casa e a sua conta (você vira administrador).
- Na aba **Casa**, copie o código de convite e mande para os moradores.
- Cada morador cria a conta pelo link, em "Tenho um código de convite".

## Observações
- Planos gratuitos podem "dormir" após um tempo parado: o primeiro acesso pode levar cerca de 1 minuto.
- O banco fica fora do app, então atualizar o código não apaga os dados.
- Nunca coloque senhas ou o `JWT_SEGREDO` no código ou no GitHub.
- Teste local do container: `docker build -t republica .` e depois `docker run -p 8080:8080 -e SPRING_DATASOURCE_URL=... -e SPRING_DATASOURCE_USERNAME=... -e SPRING_DATASOURCE_PASSWORD=... -e JWT_SEGREDO=... republica`.
