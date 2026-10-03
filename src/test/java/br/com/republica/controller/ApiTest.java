package br.com.republica.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testa o contrato HTTP: códigos de status e formato das respostas. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiTest {

    @Autowired MockMvc mvc;

    @Test
    void criarCasaSemNomeDevolve400ComOCampoComErro() throws Exception {
        mvc.perform(post("/api/casas").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").exists());
    }

    @Test
    void casaInexistenteDevolve404() throws Exception {
        mvc.perform(get("/api/casas/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void fluxoCompletoDeCriacaoDeDespesa() throws Exception {
        Long casaId = criarCasa("Casa de teste");
        Long ana = criarMorador(casaId, "Ana");
        Long beto = criarMorador(casaId, "Beto");

        String despesa = """
                {"descricao":"Pizza","valorTotal":90.00,"pagadorId":%d,"participantesIds":[%d,%d]}
                """.formatted(ana, ana, beto);

        mvc.perform(post("/api/casas/%d/despesas".formatted(casaId))
                        .contentType(MediaType.APPLICATION_JSON).content(despesa))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.divisoes", hasSize(2)))
                .andExpect(jsonPath("$.quitada").value(false));

        mvc.perform(get("/api/casas/%d/resumo".formatted(casaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acertos", hasSize(1)))
                .andExpect(jsonPath("$.acertos[0].de.nome").value("Beto"))
                .andExpect(jsonPath("$.acertos[0].valor").value(45.0));
    }

    @Test
    void regraDeNegocioViolada422() throws Exception {
        Long casaId = criarCasa("Casa de teste");
        Long ana = criarMorador(casaId, "Ana");

        String despesa = """
                {"descricao":"Sozinha","valorTotal":10.00,"pagadorId":%d,"participantesIds":[%d]}
                """.formatted(ana, ana);

        mvc.perform(post("/api/casas/%d/despesas".formatted(casaId))
                        .contentType(MediaType.APPLICATION_JSON).content(despesa))
                .andExpect(status().isUnprocessableEntity());
    }

    private Long criarCasa(String nome) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/casas")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"%s\"}".formatted(nome)))
                .andExpect(status().isCreated()).andReturn();
        return idDe(resultado);
    }

    private Long criarMorador(Long casaId, String nome) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/casas/%d/moradores".formatted(casaId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"%s\"}".formatted(nome)))
                .andExpect(status().isCreated()).andReturn();
        return idDe(resultado);
    }

    private Long idDe(MvcResult resultado) throws Exception {
        String corpo = resultado.getResponse().getContentAsString();
        return Long.valueOf(com.jayway.jsonpath.JsonPath.read(corpo, "$.id").toString());
    }
}
