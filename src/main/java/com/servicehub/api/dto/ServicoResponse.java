package com.servicehub.api.dto;

import com.servicehub.api.entity.Servico;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "ServicoResponse", description = "Representação de um serviço cadastrado")
public record ServicoResponse(

        @Schema(description = "Identificador único do serviço", example = "1")
        Long id,

        @Schema(description = "Nome do serviço", example = "Instalação de ar-condicionado")
        String nome,

        @Schema(description = "Descrição detalhada do serviço",
                example = "Instalação de split de até 12.000 BTUs, com suporte e tubulação de até 3 metros",
                nullable = true)
        String descricao,

        @Schema(description = "Categoria do serviço", example = "Climatização")
        String categoria,

        @Schema(description = "Preço do serviço em reais (R$)", example = "350.00")
        BigDecimal preco,

        @Schema(description = "Duração estimada, em minutos", example = "120")
        Integer duracaoMinutos,

        @Schema(description = "Data e hora de criação (UTC, ISO-8601)", example = "2026-09-20T15:04:05.123Z")
        Instant criadoEm,

        @Schema(description = "Data e hora da última atualização (UTC, ISO-8601)", example = "2026-09-20T15:04:05.123Z")
        Instant atualizadoEm) {

    public static ServicoResponse de(Servico servico) {
        return new ServicoResponse(
                servico.getId(),
                servico.getNome(),
                servico.getDescricao(),
                servico.getCategoria(),
                servico.getPreco(),
                servico.getDuracaoMinutos(),
                servico.getCriadoEm(),
                servico.getAtualizadoEm());
    }
}
