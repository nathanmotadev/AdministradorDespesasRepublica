# Desafios para evoluir o projeto

Cada item é uma melhoria que mostra um conhecimento diferente. Faça no seu ritmo, de preferência uma por branch, com commit e teste. Pode me pedir dicas ou revisão de cada uma.

## Nível 1: regras de negócio
1. **Divisão com valores personalizados.** Hoje a divisão é sempre igual. Permita informar quanto cada participante paga (ex.: "Larissa paga 40%"). Dica: o `DespesaRequest` pode receber uma lista de `{moradorId, valor}` opcional, e o service valida que a soma fecha o total.
2. **Desfazer pagamento.** Um endpoint para voltar uma divisão de `PAGA` para `COBRADA` caso o recebimento tenha sido marcado por engano.
3. **Despesas recorrentes.** Aluguel e internet se repetem todo mês. Crie um modelo de despesa recorrente e gere as despesas do mês. Estudo: `@Scheduled`.

## Nível 2: qualidade e API
4. **Paginação** em `GET /despesas` com `Pageable` do Spring Data.
5. **Migrações com Flyway** no lugar de `ddl-auto=update`, para versionar o banco.
6. **Testes de repositório** com `@DataJpaTest` para a query `findEmAbertoPorCasa`.
7. **Dockerfile + docker-compose** com a aplicação e um MySQL. Estudo: variáveis de ambiente no Spring.

## Nível 3: segurança e produto
8. **Login com JWT** (Spring Security): cada pessoa tem conta e só enxerga as casas em que mora. Estudo: filtros, `PasswordEncoder`, claims.
9. **Convite por link** para um morador entrar na casa.
10. **PIX copia-e-cola** na mensagem de cobrança: gere o payload do PIX com o valor da dívida (padrão BR Code do Banco Central).
11. **Notificações**: lembrete automático de cobranças vencidas por e-mail (`spring-boot-starter-mail`) ou WhatsApp.
12. **Deploy** gratuito (Render, Railway ou Fly.io) e link no portfólio.

## Para a entrevista
Esteja pronto para explicar: por que DTOs, por que `BigDecimal`, por que os acertos são calculados e não gravados, a diferença entre os status HTTP 400 e 422, e o que você faria para escalar para milhares de casas.
