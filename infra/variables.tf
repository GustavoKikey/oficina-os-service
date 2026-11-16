variable "ambiente" {
  description = "Ambiente lógico (hom | prod). Prefixa os parâmetros no SSM."
  type        = string

  validation {
    condition     = contains(["hom", "prod"], var.ambiente)
    error_message = "Ambiente deve ser 'hom' ou 'prod'."
  }
}

variable "regiao" {
  description = "Região AWS. O Learner Lab só libera us-east-1."
  type        = string
  default     = "us-east-1"
}

variable "projeto" {
  description = "Nome do projeto, usado em nomes de recurso e no path do SSM."
  type        = string
  default     = "oficina"
}

variable "versao_postgres" {
  description = "Major do PostgreSQL (o RDS escolhe a minor). 16, como nos testes."
  type        = string
  default     = "16"
}

variable "classe_instancia" {
  description = "Classe da instância. db.t3.micro: a menor, e cabe no crédito do lab."
  type        = string
  default     = "db.t3.micro"
}

variable "armazenamento_gb" {
  description = "Armazenamento em GB (20 é o mínimo do gp3)."
  type        = number
  default     = 20
}

variable "nome_banco" {
  description = "Nome do banco — o mesmo do docker-compose."
  type        = string
  default     = "oficina_os"
}

variable "usuario_master" {
  description = "Usuário master. 'admin' e 'postgres' são reservados pelo RDS."
  type        = string
  default     = "oficina"
}

variable "dias_backup" {
  description = "Retenção de backup automático. 1 dia: o ambiente do lab é recriado a cada sessão."
  type        = number
  default     = 1
}

variable "protecao_exclusao" {
  description = "Deletion protection do RDS. Desligada: o ambiente do lab é destruído ao fim de cada sessão."
  type        = bool
  default     = false
}
