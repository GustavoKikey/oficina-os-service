# =====================================================================
# Identidade — chaves do JWT e token da rota interna
# ---------------------------------------------------------------------
# Par de chaves RSA gerado aqui, a cada ambiente: substitui a chave que a
# fase 3 deixou versionada no repositório (e que por isso era considerada
# comprometida). A privada fica no Secrets Manager — só o OS Service (que
# emite tokens de funcionário) e a Lambda (tokens de cliente) a leem. A
# pública vai para o SSM: Execução e Billing só validam.
#
# O token interno protege GET /internal/clientes (ADR 011). A rota só é
# alcançável pelo load balancer interno, dentro da VPC; o token é a
# segunda camada.
# =====================================================================

resource "tls_private_key" "jwt" {
  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "aws_secretsmanager_secret" "jwt" {
  name                    = "${local.nome}-jwt"
  description             = "Chave privada que assina os JWT da oficina (OS Service e Lambda)"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id = aws_secretsmanager_secret.jwt.id
  secret_string = jsonencode({
    # PKCS#8 ("BEGIN PRIVATE KEY"): o formato que o smallrye-jwt lê.
    privateKey = tls_private_key.jwt.private_key_pem_pkcs8
  })
}

resource "random_password" "token_interno" {
  length  = 48
  special = false # vai em header HTTP
}

resource "aws_ssm_parameter" "token_interno" {
  name        = "${local.prefixo_ssm}/os/token-interno"
  description = "X-Internal-Token de GET /internal/clientes (OS Service e Lambda)"
  type        = "SecureString"
  value       = random_password.token_interno.result
  overwrite   = true
}
