# ── Network Outputs ───────────────────────────────────────────────────────────

output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}

output "public_subnet_ids" {
  description = "Public subnet IDs"
  value       = module.vpc.public_subnet_ids
}

output "private_subnet_ids" {
  description = "Private subnet IDs"
  value       = module.vpc.private_subnet_ids
}

# ── RDS Outputs ────────────────────────────────────────────────────────────────

output "rds_endpoint" {
  description = "RDS database endpoint (host:port)"
  value       = module.rds.rds_endpoint
}

output "rds_address" {
  description = "RDS database hostname"
  value       = module.rds.rds_address
}

output "rds_id" {
  description = "RDS instance ID"
  value       = module.rds.rds_id
}

# ── EC2 Outputs ────────────────────────────────────────────────────────────────

output "alb_dns_name" {
  description = "DNS name of the Application Load Balancer for EC2"
  value       = module.ec2.alb_dns_name
}

output "alb_arn" {
  description = "ARN of the Application Load Balancer"
  value       = module.ec2.alb_arn
}

output "ec2_instance_id" {
  description = "EC2 instance ID"
  value       = module.ec2.ec2_instance_id
}

# ── EKS Outputs ────────────────────────────────────────────────────────────────

output "eks_cluster_id" {
  description = "EKS cluster ID"
  value       = module.eks.cluster_id
}

output "eks_cluster_endpoint" {
  description = "EKS cluster endpoint"
  value       = module.eks.cluster_endpoint
}

output "eks_cluster_certificate_authority" {
  description = "EKS cluster certificate authority (base64 encoded)"
  value       = module.eks.cluster_certificate_authority
  sensitive   = true
}

output "eks_oidc_provider_arn" {
  description = "ARN of the OIDC provider for EKS (for IRSA)"
  value       = module.eks.oidc_provider_arn
}

# ── Connection Strings ─────────────────────────────────────────────────────────

output "database_connection_string" {
  description = "Database connection string for Spring Boot"
  value       = "jdbc:mysql://${module.rds.rds_address}:3306/${var.db_name}"
  sensitive   = true
}

output "ec2_endpoint" {
  description = "EC2 application endpoint via ALB"
  value       = "http://${module.ec2.alb_dns_name}"
}

output "eks_cluster_name" {
  description = "EKS cluster name for kubectl configuration"
  value       = "wallet-${var.environment}-cluster"
}
