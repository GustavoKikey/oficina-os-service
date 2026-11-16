# =====================================================================
# Banco do OS Service — RDS PostgreSQL
# ---------------------------------------------------------------------
# Instância só deste serviço: ninguém mais tem a URL nem a senha. O
# schema vem do Flyway no startup do serviço; aqui nasce a instância vazia.
# =====================================================================

locals {
  prefixo_ssm = "/${var.projeto}/${var.ambiente}"
  nome        = "${var.projeto}-${var.ambiente}-os"
}

# Rede publicada pela plataforma (oficina-infra-k8s, ssm.tf).
data "aws_ssm_parameter" "subnet_ids" {
  name = "${local.prefixo_ssm}/rede/subnet-ids"
}

data "aws_ssm_parameter" "sg_banco_id" {
  name = "${local.prefixo_ssm}/rede/sg-banco-id"
}

resource "random_password" "banco" {
  length = 32
  # Conjunto que não quebra a URL JDBC.
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "aws_secretsmanager_secret" "banco" {
  name        = "${local.nome}-db"
  description = "Credenciais do banco do OS Service"
  # O Learner Lab não deixa reaproveitar o nome antes da janela de recuperação.
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "banco" {
  secret_id = aws_secretsmanager_secret.banco.id
  secret_string = jsonencode({
    username = var.usuario_master
    password = random_password.banco.result
  })
}

resource "aws_db_subnet_group" "este" {
  name       = local.nome
  subnet_ids = split(",", nonsensitive(data.aws_ssm_parameter.subnet_ids.value))
}

resource "aws_db_instance" "este" {
  identifier                 = local.nome
  engine                     = "postgres"
  engine_version             = var.versao_postgres
  auto_minor_version_upgrade = true
  instance_class             = var.classe_instancia

  db_name  = var.nome_banco
  username = var.usuario_master
  password = random_password.banco.result

  allocated_storage = var.armazenamento_gb
  storage_type      = "gp3"
  storage_encrypted = true

  db_subnet_group_name = aws_db_subnet_group.este.name
  # SG da plataforma: aceita 5432 só dos nós do cluster.
  vpc_security_group_ids = [nonsensitive(data.aws_ssm_parameter.sg_banco_id.value)]
  publicly_accessible    = false

  backup_retention_period = var.dias_backup
  skip_final_snapshot     = true
  deletion_protection     = var.protecao_exclusao
  # Multi-AZ dobraria o custo: débito declarado (arquitetura.md, restrições).
  multi_az          = false
  apply_immediately = true

  enabled_cloudwatch_logs_exports = ["postgresql"]
}
