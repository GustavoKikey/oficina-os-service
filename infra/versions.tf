# =====================================================================
# Infraestrutura do OS Service (fase 4)
# ---------------------------------------------------------------------
# O que é só deste serviço: o banco PostgreSQL, o par de chaves que
# assina os JWT (o OS Service emite os tokens de funcionário e a Lambda,
# os de cliente) e o token da rota interna /internal/clientes.
#
# Depende da plataforma (oficina-infra-k8s), lida pelo contrato no SSM.
# Ordem: oficina-infra-k8s -> [este] -> deploy (cd.yml) -> oficina-auth-lambda
# =====================================================================
terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Mesmo bucket de state da plataforma; a chave (os-service/<ambiente>/...)
  # chega por -backend-config no init. Ver scripts/aplicar.sh do
  # oficina-infra-k8s e o workflow infra.yml.
  backend "s3" {
    region  = "us-east-1"
    encrypt = true
  }
}

provider "aws" {
  region = var.regiao

  default_tags {
    tags = {
      Projeto   = var.projeto
      Ambiente  = var.ambiente
      Servico   = "os-service"
      ManagedBy = "terraform"
    }
  }
}
