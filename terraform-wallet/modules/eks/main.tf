variable "environment" {}
variable "vpc_id" {}
variable "private_subnet_ids" {}
variable "cluster_role_arn" {}
variable "node_role_arn" {}
variable "db_endpoint" {}
variable "db_name" {}
variable "db_username" {}
variable "db_password_secret_name" {}
variable "jwt_secret_name" {}
variable "docker_hub_username" {}
variable "image_tag" {}
variable "node_instance_type" {
  default = "t3.medium"
}
variable "desired_node_count" {
  default = 2
}
variable "min_node_count" {
  default = 2
}
variable "max_node_count" {
  default = 5
}

# ── Security Group for EKS ────────────────────────────────────────────────────

resource "aws_security_group" "eks_nodes" {
  name        = "wallet-${var.environment}-eks-nodes-sg"
  description = "Security group for EKS nodes"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]  # Tighten in production
  }

  ingress {
    from_port   = 0
    to_port     = 65535
    protocol    = "udp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "wallet-${var.environment}-eks-nodes-sg" }
}

# ── EKS Cluster ───────────────────────────────────────────────────────────────

resource "aws_eks_cluster" "wallet" {
  name            = "wallet-${var.environment}-cluster"
  role_arn        = var.cluster_role_arn
  version         = "1.28"  # Update to latest available version
  vpc_config {
    subnet_ids              = var.private_subnet_ids
    security_group_ids      = [aws_security_group.eks_nodes.id]
    endpoint_private_access = true
    endpoint_public_access  = true
  }

  enabled_cluster_log_types = ["api", "audit", "authenticator", "controllerManager", "scheduler"]

  depends_on = [aws_security_group.eks_nodes]

  tags = { Name = "wallet-${var.environment}-cluster" }
}

# ── CloudWatch Log Group for EKS ──────────────────────────────────────────────

resource "aws_cloudwatch_log_group" "eks" {
  name              = "/aws/eks/wallet-${var.environment}/cluster"
  retention_in_days = 7

  tags = { Name = "wallet-${var.environment}-eks-logs" }
}

# ── EKS Node Group ────────────────────────────────────────────────────────────

resource "aws_eks_node_group" "wallet" {
  cluster_name    = aws_eks_cluster.wallet.name
  node_group_name = "wallet-${var.environment}-ng"
  node_role_arn   = var.node_role_arn
  subnet_ids      = var.private_subnet_ids
  version         = aws_eks_cluster.wallet.version

  instance_types = [var.node_instance_type]

  scaling_config {
    desired_size = var.desired_node_count
    max_size     = var.max_node_count
    min_size     = var.min_node_count
  }

  update_config {
    max_unavailable_percentage = 25
  }

  remote_access {
    ec2_ssh_key = var.environment
  }

  tags = { Name = "wallet-${var.environment}-node-group" }

  depends_on = [aws_eks_cluster.wallet]
}

# ── OIDC Provider for IRSA (IAM Roles for Service Accounts) ────────────────────

data "tls_certificate" "eks" {
  url = aws_eks_cluster.wallet.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "eks" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks.certificates[0].sha1_fingerprint]
  url             = aws_eks_cluster.wallet.identity[0].oidc[0].issuer

  tags = { Name = "wallet-${var.environment}-irsa" }
}

# ── Namespace and Service Account ──────────────────────────────────────────────

resource "aws_eks_addon" "coredns" {
  cluster_name             = aws_eks_cluster.wallet.name
  addon_name               = "coredns"
  addon_version            = "v1.9.3-eksbuild.2"  # Adjust version as needed
  resolve_conflicts_on_create = "OVERWRITE"

  tags = { Name = "wallet-${var.environment}-coredns" }
}

resource "aws_eks_addon" "kube_proxy" {
  cluster_name             = aws_eks_cluster.wallet.name
  addon_name               = "kube-proxy"
  addon_version            = "v1.28.0-eksbuild.1"  # Match cluster version
  resolve_conflicts_on_create = "OVERWRITE"

  tags = { Name = "wallet-${var.environment}-kube-proxy" }
}

resource "aws_eks_addon" "vpc_cni" {
  cluster_name             = aws_eks_cluster.wallet.name
  addon_name               = "vpc-cni"
  addon_version            = "v1.14.0-eksbuild.1"
  resolve_conflicts_on_create = "OVERWRITE"

  tags = { Name = "wallet-${var.environment}-vpc-cni" }
}

# ── Outputs ────────────────────────────────────────────────────────────────────

output "cluster_id" { value = aws_eks_cluster.wallet.id }
output "cluster_arn" { value = aws_eks_cluster.wallet.arn }
output "cluster_endpoint" { value = aws_eks_cluster.wallet.endpoint }
output "cluster_certificate_authority" { value = aws_eks_cluster.wallet.certificate_authority[0].data }
output "node_group_id" { value = aws_eks_node_group.wallet.id }
output "oidc_provider_arn" { value = aws_iam_openid_connect_provider.eks.arn }
output "eks_nodes_security_group_id" { value = aws_security_group.eks_nodes.id }
