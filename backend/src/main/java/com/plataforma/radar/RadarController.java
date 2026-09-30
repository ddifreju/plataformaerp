package com.plataforma.radar;

import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/radar")
public class RadarController {
 private final RadarService service;
 public RadarController(RadarService service){this.service=service;}
 @GetMapping public Map<String,Object> dados(){return service.dados();}
 @PostMapping(value="/comandos",consumes="application/json")
 public Map<String,Object> comando(@RequestHeader("Idempotency-Key") UUID chave,@RequestBody JsonNode body){return service.comando(chave,body);}
 @PostMapping(value="/perguntar",consumes="application/json")
 public Map<String,Object> perguntar(@RequestBody JsonNode body){return service.perguntar(body.path("texto").asText(""));}
}
