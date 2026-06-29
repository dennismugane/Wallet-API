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
  }

  # Remote state: S3 bucket + DynamoDB lock table
  # Reusing ecommerce S3 bucket and DynamoDB table for cost efficiency
  backend "s3" {
    bucket         = "dennism-terraform-state-dev-2026"     # shared with ecommerce
    key            = "wallet/prod/terraform.tfstate"        # unique path for wallet
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "ecommerce-tfstate-lock"              # shared with ecommerce
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

# ── Modules ────────────────────────────────────────────────────────────────────

module "vpc" {
  source      = "./modules/vpc"
  environment = var.environment
  vpc_cidr    = var.vpc_cidr
}

module "secrets" {
  source      = "./modules/secrets"
  environment = var.environment
  db_password = var.db_password
  jwt_secret  = var.jwt_secret
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
  ec2_security_group  = module.ec2.backend_sg_id
  eks_security_group  = module.eks.eks_nodes_security_group_id
  db_name             = var.db_name
  db_username         = var.db_username
  db_password         = var.db_password
  db_instance_class   = var.db_instance_class
}

module "ec2" {
  source                  = "./modules/ec2"
  environment             = var.environment
  vpc_id                  = module.vpc.vpc_id
  public_subnet_ids       = module.vpc.public_subnet_ids
  instance_type           = var.ec2_instance_type
  key_name                = var.key_pair_name
  iam_instance_profile    = module.iam.ec2_instance_profile_name
  rds_endpoint            = module.rds.rds_endpoint
  db_name                 = var.db_name
  db_username             = var.db_username
  db_password_secret_name = module.secrets.db_password_secret_name
  jwt_secret_name         = module.secrets.jwt_secret_name
  docker_hub_username     = var.docker_hub_username
  docker_hub_password     = var.docker_hub_password
  image_tag               = var.image_tag
}

module "eks" {
  source                 = "./modules/eks"
  environment            = var.environment
  vpc_id                 = module.vpc.vpc_id
  private_subnet_ids     = module.vpc.private_subnet_ids
  cluster_role_arn       = module.iam.eks_cluster_role_arn
  node_role_arn          = module.iam.eks_node_role_arn
  db_endpoint            = module.rds.rds_endpoint
  db_name                = var.db_name
  db_username            = var.db_username
  db_password_secret_name = module.secrets.db_password_secret_name
  jwt_secret_name        = module.secrets.jwt_secret_name
  docker_hub_username    = var.docker_hub_username
  image_tag              = var.image_tag
  node_instance_type     = var.eks_node_instance_type
  desired_node_count     = var.eks_desired_node_count
  min_node_count         = var.eks_min_node_count
  max_node_count         = var.eks_max_node_count
}
