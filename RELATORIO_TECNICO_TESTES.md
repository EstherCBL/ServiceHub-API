# Relatório Técnico — ServiceHub API

> Relatório gerado a partir da análise real do código, da execução do projeto e de testes HTTP/SQL feitos em **20/09/2026**.
> Todos os resultados abaixo foram observados nesta sessão. Onde algo **não** foi executado ou **não** pôde ser confirmado, isso está dito explicitamente.
> Nenhuma senha ou credencial é reproduzida neste documento.
>
> **Atualização de 21/09/2026:** as seções 1–16 abaixo foram preservadas como escritas em 20/09/2026 e descrevem aquela execução (PostgreSQL portátil, `java -jar` no host); cada uma das seções 2, 12, 13, 14 e 15 traz um aviso do que mudou depois. A **seção 17** registra a validação com Docker e a correção dos defeitos P1 e P2, e lista o que continua pendente.
>
> **Estado atual (fim de 21/09/2026):** PostgreSQL e aplicação validados em Docker; **P1 e P2 corrigidos**; **41 testes automatizados, todos aprovados** (`.\mvnw.cmd -B clean test`, JDK 21.0.12); pendentes: P3, P4, P6, P7, divergências D1–D4 do OpenAPI e testes contra PostgreSQL real (seção 17.11).

---

## 1. Identificação do projeto

| Item | Valor (fonte) |
|---|---|
| Nome do projeto | ServiceHub API (`artifactId` `servicehub-api`, versão `0.1.0-SNAPSHOT`) — `pom.xml` |
| Objetivo | API RESTful de um catálogo de serviços. A entrega atual (Checkpoint) implementa o recurso **Serviços** com CRUD completo — `README.md`, `OpenApiConfig` |
| Data da análise | 20/09/2026 |
| Linguagem / versão do Java | Java 21 (`java.version=21` no `pom.xml`). JDK usado nos testes: OpenJDK 21.0.12.1 (`java -version`) |
| Framework | Spring Boot **3.5.16** (`spring-boot-starter-parent`) |
| Gerenciador de dependências / build | **Maven**, com Maven Wrapper (`mvnw`, `mvnw.cmd`; distribuição `apache-maven-3.9.11` encontrada em `~/.m2/wrapper`) |
| Banco de dados | PostgreSQL (testes manuais com PostgreSQL **16.4**); H2 em memória nos testes automatizados |
| Migrations | Flyway 11.7.2 |
| Documentação da API | springdoc-openapi-starter-webmvc-ui **2.8.17** (Swagger UI 5.32.2, OpenAPI 3.1.0 gerado) |

Versões efetivamente empacotadas no JAR (`BOOT-INF/lib`): spring-webmvc 6.2.19, spring-data-jpa 3.5.13, hibernate-core 6.6.53.Final, hibernate-validator 8.0.3.Final, jackson-databind 2.21.4, HikariCP 6.3.3, tomcat-embed-core 10.1.55, postgresql (driver) 42.7.11, flyway-core / flyway-database-postgresql 11.7.2, springdoc-openapi-starter-webmvc-ui 2.8.17, swagger-ui 5.32.2.

---

## 2. Resumo executivo

> **Atualização (21/09/2026):** o resumo abaixo é o de 20/09. Depois dele, o `docker-compose.yml` foi executado e validado, **P1 e P2 foram corrigidos** (a "Funcionalidades corrigidas: nenhuma" não vale mais) e a suíte passou de 27 para **41 testes** — ver seções 17.3 a 17.15.

**O que foi analisado:** estrutura de diretórios, `pom.xml`, `application.properties`, `messages.properties`, migration Flyway, todas as classes Java de produção (10 arquivos) e de teste (2 classes), `docker-compose.yml`, `.env.example`, `.gitignore`, `README.md`.

**Funcionalidades encontradas (todas confirmadas por execução):**
- CRUD completo do recurso `Servico` em `/api/v1/servicos` (5 endpoints);
- validação de entrada com Bean Validation e erros no padrão Problem Details (RFC 9457, `application/problem+json`);
- migrations Flyway (`V1__criar_tabela_servicos.sql`) e `ddl-auto=validate`;
- Swagger UI e `/v3/api-docs` funcionando, com `@Tag`, `@Operation`, `@ApiResponse`, `@Parameter` e `@Schema`.

**Funcionalidades corrigidas:** **nenhuma.** Nenhum arquivo de código-fonte, teste ou configuração foi alterado (ver seção 12 — os defeitos encontrados foram apenas registrados, com a justificativa). Arquivos adicionados: este relatório e `docs/evidencias/`.

**Testes executados:**
- Automatizados: `./mvnw clean test` → **27 testes, 0 falhas, 0 erros, 0 ignorados**, `BUILD SUCCESS`.
- Manuais contra a aplicação real + PostgreSQL 16.4: **86 execuções HTTP** (5 endpoints + cenários negativos + rotas inexistentes + falha de banco), consultas SQL antes/depois, inspeção do esquema e do `/v3/api-docs`, renderização do Swagger UI em navegador headless.
- Resultado dos 86 testes HTTP: 85 marcados `PASS` e 1 `FAIL` (PUT-15, causado pela codificação do `curl` no Windows — não é defeito da API; repetido com sucesso em PUT-15b). **Atenção:** 4 dos 85 `PASS` são cenários de *observação* (POST-18, POST-31, PUT-05, PUT-16), aceitos por serem exploratórios, dois deles expondo defeitos (seção 12).

**Testes não executados (e por quê):**
- `docker compose up` / `docker-compose.yml`: **Docker não está instalado** neste ambiente.
- Swagger UI com *Try it out* interativo: a extensão do Chrome não conectou (2 tentativas). Só foi validada a **renderização** da UI (Edge headless).
- `./mvnw spring-boot:run`: a aplicação foi iniciada pelo JAR (`java -jar`), não por este comando.
- Postman: não disponível/usado. Testes de carga, concorrência e autenticação: fora do escopo (a API não tem autenticação).

**Problemas identificados:** 2 defeitos de validação/dados, 3 divergências/inconsistências de contrato e mensagens, e alguns riscos operacionais (seção 12).

---

## 3. Estrutura e arquitetura do projeto

### 3.1 Organização dos diretórios

```
ServiceHub-API/
├── pom.xml, mvnw, mvnw.cmd, .mvn/wrapper/
├── docker-compose.yml, .env.example, .gitignore, README.md
├── docs/                      (ServiceHub-API-Checkpoint.pptx, img/, evidencias/ ← adicionada por este relatório)
├── src/main/java/com/servicehub/api/
│   ├── ServiceHubApiApplication.java      (@SpringBootApplication)
│   ├── config/OpenApiConfig.java
│   ├── controller/ServicoController.java
│   ├── dto/ServicoRequest.java, ServicoResponse.java   (records)
│   ├── entity/Servico.java
│   ├── exception/GlobalExceptionHandler.java, RecursoNaoEncontradoException.java
│   ├── repository/ServicoRepository.java
│   └── service/ServicoService.java
├── src/main/resources/
│   ├── application.properties, messages.properties
│   └── db/migration/V1__criar_tabela_servicos.sql
└── src/test/
    ├── java/.../controller/ServicoControllerTest.java
    ├── java/.../service/ServicoServiceTest.java
    └── resources/application-test.properties
```

### 3.2 Arquitetura em camadas e responsabilidades

| Camada | Classe | Responsabilidade real no código |
|---|---|---|
| Controller | `ServicoController` | Mapeia as rotas, recebe o corpo (`@Valid @RequestBody`), monta o `Location` do POST e o status HTTP; carrega as anotações OpenAPI. Não contém regra de negócio. |
| Service | `ServicoService` | Orquestra o CRUD, controla transações (`@Transactional`; `readOnly = true` nas leituras), lança `RecursoNaoEncontradoException` quando o ID não existe. |
| Repository | `ServicoRepository` | Interface `JpaRepository<Servico, Long>` sem métodos adicionais (acesso a dados via Spring Data JPA). |
| Entity | `Servico` | Entidade JPA mapeada para a tabela `servicos`. |
| DTO | `ServicoRequest` / `ServicoResponse` | *Records*. O Request carrega as regras de validação e converte para/aplica na entidade (`paraEntidade()`, `aplicarEm()`); o Response converte a entidade (`ServicoResponse.de(...)`). |
| Exceções | `GlobalExceptionHandler`, `RecursoNaoEncontradoException` | Traduz exceções em `ProblemDetail`. |
| Config | `OpenApiConfig` | Metadados do OpenAPI e ordenação alfabética dos caminhos. |

Não existe camada de *mapper* separada (a conversão fica nos próprios DTOs) nem interface de service (a classe `ServicoService` é concreta).

### 3.3 Fluxo de uma requisição (exemplo: `POST /api/v1/servicos`)

1. Tomcat recebe a requisição; o `DispatcherServlet` a encaminha ao `ServicoController.criar`.
2. O Jackson desserializa o JSON em `ServicoRequest`; `@Valid` dispara o Bean Validation. Se falhar → `MethodArgumentNotValidException` → `GlobalExceptionHandler.handleMethodArgumentNotValid` → `400` com a lista `erros`.
3. `ServicoService.criar` (transação aberta) chama `request.paraEntidade()` (aplica `strip()` em `nome` e `categoria`) e `repository.save(...)`.
4. O Hibernate executa o `INSERT` (ID gerado por `GenerationType.IDENTITY` → coluna `GENERATED BY DEFAULT AS IDENTITY`); `@CreationTimestamp`/`@UpdateTimestamp` preenchem `criado_em`/`atualizado_em`.
5. O service devolve `ServicoResponse`; o controller retorna `201 Created` com o cabeçalho `Location` (`ServletUriComponentsBuilder`).

### 3.4 Padrões identificados
Arquitetura em camadas; DTOs como *records* imutáveis; *Repository* (Spring Data); tratamento global de exceções com `@RestControllerAdvice` estendendo `ResponseEntityExceptionHandler`; Problem Details (RFC 9457); injeção de dependência por construtor; migrations versionadas.

---

## 4. Configuração do Spring Boot

### 4.1 Inicialização
- **Classe principal:** `com.servicehub.api.ServiceHubApiApplication` (`@SpringBootApplication`, `SpringApplication.run`).
- **Comando usado nesta análise** (após `mvnw.cmd -B clean test` e `mvnw.cmd -B -DskipTests package`):
  `java -jar target\servicehub-api-0.1.0-SNAPSHOT.jar`, com as variáveis `DB_URL`, `DB_USER` e `DB_PASSWORD` definidas no ambiente (valores sensíveis omitidos).
- **Resultado:** `Started ServiceHubApiApplication in 5.874 seconds`; Tomcat na porta 8080; Flyway aplicou a migration v1.

### 4.2 Dependências (`pom.xml`)

| Dependência | Escopo | Finalidade |
|---|---|---|
| `spring-boot-starter-web` | compile | Spring MVC + Tomcat embarcado + Jackson |
| `spring-boot-starter-data-jpa` | compile | Spring Data JPA + Hibernate + HikariCP |
| `spring-boot-starter-validation` | compile | Bean Validation (Hibernate Validator) |
| `org.postgresql:postgresql` | runtime | Driver JDBC do PostgreSQL |
| `flyway-core`, `flyway-database-postgresql` | compile | Migrations (o módulo específico é necessário no Flyway 10+) |
| `springdoc-openapi-starter-webmvc-ui` 2.8.17 | compile | Gera `/v3/api-docs` e serve o Swagger UI |
| `spring-boot-starter-test` | test | JUnit 5, Mockito, MockMvc, AssertJ |
| `com.h2database:h2` | test | Banco em memória dos testes automatizados |

Plugin de build: `spring-boot-maven-plugin` (gera o JAR executável: `servicehub-api-0.1.0-SNAPSHOT.jar`, ~57,9 MB).

### 4.3 Configurações da aplicação

Arquivo: `src/main/resources/application.properties`.

| Configuração | O que faz / como influencia | Validada na execução? |
|---|---|---|
| `spring.application.name=servicehub-api` | Nome da aplicação (aparece nos logs). | Sim — logs mostram `[servicehub-api]`. |
| `spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/servicehub}` | URL JDBC lida da variável `DB_URL`; padrão: banco `servicehub` em localhost:5432. | Sim — usei `DB_URL` apontando para o banco `servicehub_relatorio`; o Flyway confirmou `jdbc:postgresql://localhost:5432/servicehub_relatorio (PostgreSQL 16.4)`. |
| `spring.datasource.username=${DB_USER:servicehub}` | Usuário (variável `DB_USER`; padrão `servicehub`). | Sim (usuário definido por variável). |
| `spring.datasource.password=${DB_PASSWORD}` | Senha **sem valor padrão**: nenhuma senha no repositório. | Sim — ver 4.4. |
| `spring.jpa.hibernate.ddl-auto=validate` | O Hibernate **só valida** o esquema contra as entidades; não cria nem altera tabelas. | Parcial — a aplicação subiu com o esquema criado pelo Flyway (validação passou). Não provoquei uma divergência para ver a validação falhar. |
| `spring.jpa.open-in-view=false` | Desativa o *Open Session In View*: a sessão JPA não fica aberta durante a renderização da resposta. | Configuração lida; não exercitada isoladamente (não há relacionamentos lazy). |
| `spring.flyway.enabled=true` / `spring.flyway.locations=classpath:db/migration` | Executa migrations na inicialização. | Sim — `Successfully applied 1 migration to schema "public", now at version v1`. |
| `springdoc.swagger-ui.path=/swagger-ui.html` | Caminho do Swagger UI. | Sim — `/swagger-ui.html` → `302` para `/swagger-ui/index.html`. |
| `springdoc.api-docs.path=/v3/api-docs` | Caminho do JSON OpenAPI. | Sim — `200`. |

**Não configurados (comportamento padrão do Spring Boot):** `server.port` (padrão **8080**, confirmado no log do Tomcat), profiles em `main` (log: `No active profile set, falling back to 1 default profile: "default"`), CORS, Jackson, pool Hikari, `spring.mvc.problemdetails.*`, `spring.messages.*`.

### 4.4 Variáveis de ambiente
| Variável | Padrão | Observação |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/servicehub` | Opcional |
| `DB_USER` | `servicehub` | Opcional |
| `DB_PASSWORD` | *(nenhum)* | Obrigatória na prática |

**Teste feito:** iniciei uma segunda instância (porta 8081) **sem** `DB_PASSWORD` (usando o `DB_USER` padrão). Resultado: o processo **encerrou com `exit=1`** e o log mostra `FATAL: password authentication failed for user "servicehub"` (SQLState 28P01, propagado por Flyway/Hikari). Ou seja, a aplicação de fato não sobe, mas a mensagem é um erro de autenticação do PostgreSQL, **não** uma mensagem explícita de "variável DB_PASSWORD ausente".

### 4.5 Perfil de testes
`src/test/resources/application-test.properties` (ativado por `@ActiveProfiles("test")` em `ServicoControllerTest`): H2 em memória em modo PostgreSQL (`MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;...`), usuário `sa`, `ddl-auto=validate` e Flyway habilitado — a **mesma migration** roda no H2 e o Hibernate valida o esquema.

### 4.6 Outras configurações relevantes
- **Tratamento global de exceções:** `GlobalExceptionHandler` (`@RestControllerAdvice` estendendo `ResponseEntityExceptionHandler`) — validado nos testes de erro (seção 8).
- **Mensagens em português:** `messages.properties` fornece `title`/`detail` dos Problem Details do Spring MVC (`problemDetail.title.<exceção>` / `problemDetail.<exceção>`); usado automaticamente pelo `MessageSource` padrão.
- **Validação:** `spring-boot-starter-validation` + `@Valid` no controller + anotações no `ServicoRequest`. Sem configuração adicional.
- **CORS:** não configurado. Verificado (GET-16 e GET-17): requisição com `Origin` não recebe `Access-Control-Allow-Origin`; *preflight* retorna `403` com o texto `Invalid CORS request`.
- **OpenAPI:** `OpenApiConfig` define `Info` (título "ServiceHub API", versão `v1`, descrição) e o `OpenApiCustomizer` `ordenarCaminhos` (ordem alfabética dos paths).
- **Aviso do springdoc no log:** `SpringDoc /v3/api-docs endpoint is enabled by default. To disable it in production, set springdoc.api-docs.enabled=false` (idem para o Swagger UI) — os endpoints de documentação ficam públicos em produção a menos que sejam desabilitados.

---

## 5. Recurso implementado com CRUD: `Servico`

### 5.1 Finalidade
Representa um serviço oferecido na plataforma (ex.: "Instalação de ar-condicionado"), com categoria, preço e duração estimada. É o único recurso do projeto e possui CRUD completo.

### 5.2 Entidade `Servico` (tabela `servicos`)

| Campo Java | Coluna | Tipo (Java → SQL) | Restrições |
|---|---|---|---|
| `id` | `id` | `Long` → `BIGINT` | **PK**, `GENERATED BY DEFAULT AS IDENTITY` (`GenerationType.IDENTITY`) |
| `nome` | `nome` | `String` → `VARCHAR(100)` | `NOT NULL` |
| `descricao` | `descricao` | `String` → `VARCHAR(500)` | opcional |
| `categoria` | `categoria` | `String` → `VARCHAR(60)` | `NOT NULL` |
| `preco` | `preco` | `BigDecimal` → `NUMERIC(10,2)` | `NOT NULL`, `CHECK (preco > 0)` |
| `duracaoMinutos` | `duracao_minutos` | `Integer` → `INTEGER` | `NOT NULL`, `CHECK (duracao_minutos > 0)` |
| `criadoEm` | `criado_em` | `Instant` → `TIMESTAMPTZ` | `NOT NULL`, `@CreationTimestamp`, `updatable=false` |
| `atualizadoEm` | `atualizado_em` | `Instant` → `TIMESTAMPTZ` | `NOT NULL`, `@UpdateTimestamp` |

Relacionamentos: **nenhum** (tabela única). Não há regra de unicidade (confirmado: POST-30 aceitou nome duplicado).

### 5.3 DTOs e validações (`ServicoRequest`)

| Campo | Regras (mensagem retornada pela API) |
|---|---|
| `nome` | `@NotBlank` ("O nome é obrigatório"); `@Size(3..100)` ("O nome deve ter entre 3 e 100 caracteres") |
| `descricao` | opcional; `@Size(max=500)` ("A descrição deve ter no máximo 500 caracteres") |
| `categoria` | `@NotBlank` ("A categoria é obrigatória"); `@Size(max=60)` |
| `preco` | `@NotNull`; `@DecimalMin("0.01")` ("O preço deve ser maior que zero"); `@DecimalMax("99999999.99")` ("O preço excede o valor máximo permitido"); `@Digits(8,2)` ("...no máximo 2 casas decimais") |
| `duracaoMinutos` | `@NotNull`; `@Min(1)`; `@Max(100000)` |

`ServicoResponse` devolve: `id, nome, descricao, categoria, preco, duracaoMinutos, criadoEm, atualizadoEm` (datas em UTC/ISO-8601).

### 5.4 Tratamento de erros (`GlobalExceptionHandler`)
- `RecursoNaoEncontradoException` → `404` ("Recurso não encontrado", detalhe `Serviço com id N não encontrado`).
- `MethodArgumentNotValidException` → `400` ("Dados inválidos") + array `erros[{campo, mensagem}]`.
- Exceções do Spring MVC (JSON inválido, tipo de parâmetro, 405, 415, rota inexistente…) → tratadas pela classe base, com textos de `messages.properties`.
- `Exception` (qualquer outra) → `500` genérico ("Erro interno do servidor"), com o erro completo registrado só no log.

### 5.5 Persistência
Spring Data JPA/Hibernate com `JpaRepository`. Esquema criado pelo Flyway (`V1__criar_tabela_servicos.sql`); Hibernate só valida. Conexões via HikariCP.

### 5.6 Fluxo de cada operação (real, conforme `ServicoService`)
- **Criar:** `save(request.paraEntidade())` → `INSERT` → resposta `201` + `Location`.
- **Listar:** `findAll(Sort.by("id"))` (transação `readOnly`) → lista ordenada por `id`.
- **Buscar:** `findById` → se vazio, `RecursoNaoEncontradoException` (404).
- **Atualizar (PUT):** busca a entidade (404 se não existir) → `aplicarEm` substitui todos os campos (descrição omitida vira `null`) → `saveAndFlush` → `atualizado_em` renovado, `criado_em` preservado.
- **Excluir:** busca a entidade (404 se não existir) → `delete` → `204` sem corpo.

---

## 6. Lista completa de endpoints

Endpoints da API (mapeados em `ServicoController`; prefixo `@RequestMapping("/api/v1/servicos")`, `produces = application/json`):

| Método | URL completa | Método Java | Finalidade | Status esperado (código) | Status confirmado nos testes |
|---|---|---|---|---|---|
| `POST` | `http://localhost:8080/api/v1/servicos` | `ServicoController.criar` → `ServicoService.criar` | Cadastrar serviço | `201` (+`Location`) / `400` / `415` | 201, 400, 415 |
| `GET` | `/api/v1/servicos` | `listar` | Listar todos | `200` | 200 |
| `GET` | `/api/v1/servicos/{id}` | `buscarPorId` | Buscar por ID | `200` / `400` / `404` | 200, 400, 404 |
| `PUT` | `/api/v1/servicos/{id}` | `atualizar` | Substituir serviço (PUT completo) | `200` / `400` / `404` / `415` | 200, 400, 404, 415 |
| `DELETE` | `/api/v1/servicos/{id}` | `excluir` (`@ResponseStatus(NO_CONTENT)`) | Excluir | `204` / `400` / `404` | 204, 400, 404 |

- **Parâmetros de rota:** `id` (`Long`, `@PathVariable`; documentado no OpenAPI como `integer/int64`, obrigatório).
- **Parâmetros de consulta:** **nenhum** (sem paginação nem filtros — GET-11 confirmou que `page`, `size` e `categoria` são ignorados e a lista completa é retornada).
- **Corpo:** `ServicoRequest` (JSON) em POST e PUT. **Resposta:** `ServicoResponse` (POST/PUT/GET id), `ServicoResponse[]` (GET lista), vazio (DELETE).
- **PATCH não existe** (PATCH → `405`, `Allow: PUT, GET, DELETE`).

Endpoints de infraestrutura (springdoc; sondados com `curl`):

| URL | Resultado observado |
|---|---|
| `GET /swagger-ui.html` | `302` → `/swagger-ui/index.html` |
| `GET /swagger-ui/index.html` | `200 text/html` (título "Swagger UI") |
| `GET /v3/api-docs` | `200 application/json` (7.728 bytes) |
| `GET /v3/api-docs.yaml` | `200 application/vnd.oai.openapi` |
| `GET /v3/api-docs/swagger-config` | `200 application/json` |

Não existe Actuator (`/actuator/health` → `404`).

---

## 7. Testes executados (HTTP contra a aplicação real + PostgreSQL 16.4)

**Método:** `curl` (Git Bash, Windows) contra `http://localhost:8080`, com um script que grava requisição, status, tempo e corpo. Banco novo `servicehub_relatorio` (0 tabelas no início; Flyway criou o esquema). Cabeçalho enviado: `Content-Type: application/json` (exceto nos cenários de Content-Type incorreto/ausente). Tempos: primeira requisição 414 ms (aquecimento); demais tipicamente 3–30 ms.

**Evidência completa:** `docs/evidencias/evidencias-testes-http.txt` (87 blocos: 86 testes + 1 execução inconclusiva repetida de PUT-15d) e `docs/evidencias/resultados-testes-http.tsv`.

> **Legenda:** ✅ Aprovado (status igual ao esperado) · ➖ Observação (cenário exploratório; ver seção 12) · ❌ Reprovado.
> Os textos de resposta são resumos; os JSON completos estão no arquivo de evidências.

### 7.1 `POST /api/v1/servicos` — Criar

| ID | Cenário | Requisição (resumo) | Esperado | Obtido | Resposta (resumo) | Situação |
|---|---|---|---|---|---|---|
| POST-01 | Serviço válido com acentos e descrição | `@post-valido.json` (nome "Instalação de ar-condicionado", preco 350.00, 120 min) | 201 | **201** | `Location: .../servicos/1`; `{"id":1,...,"criadoEm":"2026-09-20T16:02:39.402492Z"}` | ✅ |
| POST-02 | Válido sem descrição | `{"nome":"Limpeza residencial","categoria":"Limpeza","preco":120.50,"duracaoMinutos":180}` | 201 | **201** | `id:2`, `"descricao":null` | ✅ |
| POST-03 | Espaços nas bordas de nome/categoria | `"  Pintura de parede  "`, `"  Reformas  "` | 201 | **201** | retornou `"Pintura de parede"` / `"Reformas"` (strip aplicado) | ✅ |
| POST-04 | Corpo `{}` | `{}` | 400 | **400** | `title:"Dados inválidos"`, 4 erros (nome, preco, categoria, duracaoMinutos) | ✅ |
| POST-05 | Sem corpo (Content-Type JSON) | — | 400 | **400** | `title:"Corpo da requisição inválido"` | ✅ |
| POST-06 | Corpo sem Content-Type | JSON válido, sem header | 415 | **415** | `title:"Tipo de conteúdo não suportado"`, detail "Use application/json" | ✅ |
| POST-07 | `Content-Type: text/plain` | JSON válido | 415 | **415** | idem | ✅ |
| POST-08 | JSON malformado (não fecha) | `{"nome":"Teste",...,"preco":10,` | 400 | **400** | "Corpo da requisição inválido" | ✅ |
| POST-09 | Corpo não-JSON | `isto nao e json` | 400 | **400** | idem | ✅ |
| POST-10 | `preco = 0` | | 400 | **400** | erro em `preco`: "O preço deve ser maior que zero" | ✅ |
| POST-11 | `preco = -5` | | 400 | **400** | idem | ✅ |
| POST-12 | `preco = 10.123` | | 400 | **400** | "O preço deve ter no máximo 2 casas decimais" | ✅ |
| POST-13 | `preco = 100000000` | | 400 | **400** | 2 erros em `preco` (casas decimais + valor máximo) | ✅ |
| POST-14 | `preco = "abc"` (tipo inválido) | | 400 | **400** | "Corpo da requisição inválido" | ✅ |
| POST-15 | `duracaoMinutos = 0` | | 400 | **400** | "A duração estimada deve ser de pelo menos 1 minuto" | ✅ |
| POST-16 | `duracaoMinutos = 100001` | | 400 | **400** | "A duração estimada excede o valor máximo permitido" | ✅ |
| POST-17 | `duracaoMinutos = "muito"` | | 400 | **400** | "Corpo da requisição inválido" | ✅ |
| POST-18 | `duracaoMinutos = 1.5` | | 400 ou 201 (exploratório) | **201** | `"duracaoMinutos":1` — **valor truncado silenciosamente** | ➖ **defeito P2** |
| POST-19 | `nome = ""` | | 400 | **400** | 2 erros em `nome` (tamanho + obrigatório) | ✅ |
| POST-20 | `nome` só com espaços | `"     "` | 400 | **400** | "O nome é obrigatório" | ✅ |
| POST-21 | `nome` com 2 caracteres | `"ab"` | 400 | **400** | "O nome deve ter entre 3 e 100 caracteres" | ✅ |
| POST-22 | `nome` com 101 caracteres | | 400 | **400** | idem | ✅ |
| POST-23 | `categoria = ""` | | 400 | **400** | "A categoria é obrigatória" | ✅ |
| POST-24 | `categoria` com 61 caracteres | | 400 | **400** | "A categoria deve ter no máximo 60 caracteres" | ✅ |
| POST-25 | `descricao` com 501 caracteres | | 400 | **400** | "A descrição deve ter no máximo 500 caracteres" | ✅ |
| POST-26 | Obrigatórios explicitamente `null` | | 400 | **400** | 4 erros de obrigatoriedade | ✅ |
| POST-27 | Limites máximos válidos | nome 100, categoria 60, descrição 500, preco 99999999.99, 100000 min | 201 | **201** | `id:5`, persistido | ✅ |
| POST-28 | Limites mínimos válidos | nome 3, preco 0.01, 1 min | 201 | **201** | `id:6` | ✅ |
| POST-29 | `id` e propriedade desconhecida no corpo | `{"id":9999,"campoExtra":"x",...}` | 201 | **201** | `id:7` (o `id` do corpo foi ignorado; propriedade extra ignorada) | ✅ |
| POST-30 | Nome duplicado | mesmo corpo de POST-02 | 201 | **201** | `id:8` (não há unicidade) | ✅ |
| POST-31 | Nome `"  ab "` (5 caracteres antes do `strip`) | | 400 ou 201 (exploratório) | **201** | `"nome":"ab"` (**2 caracteres persistidos**, contrariando `min=3`) | ➖ **defeito P1** |

**Verificação no banco após os POST:** `SELECT` confirmou 9 linhas (ids 1–9) com os valores enviados (ex.: id 1 `preco=350.00`, `duracao_minutos=120`; id 3 `nome="Pintura de parede"`; id 4 `duracao_minutos=1`; id 9 `nome="ab"`), `criado_em = atualizado_em` em todas.

### 7.2 `GET /api/v1/servicos` e `GET /api/v1/servicos/{id}`

| ID | Cenário | Requisição | Esperado | Obtido | Resposta (resumo) | Situação |
|---|---|---|---|---|---|---|
| GET-01 | Listar todos | `GET /api/v1/servicos` | 200 | **200** | array JSON com os 9 serviços, ordenado por `id`, `application/json` | ✅ |
| GET-02 | Buscar existente | `GET /1` | 200 | **200** | objeto completo do serviço 1 | ✅ |
| GET-03 | Buscar existente (strip) | `GET /3` | 200 | **200** | `"nome":"Pintura de parede"` | ✅ |
| GET-04 | Escala do preço | `GET /4` | 200 | **200** | `"preco":10.00` (o POST respondeu `10`; ver P4) | ✅ |
| GET-05 | ID inexistente | `GET /99999` | 404 | **404** | `application/problem+json`, "Serviço com id 99999 não encontrado" | ✅ |
| GET-06 | ID não numérico | `GET /abc` | 400 | **400** | "Parâmetro inválido" / "O valor informado para o parâmetro 'id' é inválido" | ✅ |
| GET-07 | ID zero | `GET /0` | 404 | **404** | "Serviço com id 0 não encontrado" | ✅ |
| GET-08 | ID negativo | `GET /-1` | 404 | **404** | idem | ✅ |
| GET-09 | ID acima de `Long` | `GET /99999999999999999999` | 400 | **400** | "Parâmetro inválido" | ✅ |
| GET-10 | ID decimal | `GET /1.5` | 400 | **400** | idem | ✅ |
| GET-11 | Paginação/filtro (não implementados) | `?page=0&size=2&categoria=Limpeza` | 200 | **200** | parâmetros ignorados; lista completa | ✅ |
| GET-12 | Barra final | `GET /api/v1/servicos/` | 404 | **404** | "A rota solicitada não existe" | ✅ |
| GET-13 | `Accept: application/xml` | `GET /1` | 406 | **406** | `title:"Not Acceptable"` (**em inglês**; ver P3) | ✅ |
| GET-14 | `HEAD` | `HEAD /api/v1/servicos` | 200 | **200** | cabeçalhos, sem corpo | ✅ |
| GET-15 | `OPTIONS` | `OPTIONS /api/v1/servicos` | 200 | **200** | `Allow: POST,GET,HEAD,OPTIONS` | ✅ |
| GET-16 | `Origin` sem CORS configurado | `GET /1` + `Origin: http://exemplo.com` | 200 | **200** | sem `Access-Control-Allow-Origin` | ✅ |
| GET-17 | *Preflight* CORS | `OPTIONS` + `Access-Control-Request-Method` | 403 | **403** | corpo `Invalid CORS request` | ✅ |

**Consistência:** o número de itens da listagem (7, após as exclusões) coincidiu com `SELECT count(*)` (7).

### 7.3 `PUT /api/v1/servicos/{id}` — Atualizar

| ID | Cenário | Requisição | Esperado | Obtido | Resposta / verificação | Situação |
|---|---|---|---|---|---|---|
| PUT-01 | Atualização completa válida | `PUT /1` `@put-valido.json` (preco 480.00, 150 min) | 200 | **200** | `criadoEm` inalterado (`...16:02:39.402492Z`); `atualizadoEm` renovado (`...16:05:05.871330Z`) | ✅ |
| PUT-02 | Reconsulta após o PUT | `GET /1` | 200 | **200** | dados atualizados persistidos; **SQL** confirmou `nome`, `preco=480.00`, `criado_em` igual ao anterior, `atualizado_em` maior | ✅ |
| PUT-03 | PUT sem `descricao` | corpo sem o campo | 200 | **200** | `"descricao":null`; **SQL** confirmou descrição vazia (semântica de PUT completo) | ✅ |
| PUT-04 | ID inexistente, corpo válido | `PUT /99999` | 404 | **404** | "Serviço com id 99999 não encontrado" | ✅ |
| PUT-05 | ID inexistente, corpo inválido `{}` | `PUT /99999` | 400 ou 404 (exploratório) | **400** | a validação do corpo ocorre **antes** da busca do ID | ➖ ordem de validação registrada |
| PUT-06 | Corpo `{}` em ID existente | `PUT /2` | 400 | **400** | 4 erros de obrigatoriedade | ✅ |
| PUT-07 | `preco = 0` | `PUT /2` | 400 | **400** | "O preço deve ser maior que zero" | ✅ |
| PUT-08 | JSON malformado | `PUT /2` | 400 | **400** | "Corpo da requisição inválido" | ✅ |
| PUT-09 | Sem corpo | `PUT /2` | 400 | **400** | idem | ✅ |
| PUT-10 | `Content-Type: text/plain` | `PUT /2` | 415 | **415** | "Tipo de conteúdo não suportado" | ✅ |
| PUT-11 | Sem Content-Type | `PUT /2` | 415 | **415** | idem | ✅ |
| PUT-12 | ID não numérico | `PUT /abc` | 400 | **400** | "Parâmetro inválido" | ✅ |
| PUT-13 | `PATCH` (não implementado) | `PATCH /2` | 405 | **405** | `Allow: PUT, GET, DELETE`; "O método HTTP PATCH não é suportado para este recurso" | ✅ |
| PUT-14 | `PUT` na coleção | `PUT /api/v1/servicos` | 405 | **405** | `Allow: POST, GET` | ✅ |
| — | Serviço 2 após os PUT inválidos | **SQL** | — | — | registro **inalterado** (`atualizado_em` original) | ✅ |
| PUT-15 | Strip + `id` divergente no corpo, acento **inline** | `PUT /2` | 200 | **400** | "Corpo da requisição inválido" | ❌ **artefato de teste** (ver abaixo) |
| PUT-15b | Repetição de PUT-15 com corpo em **arquivo UTF-8** | `@put-strip-id.json` (`"id":555`, `"  Limpeza pós-obra  "`) | 200 | **200** | `"nome":"Limpeza pós-obra"`, `id:2` (o `id:555` do corpo foi ignorado; **SQL**: `count(*) where id=555` = 0) | ✅ |
| PUT-15c | Controle: mesmo texto com acento inline | `PUT /2` | 400 | **400** | reproduz o erro de PUT-15 | ✅ (controle) |
| PUT-15d | Controle: acento escapado `ó` em arquivo ASCII | `PUT /2` | 200 | **200** | `"nome":"Limpeza pós-obra ASCII"` | ✅ (controle) |
| PUT-16 | Nome `"  ab "` | `PUT /6` | 400 ou 200 (exploratório) | **200** | `"nome":"ab"` persistido | ➖ **defeito P1** |

**Sobre PUT-15:** o mesmo conteúdo é rejeitado quando enviado como argumento inline (PUT-15, PUT-15c) e aceito quando enviado em arquivo UTF-8 (PUT-15b) ou com escape ASCII (PUT-15d). **Conclusão (confirmada por comparação):** a falha é do transporte do `curl.exe` no Windows/Git Bash (que já é alertada no `README.md`), não da API. O PUT-15d foi executado duas vezes: a primeira execução (com o escape convertido pelo shell antes do envio) foi inconclusiva e substituída pela segunda; ambas constam nas evidências.

### 7.4 `DELETE /api/v1/servicos/{id}` — Excluir

| ID | Cenário | Requisição | Esperado | Obtido | Resposta / verificação | Situação |
|---|---|---|---|---|---|---|
| DEL-01 | Excluir existente | `DELETE /9` | 204 | **204** | sem corpo | ✅ |
| DEL-02 | Consultar após exclusão | `GET /9` | 404 | **404** | "Serviço com id 9 não encontrado" | ✅ |
| — | **SQL** após DEL-01 | | | | `count(*) where id=9` = 0; total caiu de 9 para 8 | ✅ |
| DEL-03 | Excluir o mesmo ID de novo | `DELETE /9` | 404 | **404** | (o DELETE não é idempotente quanto ao status) | ✅ |
| DEL-04 | ID inexistente | `DELETE /99999` | 404 | **404** | | ✅ |
| DEL-05 | ID inválido | `DELETE /abc` | 400 | **400** | "Parâmetro inválido" | ✅ |
| DEL-06 | `DELETE` na coleção | `DELETE /api/v1/servicos` | 405 | **405** | `Allow: POST, GET` | ✅ |
| DEL-07 | ID negativo | `DELETE /-1` | 404 | **404** | | ✅ |
| DEL-08 | Excluir `id=8` | `DELETE /8` | 204 | **204** | | ✅ |
| DEL-09 | Listagem após exclusões | `GET` | 200 | **200** | ids 8 e 9 ausentes; **SQL**: ids restantes `1,2,3,4,5,6,7` (7 linhas) | ✅ |

### 7.5 Rotas inexistentes e métodos não suportados

| ID | Requisição | Esperado | Obtido | Resposta | Situação |
|---|---|---|---|---|---|
| ROTA-01 | `GET /api/v1/foo` | 404 | **404** | "Recurso não encontrado" / "A rota solicitada não existe" | ✅ |
| ROTA-02 | `GET /` | 404 | **404** | idem | ✅ |
| ROTA-03 | `GET /api/v1/servicos/1/extra` | 404 | **404** | idem | ✅ |
| ROTA-04 | `GET /api/v2/servicos` | 404 | **404** | idem | ✅ |
| ROTA-05 | `GET /api/servicos` | 404 | **404** | idem | ✅ |
| ROTA-06 | `POST /api/v1/servicos/1` | 405 | **405** | `Allow: PUT, GET, DELETE` | ✅ |
| ROTA-07 | `POST /api/v1/foo` | 404 | **404** | idem | ✅ |

### 7.6 Falha de infraestrutura (erro 500)

| ID | Cenário | Esperado | Obtido | Resposta | Situação |
|---|---|---|---|---|---|
| ERR-01 | `GET /api/v1/servicos` com o PostgreSQL **parado** (`pg_ctl stop`) | 500 | **500** (após **~30 s**) | `{"title":"Erro interno do servidor","status":500,"detail":"Ocorreu um erro interno. Tente novamente mais tarde."}` — sem vazamento de detalhes internos | ✅ |
| ERR-02 | `POST` válido com o banco parado | 500 | **500** (~30 s) | idem | ✅ |
| ERR-03 | `GET` depois de religar o PostgreSQL | 200 | **200** | pool Hikari se recuperou sem reiniciar a aplicação | ✅ |

Log da aplicação: `GlobalExceptionHandler : Erro inesperado ao processar a requisição` com `CannotCreateTransactionException` / `JDBCConnectionException: Unable to acquire JDBC Connection [HikariPool-1 - Connection is not available, request timed out after 30000ms]` (o *timeout* de 30 s é o padrão do Hikari; a aplicação não o altera).

---

## 8. Testes negativos e validações — síntese

| Categoria pedida | Cenário | Resultado real |
|---|---|---|
| Requisição sem corpo | POST-05, PUT-09 | `400` "Corpo da requisição inválido" |
| JSON inválido | POST-08/09, PUT-08 | `400` idem |
| Campos obrigatórios ausentes / `null` | POST-04, POST-26, PUT-06 | `400` "Dados inválidos" + `erros[]` com `campo`/`mensagem` |
| Valores vazios / só espaços | POST-19/20/23 | `400` |
| Tipos inválidos | POST-14, POST-17 | `400` "Corpo da requisição inválido" (mensagem genérica, sem indicar o campo) |
| Valores fora dos limites | POST-10–13, 15, 16, 21, 22, 24, 25 | `400` com a mensagem específica do campo |
| IDs inexistentes | GET-05/07/08, PUT-04, DEL-03/04/07 | `404` "Serviço com id N não encontrado" |
| IDs em formato inválido | GET-06/09/10, PUT-12, DEL-05 | `400` "Parâmetro inválido" |
| Rotas inexistentes | ROTA-01–05, 07 | `404` "A rota solicitada não existe" |
| Métodos não suportados | PUT-13, PUT-14, DEL-06, ROTA-06 | `405` com cabeçalho `Allow` |
| Content-Type incorreto/ausente | POST-06/07, PUT-10/11 | `415` |
| `Accept` incompatível | GET-13 | `406` (título em inglês — P3) |
| Dados duplicados | POST-30 | **Não aplicável:** não há regra de unicidade; aceito (`201`) |
| Erro inesperado | ERR-01/02 | `500` genérico |

Todas as respostas de erro do CRUD têm `Content-Type: application/problem+json` e o campo `instance` com o caminho requisitado. Exceção: o *preflight* CORS bloqueado (`403`, texto puro `Invalid CORS request`), gerado pelo Spring antes do controller.

Cenário **não** testado por não ser aplicável: violação de unicidade (não existe); autenticação/autorização (não existe).

---

## 9. Banco de dados e persistência

- **Banco:** PostgreSQL 16.4 (servidor portátil local, porta 5432). Banco de teste **novo**: `servicehub_relatorio`. A aplicação se conectou com as credenciais das variáveis de ambiente (usuário de teste com privilégios de administração local — em uso normal o padrão do projeto é o usuário `servicehub`).
- **Conexão:** `spring.datasource.*` → HikariCP (`HikariPool-1`, `Start completed`). Durante os testes, o `pg_stat_activity` mostrou 10 conexões ociosas da aplicação (o pool usa o tamanho padrão do Hikari, que não é alterado no projeto); a única conexão "ativa" listada era a própria consulta de verificação.
- **Migrations:** `flyway_schema_history` contém 1 registro: versão `1`, `criar tabela servicos`, `V1__criar_tabela_servicos.sql`, `success = t`. Log: `Successfully applied 1 migration ... now at version v1 (execution time 00:00.032s)`.
- **Tabelas existentes:** `servicos` e `flyway_schema_history`.
- **Estrutura de `servicos` (consultada em `information_schema`):** exatamente as colunas/tipos da seção 5.2 (`nome varchar(100)`, `descricao varchar(500)`, `categoria varchar(60)`, `preco numeric(10,2)`, timestamps `timestamptz`, `id bigint`).
- **Constraints (consultadas em `pg_constraint`):** `servicos_pkey` (PK em `id`), `ck_servicos_preco` (`preco > 0`), `ck_servicos_duracao` (`duracao_minutos > 0`). Não há chaves estrangeiras.
- **Integridade aplicada pelo próprio banco** (INSERTs diretos por SQL): `preco = 0` → `violates check constraint "ck_servicos_preco"`; `duracao_minutos = 0` → `violates check constraint "ck_servicos_duracao"`; `nome = null` → `violates not-null constraint`. (Esses três INSERTs falhos consumiram os IDs 10–12 da sequência; isso é comportamento normal de sequências e só afeta este banco de teste.)
- **Estratégia de persistência:** JPA/Hibernate mapeia `Servico` → `servicos`; IDs por *identity*; `@CreationTimestamp`/`@UpdateTimestamp` (`Instant` → `TIMESTAMPTZ`); Hibernate em modo `validate`, portanto qualquer divergência entre entidade e migration impediria a inicialização.
- **Resultados da verificação dos dados:** POST → linha criada (SQL confirmou); GET → consistente com o SQL; PUT → colunas alteradas, `criado_em` preservado, `atualizado_em` maior; DELETE → linha removida e total decrescido; PUTs/POSTs inválidos → nenhuma linha criada/alterada. Estado final: 7 linhas (ids 1–7).

---

## 10. Documentação Swagger/OpenAPI

- **Dependência:** `springdoc-openapi-starter-webmvc-ui` 2.8.17. **Configuração:** `springdoc.swagger-ui.path`, `springdoc.api-docs.path` e a classe `OpenApiConfig` (bean `OpenAPI` com `Info`; bean `OpenApiCustomizer` que ordena os paths).

### 10.1 URLs testadas (todas acessadas de fato)
| URL | Resultado |
|---|---|
| `http://localhost:8080/swagger-ui.html` | `302` → `/swagger-ui/index.html` |
| `http://localhost:8080/swagger-ui/index.html` | `200`, HTML "Swagger UI" |
| `http://localhost:8080/v3/api-docs` | `200`, JSON OpenAPI 3.1.0 (salvo em `docs/evidencias/openapi-v3-api-docs.json`) |
| `http://localhost:8080/v3/api-docs.yaml`, `/v3/api-docs/swagger-config` | `200` |
| `/swagger-ui/swagger-ui.css`, `swagger-ui-bundle.js`, `swagger-initializer.js` | `200` |

### 10.2 Swagger UI renderizado
Como a extensão do Chrome não conectou, usei o **Microsoft Edge em modo headless** (`--dump-dom`), que executa o JavaScript do Swagger UI. No DOM renderizado: versão `v1`, tag "Serviços" e **5 blocos de operação** — `GET /api/v1/servicos`, `POST /api/v1/servicos`, `GET /api/v1/servicos/{id}`, `PUT /api/v1/servicos/{id}`, `DELETE /api/v1/servicos/{id}` — com os summaries "Listar todos os serviços", "Cadastrar um novo serviço", "Buscar um serviço por ID", "Atualizar um serviço" e "Excluir um serviço". O `swagger-config` aponta para `url: /v3/api-docs`.

**Limitação:** **não** executei *Try it out* → *Execute* pela interface (exige interação de navegador). Consequência: a afirmação do `README.md` de que a execução pelo Swagger UI foi feita (imagens em `docs/img/`) **não foi reproduzida** nesta análise. Como o Swagger UI chama a mesma origem (`localhost:8080`), as respostas seriam as mesmas dos testes de `curl` da seção 7, mas isso é inferência, não teste.

### 10.3 O que o `/v3/api-docs` contém (conferido no JSON)
| Item | Resultado |
|---|---|
| Endpoints e métodos | 5 operações nos 2 paths, métodos corretos |
| `summary` | presente nas 5 |
| `description` | presente nas 5 |
| Parâmetro `id` | `path`, `required: true`, `integer/int64`, com descrição e exemplo `1` (nas 3 operações com `{id}`) |
| Corpo de requisição | POST e PUT: `required: true`, `application/json` → `ServicoRequest` |
| Respostas documentadas | POST: 201, 400 · GET lista: 200 · GET id: 200, 400, 404 · PUT: 200, 400, 404 · DELETE: 204, 400, 404 |
| Schemas | `ServicoRequest` (com `required`, `minLength/maxLength`, `minimum/maximum`, exemplos), `ServicoResponse` (com exemplos), `ProblemDetail` |
| Exemplos | `ExampleObject` em POST/400 ("Dados inválidos") e GET id/404 ("Serviço inexistente"); exemplos por campo nos schemas |
| `servers` | `http://localhost:8080` ("Generated server url") |

### 10.4 Anotações utilizadas e sua contribuição
| Anotação | Onde | Efeito na documentação |
|---|---|---|
| `@Tag` | `ServicoController` | Agrupa os endpoints sob "Serviços" com descrição |
| `@Operation` (`summary`, `description`) | cada método do controller | Título e descrição de cada operação |
| `@ApiResponse` (repetida diretamente; `@ApiResponses` **não** é usada) | cada método | Códigos de status, descrição, `content`, `schema` e exemplos |
| `@Parameter` | `@PathVariable id` | Descrição e exemplo do parâmetro de rota |
| `@Schema` | records `ServicoRequest`/`ServicoResponse` (classe e campos) e dentro de `@Content` | Descrição, exemplos, limites e obrigatoriedade (`requiredMode`) de cada campo |
| `@Content`, `@ArraySchema`, `@ExampleObject` | `@ApiResponse` | Tipo de mídia, array de `ServicoResponse` e exemplos de resposta |

### 10.5 Divergências entre a documentação e o comportamento real
| # | Divergência (confirmada comparando `/v3/api-docs` com respostas reais) |
|---|---|
| D1 | O schema `ProblemDetail` expõe uma propriedade `properties` (objeto). O JSON real coloca as propriedades de extensão **no nível raiz** (ex.: `"erros":[...]`). O campo `erros` só aparece no *exemplo*, não no schema. |
| D2 | Respostas **415, 405, 406 e 500** ocorrem de fato mas **não** estão documentadas. |
| D3 | `PUT` e `DELETE`/`GET id` têm respostas 400/404 sem exemplos (só POST/400 e GET id/404 têm). |
| D4 | O cabeçalho `Location` do 201 é mencionado na descrição, mas não declarado em `headers` da resposta. |
| D5 | `ServicoRequest.nome` documenta `minLength: 3`, mas a API aceita `"  ab "` (P1) — a documentação descreve o contrato pretendido, que o código não garante integralmente. |

Nenhuma dessas divergências foi corrigida (são melhorias de documentação, não erros de execução, e alterá-las exigiria mudanças de modelo/anotação fora do escopo desta análise).

---

## 11. Testes automatizados

- **Existem:** `ServicoControllerTest` (integração, `@SpringBootTest` + `@AutoConfigureMockMvc` + perfil `test`/H2) e `ServicoServiceTest` (unitário, Mockito).
- **Comando executado:** `.\mvnw.cmd -B clean test` (log em arquivo temporário da sessão, não versionado; duração ≈ 59 s, incluindo download/compilação).
- **Resultado:** `Tests run: 27, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS`.

| Classe / grupo | Testes | Falhas |
|---|---|---|
| `ServicoControllerTest` › POST | 8 | 0 |
| › GET (lista + id) | 5 | 0 |
| › PUT | 3 | 0 |
| › DELETE | 3 | 0 |
| › Erros genéricos do Spring MVC (404 de rota, 405, 415) | 3 | 0 |
| › Documentação OpenAPI | 1 | 0 |
| `ServicoServiceTest` | 4 | 0 |
| **Total** | **27** | **0** |

(Soma por relatório do Surefire: `ServicoControllerTest` = 23, `ServicoServiceTest` = 4.) O total de 27 **coincide** com o informado no `README.md`.

- **Relatórios gerados:** `target/surefire-reports/` (`TEST-*.xml` e `*.txt` das duas classes).
- **Avisos no console (não são falhas):** Mockito informando *self-attaching* do agente inline-mock-maker ("will no longer work in future releases of the JDK") e o aviso do JVM sobre carregamento dinâmico de agente.
- **Limitações da cobertura:**
  - todos os testes de controller rodam em **H2** (modo PostgreSQL), não em PostgreSQL — o comportamento no PostgreSQL só foi verificado manualmente (seção 7 e 9);
  - **nenhum** teste cobre P1 (nome de 2 caracteres com espaços nas bordas) nem P2 (duração decimal), pelo que os defeitos não foram detectados pelos testes existentes;
  - sem testes de mensagem para `406`; sem teste de inicialização sem `DB_PASSWORD`; não foi medida cobertura de código (não há JaCoCo no `pom.xml`).

---

## 12. Problemas encontrados e correções

> **Atualização (21/09/2026):** **P1 e P2 foram corrigidos** (com testes) — ver seção 17.14; a D5 deixou de se aplicar. P3, P4, P6, P7 e as divergências D1–D4 seguem como descritas abaixo; a P8 (limitações do ambiente) foi em parte superada, pois o Docker foi instalado e a extensão do Chrome passou a funcionar.

**Nenhuma correção foi aplicada.** O código-fonte, os testes e as configurações do projeto permanecem exatamente como foram encontrados (nenhum arquivo existente foi editado; foram adicionados apenas este relatório e a pasta `docs/evidencias/`). Abaixo, cada problema com o status da causa.

### P1 — `@Size(min=3)` do `nome` é contornado por espaços nas bordas — **defeito confirmado**
- **Onde:** `ServicoRequest` (`@Size` em `nome`; `strip()` em `paraEntidade()` e `aplicarEm()`).
- **Evidência:** POST-31 e PUT-16 (`"  ab "` → `201`/`200`, retornado e persistido como `"ab"`; SQL: ids 6 e 9 com `nome = 'ab'`), enquanto `"ab"` puro é rejeitado (POST-21).
- **Causa (confirmada pela leitura do código + execução):** a validação Bean Validation roda sobre o texto **bruto**; o `strip()` é aplicado depois, na conversão para entidade.
- **Correção realizada:** **nenhuma, de propósito.** A correção óbvia (aplicar `strip()` no construtor do *record* antes da validação) mudaria o formato das respostas de erro (um nome só com espaços passaria a gerar dois erros em `nome`, `@NotBlank` + `@Size`) e quebraria a expectativa do teste existente `rejeitaNomeEmBrancoEDuracaoInvalida` (`containsInAnyOrder("nome","duracaoMinutos")`). Isso é decisão de design do dono do projeto, e as regras desta tarefa vetam alterar comportamento da API sem necessidade clara.
- **Recomendação:** fazer o `strip()` antes da validação (ex.: no construtor compacto do record) **e** ajustar o teste citado; adicionar teste para `"  ab "`.

### P2 — `duracaoMinutos` decimal é truncado silenciosamente — **comportamento confirmado; causa provável**
- **Evidência:** POST-18 (`1.5` → `201`, `"duracaoMinutos":1`; SQL id 4: `1`).
- **Causa (provável, não confirmada em código):** padrão do Jackson de aceitar float para tipos inteiros. Confirmei apenas que o projeto **não** define nenhuma propriedade `spring.jackson.*`.
- **Correção realizada:** nenhuma (é mudança de contrato de entrada).
- **Recomendação:** avaliar `spring.jackson.deserialization.accept-float-as-int=false` (passaria a retornar `400`), com teste.

### P3 — `406 Not Acceptable` com título em inglês — **confirmado**
- **Evidência:** GET-13 → `"title":"Not Acceptable"`, `"detail":"Acceptable representations: [application/json]."`.
- **Causa (confirmada por leitura de `messages.properties`):** não há entrada para `HttpMediaTypeNotAcceptableException`; os demais erros do Spring MVC têm.
- **Correção realizada:** nenhuma (cosmético). **Recomendação:** adicionar `problemDetail.title.org.springframework.web.HttpMediaTypeNotAcceptableException` e `problemDetail.org...`.

### P4 — Escala de `preco` difere entre a resposta do POST/PUT e a do GET — **confirmado; causa provável**
- **Evidência:** POST-29 e PUT-15b responderam `"preco":10` / `200`; o GET seguinte respondeu `10.00` / `200.00` (GET-04, DEL-09).
- **Causa (provável):** o `ServicoResponse` do POST/PUT é montado a partir da entidade em memória, com o `BigDecimal` recebido, sem recarregar do banco (`numeric(10,2)`).
- **Correção realizada:** nenhuma. Valor numericamente igual; impacto apenas de formatação. **Recomendação:** normalizar com `setScale(2)` ou recarregar.

### P5 — Divergências do OpenAPI (D1–D5, seção 10.5) — **confirmadas**; sem correção (melhorias de documentação).

### P6 — Sem `DB_PASSWORD`, o erro é de autenticação, não uma mensagem explícita — **confirmado** (seção 4.4). Correção: nenhuma. **Recomendação:** documentar o sintoma no README ou validar a variável na inicialização.

### P7 — Falha de banco leva ~30 s para responder 500 — **confirmado** (ERR-01/02). Causa: *timeout* padrão do Hikari (`request timed out after 30000ms`). Correção: nenhuma. **Recomendação:** avaliar `spring.datasource.hikari.connection-timeout` menor.

### P8 — Limitações do ambiente / do processo de teste (não são defeitos do projeto)
- **PUT-15 (❌):** falha de transporte do `curl.exe` com acentos inline — causa confirmada (PUT-15b/15c/15d). Corrigido no *teste*, não na API.
- **Docker ausente:** `docker-compose.yml` foi apenas **lido**, não executado. O `README.md` declara o mesmo.
- **Extensão do Chrome indisponível:** sem *Try it out* interativo (seção 10.2).
- **`mvnw clean`:** o `clean` removeu o conteúdo anterior de `target/` (ignorado pelo Git); o JAR foi regenerado com `package`.
- **Git:** apenas o commit inicial existe; `pom.xml`, `src/`, `docker-compose.yml`, `docs/`, `.gitignore` etc. aparecem como **não rastreados** e o `README.md` como **modificado** e não commitado. Risco de perda de trabalho, não de funcionamento.
- **Aviso operacional:** endpoints `/v3/api-docs` e Swagger UI habilitados por padrão (aviso do springdoc no log).

---

## 13. Resultado geral da validação

> **Atualização (21/09/2026):** o quadro abaixo é o de 20/09. O quadro atual está na seção 17.12 (Docker) e o estado dos defeitos P1/P2, na 17.14.

| Pergunta | Resposta (com base em evidência desta sessão) |
|---|---|
| A aplicação iniciou? | **Sim** — 5,874 s, Tomcat na 8080, Flyway v1 aplicado, sem erros de inicialização |
| A compilação foi concluída? | **Sim** — `mvnw clean test` e `mvnw package` com `BUILD SUCCESS` |
| O CRUD foi testado? | **Sim** — POST, GET (lista e id), PUT e DELETE, com verificação por SQL |
| Todas as rotas foram testadas? | **Sim para as 5 rotas da API** e para as 5 URLs de documentação (sondadas). Não coberto: *Try it out* interativo |
| O banco de dados foi validado? | **Sim** — conexão, migration, esquema, constraints, persistência de C/R/U/D e integridade por SQL direto |
| O Swagger foi acessado? | **Sim** — `/swagger-ui.html` (302→index), `/swagger-ui/index.html` (200), `/v3/api-docs` (200); UI **renderizada** em Edge headless |
| A documentação foi verificada? | **Sim**, com 5 divergências registradas (D1–D5) |
| Testes automatizados executados? | **Sim** — 27 executados, 27 aprovados, 0 falhas/erros/ignorados |
| Existem pendências? | **Sim** — defeitos P1 e P2 não corrigidos; P3–P7 recomendações; `docker compose` e Swagger *Try it out* não validados; código não versionado no Git |

Placar dos 86 testes HTTP: **85 `PASS`** (dos quais 4 são observações exploratórias — POST-18, POST-31, PUT-05, PUT-16) e **1 `FAIL`** (PUT-15, artefato de teste, repetido com sucesso em PUT-15b). Este resultado **não** deve ser lido como "100% aprovado": há defeitos reais de validação (P1, P2) que os testes exploratórios expuseram.

---

## 14. Como executar o projeto

> **Atualização (21/09/2026):** a "Opção A — Docker Compose" abaixo **foi executada e validada** (seção 17.3), e há instruções atualizadas, com `DB_PASSWORD` na linha de comando, no `README.md`. O Maven Wrapper também foi executado no Windows (17.13).

### Pré-requisitos
JDK 21; PostgreSQL 16 (local **ou** Docker); Maven não precisa ser instalado (Maven Wrapper incluso).

### Banco de dados
- **Opção A — Docker Compose** (`docker-compose.yml`; **não executado nesta análise**):
  ```bash
  cp .env.example .env      # edite e defina DB_PASSWORD
  docker compose up -d
  ```
- **Opção B — PostgreSQL já instalado:**
  ```sql
  CREATE DATABASE servicehub;
  ```
  (Nesta análise usei um servidor PostgreSQL 16.4 portátil e um banco `servicehub_relatorio`, apontado por `DB_URL`.)

### Variáveis de ambiente
| Variável | Padrão | Obrigatória |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/servicehub` | não |
| `DB_USER` | `servicehub` | não |
| `DB_PASSWORD` | — | **sim** |

PowerShell:
```powershell
$env:DB_USER = "servicehub"; $env:DB_PASSWORD = "<sua-senha>"
```
Bash: `export DB_USER=servicehub DB_PASSWORD=<sua-senha>`

### Migrations
Automáticas: o Flyway executa `V1__criar_tabela_servicos.sql` na inicialização (confirmado).

### Inicialização
- Documentado no README (não executado por mim): `./mvnw spring-boot:run` (Windows: `.\mvnw.cmd spring-boot:run`).
- **Executado por mim:**
  ```powershell
  .\mvnw.cmd -B -DskipTests package
  java -jar target\servicehub-api-0.1.0-SNAPSHOT.jar
  ```

### Acesso
- API: `http://localhost:8080/api/v1/servicos`
- Swagger UI: `http://localhost:8080/swagger-ui.html` · OpenAPI: `http://localhost:8080/v3/api-docs`

Exemplo (Windows: envie corpos com acentos em arquivo UTF-8 com `--data-binary @arquivo.json`):
```bash
curl -i -X POST http://localhost:8080/api/v1/servicos -H "Content-Type: application/json" \
  -d '{"nome":"Limpeza residencial","categoria":"Limpeza","preco":120.50,"duracaoMinutos":180}'
```

### Testes
```bash
./mvnw test        # Windows: .\mvnw.cmd test  (não requer PostgreSQL nem Docker: usa H2)
```

---

## 15. Conclusão

> **Atualização (21/09/2026):** a conclusão abaixo é a de 20/09. Desde então: `docker-compose.yml` validado, **P1 e P2 corrigidos**, **41 testes** aprovados. Continuam válidas as demais limitações e recomendações (P3, P4, P6, P7, D1–D4, testes com PostgreSQL real).

O ServiceHub API está **funcional** no que se propõe: o CRUD do recurso `Serviços` compilou, iniciou e respondeu corretamente em PostgreSQL 16.4 real, com persistência confirmada por SQL, migration Flyway aplicada, restrições de banco eficazes, erros padronizados em Problem Details e Swagger/OpenAPI acessíveis e coerentes com a maior parte do comportamento. Os 27 testes automatizados passam.

**Limitações e pontos de atenção:** (1) defeito de validação **P1** — nomes de 2 caracteres com espaços nas bordas são aceitos, contrariando o contrato documentado; (2) **P2** — duração decimal truncada silenciosamente; (3) documentação OpenAPI com o schema `ProblemDetail` inexato e respostas de erro comuns não documentadas; (4) testes automatizados apenas em H2 e sem cobertura dos cenários P1/P2; (5) sem paginação, filtros ou autenticação (limitações já declaradas no README); (6) `docker-compose.yml` e o *Try it out* do Swagger não foram validados neste ambiente; (7) código ainda não versionado no Git.

**Melhorias recomendadas (por prioridade):** corrigir P1 (e ajustar/adicionar testes); decidir sobre P2; completar o OpenAPI (schema de `ProblemDetail`, respostas 415/406/500, exemplos de PUT); localizar o `406`; adicionar teste de integração com PostgreSQL (ex.: Testcontainers); definir `connection-timeout` do pool; desabilitar/proteger Swagger em produção; versionar o projeto.

---

## 16. Anexos e evidências

### Arquivos de evidência (criados por esta análise em `docs/evidencias/`)
| Arquivo | Conteúdo |
|---|---|
| `docs/evidencias/evidencias-testes-http.txt` | Requisição, status, tempo e corpo de cada teste HTTP (87 blocos) |
| `docs/evidencias/resultados-testes-http.tsv` | Tabela resumida: ID, requisição, esperado, obtido, PASS/FAIL |
| `docs/evidencias/openapi-v3-api-docs.json` | Resposta real de `GET /v3/api-docs` |
| `target/surefire-reports/` | Relatórios do Surefire (27 testes) |

Nenhum desses arquivos contém credenciais (verificado por busca da senha em cada um).

### Comandos utilizados (resumo)
```text
.\mvnw.cmd -B clean test                      → 27 testes, BUILD SUCCESS
.\mvnw.cmd -B -DskipTests package             → target\servicehub-api-0.1.0-SNAPSHOT.jar
pg_ctl start -D <pgdata>                      → PostgreSQL 16.4 (porta 5432)
psql ... "create database servicehub_relatorio"
java -jar target\servicehub-api-0.1.0-SNAPSHOT.jar   (DB_URL/DB_USER/DB_PASSWORD no ambiente)
curl -s -D <hdr> -o <body> -w '%{http_code} %{time_total}' -X <M> ...     (86 testes)
psql ... (consultas: contagens, colunas, constraints, flyway_schema_history, INSERTs inválidos)
msedge --headless=new --dump-dom http://localhost:8080/swagger-ui.html
pg_ctl stop  /  pg_ctl start                  → teste de falha (500) e recuperação
java -jar ... --server.port=8081   (sem DB_PASSWORD)  → exit=1
```

### Exemplos reais
**POST-01 → `201 Created`** (`Location: http://localhost:8080/api/v1/servicos/1`)
```json
{"id":1,"nome":"Instalação de ar-condicionado","descricao":"Instalação de split de até 12.000 BTUs, com suporte e tubulação de até 3 metros","categoria":"Climatização","preco":350.00,"duracaoMinutos":120,"criadoEm":"2026-09-20T16:02:39.402492Z","atualizadoEm":"2026-09-20T16:02:39.402492Z"}
```
**POST-04 (`{}`) → `400 application/problem+json`**
```json
{"type":"about:blank","title":"Dados inválidos","status":400,"detail":"Um ou mais campos são inválidos","instance":"/api/v1/servicos","erros":[{"campo":"nome","mensagem":"O nome é obrigatório"},{"campo":"preco","mensagem":"O preço é obrigatório"},{"campo":"categoria","mensagem":"A categoria é obrigatória"},{"campo":"duracaoMinutos","mensagem":"A duração estimada é obrigatória"}]}
```
**GET-05 → `404`**
```json
{"type":"about:blank","title":"Recurso não encontrado","status":404,"detail":"Serviço com id 99999 não encontrado","instance":"/api/v1/servicos/99999"}
```
**PUT-01 → `200`** (`criadoEm` preservado, `atualizadoEm` renovado)
```json
{"id":1,"nome":"Instalação de split 18.000 BTUs","descricao":"Instalação completa com tubulação de até 5 metros","categoria":"Climatização","preco":480.00,"duracaoMinutos":150,"criadoEm":"2026-09-20T16:02:39.402492Z","atualizadoEm":"2026-09-20T16:05:05.871330Z"}
```
**DEL-01 → `204 No Content`** (sem corpo)

### Logs resumidos da inicialização
```text
No active profile set, falling back to 1 default profile: "default"
HikariPool-1 - Start completed.
Database: jdbc:postgresql://localhost:5432/servicehub_relatorio (PostgreSQL 16.4)
Migrating schema "public" to version "1 - criar tabela servicos"
Successfully applied 1 migration to schema "public", now at version v1 (execution time 00:00.032s)
Tomcat started on port 8080 (http) with context path '/'
Started ServiceHubApiApplication in 5.874 seconds
WARN SpringDoc /v3/api-docs endpoint is enabled by default. ...
```
Durante toda a execução normal, os únicos `WARN/ERROR` no log da aplicação foram: os avisos do springdoc, os `PageNotFound ... Request method 'X' is not supported` dos testes de `405`, e os erros **induzidos** (banco parado) do teste ERR-01/02.

### Arquivos relevantes
`pom.xml`; `src/main/resources/application.properties`, `messages.properties`, `db/migration/V1__criar_tabela_servicos.sql`; `src/main/java/com/servicehub/api/{ServiceHubApiApplication, config/OpenApiConfig, controller/ServicoController, dto/ServicoRequest, dto/ServicoResponse, entity/Servico, exception/GlobalExceptionHandler, exception/RecursoNaoEncontradoException, repository/ServicoRepository, service/ServicoService}.java`; `src/test/java/com/servicehub/api/{controller/ServicoControllerTest, service/ServicoServiceTest}.java`; `src/test/resources/application-test.properties`; `docker-compose.yml`; `.env.example`.

---

## 17. Validação com Docker (21/09/2026)

> Esta seção acrescenta uma segunda rodada de validação, feita em **21/09/2026**, agora com PostgreSQL e aplicação rodando em **contêineres Docker**. Tudo o que está aqui foi observado nesta execução; o que não foi executado está dito explicitamente na seção 17.10. A senha do banco (informada pelo usuário) foi passada **somente na linha de comando** e **não** é reproduzida aqui nem em nenhum arquivo do projeto (verificado por busca; nenhum arquivo `.env` foi criado).

### 17.1 Resumo

| Item | Resultado |
|---|---|
| PostgreSQL via `docker compose` | **Subiu e ficou `healthy`** (PostgreSQL 16.15) |
| Aplicação Spring Boot em contêiner | **Subiu em 9,18 s**; conectou ao PostgreSQL do contêiner; Flyway aplicou a `V1`; Hibernate validou o esquema |
| Testes automatizados (`./mvnw clean test` em contêiner) | **27 executados, 0 falhas, 0 erros, 0 ignorados** — `BUILD SUCCESS` |
| CRUD por HTTP (26 execuções) | **24 `PASS` e 2 `FAIL`**. Os 2 `FAIL` são os defeitos **P1** e **P2** já registrados na seção 12, **reconfirmados** (não são novos) |
| Banco de dados | Persistência de POST, PUT e DELETE confirmada por SQL direto no contêiner |
| Swagger | `/swagger-ui.html`, `/swagger-ui/index.html` e `/v3/api-docs` acessíveis; UI renderizada no Chrome; *Try it out* executado **só para `GET /api/v1/servicos`** |
| Código-fonte alterado | **Nenhum** |

### 17.2 Ambiente e versões

| Item | Valor (fonte) |
|---|---|
| Docker | **29.8.0** (build `88096ef`) — `docker --version` |
| Docker Compose | **v5.5.1** — `docker compose version` |
| WSL 2 / virtualização | Informados como funcionando pelo usuário no início da sessão; nesta sessão foi confirmado apenas que `docker ps`/`docker run` funcionam. Não reverifiquei a BIOS |
| Sistema | Windows 11 Pro (10.0.26200) |
| Imagem do banco | `postgres:16` → servidor **PostgreSQL 16.15 (Debian 16.15-1.pgdg13+2)** — `select version();` |
| Imagem de build/execução | `maven:3.9-eclipse-temurin-21` → **Java 21.0.12** (log da aplicação); Maven `3.9.11` obtido pelo wrapper (`.mvn/wrapper/maven-wrapper.properties`) |
| Spring Boot / Hibernate / Tomcat | 3.5.16 / 6.6.53.Final / 10.1.55 (logs de inicialização) |

**Por que o Maven e a aplicação rodaram em contêineres:** no início da sessão o `java` **não estava no PATH** do Windows (nenhum `java.exe` foi encontrado; o JDK portátil usado em 20/09 não existia mais). Em vez de instalar software no sistema, foi usada a imagem `maven:3.9-eclipse-temurin-21`, com o projeto montado como volume e um volume nomeado `servicehub-m2` para o cache do Maven. O usuário instalou depois o JDK 21 no Windows (`C:\Program Files\Java\jdk-21.0.12`) e configurou `JAVA_HOME`/Path; essa instalação foi verificada e usada **só ao final** para rodar `.\mvnw.cmd test` (seção 17.13). Todas as demais execuções desta seção (build, aplicação e testes HTTP) usaram os contêineres. Consequência: o comando `./mvnw` do projeto foi executado **dentro do contêiner Linux**, não no shell do Windows (o `mvnw` tem finais de linha LF, portanto funciona no contêiner).

### 17.3 Inicialização do PostgreSQL (`docker-compose.yml`)

Comando (senha omitida aqui; foi passada na própria linha de comando, sem `.env`):

```bash
DB_PASSWORD=<omitida> docker compose up -d
```

**Por que assim:** o `docker-compose.yml` declara `POSTGRES_PASSWORD: ${DB_PASSWORD:?Defina DB_PASSWORD no arquivo .env}`, ou seja, o Compose **recusa** subir sem `DB_PASSWORD`. `DB_NAME` e `DB_USER` têm padrão `servicehub`. Como o volume era novo, o banco foi inicializado com essa senha.

Resultado observado:
- Imagem `postgres:16` baixada; criados a rede `servicehub-api_default`, o volume `servicehub-api_servicehub-pgdata` (o Compose acrescenta o prefixo do projeto ao nome `servicehub-pgdata` do arquivo) e o contêiner `servicehub-postgres`.
- `docker compose ps`: `servicehub-postgres  postgres:16  Up 6 seconds (healthy)  0.0.0.0:5432->5432/tcp`.
- **Healthcheck** (`pg_isready -U servicehub -d servicehub`, intervalo 5 s): `Status: healthy`, `FailingStreak: 0`, saída `/var/run/postgresql:5432 - accepting connections`. Já estava `healthy` na primeira verificação (tentativa 1 de 20).
- Log do PostgreSQL: `database system is ready to accept connections`.
- Antes da aplicação, o banco estava vazio: `\dt` → `Did not find any relations.`

**Problema observado:** a primeira tentativa de `docker compose ps` (executada depois do `up`, sem a variável) **falhou** com `error while interpolating services.postgres.environment.POSTGRES_PASSWORD: required variable DB_PASSWORD is missing a value: Defina DB_PASSWORD no arquivo .env`. Ou seja, **todo** comando `docker compose` (inclusive `ps` e `down`) exige `DB_PASSWORD` no ambiente. Contorno: repetir o comando com a variável. Nenhuma correção foi aplicada (ver 17.11).

### 17.4 Configuração da conexão da aplicação

A aplicação foi iniciada em um contêiner na **mesma rede** do Compose (`servicehub-api_default`):

```bash
docker run -d --name servicehub-app --network servicehub-api_default -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://servicehub-postgres:5432/servicehub \
  -e DB_USER=servicehub -e DB_PASSWORD=<omitida> \
  -v "<projeto>/target:/app" -w /app maven:3.9-eclipse-temurin-21 \
  java -jar servicehub-api-0.1.0-SNAPSHOT.jar
```

| Variável | Valor | Por que foi necessário |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://servicehub-postgres:5432/servicehub` | O padrão do `application.properties` é `localhost:5432`, que dentro do contêiner da aplicação apontaria para **ela mesma**. Na rede do Compose, o banco é alcançável pelo nome do contêiner |
| `DB_USER` | `servicehub` | Mesmo usuário criado pelo Compose (`POSTGRES_USER`) |
| `DB_PASSWORD` | *(omitida)* | Mesma senha do `POSTGRES_PASSWORD`; `spring.datasource.password=${DB_PASSWORD}` não tem valor padrão |

Nota operacional: como a senha foi passada por `-e`, ela fica visível no ambiente do contêiner (`docker inspect`). É um contêiner local e descartável; em ambiente real, usar *secrets*.

### 17.5 Build e testes automatizados (em contêiner)

```bash
docker run --rm -v "<projeto>:/workspace" -v servicehub-m2:/root/.m2 -w /workspace \
  maven:3.9-eclipse-temurin-21 ./mvnw -B clean test        # e, depois: ./mvnw -B -DskipTests package
```

| Comando | Resultado |
|---|---|
| `./mvnw -B clean test` | `Tests run: 27, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS` — 01:06 min (inclui o download inicial das dependências) |
| `./mvnw -B -DskipTests package` | `BUILD SUCCESS` — 11,181 s — gerou `target/servicehub-api-0.1.0-SNAPSHOT.jar` (57.858.627 bytes) |

Por classe (Surefire): `ServicoServiceTest` = 4; `ServicoControllerTest` = 23, distribuídos nos grupos aninhados POST (8), GET (5), PUT (3), DELETE (3), Erros genéricos do Spring MVC (3) e Documentação OpenAPI (1) — o mesmo total do relatório de 20/09. O relatório do Surefire mostra `Tests run: 0` para a classe externa `ServicoControllerTest` porque os testes estão nas classes `@Nested`.

**Compilação:** confirmada (`compile`, `testCompile` e `package` com `BUILD SUCCESS`). **Limitação:** esses testes usam **H2 em memória** (`application-test.properties`), não o PostgreSQL do contêiner; o PostgreSQL só foi exercitado pelos testes HTTP da seção 17.7. O comando `./mvnw spring-boot:run` **não** foi executado (a aplicação foi iniciada pelo JAR).

### 17.6 Inicialização do Spring Boot (log do contêiner `servicehub-app`)

```text
Starting ServiceHubApiApplication v0.1.0-SNAPSHOT using Java 21.0.12 with PID 1
No active profile set, falling back to 1 default profile: "default"
Tomcat initialized with port 8080 (http)
HikariPool-1 - Start completed.
Database: jdbc:postgresql://servicehub-postgres:5432/servicehub (PostgreSQL 16.15)
Schema history table "public"."flyway_schema_history" does not exist yet
Successfully validated 1 migration
Migrating schema "public" to version "1 - criar tabela servicos"
Successfully applied 1 migration to schema "public", now at version v1 (execution time 00:00.013s)
HHH000412: Hibernate ORM core version 6.6.53.Final
Tomcat started on port 8080 (http) with context path '/'
Started ServiceHubApiApplication in 9.18 seconds
```

Conexão com o banco **confirmada** (Hikari `Start completed`; Flyway leu `PostgreSQL 16.15` no host `servicehub-postgres`). Esquema validado pelo Hibernate (`ddl-auto=validate`): a aplicação subiu sem erro. Ao final da rodada, o log da aplicação tinha 33 linhas `INFO`, 3 `WARN` e **0 `ERROR`**; os `WARN` são os dois avisos do springdoc (endpoints de documentação habilitados por padrão) e `PageNotFound ... Request method 'PATCH' is not supported`, gerado de propósito pelo teste PATCH-01.

### 17.7 Testes das rotas CRUD (`curl` no Windows contra `http://localhost:8080`)

Método: `curl` (Git Bash), corpos em arquivos UTF-8 (`--data-binary @arquivo`, por causa do problema de codificação do `curl` no Windows já descrito na seção 7.3), banco **novo** (Flyway acabara de criar o esquema). Foram **26 execuções** (cenários selecionados a partir da lista pedida; **não** foi repetida a bateria de 86 testes de 20/09). Evidência completa: `docs/evidencias/docker-evidencias-testes-http.txt` (requisição, status e corpo de cada teste), `docs/evidencias/docker-resultados-testes-http.tsv` e os corpos enviados em `docs/evidencias/docker-corpos-requisicao/`.

Legenda: ✅ status obtido = esperado · ❌ status obtido ≠ esperado.

| ID | Método e URL | Corpo enviado | Esperado | Obtido | Resposta (resumo) | Resultado |
|---|---|---|---|---|---|---|
| POST-01 | `POST /api/v1/servicos` | `post-valido.json` (nome "Instalação de ar-condicionado", categoria "Climatização", preco 350.00, 120 min, com descrição) | 201 | **201** | `Location: .../servicos/1`; `{"id":1,...,"preco":350.00,"duracaoMinutos":120}` | ✅ |
| POST-02 | `POST /api/v1/servicos` | `post-sem-descricao.json` ("Limpeza residencial", 120.50, 180 min) | 201 | **201** | `id:2`, `"descricao":null` | ✅ |
| POST-03 | `POST /api/v1/servicos` | `{}` | 400 | **400** | `problem+json` "Dados inválidos", 4 erros (categoria, duracaoMinutos, nome, preco) | ✅ |
| POST-04 | `POST /api/v1/servicos` | `preco: 0` | 400 | **400** | erro em `preco`: "O preço deve ser maior que zero" | ✅ |
| POST-05 | `POST /api/v1/servicos` | `preco: -5` | 400 | **400** | idem | ✅ |
| POST-06 | `POST /api/v1/servicos` | `nome: "ab"` | 400 | **400** | "O nome deve ter entre 3 e 100 caracteres" | ✅ |
| POST-07 | `POST /api/v1/servicos` | `duracaoMinutos: 0` | 400 | **400** | "A duração estimada deve ser de pelo menos 1 minuto" | ✅ |
| POST-08 | `POST /api/v1/servicos` | JSON malformado | 400 | **400** | "Corpo da requisição inválido" | ✅ |
| POST-09 | `POST /api/v1/servicos` | JSON válido, **sem** `Content-Type` | 415 | **415** | "Tipo de conteúdo não suportado" | ✅ |
| GET-01 | `GET /api/v1/servicos` | — | 200 | **200** | array com os serviços 1 e 2 | ✅ |
| GET-02 | `GET /api/v1/servicos/1` | — | 200 | **200** | objeto completo do serviço 1 | ✅ |
| GET-03 | `GET /api/v1/servicos/99999` | — | 404 | **404** | "Serviço com id 99999 não encontrado" | ✅ |
| GET-04 | `GET /api/v1/servicos/abc` | — | 400 | **400** | "Parâmetro inválido" | ✅ |
| PUT-01 | `PUT /api/v1/servicos/1` | `put-valido.json` (nome "Instalação de split 18.000 BTUs", 480.00, 150 min) | 200 | **200** | `criadoEm` inalterado; `atualizadoEm` renovado (`...03:25:50.066210Z`) | ✅ |
| PUT-02 | `GET /api/v1/servicos/1` (após o PUT) | — | 200 | **200** | dados atualizados (preco 480.00, 150 min) | ✅ |
| PUT-03 | `PUT /api/v1/servicos/99999` | `put-valido.json` | 404 | **404** | "Serviço com id 99999 não encontrado" | ✅ |
| PUT-04 | `PUT /api/v1/servicos/2` | `put-invalido.json` (nome vazio, categoria vazia, preco -1, duração 0) | 400 | **400** | "Dados inválidos" com erros de nome, categoria, preco e duração | ✅ |
| PATCH-01 | `PATCH /api/v1/servicos/1` | `put-valido.json` | 405 | **405** | `Allow: DELETE, PUT, GET`; "O método HTTP PATCH não é suportado para este recurso" (PATCH **não é implementado**) | ✅ |
| DEL-01 | `DELETE /api/v1/servicos/2` | — | 204 | **204** | sem corpo | ✅ |
| DEL-02 | `GET /api/v1/servicos/2` (após o DELETE) | — | 404 | **404** | "Serviço com id 2 não encontrado" | ✅ |
| DEL-03 | `DELETE /api/v1/servicos/2` (de novo) | — | 404 | **404** | idem | ✅ |
| DEL-04 | `DELETE /api/v1/servicos/99999` | — | 404 | **404** | "Serviço com id 99999 não encontrado" | ✅ |
| DEL-05 | `DELETE /api/v1/servicos/abc` | — | 400 | **400** | "Parâmetro inválido" | ✅ |
| GET-05 | `GET /api/v1/servicos` (após o DELETE) | — | 200 | **200** | só o serviço 1 (o 2 sumiu) | ✅ |
| OBS-P1 | `POST /api/v1/servicos` | `nome: "  ab "` (`post-nome-espacos.json`) | 400 (contrato: `min=3`) | **201** | `"nome":"ab"` persistido (2 caracteres) | ❌ **defeito P1 reconfirmado** |
| OBS-P2 | `POST /api/v1/servicos` | `duracaoMinutos: 1.5` (`post-duracao-decimal.json`) | 400 (contrato: inteiro) | **201** | `"duracaoMinutos":1` (truncado) | ❌ **defeito P2 reconfirmado** |

**Placar:** 24 ✅ / 2 ❌ (OBS-P1 e OBS-P2). Os 2 ❌ não são regressões: são os defeitos P1 e P2 da seção 12, que **continuam presentes** porque nenhum código foi alterado. Observação sobre a leitura do placar: nas rodadas de 20/09 esses dois cenários foram tratados como "observação" (`PASS` exploratório); aqui foram medidos contra o contrato documentado (`minLength: 3` e inteiro) e por isso aparecem como `FAIL`.

**Confirmação de P4 (escala do preço):** a resposta do POST OBS-P1 trouxe `"preco":10` e o banco guarda `10.00`; o GET/SQL mostra `10.00`.

### 17.8 Verificação direta no PostgreSQL

Consultas via `docker exec servicehub-postgres psql -U servicehub -d servicehub -c "..."` (saída completa em `docs/evidencias/docker-verificacao-banco.txt`):

| Verificação | Resultado |
|---|---|
| Migrations | `flyway_schema_history`: 1 registro — versão `1`, "criar tabela servicos", `V1__criar_tabela_servicos.sql`, `success = t` |
| Constraints de `servicos` | `servicos_pkey` (PK `id`), `ck_servicos_preco` (`preco > 0`), `ck_servicos_duracao` (`duracao_minutos > 0`) |
| POST persistido | Após POST-01/02: 2 linhas (ids 1 e 2) com os valores enviados (350.00/120 e 120.50/180, descrição do id 2 vazia). Os POSTs inválidos (POST-03 a POST-09) **não** criaram linhas |
| GET coerente | Os valores retornados pelo GET-01/02 coincidem com o SQL |
| PUT salvo | Serviço 1 antes: "Instalação de ar-condicionado", 350.00, 120 min. Depois: "Instalação de split 18.000 BTUs", **480.00**, **150** min; `criado_em` idêntico (`03:25:45.279681+00`); `atualizado_em` avançou (`03:25:45.279651` → `03:25:50.066210`) |
| PUT inválido não altera | Serviço 2 após o PUT-04: nome, preço e duração iguais aos do POST-02 (Limpeza residencial, 120.50, 180) |
| DELETE removeu | Após DEL-01: `linhas_id_excluido = 0`, `total = 1` |
| Estado final | ids 1 ("Instalação de split 18.000 BTUs"), 3 (`"ab"`, do OBS-P1) e 4 (duração `1`, do OBS-P2) — 3 linhas |
| Erros de integridade/persistência | Nenhum erro observado nos logs da aplicação nem nas consultas |

**Ressalvas honestas sobre esta verificação:**
1. O script incluiu uma coluna "inalterado" (`criado_em = atualizado_em`) para o serviço 2 que retornou `f`. **Não** prova que o registro foi alterado: já na criação `criado_em` e `atualizado_em` diferem por microssegundos (ex.: `...279681` × `...279651`), pois cada timestamp é gerado separadamente. Em 20/09 (seção 9) as duas colunas coincidiram; aqui não. Sem impacto funcional. A conclusão "PUT inválido não altera" apoia-se na comparação de nome, preço e duração, **não** de `atualizado_em`.
2. A integridade imposta pelo banco (INSERT direto violando `CHECK`/`NOT NULL`) **não** foi repetida nesta rodada; permanece válido o resultado da seção 9 (20/09). Aqui só foi conferida a **existência** das constraints.

### 17.9 Validação do Swagger / OpenAPI

**URLs** (`curl`; saída em `docs/evidencias/docker-swagger-urls.txt`):

| URL | Resultado |
|---|---|
| `/swagger-ui.html` | `302` → `/swagger-ui/index.html` |
| `/swagger-ui/index.html` | `200 text/html`, título "Swagger UI" |
| `/v3/api-docs` | `200 application/json` (7.728 bytes) |
| `/v3/api-docs.yaml` | `200 application/vnd.oai.openapi` |
| `/v3/api-docs/swagger-config` | `200` (`"url":"/v3/api-docs"`) |
| `/swagger-ui/swagger-ui-bundle.js` e `swagger-ui.css` | `200` |

**Navegador:** desta vez a extensão do Chrome **conectou**. O Swagger UI abriu em `http://localhost:8080/swagger-ui/index.html`, exibindo "ServiceHub API v1 (OAS 3.1)", o servidor `http://localhost:8080 - Generated server url`, a tag "Serviços" com as **5 operações** (`GET`/`POST` `/api/v1/servicos`; `GET`/`PUT`/`DELETE` `/api/v1/servicos/{id}`, com seus summaries) e a seção **Schemas** com `ServicoRequest`, `ServicoResponse` e `ProblemDetail`.

***Try it out* (limitação de 20/09 parcialmente resolvida):** expandi `GET /api/v1/servicos`, cliquei em *Try it out* e *Execute*. A UI mostrou `curl -X 'GET' 'http://localhost:8080/api/v1/servicos' -H 'accept: application/json'`, **Code 200** e o corpo com os registros reais do banco (na captura, ids 1 e 3). **Não** executei POST, PUT nem DELETE pela interface. Não há arquivo de captura de tela desta rodada; a evidência é a observação registrada aqui. Duas capturas deram *timeout* transitório na extensão; uma nova tentativa funcionou.

**Conteúdo do `/v3/api-docs`** (analisado por script sobre o JSON):

| Item | Resultado |
|---|---|
| Endpoints e métodos | 5 operações em 2 paths, com os métodos corretos (GET, POST em `/api/v1/servicos`; GET, PUT, DELETE em `/api/v1/servicos/{id}`) |
| `summary` e `description` | Presentes nas 5 operações |
| Parâmetro `id` | `path`, obrigatório, `integer/int64`, com descrição e exemplo `1` (GET id, PUT, DELETE) |
| Corpos de requisição | POST e PUT: obrigatório, `application/json` → `ServicoRequest` |
| Status documentados | POST: 201, 400 · GET lista: 200 · GET id: 200, 400, 404 · PUT: 200, 400, 404 · DELETE: 204, 400, 404 |
| Schemas | `ServicoRequest` (obrigatórios: categoria, duracaoMinutos, nome, preco; `nome` com `minLength 3`/`maxLength 100`), `ServicoResponse`, `ProblemDetail` |
| Exemplos de resposta | Somente POST/400 ("Dados inválidos") e GET id/404 ("Serviço inexistente") |

**Documentação × comportamento real:** o JSON de `/v3/api-docs` desta rodada é **idêntico** (comparado por script) ao salvo em 20/09 em `docs/evidencias/openapi-v3-api-docs.json`, portanto as divergências D1–D5 da seção 10.5 **continuam válidas** e não foram salvas de novo. Confirmações com esta rodada: os status observados de POST (201/400), GET (200/400/404), PUT (200/400/404) e DELETE (204/400/404) **estão todos documentados**; foram observados **e não documentados** o `415` (POST-09) e o `405` (PATCH-01); o cabeçalho `Location` do `201` foi recebido de fato (POST-01), mas o OpenAPI não o declara em `headers`; o schema `ProblemDetail` continua com uma propriedade `properties` que não corresponde ao JSON real (`erros` fica no nível raiz); e `minLength: 3` de `nome` é contrariado pelo P1.

### 17.10 Testes que **não** foram executados

- **Bateria completa de 86 testes HTTP de 20/09:** não repetida; foram 26 cenários selecionados.
- ***Try it out* de POST, PUT e DELETE** pela interface do Swagger (só o GET foi executado). Portanto a afirmação do `README.md` sobre a execução de POST/201 no Swagger (`docs/img/swagger-resposta-201.png`) continua sem reprodução minha.
- **Aplicação sem `DB_PASSWORD`**, **PostgreSQL parado** (erro 500/timeout de 30 s) e **INSERTs SQL que violam constraints**: não repetidos em Docker (valem os resultados de 20/09).
- **`./mvnw spring-boot:run`** e execução da **aplicação** diretamente no Windows (o JDK local só foi usado para `.\mvnw.cmd test`, seção 17.13; a aplicação nunca subiu fora de contêiner nesta rodada).
- **Testes automatizados contra PostgreSQL** (só existem em H2). Postman, carga, concorrência e autenticação: fora do escopo.
- **`docker compose down`**: não executado; os contêineres `servicehub-postgres` e `servicehub-app` **continuam rodando** ao fim da sessão, com o volume de dados preservado.

### 17.11 Problemas encontrados, correções e pendências

| # | Problema | Causa / evidência | Correção realizada |
|---|---|---|---|
| D-1 | `java` ausente no PATH do Windows | Nenhum `java.exe` encontrado no início da sessão | **Contornado** com a imagem `maven:3.9-eclipse-temurin-21` (sem alterar o sistema) |
| D-2 | `docker compose ps` falhou sem `DB_PASSWORD` | O `docker-compose.yml` usa `${DB_PASSWORD:?...}` e o Compose interpola o arquivo em **todo** comando; a mensagem cita "arquivo .env" | **Corrigido depois (mensagem e comentário de `docker-compose.yml`):** a mensagem agora diz "Defina DB_PASSWORD (arquivo .env ou variável de ambiente)". O `README.md` passou a documentar que todos os comandos do Compose exigem a variável. O comportamento (exigir a variável) não mudou |
| D-3 | **P1** reconfirmado (`"  ab "` → 201, `nome = "ab"`) | OBS-P1 e SQL (id 3) | **Nenhuma** (ver seção 12 / P1) |
| D-4 | **P2** reconfirmado (`1.5` → 201, gravado como `1`) | OBS-P2 e SQL (id 4) | **Nenhuma** (ver seção 12 / P2) |
| D-5 | **P4** reconfirmado (POST devolve `10`, banco/GET `10.00`) | OBS-P1 × SQL | **Nenhuma** |
| D-6 | D1–D5 do OpenAPI inalteradas | `/v3/api-docs` idêntico ao de 20/09 | **Nenhuma** |
| D-7 | Timestamps de criação diferem por microssegundos | SQL do serviço 1 (`criado_em` × `atualizado_em`) | Nenhuma; apenas registrado (sem impacto funcional) |
| D-8 | Extensão do Chrome com 2 *timeouts* de captura | Erro `Page.captureScreenshot timed out` | Nova tentativa funcionou |

**Correções realizadas nesta rodada:** **nenhuma** no código, nos testes ou nas configurações do projeto. Arquivos **adicionados**: `docs/evidencias/docker-evidencias-testes-http.txt`, `docker-resultados-testes-http.tsv`, `docker-verificacao-banco.txt`, `docker-swagger-urls.txt` e `docker-corpos-requisicao/` (12 arquivos JSON). Arquivo **alterado**: este relatório (aviso no topo e esta seção 17). O diretório `target/` (ignorado pelo Git) foi regenerado pelos contêineres.

**Pendências restantes:**
1. ~~Corrigir/decidir P1 e P2 (e ajustar/adicionar testes)~~ — **P1 e P2 foram corrigidos com testes em 21/09/2026 (seção 17.14).** Continuam pendentes: avaliar P3, P4, P6, P7 e as divergências D2–D4 (recomendações na seção 12); a D5 deixou de se aplicar com a correção do P1.
2. Executar POST, PUT e DELETE pelo *Try it out* do Swagger, se essa evidência for exigida.
3. Adicionar teste de integração com PostgreSQL (ex.: Testcontainers), já que os automatizados usam H2.
4. Documentar no `README.md` a execução em Docker (`DB_PASSWORD` em todos os comandos do Compose, `DB_URL` com o nome do contêiner quando a aplicação também roda em contêiner).
5. ~~Decidir se os contêineres devem ser parados~~ — **parados em 21/09/2026 (seção 17.14)**, sem remover o volume. Continua pendente limpar os dados de teste do banco (ids 1, 3 e 4).
6. ~~**Git**~~ — resolvido em 21/09/2026 (seção 17.16): as correções de P1/P2 (que haviam ido parar no commit `6be3de9` do upgrade para Java 25) foram separadas em um commit próprio, e a documentação em outro, na branch `correcao-p1-p2`. Nenhum push foi feito.

### 17.12 Resultado geral da validação com Docker

| Pergunta | Resposta (com base em evidência desta rodada) |
|---|---|
| A aplicação compila? | **Sim** — `./mvnw clean test` e `package` com `BUILD SUCCESS` (em contêiner) |
| O PostgreSQL está funcionando? | **Sim** — `servicehub-postgres` `healthy`, PostgreSQL 16.15, aceitou as conexões da aplicação e do `psql` |
| A aplicação iniciou e conectou? | **Sim** — 9,18 s, Flyway `V1` aplicada, sem `ERROR` no log |
| O CRUD foi testado? | **Sim** — POST, GET (lista e id), PUT e DELETE, com cenários negativos e conferência por SQL (26 execuções: 24 ✅ e 2 ❌ = P1 e P2) |
| O Swagger foi validado? | **Sim** — 3 URLs principais acessadas, UI no Chrome, *Try it out* do GET; documentação analisada. Divergências D1–D5 persistem |
| Testes automatizados? | **Sim** — 27/27 aprovados (H2), tanto em contêiner (17.5) quanto nativamente no Windows com `.\mvnw.cmd test` (17.13) |
| Existem pendências? | **Sim** — seção 17.11 |

**Comandos executados nesta rodada (resumo):**

```text
docker --version / docker compose version / docker ps -a / docker image ls / docker volume ls
DB_PASSWORD=<omitida> docker compose up -d
DB_PASSWORD=<omitida> docker compose ps            (mais docker inspect no healthcheck)
docker exec servicehub-postgres psql -U servicehub -d servicehub -c "select version();" -c "\dt"
docker pull maven:3.9-eclipse-temurin-21
docker run --rm ... maven:3.9-eclipse-temurin-21 ./mvnw -B clean test
docker run --rm ... maven:3.9-eclipse-temurin-21 ./mvnw -B -DskipTests package
docker run -d --name servicehub-app --network servicehub-api_default -p 8080:8080 -e DB_URL=... -e DB_USER=... -e DB_PASSWORD=<omitida> ... java -jar ...
docker logs servicehub-app
curl -s -D <hdr> -o <body> -w '%{http_code}' -X <M> ...     (26 execuções, script bash)
docker exec servicehub-postgres psql ... (consultas de verificação)
curl das URLs do Swagger + análise do /v3/api-docs; Chrome: /swagger-ui.html, Try it out do GET
.\mvnw.cmd -B test                                  (Windows, JDK local; ver 17.13)
```

### 17.13 Testes automatizados nativos no Windows (`.\mvnw.cmd test`)

Depois que o usuário instalou o JDK 21 no Windows, a pendência "executar o `mvnw.cmd` no host" foi fechada.

**Java local verificado** (antes do teste):

| Item | Valor |
|---|---|
| JDK | Oracle Java SE 21.0.12 LTS (build `21.0.12+7-LTS-205`), `java` e `javac` 21.0.12 |
| Caminho | `C:\Program Files\Java\jdk-21.0.12` (também registrado em `HKLM\SOFTWARE\JavaSoft\JDK\21.0.12`) |
| `JAVA_HOME` | Definida no ambiente do **usuário** com esse caminho (a do sistema está vazia) |
| Path do usuário | Contém `%JAVA_HOME%\bin` (tipo `ExpandString`, portanto a variável é expandida). Contém também `C:\Program Files\Java\jdk-21.0.12` **sem** `\bin`: entrada inócua, pode ser removida |
| Path do sistema | Contém o atalho do instalador Oracle (`C:\Program Files\Common Files\Oracle\Java\javapath`), que vem **antes** e aponta para o mesmo JDK (hoje sem conflito) |

**Execução:** o terminal da sessão foi aberto antes da instalação; por isso `JAVA_HOME` e o Path foram relidos do registro dentro do próprio comando. Foi usado `.\mvnw.cmd -B test` **sem `clean`**, porque o `clean` apagaria o `target/servicehub-api-0.1.0-SNAPSHOT.jar` que o contêiner `servicehub-app` está executando.

| Item | Resultado |
|---|---|
| Comando | `.\mvnw.cmd -B test` (PowerShell, Windows 11) |
| Java usado | 21.0.12 (log: `Starting ServicoControllerTest using Java 21.0.12`) |
| Maven | distribuição `apache-maven-3.9.11` do wrapper (`~/.m2/wrapper/dists`) |
| Código de saída | `0` |
| Resultado | `Tests run: 27, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS` |
| Tempo | `Total time: 14.442 s` (16,6 s medidos pelo terminal; dependências já em cache local) |
| Por classe | `ServicoServiceTest` = 4; `ServicoControllerTest` = 23 (POST 8, GET 5, PUT 3, DELETE 3, erros genéricos 3, OpenAPI 1) — idêntico à execução em contêiner (17.5) e à de 20/09 (seção 11) |

**Avisos no console (não são falhas):** o Mockito informando *self-attaching* do agente e o aviso do JVM sobre carregamento dinâmico de agente (`A Java agent has been loaded dynamically ... byte-buddy-agent-1.17.8.jar`), os mesmos citados na seção 11.

**Limites desta verificação:** os testes continuam usando **H2**, então não exercitam o PostgreSQL. A **aplicação não foi iniciada** no Windows (`spring-boot:run` e `java -jar` no host seguem não executados nesta rodada, além do contêiner). O JAR em `target/` permaneceu o mesmo (gerado às 00:24) e o contêiner `servicehub-app` continuou respondendo `200` em `GET /api/v1/servicos` depois do teste. O log do Maven ficou no diretório temporário da sessão, **não** foi salvo em `docs/evidencias/`; as evidências desta execução são o resultado registrado aqui e os relatórios do Surefire em `target/surefire-reports/` (ignorado pelo Git).

### 17.14 Correção de P1 e P2 (21/09/2026)

A pedido do usuário, os defeitos **P1** e **P2** (seção 12) foram corrigidos, com testes. Esta é a primeira alteração de código-fonte da validação; as seções 12 e 17.7 permanecem como registro do estado **antes** da correção.

**Correção realizada**

| Defeito | Alteração | Por que resolve |
|---|---|---|
| **P1** — `"  ab "` passava pelo `@Size(min=3)` | `ServicoRequest`: novo **construtor compacto** que aplica `strip()` em `nome` e `categoria` (tolerando `null`); os `strip()` de `paraEntidade()` e `aplicarEm()` foram removidos por ficarem redundantes | O Jackson constrói o *record* pelo construtor canônico, então a normalização ocorre **antes** do Bean Validation e os limites de tamanho passam a valer para o texto que será gravado |
| **P2** — `duracaoMinutos: 1.5` era truncado para `1` | `application.properties`: `spring.jackson.deserialization.accept-float-as-int=false` | O Jackson deixa de converter decimal em inteiro e o `HttpMessageNotReadableException` cai no tratamento já existente (400 "Corpo da requisição inválido") |

**Mudanças de comportamento (intencionais e por consequência)**
1. `nome` `"  ab "` → **400** "O nome deve ter entre 3 e 100 caracteres" (antes: 201 e gravado como `"ab"`).
2. Nome **só com espaços** agora gera **2 erros** no campo `nome` (`@NotBlank` e `@Size`), como `""` já gerava; antes gerava só "O nome é obrigatório". Por isso o teste existente `rejeitaNomeEmBrancoEDuracaoInvalida` foi **ajustado** (de `containsInAnyOrder("nome","duracaoMinutos")` para `hasItems(...)` mais a checagem da mensagem "O nome é obrigatório").
3. Os limites de tamanho valem para o texto **sem espaços nas bordas**: um `nome` de 100 caracteres cercado por espaços, antes recusado (400), passa a ser aceito.
4. **Qualquer** número com parte decimal em `duracaoMinutos` é rejeitado, **inclusive `120.0`** (400). A mensagem é a genérica "Corpo da requisição inválido", sem indicar o campo (como nos demais erros de tipo).
5. `descricao` **não** é normalizada (fora do escopo).

**Testes**

| Arquivo | Alteração |
|---|---|
| `src/test/java/com/servicehub/api/dto/ServicoRequestTest.java` (novo) | 6 testes unitários com `Validator`: normalização, `null`, `"  ab "` rejeitado, `"  abc "` aceito, 100 caracteres úteis aceitos, nome só com espaços |
| `ServicoControllerTest` › POST | +6: `rejeitaNomeCurtoComEspacosNasBordas`, `normalizaNomeECategoria`, `aceitaNomeNoLimiteMaximoComEspacos`, `rejeitaCategoriaSoComEspacos`, `rejeitaDuracaoDecimal`, `rejeitaDuracaoDecimalInteira` |
| `ServicoControllerTest` › PUT | +2: `atualizaComNomeCurtoEEspacosNasBordas`, `atualizaComDuracaoDecimal` (ambos conferem que o registro original não mudou) |
| `ServicoControllerTest` › POST | 1 ajustado: `rejeitaNomeEmBrancoEDuracaoInvalida` (item 2 acima) |

Total: **27 → 41 testes** (14 novos).

**Prova de que os testes detectam os defeitos (red/green)**

| Fase | Como | Resultado |
|---|---|---|
| **Red** | Código de produção **original** (commit `1b4e9a5`) + testes novos, em cópia isolada (`git worktree`), `.\mvnw.cmd -B test`, JDK 21 | `Tests run: 41, Failures: 9, Errors: 0` — falharam os 6 testes de controller `atualizaComNomeCurtoEEspacosNasBordas`, `atualizaComDuracaoDecimal`, `rejeitaDuracaoDecimalInteira`, `aceitaNomeNoLimiteMaximoComEspacos`, `rejeitaNomeCurtoComEspacosNasBordas`, `rejeitaDuracaoDecimal` e os 3 de `ServicoRequestTest` `normalizaTexto`, `aceitaNomeNoLimiteMaximoComEspacos`, `rejeitaNomeCurtoComEspacosNasBordas`. Os outros 32 passaram (servem de proteção contra regressão) |
| **Green** | Código com as correções (commit `6be3de9` extraído por `git archive`), `.\mvnw.cmd -B test -Djava.version=21`, JDK 21 | `Tests run: 41, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS` (17,4 s) |

**Confirmação de ponta a ponta no PostgreSQL** (JAR gerado da mesma extração, contêiner temporário `servicehub-app-fix` na porta 8081, mesmo banco): **11 cenários, 11 `PASS`** — `POST` `"  ab "` → 400; `PUT` com `"  ab "` → 400; `POST` `1.5` → 400; `POST` `120.0` → 400; `PUT` `1.5` → 400; serviço 1 inalterado (`GET` 200); `POST` `"  abc  "` / `"  Reformas  "` → 201, gravado como `abc` / `Reformas` (SQL: `[abc]`, `[Reformas]`, tamanho 3); `POST` válido → 201; `DELETE` → 204 (2 vezes); `GET` após o `DELETE` → 404. Os registros criados nesse teste foram excluídos ao final (SQL: continuam só os ids 1, 3 e 4, de antes). Evidências: `docs/evidencias/docker-correcao-p1-p2-testes.txt`, `docker-correcao-p1-p2-e2e.txt`, `docker-correcao-p1-p2-e2e.tsv` e `docker-corpos-correcao-p1-p2/`.

**Problemas encontrados durante esta correção**
1. **Build concorrente no mesmo diretório.** Enquanto eu validava, uma ferramenta de upgrade para **Java 25** (extensão do VS Code, branch `appmod/java-upgrade-20260921034935`) compilava e limpava o `target/` do próprio projeto. Isso derrubou a minha primeira tentativa de *red* (`ServicoControllerTest.class ... does not exist`) e uma tentativa de *green* (`class file version 69.0` — compilado por JDK 25 — não roda no JDK 21), e apagou o JAR de `target/`. **Esses resultados foram descartados.** A primeira tentativa de *red* usou `git stash` **no diretório real** (guardando por instantes as duas alterações de produção e restaurando-as com `git stash pop`, que concluiu sem conflito e sem deixar stash); foi um erro de método, já que a pasta estava em uso por outra ferramenta. O red e o green válidos foram refeitos em pastas isoladas (worktree e extração por `git archive`), sem tocar em `target/`.
2. **As correções de P1/P2 entraram no commit `6be3de9`** ("Step 4: Upgrade to Java 25"), criado automaticamente pela ferramenta: ela incluiu os 4 arquivos que eu havia alterado (`ServicoRequest.java`, `application.properties`, `ServicoControllerTest.java`, `ServicoRequestTest.java`) junto com a mudança do `pom.xml` (`java.version` 21 → 25). O conteúdo está correto, mas **misturado** com o upgrade. Não reescrevi o histórico.
3. **Não executei os testes em JDK 25.** O texto do commit `6be3de9` afirma "Tests: 100% passed", mas essa execução foi da ferramenta e não foi verificada por mim. Todas as execuções desta seção usaram o JDK 21 (com `-Djava.version=21` sobre o `pom.xml`, que já pede 25).
4. **Dados legados.** A correção não é retroativa: os registros `id 3` (`nome = "ab"`) e `id 4` (`duracao_minutos = 1`), criados antes dela, continuam no banco (volume preservado).
5. O `mvnw` extraído por `git archive` veio com finais de linha CRLF e não roda em Linux; o JAR do teste de ponta a ponta foi gerado com o `mvn` da imagem `maven:3.9-eclipse-temurin-21`.

**Não executado:** `.\mvnw.cmd test` no diretório de trabalho real depois da correção (bloqueado pelo build concorrente); testes em JDK 25; Swagger/OpenAPI após a correção (o `minLength: 3` do `nome`, que a D5 apontava como contrariado, agora é honrado pela API, mas o JSON de `/v3/api-docs` não foi regerado).

**Contêineres:** ao final, `servicehub-app-fix` (temporário) foi **removido**; `servicehub-app` e `servicehub-postgres` foram **parados** (`docker stop`), sem `docker compose down` e **sem apagar volumes** — o volume `servicehub-api_servicehub-pgdata` (e o cache `servicehub-m2`) foi preservado. As portas 5432, 8080 e 8081 estão livres.

### 17.15 Retorno ao Java 21, teste final e atualização do README (21/09/2026)

O usuário informou que o upgrade para Java 25 terminou e autorizou voltar ao Java 21.

**O que foi desfeito e o que foi mantido.** Entre o commit `1b4e9a5` e o `6be3de9` (upgrade), a **única** alteração que não era minha foi `pom.xml`: `<java.version>21</java.version>` → `25`. Ela foi revertida **no diretório de trabalho** (`java.version` voltou a `21`), sem commit naquele momento; a branch `appmod/java-upgrade-20260921034935` continua com o `25` em `6be3de9`. As correções de P1/P2 (17.14) foram mantidas. O destino final dessas alterações está na seção 17.16.

**Teste final no diretório real** (Windows, JDK 21.0.12, Maven 3.9.11 do wrapper, `target/` recompilado do zero):

| Item | Resultado |
|---|---|
| Comando | `.\mvnw.cmd -B clean test` |
| Compilação | `Compiling 10 source files ... release 21` (produção) e `3 source files ... release 21` (testes) |
| Resultado | `Tests run: 41, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS` — `Total time: 12.777 s`, código de saída 0 |
| Por classe | `ServicoControllerTest` 31 (POST 14, GET 5, PUT 5, DELETE 3, erros genéricos 3, OpenAPI 1), `ServicoRequestTest` 6, `ServicoServiceTest` 4 |

Isso fecha o item "`.\mvnw.cmd test` no diretório de trabalho real depois da correção" listado como não executado na seção 17.14. **Continua não executado:** teste em JDK 25 (o "100% passed" do commit `6be3de9` é da ferramenta, não verificado por mim), regeração do `/v3/api-docs` após a correção e nova bateria HTTP completa. O log desta execução ficou no diretório temporário da sessão (não versionado); a evidência é o resultado registrado aqui.

**Observação sobre contagens:** os "27 testes" das seções 17.1, 17.5, 17.12 e 17.13 descrevem o estado **anterior** à correção de P1/P2; o total atual é **41**.

**Verificação adicional do Compose:** `docker compose stop` e `docker compose down` **sem** `DB_PASSWORD` também falham com `required variable DB_PASSWORD is missing a value` (antes só o `ps` havia sido testado). Um `docker compose down --dry-run` (sem remover nada) listou apenas o contêiner e a rede, não o volume.

**README atualizado** (`README.md`), para a apresentação:
- Docker Compose: passos com e sem `.env`, obrigatoriedade de `DB_PASSWORD` em todos os comandos, healthcheck, `stop`/`down` e a diferença para `down -v`; a nota "não foi executado" foi trocada pelo que foi validado (Docker 29.8.0, Compose v5.5.1, PostgreSQL 16.15) e foi acrescentado o `DB_URL` para a aplicação em contêiner.
- Regras do recurso: `nome` e `categoria` sem espaços nas bordas, limites reais de `preco` e `duracaoMinutos`, decimal rejeitado.
- Decisões de design (normalização antes da validação; tipos estritos), dois exemplos de erro `400` reais e a tabela de testes com **41 testes** (31 + 6 + 4).
- Nova seção "Validação realizada", com link para este relatório, e limitações atualizadas (OpenAPI sem `405/406/415/500`, escala do preço, Swagger habilitado por padrão, ~30 s até o `500` com o banco fora).
- Mantidos sem alteração: as imagens de `docs/img/` e a frase sobre o *Try it out* do `POST` no Swagger UI. **Não reproduzi** esse `POST` pelo Swagger (só o `GET`, seção 17.9); a afirmação é do autor do projeto.

### 17.16 Análise final, limpeza e commits (21/09/2026)

**Análise final dos arquivos do projeto.** Foram revisados: `README.md`, este relatório, `docs/` (evidências, imagens e o PPTX), `docker-compose.yml`, `.env.example`, `.gitignore`, `pom.xml`, `application.properties`, `messages.properties`, a migration e todas as classes Java, de produção e de teste.

**Limpeza feita**

| Item | Antes | Depois |
|---|---|---|
| Caminhos locais e nome de usuário do Windows em `docs/evidencias/` (22 ocorrências nos comandos `curl`) e neste relatório (2) | `@C:/Users/<usuário>/AppData/.../scratchpad/bodies/...` | Caminhos relativos às pastas de corpos versionadas (`docker-corpos-requisicao/`, `docker-corpos-correcao-p1-p2/`); conferido que **todos** os arquivos referenciados existem |
| Seções 2, 12, 13, 14 e 15 (texto de 20/09 em conflito com a seção 17) | Diziam "nenhuma correção", "Docker não executado", "27 testes" | Texto original **preservado**, com um aviso de atualização no início de cada seção; o cabeçalho do relatório ganhou o "Estado atual" |
| `docker-compose.yml` | A mensagem de erro citava só o arquivo `.env` | Cita também a variável de ambiente; o comportamento não mudou (validado com `docker compose config`, com e sem `DB_PASSWORD`) |

**Código:** revisado; **nada foi removido**. Não há import sem uso (verificado por script), e todos os getters, setters e métodos de conversão são usados. As únicas adições desta validação são o construtor compacto de `ServicoRequest` (necessário para o P1) e uma propriedade do Jackson (necessária para o P2). O bean `ordenarCaminhos` de `OpenApiConfig` só define a ordem de exibição dos caminhos no Swagger; foi mantido por não ser possível dizer, sem testar, como o Swagger UI ficaria sem ele.

**Encontrado e deliberadamente não alterado**
- `docs/ServiceHub-API-Checkpoint.pptx` está desatualizado nos slides 9 e 10 ("27 testes", "23 de integração e 4 unitários", "PostgreSQL 16.4"); hoje são **41 testes** (31 + 6 + 4) e o banco foi validado em Docker com o PostgreSQL 16.15. O arquivo não foi editado.
- `.github/modernize/` (plano, progresso, logs e *hooks* da ferramenta de upgrade para Java 25) existe só no disco: o `.gitignore` interno da pasta o exclui do Git. Não foi apagado.

**Commits** (branch **`correcao-p1-p2`**, criada a partir do commit do usuário `1b4e9a5`; nada foi enviado ao remoto)
1. `d54c56a` — *Corrige validação do nome com espaços (P1) e duração decimal (P2)*: só `ServicoRequest.java`, `application.properties` e os testes. Verificado **isoladamente** (extração por `git archive`, `.\mvnw.cmd -B test`, JDK 21, sem `-D`): **41 testes, 0 falhas, `BUILD SUCCESS`**.
2. Commit de documentação: `README.md`, este relatório, `docker-compose.yml` e `docs/evidencias/`.

O `pom.xml` desta branch é o mesmo de `1b4e9a5` (`java.version` = **21**), por isso não há commit de pom. A branch `appmod/java-upgrade-20260921034935` **não foi alterada** e mantém o commit `6be3de9` (Java 25).
