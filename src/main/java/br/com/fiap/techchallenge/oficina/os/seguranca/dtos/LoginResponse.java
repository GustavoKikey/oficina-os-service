package br.com.fiap.techchallenge.oficina.os.seguranca.dtos;

/** Resposta do login — nunca contém a senha (nem hashada nem em texto puro). */
public record LoginResponse(String accessToken, long expiresIn, String role) {}
