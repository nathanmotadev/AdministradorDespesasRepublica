# 🏠 República

**Aplicativo web para dividir as contas de quem mora junto.** Quem pagou, quem participa, quem deve quanto a quem e quando cobrar, tudo num só lugar e sem planilha.

![Java](https://img.shields.io/badge/Java-17-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3-brightgreen) ![JWT](https://img.shields.io/badge/Auth-JWT-blue) ![Testes](https://img.shields.io/badge/Testes-JUnit%205-success) ![Licença](https://img.shields.io/badge/Licen%C3%A7a-MIT-lightgrey)

> Projeto pessoal que **uso na minha própria casa**. Nasceu de um problema real: as contas da casa se perdiam em grupos de WhatsApp e planilhas.

<!-- Para adicionar prints: crie a pasta docs/img, coloque as imagens lá e descomente as linhas abaixo.
<p align="center">
  <img src="docs/img/inicio.png" width="260" alt="Tela inicial">
  <img src="docs/img/despesas.png" width="260" alt="Despesas">
  <img src="docs/img/casa.png" width="260" alt="Casa e contas fixas">
</p>
-->

---

## 🎯 Sobre o projeto e os objetivos

**O que é.** A República é um aplicativo web, com uma API em Java por trás, para controlar as despesas de uma casa compartilhada. Cada morador tem a própria conta e vê só o que é dele: o que deve, o que tem a receber e o que precisa fazer para acertar tudo.

**Por que criei.** Moro com mais gente e as contas sempre viravam confusão: quem pagou o mercado, quem ficou devendo a pizza, quanto custou a internet neste mês. Em vez de manter uma planilha, resolvi construir a solução e usá-la de verdade em casa.

**Objetivos do projeto:**

1. **Resolver um problema real.** Substituir a planilha e o WhatsApp por algo que calcula sozinho quem deve quanto a quem.
2. **Praticar desenvolvimento backend de ponta a ponta.** Sair do exercício isolado e construir um sistema completo: modelagem do domínio, API REST, banco de dados, autenticação, testes e deploy.
3. **Aplicar boas práticas.** Código em camadas, regras de negócio testadas, tratamento padronizado de erros, segurança no servidor e documentação (Swagger e diagramas UML).
4. **Aprender fazendo.** Cada funcionalidade nova (contas fixas, quem paga junto, próximas contas) surgiu de uma necessidade real da casa e virou uma oportunidade de resolver um problema técnico novo.
5. **Servir de portfólio.** Mostrar, em código aberto, como penso e como construo software.

**Para quem é.** Para quem divide moradia, como repúblicas, apartamentos de amigos e famílias, e quer uma forma justa e transparente de dividir os custos.

---

## 📌 Em 30 segundos

| | |
|---|---|
| **O problema** | Em casas compartilhadas ninguém sabe ao certo quem deve quanto a quem. |
| **A solução** | Cada gasto é registrado e dividido automaticamente. O app calcula o que cada pessoa precisa pagar ou receber. |
| **O que mostra de técnico** | API REST em camadas, autenticação com JWT, regras de negócio testadas, privacidade garantida no servidor, banco relacional e deploy com Docker. |

---

## ✨ O que o app faz

- **Dividir uma despesa.** O Nathan paga R$ 200 no mercado e marca os 4 moradores: cada um deve R$ 50 a ele. Se a pizza (R$ 90) é só para 3 pessoas, só essas 3 entram na conta.
- **Cobrar e confirmar.** Quem pagou cobra com prazo opcional, copia uma mensagem pronta para mandar no WhatsApp e marca como recebido quando o dinheiro chega.
- **Acertar as contas.** A tela inicial mostra "quem deve quanto a quem", **compensando dívidas nos dois sentidos**. Se Nathan deve R$ 60 ao Duda e o Duda deve R$ 50 ao Nathan, o acerto é o Nathan pagar apenas R$ 10.
- **Contas fixas.** Aluguel, internet e luz se repetem todo mês: cadastre uma vez e a despesa é criada sozinha no vencimento. Pode editar o valor a qualquer momento (a conta de luz muda todo mês) e o app mostra as "próximas contas" com antecedência.
- **Quem paga junto.** Duas irmãs que dividem a parte? A conta continua dividida por pessoa, mas a cobrança é uma só, no nome de uma delas.
- **Histórico por mês.** Na aba Despesas, o modo "Histórico" agrupa tudo por mês, com o total de cada um. Cada dívida mostra quando foi criada e quando vence.
- **Privacidade.** Cada morador só enxerga as despesas e dívidas em que participa.
- **Funciona no celular.** Interface pensada para a tela pequena.

---

## 🧰 Tecnologias

| Camada | Tecnologia |
|---|---|
| Linguagem e framework | Java 17, Spring Boot 3 (Web, Data JPA, Validation) |
| Segurança | Spring Security, JWT (HS256), senhas com BCrypt |
| Banco de dados | H2 em desenvolvimento, PostgreSQL em produção |
| Documentação da API | Swagger UI (springdoc-openapi) |
| Testes | JUnit 5, AssertJ, MockMvc |
| Front-end | HTML, CSS e JavaScript puro (módulos ES, sem build) |
| Entrega | Maven, Docker, Git |

---

## 🚀 Como rodar

**Pré-requisitos:** JDK 17 ou superior e Maven (ou o IntelliJ, que já traz o Maven).

1. Clone o repositório:
   ```bash
   git clone https://github.com/nathanmotadev/AdministradorDespesasRepublica.git
   cd AdministradorDespesasRepublica
   ```
2. Rode o app:
   ```bash
   mvn spring-boot:run
   ```
   No IntelliJ: abra o `pom.xml` como projeto, abra `RepublicaApplication` e clique no triângulo verde.
3. Acesse:
   - **Aplicativo:** <http://localhost:8080>
   - **Documentação da API (Swagger):** <http://localhost:8080/swagger-ui.html>
   - **Banco H2:** <http://localhost:8080/h2-console> (URL `jdbc:h2:file:./data/republica`, usuário `sa`, senha vazia)
4. **Para testar no celular**, com ele no mesmo Wi-Fi do computador: ao subir, o console mostra uma linha `No celular: http://SEU_IP:8080`.
5. Para rodar os testes: `mvn test`.

### Primeiro uso

1. Na tela inicial, **crie uma casa** (você vira o administrador).
2. Na aba **Casa**, copie o **convite** e mande para quem mora com você. Cada um cria a própria conta com o código.
3. Toque no botão **+** e registre a primeira despesa.

---

## 🏗️ Como o código está organizado

```
controller  →  service  →  repository  →  banco
  (HTTP)       (regras)    (Spring Data)
       ↘ dto ↙       ↘ model (entidades JPA)
```

- **controller:** só traduz HTTP, sem regra de negócio.
- **service:** onde ficam as regras (dividir, cobrar, gerar contas fixas).
- **repository:** acesso ao banco com Spring Data JPA.
- **dto:** `records` que definem o que a API recebe e devolve, para não expor as entidades.
- **seguranca:** JWT, filtros e verificação de permissão por casa.
- **static:** a interface (HTML, CSS e JavaScript).

Os diagramas completos (casos de uso, classes, banco de dados, sequências) estão em [`docs/UML.md`](docs/UML.md).

---

## 💡 Decisões técnicas (e por quê)

- **Dinheiro em `BigDecimal` e divisão em centavos.** `double` erra em contas de dinheiro. R$ 100,00 ÷ 3 vira 33,34 + 33,33 + 33,33, sem perder nem criar centavo (`DivisorDeDespesa`).
- **Os acertos não são gravados, são calculados.** A despesa e a dívida de cada pessoa (`PENDENTE → COBRADA → PAGA`) ficam no banco, e o "quem deve a quem" é calculado na hora. Assim ele nunca fica inconsistente com os dados.
- **Privacidade no servidor, não só na tela.** A identidade vem sempre do token JWT, nunca da URL. Quem não participa de uma despesa recebe `404`, como se ela não existisse.
- **JWT stateless.** O servidor não guarda sessão, o que facilita rodar mais de uma instância. A senha é guardada só como hash BCrypt.
- **Contas fixas sem agendador.** Em vez de um job em segundo plano, as despesas vencidas são criadas quando alguém abre o app, com trava no banco para evitar criar a mesma despesa duas vezes. É mais simples de operar numa hospedagem gratuita que "dorme".
- **Integridade do histórico.** Não se exclui despesa que já recebeu pagamento, nem se remove morador com dívida em aberto. Quem sai da casa fica inativo e o histórico é preservado.
- **Erros padronizados.** Um `@RestControllerAdvice` transforma qualquer exceção num JSON com o status HTTP correto: `400` dados inválidos, `401` sem login, `403` sem permissão, `404` não encontrado, `422` regra de negócio violada.
- **Front-end sem framework.** JavaScript puro com módulos ES: menos dependência para um app deste tamanho, e o foco do projeto é o backend.

---

## ✅ Testes

Os testes cobrem o que mais importa nesse tipo de sistema, que é o dinheiro e a privacidade:

| Teste | O que garante |
|---|---|
| `DivisorDeDespesaTest` | A divisão em centavos soma exatamente o total |
| `CenariosDaRepublicaTest` | Cenários reais: mercado, pizza e compensação de dívidas |
| `PrivacidadeDaVisaoTest` | Cada morador só vê o que é seu |
| `ContaFixaServiceTest` | Geração mensal, edição entre os meses, pausa e permissões |
| `AgrupamentoDeMoradoresTest` | Quem paga junto soma as partes e as duas pessoas veem a dívida |
| `ApiTest` | Contrato HTTP: status e formato dos erros (MockMvc) |

```bash
mvn test
```

---

## 🔌 API

A documentação interativa, com todos os endpoints e a opção de testar direto no navegador, fica no **Swagger**: <http://localhost:8080/swagger-ui.html>. Há também o arquivo [`requests.http`](requests.http) com exemplos prontos para rodar no IntelliJ.

Resumo dos grupos de rotas:

| Grupo | Rotas |
|---|---|
| Autenticação | `POST /api/auth/cadastro/casa` · `/cadastro/convite` · `/login` |
| Minha visão | `GET /api/eu` · `/api/eu/resumo` · `/api/eu/dividas` |
| Casa e moradores | `/api/casas/{id}` · `/convite` · `/moradores` · `/moradores/{id}/responsavel` |
| Despesas | `/api/casas/{id}/despesas` (registrar, listar, excluir, cobrar) |
| Cobrança | `POST /api/divisoes/{id}/cobranca` · `/pagamento` |
| Contas fixas | `/api/casas/{id}/contas-fixas` (cadastrar, editar, pausar, retomar, excluir) |

---

## ☁️ Colocar no ar

O projeto já tem `Dockerfile` e um perfil de produção com PostgreSQL. O passo a passo (Render + banco gratuito) está em [`docs/DEPLOY.md`](docs/DEPLOY.md). O segredo do JWT vem da variável de ambiente `JWT_SEGREDO` e nunca fica no código.

---

## 🗺️ Próximos passos

Veja o arquivo [`DESAFIOS.md`](DESAFIOS.md). Algumas ideias: histórico de despesas por pessoa, avisos de vencimento, exportar o extrato e cookie `httpOnly` em vez de `localStorage` para o token.

---

## 👤 Autor

**Nathan Mota**, estudante de Análise e Desenvolvimento de Sistemas (FATEC São Paulo), em busca do primeiro estágio em desenvolvimento de software.

- GitHub: [nathanmotadev](https://github.com/nathanmotadev)
- LinkedIn: [nathan-mota](https://www.linkedin.com/in/nathan-mota-ba596438a/)

## 📄 Licença

MIT. Veja o arquivo [LICENSE](LICENSE).
