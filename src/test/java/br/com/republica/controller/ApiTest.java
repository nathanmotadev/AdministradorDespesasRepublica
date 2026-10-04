package br.com.republica.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testa o contrato HTTP: login, códigos de status, formato das respostas e privacidade entre moradores. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiTest {

    @Autowired MockMvc mvc;

    record Sessao(String token, Long casaId, Long moradorId) {
    }

    // ---------- cadastro e login ----------

    @Test
    void cadastroSemNomeDeCasaDevolve400ComOCampoComErro() throws Exception {
        enviar("/api/auth/cadastro/casa", null,
                "{\"nomeCasa\":\"\",\"nome\":\"Ana\",\"email\":\"ana@email.com\",\"senha\":\"senha1234\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nomeCasa").exists());
    }

    @Test
    void senhaCurtaDevolve400() throws Exception {
        enviar("/api/auth/cadastro/casa", null,
                "{\"nomeCasa\":\"Casa\",\"nome\":\"Ana\",\"email\":\"ana@email.com\",\"senha\":\"123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.senha").exists());
    }

    @Test
    void emailJaCadastradoDevolve422() throws Exception {
        cadastrarCasa("Casa A", "Ana", "ana@email.com");
        enviar("/api/auth/cadastro/casa", null,
                "{\"nomeCasa\":\"Casa B\",\"nome\":\"Outra Ana\",\"email\":\"ANA@email.com\",\"senha\":\"senha1234\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void conviteInvalidoDevolve422() throws Exception {
        enviar("/api/auth/cadastro/convite", null,
                "{\"codigoConvite\":\"CASA-XXXXXX\",\"nome\":\"Beto\",\"email\":\"beto@email.com\",\"senha\":\"senha1234\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void loginComSenhaErradaDevolve401() throws Exception {
        cadastrarCasa("Casa A", "Ana", "ana@email.com");
        enviar("/api/auth/login", null, "{\"email\":\"ana@email.com\",\"senha\":\"errada1234\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void loginComSenhaCertaDevolveOToken() throws Exception {
        cadastrarCasa("Casa A", "Ana", "ana@email.com");
        enviar("/api/auth/login", null, "{\"email\":\"ANA@email.com\",\"senha\":\"senha1234\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.morador.admin").value(true));
    }

    // ---------- acesso ----------

    @Test
    void rotasDaApiExigemLogin() throws Exception {
        mvc.perform(get("/api/eu")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/casas/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/eu").header("Authorization", "Bearer token-falso")).andExpect(status().isUnauthorized());
    }

    @Test
    void quemNaoMoraNaCasaRecebe403() throws Exception {
        Sessao ana = cadastrarCasa("Casa da Ana", "Ana", "ana@email.com");
        Sessao dani = cadastrarCasa("Casa da Dani", "Dani", "dani@email.com");

        consultar(dani, "/api/casas/" + ana.casaId()).andExpect(status().isForbidden());
        enviar("/api/casas/" + ana.casaId() + "/despesas", dani, despesa(ana.moradorId(), dani.moradorId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void soOAdministradorGeraConvite() throws Exception {
        Sessao ana = cadastrarCasa("Casa", "Ana", "ana@email.com");
        Sessao beto = entrarComConvite(conviteDe(ana), "Beto", "beto@email.com");

        consultar(beto, "/api/casas/" + ana.casaId() + "/convite").andExpect(status().isForbidden());
        consultar(ana, "/api/casas/" + ana.casaId() + "/convite").andExpect(status().isOk());
    }

    // ---------- o fluxo do dia a dia ----------

    @Test
    void fluxoCompletoComDuasContas() throws Exception {
        Sessao ana = cadastrarCasa("Casa de teste", "Ana", "ana@email.com");
        Sessao beto = entrarComConvite(conviteDe(ana), "Beto", "beto@email.com");

        enviar("/api/casas/" + ana.casaId() + "/despesas", ana, despesa(ana.moradorId(), beto.moradorId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.divisoes", hasSize(2)))
                .andExpect(jsonPath("$.quitada").value(false));

        consultar(beto, "/api/eu/resumo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deve").value(45.0))
                .andExpect(jsonPath("$.acertos", hasSize(1)))
                .andExpect(jsonPath("$.acertos[0].pessoa.nome").value("Ana"))
                .andExpect(jsonPath("$.acertos[0].euDevo").value(true));

        consultar(ana, "/api/eu/resumo")
                .andExpect(jsonPath("$.aReceber").value(45.0))
                .andExpect(jsonPath("$.acertos[0].euDevo").value(false));

        consultar(beto, "/api/eu")
                .andExpect(jsonPath("$.morador.nome").value("Beto"))
                .andExpect(jsonPath("$.casa.moradores", hasSize(2)));
    }

    @Test
    void moradorNaoEnxergaDividasDeOutros() throws Exception {
        Sessao ana = cadastrarCasa("Casa", "Ana", "ana@email.com");
        String convite = conviteDe(ana);
        Sessao beto = entrarComConvite(convite, "Beto", "beto@email.com");
        Sessao carla = entrarComConvite(convite, "Carla", "carla@email.com");

        // Ana e Beto dividem; Carla não participa
        enviar("/api/casas/" + ana.casaId() + "/despesas", ana, despesa(ana.moradorId(), beto.moradorId()))
                .andExpect(status().isCreated());

        consultar(carla, "/api/casas/" + ana.casaId() + "/despesas").andExpect(jsonPath("$", hasSize(0)));
        consultar(carla, "/api/eu/dividas")
                .andExpect(jsonPath("$.euDevo", hasSize(0)))
                .andExpect(jsonPath("$.meDevem", hasSize(0)));
        consultar(carla, "/api/eu/resumo").andExpect(jsonPath("$.acertos", hasSize(0)));

        consultar(beto, "/api/casas/" + ana.casaId() + "/despesas")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].divisoes", hasSize(1)));
    }

    @Test
    void soQuemPagouConfirmaORecebimento() throws Exception {
        Sessao ana = cadastrarCasa("Casa", "Ana", "ana@email.com");
        String convite = conviteDe(ana);
        Sessao beto = entrarComConvite(convite, "Beto", "beto@email.com");
        Sessao carla = entrarComConvite(convite, "Carla", "carla@email.com");

        enviar("/api/casas/" + ana.casaId() + "/despesas", ana, despesa(ana.moradorId(), beto.moradorId()))
                .andExpect(status().isCreated());
        Long divisaoDoBeto = Long.valueOf(JsonPath.read(
                consultar(beto, "/api/eu/dividas").andReturn().getResponse().getContentAsString(),
                "$.euDevo[0].divisaoId").toString());
        String caminho = "/api/divisoes/" + divisaoDoBeto + "/pagamento";

        enviar(caminho, beto, null).andExpect(status().isForbidden());
        enviar(caminho, carla, null).andExpect(status().isNotFound());
        enviar(caminho, ana, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAGA"));
    }

    @Test
    void regraDeNegocioViolada422() throws Exception {
        Sessao ana = cadastrarCasa("Casa", "Ana", "ana@email.com");

        enviar("/api/casas/" + ana.casaId() + "/despesas", ana,
                "{\"descricao\":\"Sozinha\",\"valorTotal\":10.00,\"participantesIds\":[%d]}".formatted(ana.moradorId()))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---------- auxiliares ----------

    private String despesa(Long pagadorId, Long outroId) {
        return "{\"descricao\":\"Pizza\",\"valorTotal\":90.00,\"participantesIds\":[%d,%d]}".formatted(pagadorId, outroId);
    }

    private Sessao cadastrarCasa(String casa, String nome, String email) throws Exception {
        String corpo = "{\"nomeCasa\":\"%s\",\"nome\":\"%s\",\"email\":\"%s\",\"senha\":\"senha1234\"}"
                .formatted(casa, nome, email);
        return lerSessao(enviar("/api/auth/cadastro/casa", null, corpo).andExpect(status().isCreated()));
    }

    private Sessao entrarComConvite(String codigo, String nome, String email) throws Exception {
        String corpo = "{\"codigoConvite\":\"%s\",\"nome\":\"%s\",\"email\":\"%s\",\"senha\":\"senha1234\"}"
                .formatted(codigo, nome, email);
        return lerSessao(enviar("/api/auth/cadastro/convite", null, corpo).andExpect(status().isCreated()));
    }

    private String conviteDe(Sessao administrador) throws Exception {
        String corpo = consultar(administrador, "/api/casas/" + administrador.casaId() + "/convite")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.codigo");
    }

    private Sessao lerSessao(ResultActions resposta) throws Exception {
        String corpo = resposta.andReturn().getResponse().getContentAsString();
        return new Sessao(JsonPath.read(corpo, "$.token"),
                Long.valueOf(JsonPath.read(corpo, "$.casaId").toString()),
                Long.valueOf(JsonPath.read(corpo, "$.morador.id").toString()));
    }

    private ResultActions consultar(Sessao sessao, String caminho) throws Exception {
        return mvc.perform(get(caminho).header("Authorization", "Bearer " + sessao.token()));
    }

    private ResultActions enviar(String caminho, Sessao sessao, String json) throws Exception {
        var requisicao = post(caminho);
        if (sessao != null) {
            requisicao.header("Authorization", "Bearer " + sessao.token());
        }
        if (json != null) {
            requisicao.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(requisicao);
    }
}
