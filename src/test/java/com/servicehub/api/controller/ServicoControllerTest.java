package com.servicehub.api.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.servicehub.api.repository.ServicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServicoControllerTest {

    private static final String BASE = "/api/v1/servicos";

    private static final String JSON_VALIDO = """
            {
              "nome": "Instalação de ar-condicionado",
              "descricao": "Split de até 12.000 BTUs",
              "categoria": "Climatização",
              "preco": 350.00,
              "duracaoMinutos": 120
            }""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServicoRepository repository;

    @BeforeEach
    void limparBanco() {
        repository.deleteAll();
    }

    private ResultActions postar(String json) throws Exception {
        return mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long criarServico() throws Exception {
        String corpo = postar(JSON_VALIDO).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(corpo, "$.id")).longValue();
    }

    @Nested
    @DisplayName("POST /api/v1/servicos")
    class Criar {

        @Test
        @DisplayName("retorna 201, Location e o recurso criado quando os dados são válidos")
        void criaComSucesso() throws Exception {
            postar(JSON_VALIDO)
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", matchesPattern(".*" + BASE + "/\\d+")))
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.nome").value("Instalação de ar-condicionado"))
                    .andExpect(jsonPath("$.categoria").value("Climatização"))
                    .andExpect(jsonPath("$.preco").value(350.00))
                    .andExpect(jsonPath("$.duracaoMinutos").value(120))
                    .andExpect(jsonPath("$.criadoEm", notNullValue()))
                    .andExpect(jsonPath("$.atualizadoEm", notNullValue()));
        }

        @Test
        @DisplayName("aceita cadastro sem descrição (campo opcional)")
        void criaSemDescricao() throws Exception {
            postar("""
                    { "nome": "Limpeza residencial", "categoria": "Limpeza", "preco": 120.5, "duracaoMinutos": 180 }""")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.descricao").doesNotExist());
        }

        @Test
        @DisplayName("retorna 400 e lista todos os campos obrigatórios ausentes")
        void rejeitaCorpoVazio() throws Exception {
            postar("{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Dados inválidos"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.erros[*].campo",
                            containsInAnyOrder("nome", "categoria", "preco", "duracaoMinutos")));
        }

        @Test
        @DisplayName("retorna 400 para preço zero ou negativo")
        void rejeitaPrecoInvalido() throws Exception {
            postar(JSON_VALIDO.replace("350.00", "-5"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros[0].campo").value("preco"))
                    .andExpect(jsonPath("$.erros[0].mensagem").value("O preço deve ser maior que zero"));
        }

        @Test
        @DisplayName("retorna 400 para preço com mais de 2 casas decimais")
        void rejeitaPrecoComCasasExcedentes() throws Exception {
            postar(JSON_VALIDO.replace("350.00", "10.999"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros[0].campo").value("preco"));
        }

        @Test
        @DisplayName("retorna 400 para nome em branco e duração menor que 1")
        void rejeitaNomeEmBrancoEDuracaoInvalida() throws Exception {
            postar(JSON_VALIDO.replace("Instalação de ar-condicionado", "   ").replace("120", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros[*].campo", containsInAnyOrder("nome", "duracaoMinutos")));
        }

        @Test
        @DisplayName("retorna 400 para JSON malformado")
        void rejeitaJsonMalformado() throws Exception {
            postar("{ nome: ")
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Corpo da requisição inválido"))
                    .andExpect(jsonPath("$.detail").value("O corpo da requisição está ausente ou não é um JSON válido"));
        }

        @Test
        @DisplayName("não persiste nada quando a validação falha")
        void naoPersisteQuandoInvalido() throws Exception {
            postar("{}").andExpect(status().isBadRequest());
            org.junit.jupiter.api.Assertions.assertEquals(0, repository.count());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/servicos")
    class Consultar {

        @Test
        @DisplayName("retorna 200 e lista vazia quando não há serviços")
        void listaVazia() throws Exception {
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("retorna 200 e todos os serviços cadastrados")
        void listaTodos() throws Exception {
            criarServico();
            criarServico();

            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        @DisplayName("GET /{id} retorna 200 e o serviço existente")
        void buscaPorId() throws Exception {
            long id = criarServico();

            mockMvc.perform(get(BASE + "/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.nome").value("Instalação de ar-condicionado"));
        }

        @Test
        @DisplayName("GET /{id} retorna 404 quando o ID não existe")
        void buscaInexistente() throws Exception {
            mockMvc.perform(get(BASE + "/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.detail").value("Serviço com id 999999 não encontrado"));
        }

        @Test
        @DisplayName("GET /{id} retorna 400 quando o ID não é numérico")
        void buscaComIdInvalido() throws Exception {
            mockMvc.perform(get(BASE + "/abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("O valor informado para o parâmetro 'id' é inválido"));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/servicos/{id}")
    class Atualizar {

        private static final String JSON_ATUALIZADO = """
                {
                  "nome": "Instalação de split 18.000 BTUs",
                  "categoria": "Climatização",
                  "preco": 480.00,
                  "duracaoMinutos": 150
                }""";

        @Test
        @DisplayName("retorna 200, substitui os dados e preserva a data de criação")
        void atualizaComSucesso() throws Exception {
            long id = criarServico();
            String criadoEm = com.jayway.jsonpath.JsonPath.read(
                    mockMvc.perform(get(BASE + "/" + id)).andReturn().getResponse().getContentAsString(),
                    "$.criadoEm");

            mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(JSON_ATUALIZADO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.nome").value("Instalação de split 18.000 BTUs"))
                    .andExpect(jsonPath("$.preco").value(480.00))
                    .andExpect(jsonPath("$.duracaoMinutos").value(150))
                    // PUT substitui o recurso: a descrição omitida é limpa
                    .andExpect(jsonPath("$.descricao").doesNotExist())
                    .andExpect(jsonPath("$.criadoEm").value(criadoEm));

            mockMvc.perform(get(BASE + "/" + id))
                    .andExpect(jsonPath("$.nome").value("Instalação de split 18.000 BTUs"));
        }

        @Test
        @DisplayName("retorna 404 quando o ID não existe")
        void atualizaInexistente() throws Exception {
            mockMvc.perform(put(BASE + "/999999").contentType(MediaType.APPLICATION_JSON).content(JSON_ATUALIZADO))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("retorna 400 e mantém os dados originais quando o corpo é inválido")
        void atualizaComDadosInvalidos() throws Exception {
            long id = criarServico();

            mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(get(BASE + "/" + id))
                    .andExpect(jsonPath("$.nome").value("Instalação de ar-condicionado"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/servicos/{id}")
    class Excluir {

        @Test
        @DisplayName("retorna 204 sem corpo e o recurso deixa de existir")
        void excluiComSucesso() throws Exception {
            long id = criarServico();

            mockMvc.perform(delete(BASE + "/" + id))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            mockMvc.perform(get(BASE + "/" + id)).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("retorna 404 quando o ID não existe")
        void excluiInexistente() throws Exception {
            mockMvc.perform(delete(BASE + "/999999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("excluir duas vezes o mesmo ID retorna 204 e depois 404")
        void excluirDuasVezes() throws Exception {
            long id = criarServico();

            mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());
            mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Erros genéricos do Spring MVC")
    class ErrosGenericos {

        @Test
        @DisplayName("rota inexistente retorna 404 em Problem Details")
        void rotaInexistente() throws Exception {
            mockMvc.perform(get("/api/v1/nada"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("A rota solicitada não existe"));
        }

        @Test
        @DisplayName("método não suportado retorna 405")
        void metodoNaoSuportado() throws Exception {
            mockMvc.perform(patch(BASE + "/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.detail").value("O método HTTP PATCH não é suportado para este recurso"));
        }

        @Test
        @DisplayName("Content-Type diferente de JSON retorna 415")
        void tipoDeConteudoNaoSuportado() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.TEXT_PLAIN).content("texto"))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    @Nested
    @DisplayName("Documentação OpenAPI")
    class Documentacao {

        @Test
        @DisplayName("/v3/api-docs expõe todos os endpoints com summary e description")
        void apiDocsDocumentaTodosOsEndpoints() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.info.title").value("ServiceHub API"))
                    .andExpect(jsonPath("$.paths['/api/v1/servicos'].post.summary", notNullValue()))
                    .andExpect(jsonPath("$.paths['/api/v1/servicos'].post.description", notNullValue()))
                    .andExpect(jsonPath("$.paths['/api/v1/servicos'].get.summary", notNullValue()))
                    .andExpect(jsonPath("$.paths['/api/v1/servicos'].get.description", notNullValue()))
                    .andExpect(jsonPath("$.paths['/api/v1/servicos/{id}'].get.summary", notNullValue()))
                    .andExpect(jsonPath("$.paths['/api/v1/servicos/{id}'].get.description", notNullValue()))
                    .andExpect(jsonPath("$.paths['/api/v1/servicos/{id}'].put.summary", notNullValue()))
                    .andExpect(jsonPath("$.paths['/api/v1/servicos/{id}'].put.description", notNullValue()))
                    .andExpect(jsonPath("$.paths['/api/v1/servicos/{id}'].delete.summary", notNullValue()))
                    .andExpect(jsonPath("$.paths['/api/v1/servicos/{id}'].delete.description", notNullValue()));
        }
    }
}
