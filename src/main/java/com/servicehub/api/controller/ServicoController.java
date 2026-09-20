package com.servicehub.api.controller;

import com.servicehub.api.dto.ServicoRequest;
import com.servicehub.api.dto.ServicoResponse;
import com.servicehub.api.service.ServicoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(path = "/api/v1/servicos", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Serviços",
        description = "Operações de cadastro, consulta, atualização e exclusão de serviços oferecidos na plataforma ServiceHub.")
public class ServicoController {

    private final ServicoService servicoService;

    public ServicoController(ServicoService servicoService) {
        this.servicoService = servicoService;
    }

    @Operation(
            summary = "Cadastrar um novo serviço",
            description = "Cria um serviço a partir dos dados informados no corpo da requisição. "
                    + "Nome, categoria, preço e duração estimada são obrigatórios; a descrição é opcional. "
                    + "O identificador e as datas de criação e atualização são gerados pela API. "
                    + "Em caso de sucesso, o cabeçalho Location contém a URI do novo recurso.")
    @ApiResponse(responseCode = "201", description = "Serviço criado com sucesso",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ServicoResponse.class)))
    @ApiResponse(responseCode = "400",
            description = "Dados inválidos: campo obrigatório ausente, valor fora dos limites ou JSON malformado",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Dados inválidos", value = """
                            {
                              "type": "about:blank",
                              "title": "Dados inválidos",
                              "status": 400,
                              "detail": "Um ou mais campos são inválidos",
                              "instance": "/api/v1/servicos",
                              "erros": [
                                { "campo": "preco", "mensagem": "O preço deve ser maior que zero" }
                              ]
                            }""")))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServicoResponse> criar(@Valid @RequestBody ServicoRequest request) {
        ServicoResponse criado = servicoService.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(criado.id())
                .toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @Operation(
            summary = "Listar todos os serviços",
            description = "Retorna a lista com todos os serviços cadastrados, ordenados pelo identificador. "
                    + "Quando não há serviços cadastrados, retorna uma lista vazia com status 200.")
    @ApiResponse(responseCode = "200", description = "Lista de serviços (pode estar vazia)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = ServicoResponse.class))))
    @GetMapping
    public List<ServicoResponse> listar() {
        return servicoService.listar();
    }

    @Operation(
            summary = "Buscar um serviço por ID",
            description = "Retorna os dados completos do serviço identificado pelo ID informado na URI.")
    @ApiResponse(responseCode = "200", description = "Serviço encontrado",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ServicoResponse.class)))
    @ApiResponse(responseCode = "400", description = "ID em formato inválido (não numérico)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Nenhum serviço encontrado com o ID informado",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Serviço inexistente", value = """
                            {
                              "type": "about:blank",
                              "title": "Recurso não encontrado",
                              "status": 404,
                              "detail": "Serviço com id 99 não encontrado",
                              "instance": "/api/v1/servicos/99"
                            }""")))
    @GetMapping("/{id}")
    public ServicoResponse buscarPorId(
            @Parameter(description = "Identificador do serviço", example = "1") @PathVariable Long id) {
        return servicoService.buscarPorId(id);
    }

    @Operation(
            summary = "Atualizar um serviço",
            description = "Substitui todos os dados do serviço identificado pelo ID (semântica do PUT): "
                    + "o corpo deve conter a representação completa, com os mesmos campos obrigatórios do cadastro. "
                    + "Se a descrição for omitida, ela é limpa. A data de criação é preservada e a data de "
                    + "atualização é renovada.")
    @ApiResponse(responseCode = "200", description = "Serviço atualizado com sucesso",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ServicoResponse.class)))
    @ApiResponse(responseCode = "400", description = "Dados inválidos ou ID em formato inválido",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Nenhum serviço encontrado com o ID informado",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ServicoResponse atualizar(
            @Parameter(description = "Identificador do serviço a ser atualizado", example = "1") @PathVariable Long id,
            @Valid @RequestBody ServicoRequest request) {
        return servicoService.atualizar(id, request);
    }

    @Operation(
            summary = "Excluir um serviço",
            description = "Remove permanentemente o serviço identificado pelo ID. "
                    + "Em caso de sucesso, a resposta não possui corpo (204 No Content).")
    @ApiResponse(responseCode = "204", description = "Serviço excluído com sucesso (sem corpo na resposta)",
            content = @Content)
    @ApiResponse(responseCode = "400", description = "ID em formato inválido (não numérico)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Nenhum serviço encontrado com o ID informado",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(
            @Parameter(description = "Identificador do serviço a ser excluído", example = "1") @PathVariable Long id) {
        servicoService.excluir(id);
    }
}
