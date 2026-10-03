# República

API REST + interface web para dividir as contas de uma casa compartilhada: quem pagou, quem participa, quem deve quanto a quem e quando cobrar.

> **Problema real:** em repúblicas e apartamentos compartilhados, as contas se perdem em planilhas e grupos de WhatsApp. A República registra cada gasto, divide automaticamente entre quem participou, compensa dívidas cruzadas e mostra o que cada um precisa pagar ou receber.

## Como funciona

- **Mercado para todos:** Nathan paga R$ 200 e marca os 4 moradores. Cada um deve R$ 50 a ele.
- **Pizza só para alguns:** Nathan paga R$ 90 no cartão e marca só Larissa e Camilly (e ele). Matheus não entra na conta.
- **Cobrança:** Nathan cobra quando quiser, com prazo opcional, copia uma mensagem pronta e marca como recebido quando o dinheiro chegar.
- **Acertos:** a tela inicial mostra "quem deve quanto a quem", já compensando dívidas nos dois sentidos (se Nathan deve R$ 60 a Matheus e Matheus deve R$ 50 a Nathan, o acerto é Nathan pagar R$ 10).

## Tecnologias

Java 17 · Spring Boot 3 (Web, Data JPA, Validation) · H2 (arquivo) · springdoc-openapi (Swagger UI) · JUnit 5 + AssertJ + MockMvc · front-end em HTML/CSS/JavaScript puro (módulos ES, sem build).

## Como rodar no IntelliJ

1. **File → Open** e selecione o arquivo `pom.xml` (**Open as Project**). Aguarde o Maven baixar as dependências.
2. Em **File → Project Structure → Project**, use um JDK 17 ou superior.
3. Abra `RepublicaApplication` e clique no triângulo verde ao lado do `main`.
4. Acesse:
   - Aplicação: <http://localhost:8080>
   - Documentação interativa da API (Swagger): <http://localhost:8080/swagger-ui.html>
   - Console do banco H2: <http://localhost:8080/h2-console> (URL JDBC: `jdbc:h2:file:./data/republica`, usuário `sa`, senha vazia)
5. Para testar a API sem Postman, abra `requests.http` e clique no ícone verde de cada requisição.
6. Para rodar os testes: botão direito em `src/test/java` → **Run 'All Tests'** (ou `mvn test`).

## API

| Método | Rota | O que faz |
|---|---|---|
| POST | `/api/casas` | Cria uma casa |
| GET | `/api/casas` · `/api/casas/{id}` | Lista / detalha casas |
| POST | `/api/casas/{id}/moradores` | Adiciona morador |
| DELETE | `/api/casas/{id}/moradores/{moradorId}` | Remove morador (só sem dívidas) |
| POST | `/api/casas/{id}/despesas` | Registra e divide uma despesa |
| GET | `/api/casas/{id}/despesas?mes=2026-10` | Lista despesas (opcionalmente de um mês) |
| DELETE | `/api/casas/{id}/despesas/{despesaId}` | Exclui (só se ninguém pagou) |
| POST | `/api/casas/{id}/despesas/{despesaId}/cobranca` | Cobra todos de uma despesa |
| POST | `/api/divisoes/{id}/cobranca` | Cobra uma pessoa, com prazo opcional |
| POST | `/api/divisoes/{id}/pagamento` | Confirma recebimento |
| GET | `/api/casas/{id}/resumo` | Saldos e "quem deve a quem" |

Códigos de resposta: `201` criado · `204` sem conteúdo · `400` dados inválidos (com o detalhe por campo) · `404` não encontrado · `422` regra de negócio violada. Todos os erros seguem o mesmo formato JSON.

## Arquitetura e decisões

```
controller  →  service  →  repository  →  banco
   (HTTP)      (regras)     (Spring Data)
        ↘ dto ↙     ↘ model (entidades JPA)
```

- **Camadas separadas:** o controller só traduz HTTP; as regras ficam nos services; o acesso a dados fica nos repositories.
- **DTOs (`records`) em vez de expor entidades:** a API não vaza detalhes do banco e as validações (`@NotBlank`, `@DecimalMin`…) ficam na borda.
- **Modelo:** `Despesa` é o evento (quem pagou, quanto, quando); `Divisao` é a dívida de cada participante, com status `PENDENTE → COBRADA → PAGA`. Os acertos **não são gravados**, são calculados a partir das divisões em aberto, então nunca ficam inconsistentes.
- **Dinheiro:** `BigDecimal` e divisão em centavos (`DivisorDeDespesa`): R$ 100,00 ÷ 3 = 33,34 + 33,33 + 33,33, sem perder centavo.
- **Erros padronizados:** `@RestControllerAdvice` converte exceções em JSON com o status HTTP correto.
- **Integridade:** não se exclui despesa que já recebeu pagamento, nem se remove morador com dívida em aberto; quem sai da casa fica inativo e o histórico é preservado.
- **Testes:** unitário (divisão em centavos), de cenários de negócio (mercado, pizza, compensação) e de contrato HTTP (MockMvc).

## Usando MySQL em vez de H2

1. No `pom.xml`, troque a dependência `h2` por:
   ```xml
   <dependency>
       <groupId>com.mysql</groupId>
       <artifactId>mysql-connector-j</artifactId>
       <scope>runtime</scope>
   </dependency>
   ```
2. No `application.properties`:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/republica?createDatabaseIfNotExist=true
   spring.datasource.username=root
   spring.datasource.password=sua_senha
   ```

## Próximos passos

Veja o arquivo [`DESAFIOS.md`](DESAFIOS.md).
