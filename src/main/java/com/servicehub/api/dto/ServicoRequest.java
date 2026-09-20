package com.servicehub.api.dto;

import com.servicehub.api.entity.Servico;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(name = "ServicoRequest", description = "Dados para criação ou atualização completa de um serviço")
public record ServicoRequest(

        @Schema(description = "Nome do serviço", example = "Instalação de ar-condicionado",
                minLength = 3, maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 3, max = 100, message = "O nome deve ter entre 3 e 100 caracteres")
        String nome,

        @Schema(description = "Descrição detalhada do serviço (opcional)",
                example = "Instalação de split de até 12.000 BTUs, com suporte e tubulação de até 3 metros",
                maxLength = 500, nullable = true)
        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres")
        String descricao,

        @Schema(description = "Categoria em que o serviço se enquadra", example = "Climatização",
                maxLength = 60, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A categoria é obrigatória")
        @Size(max = 60, message = "A categoria deve ter no máximo 60 caracteres")
        String categoria,

        @Schema(description = "Preço do serviço em reais (R$), com até 2 casas decimais",
                example = "350.00", minimum = "0.01", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O preço é obrigatório")
        @DecimalMin(value = "0.01", message = "O preço deve ser maior que zero")
        @DecimalMax(value = "99999999.99", message = "O preço excede o valor máximo permitido")
        @Digits(integer = 8, fraction = 2, message = "O preço deve ter no máximo 2 casas decimais")
        BigDecimal preco,

        @Schema(description = "Duração estimada do serviço, em minutos", example = "120",
                minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A duração estimada é obrigatória")
        @Min(value = 1, message = "A duração estimada deve ser de pelo menos 1 minuto")
        @Max(value = 100000, message = "A duração estimada excede o valor máximo permitido")
        Integer duracaoMinutos) {

    public Servico paraEntidade() {
        return new Servico(nome.strip(), descricao, categoria.strip(), preco, duracaoMinutos);
    }

    public void aplicarEm(Servico servico) {
        servico.setNome(nome.strip());
        servico.setDescricao(descricao);
        servico.setCategoria(categoria.strip());
        servico.setPreco(preco);
        servico.setDuracaoMinutos(duracaoMinutos);
    }
}
