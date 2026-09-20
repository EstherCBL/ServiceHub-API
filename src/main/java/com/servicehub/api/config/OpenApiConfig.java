package com.servicehub.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import java.util.TreeMap;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI servicehubOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ServiceHub API")
                        .version("v1")
                        .description("API RESTful do ServiceHub, plataforma de catálogo de serviços. "
                                + "Nesta etapa (Checkpoint), o recurso **Serviços** possui CRUD completo. "
                                + "Os erros seguem o padrão Problem Details (RFC 9457)."));
    }

    /**
     * Ordena os caminhos alfabeticamente para que a coleção ({@code /servicos}) apareça
     * antes do item ({@code /servicos/{id}}) no Swagger UI.
     */
    @Bean
    public OpenApiCustomizer ordenarCaminhos() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            Paths ordenados = new Paths();
            new TreeMap<>(openApi.getPaths()).forEach(ordenados::addPathItem);
            openApi.setPaths(ordenados);
        };
    }
}
