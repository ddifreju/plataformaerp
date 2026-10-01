package com.plataforma.radar;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/** Validação de entrada compartilhada pelos serviços do Radar. Erros viram 400/403/404. */
final class RadarEntrada {

    private RadarEntrada() {}

    static void permitir(String papel, String... papeis) {
        if (!Set.of(papeis).contains(papel))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Seu cargo não permite esta ação.");
    }

    static void erro(String mensagem) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }

    static void confirmar(int linhasAlteradas) {
        if (linhasAlteradas == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado.");
    }

    static UUID id(JsonNode n, String campo) {
        try {
            return UUID.fromString(n.path(campo).asText());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador inválido.");
        }
    }

    static String texto(JsonNode n, String campo, int max) {
        String s = n.path(campo).asText("").trim();
        if (s.isBlank() || s.length() > max) erro("Confira o campo " + campo + ".");
        return s;
    }

    static String opcional(JsonNode n, String campo, int max) {
        String s = n.path(campo).asText("").trim();
        if (s.length() > max) erro("Confira o campo " + campo + ".");
        return s.isBlank() ? null : s;
    }

    static String email(JsonNode n) {
        String s = opcional(n, "email", 320);
        if (s != null && !s.matches("[^\\s@]+@[^\\s@]+")) erro("E-mail inválido.");
        return s == null ? null : s.toLowerCase();
    }

    static Integer inteiroOpcional(JsonNode n, String campo, int min, int max) {
        String s = n.path(campo).asText("").trim();
        if (s.isBlank()) return null;
        try {
            int v = Integer.parseInt(s);
            if (v < min || v > max) erro("Valor fora do limite: " + campo);
            return v;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Número inválido: " + campo);
        }
    }

    // Dinheiro: BigDecimal com escala 2, sem arredondamento silencioso (regra 2).
    static BigDecimal valor(JsonNode n, String campo) {
        try {
            BigDecimal v = new BigDecimal(n.path(campo).asText("0").trim());
            if (v.signum() < 0 || v.scale() > 2 || v.compareTo(new BigDecimal("999999999")) > 0)
                erro("Valor inválido: " + campo);
            return v.setScale(2);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor inválido: " + campo);
        }
    }

    static BigDecimal medidaOpcional(JsonNode n, String campo) {
        String s = n.path(campo).asText("").trim();
        if (s.isBlank()) return null;
        try {
            BigDecimal v = new BigDecimal(s);
            if (v.signum() <= 0 || v.scale() > 1 || v.compareTo(new BigDecimal("9999999")) > 0)
                erro("Medida inválida: " + campo);
            return v;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Medida inválida: " + campo);
        }
    }
}
