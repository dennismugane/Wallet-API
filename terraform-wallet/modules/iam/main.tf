variable "environment" {}
variable "db_password_secret_arn" {}
variable "jwt_secret_arn" {}

# ── EC2 IAM Role ────────────────────────────────────────────────────────────────

resource "aws_iam_role" "ec2_role" {
  name = "wallet-${var.environment}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ec2.amazonaws.com"
      }
    }]
  })

  tags = { Name = "wallet-${var.environment}-ec2-role" }
}

# EC2 policy for accessing secrets
resource "aws_iam_role_policy" "ec2_secrets_policy" {
  name   = "wallet-${var.environment}-ec2-secrets-policy"
  role   = aws_iam_role.ec2_role.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ]
        Resource = [
          var.db_password_secret_arn,
          var.jwt_secret_arn
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}

resource "aws_iam_instance_profile" "ec2_profile" {
  name = "wallet-${var.environment}-ec2-profile"
  role = aws_iam_role.ec2_role.name
}

# ── EKS IAM Role ────────────────────────────────────────────────────────────────

resource "aws_iam_role" "eks_cluster_role" {
  name = "wallet-${var.environment}-eks-cluster-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "eks.amazonaws.com"
      }
    }]
  })

  tags = { Name = "wallet-${var.environment}-eks-cluster-role" }
}

resource "aws_iam_role_policy_attachment" "eks_cluster_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.eks_cluster_role.name
}

# EKS Node Role
resource "aws_iam_role" "eks_node_role" {
  name = "wallet-${var.environment}-eks-node-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ec2.amazonaws.com"
      }
    }]
  })

  tags = { Name = "wallet-${var.environment}-eks-node-role" }
}

resource "aws_iam_role_policy_attachment" "eks_worker_node_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
  role       = aws_iam_role.eks_node_role.name
}

resource "aws_iam_role_policy_attachment" "eks_cni_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
  role       = aws_iam_role.eks_node_role.name
}

resource "aws_iam_role_policy_attachment" "eks_ecr_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
  role       = aws_iam_role.eks_node_role.name
}

# EKS Node policy for accessing secrets
resource "aws_iam_role_policy" "eks_node_secrets_policy" {
  name   = "wallet-${var.environment}-eks-node-secrets-policy"
  role   = aws_iam_role.eks_node_role.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ]
        Resource = [
          var.db_password_secret_arn,
          var.jwt_secret_arn
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}

# ── Outputs ─────────────────────────────────────────────────────────────────────

output "ec2_instance_profile_name" { value = aws_iam_instance_profile.ec2_profile.name }
output "eks_cluster_role_arn" { value = aws_iam_role.eks_cluster_role.arn }
output "eks_node_role_arn" { value = aws_iam_role.eks_node_role.arn }
