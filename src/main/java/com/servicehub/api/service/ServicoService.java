package com.servicehub.api.service;

import com.servicehub.api.dto.ServicoRequest;
import com.servicehub.api.dto.ServicoResponse;
import com.servicehub.api.entity.Servico;
import com.servicehub.api.exception.RecursoNaoEncontradoException;
import com.servicehub.api.repository.ServicoRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoService {

    private final ServicoRepository repository;

    public ServicoService(ServicoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ServicoResponse criar(ServicoRequest request) {
        Servico salvo = repository.save(request.paraEntidade());
        return ServicoResponse.de(salvo);
    }

    @Transactional(readOnly = true)
    public List<ServicoResponse> listar() {
        return repository.findAll(Sort.by("id")).stream()
                .map(ServicoResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServicoResponse buscarPorId(Long id) {
        return ServicoResponse.de(buscarEntidade(id));
    }

    @Transactional
    public ServicoResponse atualizar(Long id, ServicoRequest request) {
        Servico servico = buscarEntidade(id);
        request.aplicarEm(servico);
        return ServicoResponse.de(repository.saveAndFlush(servico));
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscarEntidade(id));
    }

    private Servico buscarEntidade(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço", id));
    }
}
