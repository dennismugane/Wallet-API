terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Remote state: S3 bucket + DynamoDB lock table
  backend "s3" {
    bucket         = "wallet-terraform-state-dev-2026"
    key            = "wallet/prod/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "wallet-tfstate-lock"
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "wallet"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

resource "aws_key_pair" "wallet_key" {
  key_name   = var.key_pair_name
  public_key = file(var.key_pair_public_key_path)
}

# ── Modules ────────────────────────────────────────────────────────────────────

module "vpc" {
  source      = "./modules/vpc"
  environment = var.environment
  vpc_cidr    = var.vpc_cidr
}

module "secrets" {
  source      = "./modules/secrets"
  environment = var.environment
  jwt_secret  = var.jwt_secret
  db_username = var.db_username
}

module "iam" {
  source                 = "./modules/iam"
  environment            = var.environment
  db_password_secret_arn = module.secrets.db_password_secret_arn
  jwt_secret_arn         = module.secrets.jwt_secret_arn
}

module "rds" {
  source              = "./modules/rds"
  environment         = var.environment
  vpc_id              = module.vpc.vpc_id
  private_subnet_ids  = module.vpc.private_subnet_ids
  db_name             = var.db_name
  db_username         = var.db_username
  db_password         = module.secrets.db_password
  db_instance_class   = var.db_instance_class
  eks_security_group_id = module.eks.node_security_group_id
}

module "eks" {
  source               = "./modules/eks"
  environment          = var.environment
  vpc_id               = module.vpc.vpc_id
  private_subnet_ids   = module.vpc.private_subnet_ids
  eks_node_instance_type = var.eks_node_instance_type
  eks_desired_node_count = var.eks_desired_node_count
  eks_min_node_count     = var.eks_min_node_count
  eks_max_node_count     = var.eks_max_node_count
}


# ── DB URL secret — lives here, not inside modules/secrets, ─────────────────
# because it needs module.rds's output, avoiding a circular dependency

resource "aws_secretsmanager_secret" "db_url" {
  name                    = "wallet-${var.environment}/db-url"
  recovery_window_in_days = 0

  tags = { Name = "wallet-${var.environment}-db-url" }
}

resource "aws_secretsmanager_secret_version" "db_url" {
  secret_id     = aws_secretsmanager_secret.db_url.id
  secret_string = "jdbc:postgresql://${module.rds.rds_endpoint}/${var.db_name}"
}

