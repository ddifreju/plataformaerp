package com.plataforma.radar;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice(assignableTypes = RadarController.class)
public class RadarErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(
                        Map.of(
                                "mensagem",
                                e.getReason() == null ? "Operação inválida." : e.getReason()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflito() {
        return ResponseEntity.status(409)
                .body(
                        Map.of(
                                "mensagem",
                                "Registro duplicado ou vínculo inválido. Revise os dados e tente"
                                        + " novamente."));
    }
}
