package br.com.fiap.agendamento.controller;

import br.com.fiap.agendamento.dto.LoginRequest;
import br.com.fiap.agendamento.dto.LoginResponse;
import br.com.fiap.agendamento.service.AutenticacaoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unico endpoint REST do servico: a troca de credenciais por um JWT.
 * Todo o restante da API e exposto via GraphQL.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AutenticacaoService autenticacaoService;

    public AuthController(AutenticacaoService autenticacaoService) {
        this.autenticacaoService = autenticacaoService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest requisicao) {
        return ResponseEntity.ok(autenticacaoService.autenticar(requisicao));
    }
}
