output "db_endpoint" {
  description = "Host do RDS do OS Service."
  value       = aws_db_instance.este.address
}

output "jdbc_url" {
  description = "URL JDBC do banco do OS Service."
  # Montada a partir do RDS, não lida do parâmetro do SSM: o valor de um
  # aws_ssm_parameter é marcado como sensível, e o plan recusa expô-lo num output.
  value = "jdbc:postgresql://${aws_db_instance.este.address}:${aws_db_instance.este.port}/${var.nome_banco}"
}

output "db_secret_arn" {
  description = "ARN do secret com as credenciais do banco."
  value       = aws_secretsmanager_secret.banco.arn
}

output "jwt_secret_arn" {
  description = "ARN do secret com a chave privada do JWT."
  value       = aws_secretsmanager_secret.jwt.arn
}
