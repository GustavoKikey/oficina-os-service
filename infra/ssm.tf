# =====================================================================
# Contrato publicado por este serviço
# ---------------------------------------------------------------------
#   /oficina/{env}/os/db/jdbc-url            -> cd.yml deste repo
#   /oficina/{env}/os/db/secret-arn          -> cd.yml deste repo
#   /oficina/{env}/jwt/public-key            -> cd.yml dos três serviços
#   /oficina/{env}/jwt/private-key-secret-arn-> cd.yml deste repo, Lambda
#   /oficina/{env}/os/token-interno          -> cd.yml deste repo, Lambda
#   /oficina/{env}/os/url-interna            -> publicado pelo cd.yml
#                                               (só existe depois do deploy)
# =====================================================================

resource "aws_ssm_parameter" "db_jdbc_url" {
  name        = "${local.prefixo_ssm}/os/db/jdbc-url"
  description = "URL JDBC do banco do OS Service"
  type        = "String"
  value       = "jdbc:postgresql://${aws_db_instance.este.address}:${aws_db_instance.este.port}/${var.nome_banco}"
  overwrite   = true
}

resource "aws_ssm_parameter" "db_secret_arn" {
  name        = "${local.prefixo_ssm}/os/db/secret-arn"
  description = "ARN do secret com usuario e senha do banco do OS Service"
  type        = "String"
  value       = aws_secretsmanager_secret.banco.arn
  overwrite   = true
}

resource "aws_ssm_parameter" "jwt_public_key" {
  name        = "${local.prefixo_ssm}/jwt/public-key"
  description = "Chave publica (PEM) que valida os JWT da oficina"
  type        = "String"
  value       = tls_private_key.jwt.public_key_pem
  overwrite   = true
}

resource "aws_ssm_parameter" "jwt_private_key_secret_arn" {
  name        = "${local.prefixo_ssm}/jwt/private-key-secret-arn"
  description = "ARN do secret com a chave privada do JWT"
  type        = "String"
  value       = aws_secretsmanager_secret.jwt.arn
  overwrite   = true
}
