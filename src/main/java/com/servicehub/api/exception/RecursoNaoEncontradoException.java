package com.servicehub.api.exception;

public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String recurso, Long id) {
        super("%s com id %d não encontrado".formatted(recurso, id));
    }
}
