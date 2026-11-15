package br.com.fiap.techchallenge.oficina.os.atendimento.dtos;

import java.util.UUID;

/**
 * O que a Lambda de autenticação precisa para decidir se emite o token — os mesmos
 * campos que ela lia da tabela na fase 3 (ADR 011).
 */
public record ClienteAutenticacaoResponse(UUID id, String nome, boolean ativo) {}
