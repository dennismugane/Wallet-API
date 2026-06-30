variable "aws_region" {
  description = "AWS region to deploy resources into"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment (prod, staging, dev)"
  type        = string
  default     = "prod"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

# ── EC2 Configuration ──────────────────────────────────────────────────────────

variable "ec2_instance_type" {
  description = "EC2 instance type for the backend application"
  type        = string
  default     = "t3.small"
}

variable "key_pair_name" {
  description = "Name of the EC2 key pair to use for SSH access"
  type        = string
  default     = "wallet-key"
}

variable "key_pair_public_key_path" {
  description = "Local path to the SSH public key file to import into AWS as an EC2 key pair"
  type        = string
  default     = "wallet-key.pub"
}

variable "docker_hub_username" {
  description = "Docker Hub username for pulling images"
  type        = string
}

variable "docker_hub_password" {
  description = "Docker Hub password/token for authentication"
  type        = string
  sensitive   = true
}

variable "image_tag" {
  description = "Docker image tag to deploy (e.g. latest, v1.0.0, or git SHA)"
  type        = string
  default     = "latest"
}

# ── RDS Configuration ──────────────────────────────────────────────────────────

variable "db_name" {
  description = "Name of the initial database to create"
  type        = string
  default     = "wallet"
}

variable "db_username" {
  description = "Master username for the database"
  type        = string
  default     = "admin"
}

variable "db_password" {
  description = "Master password for the database (use TF_VAR_db_password env var)"
  type        = string
  sensitive   = true
}

variable "db_instance_class" {
  description = "RDS instance class (db.t3.micro, db.t3.small, etc.)"
  type        = string
  default     = "db.t3.micro"
}

# ── Secrets ────────────────────────────────────────────────────────────────────

variable "jwt_secret" {
  description = "JWT secret for authentication (use TF_VAR_jwt_secret env var)"
  type        = string
  sensitive   = true
}

# ── EKS Configuration ──────────────────────────────────────────────────────────

variable "eks_node_instance_type" {
  description = "Instance type for EKS worker nodes"
  type        = string
  default     = "t3.medium"
}

variable "eks_desired_node_count" {
  description = "Desired number of worker nodes in the EKS cluster"
  type        = number
  default     = 2
}

variable "eks_min_node_count" {
  description = "Minimum number of worker nodes"
  type        = number
  default     = 2
}

variable "eks_max_node_count" {
  description = "Maximum number of worker nodes for auto-scaling"
  type        = number
  default     = 5
}
