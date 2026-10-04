# Documentação UML — república

Diagramas escritos em [Mermaid](https://mermaid.js.org/): o GitHub e o IntelliJ (plugin *Mermaid*) desenham tudo direto deste arquivo.
Eles descrevem o código da branch `feature/login`.

Índice: 1. Casos de uso · 2. Arquitetura em camadas · 3. Classes do domínio · 4. Banco de dados (ER) · 5. Estados da dívida · 6. Sequência: login e cadastro · 7. Sequência: registrar e cobrar uma despesa · 8. Segurança por requisição

---

## 1. Casos de uso

```mermaid
flowchart LR
    visitante([Visitante])
    morador([Morador])
    admin([Administrador])
    pagador([Pagador da despesa])

    subgraph Sistema["república"]
        UC1(Abrir uma casa nova)
        UC2(Entrar com código de convite)
        UC3(Fazer login)
        UC4(Ver meu resumo: devo / a receber)
        UC5(Ver minhas dívidas)
        UC6(Registrar despesa)
        UC7(Listar despesas em que participo)
        UC8(Cobrar uma dívida)
        UC9(Confirmar pagamento)
        UC10(Excluir despesa)
        UC11(Ver código de convite)
        UC12(Renomear a casa)
        UC13(Adicionar / remover morador)
    end

    visitante --> UC1
    visitante --> UC2
    visitante --> UC3
    morador --> UC4
    morador --> UC5
    morador --> UC6
    morador --> UC7
    pagador --> UC8
    pagador --> UC9
    pagador --> UC10
    admin --> UC11
    admin --> UC12
    admin --> UC13
    admin -. "também é" .-> morador
    pagador -. "também é" .-> morador
```

Quem registra a despesa é sempre o pagador, e só ele cobra, confirma pagamento ou exclui.

---

## 2. Arquitetura em camadas

```mermaid
flowchart TB
    subgraph Front["Front-end (static/ — JS puro)"]
        app[app.js] --> views[views/*]
        views --> apijs[api.js]
    end

    subgraph Back["Back-end (Spring Boot)"]
        subgraph Seg["seguranca"]
            cfg[SegurancaConfig<br/>filtro JWT, BCrypt]
            jwts[JwtService]
            autz[AutorizacaoService]
        end
        subgraph Ctl["controller"]
            c1[AuthController]
            c2[EuController]
            c3[CasaController]
            c4[DespesaController]
            c5[DivisaoController]
        end
        subgraph Svc["service"]
            s1[CadastroService]
            s2[AuthService]
            s3[CasaService]
            s4[DespesaService]
            s5[PainelService]
            s6[AcertoService]
            s7[DivisorDeDespesa]
        end
        subgraph Rep["repository (Spring Data JPA)"]
            r1[UsuarioRepository]
            r2[CasaRepository]
            r3[MoradorRepository]
            r4[DespesaRepository]
            r5[DivisaoRepository]
        end
        subgraph Mod["model (entidades)"]
            m[Usuario · Morador · Casa · Despesa · Divisao]
        end
        exc[exception<br/>ApiExceptionHandler]
        dto[dto<br/>Dtos: requests e responses]
    end

    db[(H2 em arquivo)]

    apijs -- "HTTP + JSON<br/>Authorization: Bearer" --> cfg
    cfg --> Ctl
    Ctl --> autz
    Ctl --> Svc
    Svc --> Rep
    Rep --> Mod
    Rep --> db
    s1 & s2 --> jwts
    Ctl -.-> dto
    Svc -.-> dto
    exc -. "traduz erros em 401/403/404/422" .-> Ctl
```

Regra de dependência: o controller nunca fala com repositório; o serviço nunca conhece HTTP. A identidade vem do token, nunca de um id na URL.

---

## 3. Diagrama de classes (domínio)

```mermaid
classDiagram
    class Casa {
        -Long id
        -String nome
        -String codigoConvite
    }
    class Morador {
        -Long id
        -String nome
        -boolean ativo
        -boolean admin
    }
    class Usuario {
        -Long id
        -String email
        -String senhaHash
    }
    class Despesa {
        -Long id
        -String descricao
        -BigDecimal valorTotal
        -LocalDate data
        +envolve(Long moradorId) boolean
    }
    class Divisao {
        -Long id
        -BigDecimal valor
        -StatusDivisao status
        -LocalDate cobradaEm
        -LocalDate vencimento
        -LocalDate pagaEm
    }
    class StatusDivisao {
        <<enumeration>>
        PENDENTE
        COBRADA
        PAGA
    }

    Casa "1" o-- "0..*" Morador : moradores
    Usuario "1" --> "1" Morador : conta de
    Casa "1" <-- "0..*" Despesa : pertence a
    Despesa "0..*" --> "1" Morador : pagador
    Despesa "1" *-- "1..*" Divisao : divisoes
    Divisao "0..*" --> "1" Morador : devedor
    Divisao --> StatusDivisao
```

- `Despesa` e `Divisao` formam uma composição (cascade + orphanRemoval): apagar a despesa apaga as divisões.
- Um morador pode existir **sem** `Usuario` (morador cadastrado pelo admin, sem conta).
- A parte do próprio pagador entra como `Divisao` já `PAGA`.

### Serviços e segurança (classes principais)

```mermaid
classDiagram
    class AuthController
    class EuController
    class CasaController
    class DespesaController
    class DivisaoController

    class CadastroService {
        +cadastrarComCasa(req) SessaoResponse
        +cadastrarComConvite(req) SessaoResponse
    }
    class AuthService {
        +login(req) SessaoResponse
    }
    class CasaService {
        +buscar(casaId) CasaResponse
        +renomear(casaId, req) CasaResponse
        +codigoDeConvite(casaId) ConviteResponse
        +adicionarMorador(casaId, req) MoradorResponse
        +desativarMorador(casaId, moradorId)
    }
    class DespesaService {
        +registrar(casaId, req, solicitanteId) DespesaResponse
        +listar(casaId, mes, solicitanteId) List~DespesaResponse~
        +buscar(casaId, despesaId, solicitanteId) DespesaResponse
        +excluir(casaId, despesaId, solicitanteId)
        +cobrarTodos(casaId, despesaId, vencimento, solicitanteId) DespesaResponse
        +cobrar(divisaoId, vencimento, solicitanteId) DivisaoResponse
        +registrarPagamento(divisaoId, solicitanteId) DivisaoResponse
    }
    class PainelService {
        +meuResumo(casaId, moradorId) MeuResumoResponse
        +minhasDividas(casaId, moradorId) MinhasDividasResponse
    }
    class DivisorDeDespesa {
        +dividir(total, partes) List~BigDecimal~$
    }
    class AutorizacaoService {
        +logado(jwt) Morador
        +membroDaCasa(jwt, casaId) Morador
        +administradorDaCasa(jwt, casaId) Morador
    }
    class JwtService {
        +gerar(usuario) String
    }

    AuthController --> CadastroService
    AuthController --> AuthService
    EuController --> AutorizacaoService
    EuController --> CasaService
    EuController --> PainelService
    CasaController --> CasaService
    CasaController --> AutorizacaoService
    DespesaController --> DespesaService
    DespesaController --> AutorizacaoService
    DivisaoController --> DespesaService
    DivisaoController --> AutorizacaoService
    CadastroService --> CasaService
    CadastroService --> JwtService
    AuthService --> JwtService
    DespesaService --> DivisorDeDespesa
```

---

## 4. Banco de dados (modelo entidade-relacionamento)

```mermaid
erDiagram
    CASA ||--o{ MORADOR : "tem"
    CASA ||--o{ DESPESA : "tem"
    MORADOR ||--o| USUARIO : "pode ter conta"
    MORADOR ||--o{ DESPESA : "paga"
    DESPESA ||--|{ DIVISAO : "se divide em"
    MORADOR ||--o{ DIVISAO : "deve"

    CASA {
        bigint id PK
        varchar nome
        varchar codigo_convite UK
    }
    MORADOR {
        bigint id PK
        varchar nome
        boolean ativo
        boolean administrador
        bigint casa_id FK
    }
    USUARIO {
        bigint id PK
        varchar email UK
        varchar senha_hash
        bigint morador_id FK, UK
    }
    DESPESA {
        bigint id PK
        varchar descricao
        decimal valor_total
        date data
        bigint casa_id FK
        bigint pagador_id FK
    }
    DIVISAO {
        bigint id PK
        decimal valor
        varchar status
        date cobrada_em
        date vencimento
        date paga_em
        bigint despesa_id FK
        bigint devedor_id FK
    }
```

---

## 5. Estados de uma dívida (`Divisao.status`)

```mermaid
stateDiagram-v2
    [*] --> PENDENTE : despesa registrada<br/>(devedores)
    [*] --> PAGA : parte do próprio pagador
    PENDENTE --> COBRADA : pagador cobra<br/>(define vencimento)
    COBRADA --> COBRADA : cobrar de novo<br/>(novo vencimento)
    PENDENTE --> PAGA : pagador confirma pagamento
    COBRADA --> PAGA : pagador confirma pagamento
    PAGA --> [*]
```

Uma dívida COBRADA com vencimento no passado aparece como **atrasada** (calculado na resposta, não é um status guardado).

---

## 6. Sequência: cadastro com convite e login

```mermaid
sequenceDiagram
    actor U as Visitante
    participant F as Front-end (auth.js)
    participant AC as AuthController
    participant CS as CadastroService
    participant R as Repositórios
    participant J as JwtService

    U->>F: código CASA-XXXXXX, nome, e-mail, senha
    F->>AC: POST /api/auth/cadastro/convite
    AC->>CS: cadastrarComConvite(request)
    CS->>R: CasaRepository.findByCodigoConvite
    alt código inválido ou e-mail já usado
        CS-->>AC: RegraDeNegocioException
        AC-->>F: 422 {mensagem}
    else ok
        CS->>R: salva Morador + Usuario (senha com BCrypt)
        CS->>J: gerar(usuario)
        J-->>CS: JWT (moradorId, casaId)
        CS-->>AC: SessaoResponse
        AC-->>F: 201 {token, casaId, morador}
        F->>F: guarda token em localStorage
    end

    Note over U,F: Login depois disso
    U->>F: e-mail e senha
    F->>AC: POST /api/auth/login
    AC->>CS: (AuthService) login
    alt credenciais erradas ou morador inativo
        AC-->>F: 401 (mesma mensagem nos três casos)
    else ok
        AC-->>F: 200 {token, casaId, morador}
    end
```

---

## 7. Sequência: registrar e cobrar uma despesa

```mermaid
sequenceDiagram
    actor P as Pagador (logado)
    actor D as Devedor
    participant F as Front-end
    participant DC as DespesaController
    participant A as AutorizacaoService
    participant DS as DespesaService
    participant Div as DivisorDeDespesa
    participant R as Repositórios

    P->>F: descrição, valor, data, participantes
    F->>DC: POST /api/casas/{id}/despesas (Bearer token)
    DC->>A: membroDaCasa(jwt, casaId)
    A-->>DC: Morador logado
    DC->>DS: registrar(casaId, request, solicitanteId)
    DS->>Div: dividir(valorTotal, nParticipantes)
    Div-->>DS: partes em centavos (resto distribuído)
    DS->>R: salva Despesa + Divisoes (PENDENTE; a do pagador já PAGA)
    DS-->>DC: DespesaResponse
    DC-->>F: 201

    P->>F: "Cobrar" com vencimento
    F->>DC: POST /api/divisoes/{id}/cobranca
    DC->>DS: cobrar(divisaoId, vencimento, solicitanteId)
    DS->>DS: exigirPagador (senão 403)
    DS->>R: status = COBRADA
    DS-->>F: DivisaoResponse

    D->>F: abre "Minha visão"
    F->>F: GET /api/eu/dividas
    Note right of D: só vê dívidas em que participa

    P->>F: confirma que recebeu
    F->>DC: POST /api/divisoes/{id}/pagamento
    DS->>R: status = PAGA, pagaEm = hoje
```

---

## 8. Segurança por requisição

```mermaid
flowchart TD
    req[Requisição /api/...] --> tk{Tem JWT válido?}
    tk -- não --> e401[401 Não autenticado]
    tk -- sim --> id[Lê moradorId e casaId do token]
    id --> mem{Morador ativo<br/>nesta casa?}
    mem -- não --> e403[403 Acesso negado]
    mem -- sim --> papel{Ação exige<br/>admin ou pagador?}
    papel -- "sim, mas não é" --> e403
    papel -- ok --> vis{Recurso é de<br/>outra pessoa?}
    vis -- sim --> e404[404: esconde que existe]
    vis -- não --> regra{Regra de negócio ok?}
    regra -- não --> e422[422]
    regra -- sim --> ok[200 / 201 / 204]
```

Os arquivos estáticos (`/`, `/js`, `/css`), o Swagger e o H2 console ficam liberados (`permitAll`); todo o resto exige token.
