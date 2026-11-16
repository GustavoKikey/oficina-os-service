output "db_endpoint" {
  description = "Host do RDS do OS Service."
  value       = aws_db_instance.este.address
}

output "jdbc_url" {
  description = "URL JDBC do banco do OS Service."
  value       = aws_ssm_parameter.db_jdbc_url.value
}

output "db_secret_arn" {
  description = "ARN do secret com as credenciais do banco."
  value       = aws_secretsmanager_secret.banco.arn
}

output "jwt_secret_arn" {
  description = "ARN do secret com a chave privada do JWT."
  value       = aws_secretsmanager_secret.jwt.arn
}
