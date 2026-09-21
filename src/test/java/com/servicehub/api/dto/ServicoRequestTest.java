package com.servicehub.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServicoRequestTest {

    private static ValidatorFactory fabrica;

    @BeforeAll
    static void criarValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void fecharValidador() {
        fabrica.close();
    }

    private static ServicoRequest comNome(String nome) {
        return new ServicoRequest(nome, null, "Reformas", new BigDecimal("200.00"), 240);
    }

    private static Set<ConstraintViolation<ServicoRequest>> validar(ServicoRequest request) {
        return fabrica.getValidator().validate(request);
    }

    @Test
    @DisplayName("remove os espaços das bordas de nome e categoria na construção")
    void normalizaTexto() {
        ServicoRequest request =
                new ServicoRequest("  Pintura de parede  ", null, " Reformas ", new BigDecimal("200.00"), 240);

        assertThat(request.nome()).isEqualTo("Pintura de parede");
        assertThat(request.categoria()).isEqualTo("Reformas");
    }

    @Test
    @DisplayName("aceita nome e categoria nulos sem lançar exceção (a obrigatoriedade é validada depois)")
    void aceitaNulos() {
        ServicoRequest request = new ServicoRequest(null, null, null, null, null);

        assertThat(request.nome()).isNull();
        assertThat(request.categoria()).isNull();
        assertThat(validar(request)).extracting(v -> v.getPropertyPath().toString())
                .contains("nome", "categoria", "preco", "duracaoMinutos");
    }

    @Test
    @DisplayName("rejeita nome com menos de 3 caracteres úteis, mesmo com espaços nas bordas (P1)")
    void rejeitaNomeCurtoComEspacosNasBordas() {
        Set<ConstraintViolation<ServicoRequest>> violacoes = validar(comNome("  ab "));

        assertThat(violacoes).hasSize(1);
        ConstraintViolation<ServicoRequest> violacao = violacoes.iterator().next();
        assertThat(violacao.getPropertyPath().toString()).isEqualTo("nome");
        assertThat(violacao.getMessage()).isEqualTo("O nome deve ter entre 3 e 100 caracteres");
    }

    @Test
    @DisplayName("aceita nome com exatamente 3 caracteres úteis cercado por espaços")
    void aceitaNomeNoLimiteMinimoComEspacos() {
        assertThat(validar(comNome("  abc "))).isEmpty();
    }

    @Test
    @DisplayName("aceita nome de 100 caracteres úteis cercado por espaços (o limite vale para o texto gravado)")
    void aceitaNomeNoLimiteMaximoComEspacos() {
        assertThat(validar(comNome("  " + "a".repeat(100) + "  "))).isEmpty();
    }

    @Test
    @DisplayName("rejeita nome só com espaços como obrigatório")
    void rejeitaNomeSoComEspacos() {
        assertThat(validar(comNome("     "))).extracting(ConstraintViolation::getMessage)
                .contains("O nome é obrigatório");
    }
}
