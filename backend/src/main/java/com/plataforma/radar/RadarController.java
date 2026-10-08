package com.plataforma.radar;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/radar")
public class RadarController {
    private final RadarService service;

    public RadarController(RadarService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> dados() {
        return service.dados();
    }

    @PostMapping(value = "/comandos", consumes = "application/json")
    public Map<String, Object> comando(
            @RequestHeader("Idempotency-Key") UUID chave, @RequestBody JsonNode body) {
        return service.comando(chave, body);
    }

    @PostMapping(value = "/produtos/{id}/imagens", consumes = "multipart/form-data")
    public Map<String, Object> enviarImagem(
            @PathVariable UUID id, @RequestParam("arquivo") MultipartFile arquivo)
            throws IOException {
        UUID imagem = service.adicionarImagem(id, arquivo.getBytes(), arquivo.getContentType());
        return Map.of("id", imagem, "mensagem", "Imagem adicionada.");
    }

    @GetMapping("/imagens/{id}")
    public ResponseEntity<byte[]> imagem(@PathVariable UUID id) {
        var img = service.imagem(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType((String) img.get("tipo_conteudo")))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .body((byte[]) img.get("dados"));
    }

    @DeleteMapping("/imagens/{id}")
    public Map<String, Object> removerImagem(@PathVariable UUID id) {
        service.removerImagem(id);
        return Map.of("mensagem", "Imagem removida.");
    }

    @PostMapping("/imagens/{id}/principal")
    public Map<String, Object> imagemPrincipal(@PathVariable UUID id) {
        service.tornarImagemPrincipal(id);
        return Map.of("mensagem", "Imagem principal definida.");
    }

    @GetMapping("/clientes/{id}")
    public Map<String, Object> cliente(@PathVariable UUID id) {
        return service.cliente(id);
    }

    @GetMapping("/clientes/etiquetas")
    public java.util.List<Map<String, Object>> etiquetas(@RequestParam java.util.List<UUID> ids) {
        return service.etiquetas(ids);
    }

    @PostMapping(value = "/vendedores/{id}/senha", consumes = "application/json")
    public Map<String, Object> senhaVendedor(@PathVariable UUID id, @RequestBody JsonNode body) {
        service.alterarSenhaVendedor(
                id, body.path("senha").asText(null), body.path("confirmacao").asText(null));
        return Map.of("mensagem", "Senha de acesso alterada.");
    }

    @PostMapping(value = "/usuarios", consumes = "application/json")
    public Map<String, Object> criarUsuario(@RequestBody JsonNode body) {
        UUID id = service.criarUsuario(body);
        return Map.of("id", id, "mensagem", "Usuário criado. Passe o e-mail e a senha para a pessoa.");
    }

    @PostMapping(value = "/usuarios/{id}/senha", consumes = "application/json")
    public Map<String, Object> senhaUsuario(@PathVariable UUID id, @RequestBody JsonNode body) {
        service.alterarSenhaUsuario(
                id, body.path("senha").asText(null), body.path("confirmacao").asText(null));
        return Map.of("mensagem", "Senha alterada.");
    }

    @PostMapping(value = "/empresa/logo", consumes = "multipart/form-data")
    public Map<String, Object> enviarLogo(@RequestParam("arquivo") MultipartFile arquivo)
            throws IOException {
        service.salvarLogo(arquivo.getBytes(), arquivo.getContentType());
        return Map.of("mensagem", "Logo atualizado.");
    }

    @DeleteMapping("/empresa/logo")
    public Map<String, Object> removerLogo() {
        service.removerLogo();
        return Map.of("mensagem", "Logo removido.");
    }

    @GetMapping("/empresa/logo")
    public ResponseEntity<byte[]> logo() {
        var l = service.logo();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType((String) l.get("logo_tipo")))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body((byte[]) l.get("logo"));
    }

    @GetMapping("/produtos/{id}/historico")
    public Map<String, Object> historicoProduto(@PathVariable UUID id) {
        return service.historicoProduto(id);
    }

    @GetMapping("/vendedores/{id}")
    public Map<String, Object> vendedor(@PathVariable UUID id) {
        return service.vendedor(id);
    }

    @PostMapping(value = "/clientes/{id}/anexos", consumes = "multipart/form-data")
    public Map<String, Object> enviarAnexo(
            @PathVariable UUID id, @RequestParam("arquivo") MultipartFile arquivo)
            throws IOException {
        UUID anexo =
                service.adicionarAnexo(
                        id,
                        arquivo.getOriginalFilename(),
                        arquivo.getContentType(),
                        arquivo.getBytes());
        return Map.of("id", anexo, "mensagem", "Anexo adicionado.");
    }

    // Anexo sempre baixa (nunca abre inline) e não fica em cache: pode ter dado pessoal.
    @GetMapping("/anexos/{id}")
    public ResponseEntity<byte[]> anexo(@PathVariable UUID id) {
        var a = service.anexo(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename((String) a.get("nome_arquivo"), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .cacheControl(CacheControl.noStore())
                .body((byte[]) a.get("dados"));
    }

    @DeleteMapping("/anexos/{id}")
    public Map<String, Object> removerAnexo(@PathVariable UUID id) {
        service.removerAnexo(id);
        return Map.of("mensagem", "Anexo removido.");
    }

    @GetMapping("/relatorios")
    public Map<String, Object> relatorios(
            @RequestParam(required = false) String de, @RequestParam(required = false) String ate) {
        return service.relatorios(de, ate);
    }

    @GetMapping("/relatorios/fonte")
    public Map<String, Object> relatorioFonte(
            @RequestParam String nome,
            @RequestParam(required = false) String de,
            @RequestParam(required = false) String ate) {
        return service.relatorioFonte(nome, de, ate);
    }

    @PostMapping(value = "/perguntar", consumes = "application/json")
    public Map<String, Object> perguntar(@RequestBody JsonNode body) {
        return service.perguntar(body.path("texto").asText(""));
    }
}
