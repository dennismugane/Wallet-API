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


# ── EKS Outputs ───────────────────────────────────────────────────────────────

output "eks_cluster_name" {
  description = "EKS cluster name"
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  description = "EKS cluster endpoint"
  value       = module.eks.cluster_endpoint
}

output "eks_cluster_security_group_id" {
  description = "EKS cluster security group id"
  value       = module.eks.cluster_security_group_id
}

output "eks_node_security_group_id" {
  description = "EKS node security group id"
  value       = module.eks.node_security_group_id
}


