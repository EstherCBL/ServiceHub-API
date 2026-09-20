package com.servicehub.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.servicehub.api.dto.ServicoRequest;
import com.servicehub.api.dto.ServicoResponse;
import com.servicehub.api.entity.Servico;
import com.servicehub.api.exception.RecursoNaoEncontradoException;
import com.servicehub.api.repository.ServicoRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServicoServiceTest {

    @Mock
    private ServicoRepository repository;

    @InjectMocks
    private ServicoService service;

    private final ServicoRequest request =
            new ServicoRequest("  Pintura de parede  ", null, " Reformas ", new BigDecimal("200.00"), 240);

    @Test
    @DisplayName("criar remove espaços das bordas de nome e categoria antes de salvar")
    void criarNormalizaTexto() {
        when(repository.save(any(Servico.class))).thenAnswer(inv -> inv.getArgument(0));

        ServicoResponse resposta = service.criar(request);

        assertThat(resposta.nome()).isEqualTo("Pintura de parede");
        assertThat(resposta.categoria()).isEqualTo("Reformas");
        assertThat(resposta.preco()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("buscarPorId lança RecursoNaoEncontradoException quando o ID não existe")
    void buscarInexistente() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(42L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Serviço com id 42 não encontrado");
    }

    @Test
    @DisplayName("atualizar não salva nada quando o ID não existe")
    void atualizarInexistente() {
        when(repository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(7L, request))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("excluir não remove nada quando o ID não existe")
    void excluirInexistente() {
        when(repository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(7L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(repository, never()).delete(any());
    }
}
