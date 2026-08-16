variable "environment" {}
variable "vpc_id" {}
variable "private_subnet_ids" {}
variable "db_name" {}
variable "db_username" {}
variable "db_password" { sensitive = true }
variable "db_instance_class" {}
variable "eks_security_group_id" {
  description = "Security group ID used by EKS worker nodes"
  type        = string
}

# ── DB Subnet Group ────────────────────────────────────────────────────────────

resource "aws_db_subnet_group" "main" {
  name       = "wallet-${var.environment}-db-subnet"
  subnet_ids = var.private_subnet_ids

  tags = { Name = "wallet-${var.environment}-db-subnet-group" }
}

# ── Security Group ──────────────────────────────────────────────────────────────

resource "aws_security_group" "rds" {
  name        = "wallet-${var.environment}-rds-sg"
  description = "PostgreSQL access from EKS only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "PostgreSQL from EKS nodes"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.eks_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "wallet-${var.environment}-rds-sg"
  }
}

# ── Parameter Group ────────────────────────────────────────────────────────────

resource "aws_db_parameter_group" "postgres" {
  name   = "wallet-${var.environment}-postgres15"
  family = "postgres15"

  parameter {
    name  = "log_statement"
    value = "all"
  }

  parameter {
    name  = "log_duration"
    value = "on"
  }

  tags = { Name = "wallet-${var.environment}-postgres-params" }
}

# ── RDS Instance ────────────────────────────────────────────────────────────────

resource "aws_db_instance" "main" {
  identifier              = "wallet-${var.environment}-postgres"
  engine                  = "postgres"
  engine_version          = "15.7"
  instance_class          = var.db_instance_class
  allocated_storage       = 20
  max_allocated_storage   = 100         # auto-scaling storage up to 100 GB
  storage_type            = "gp3"
  storage_encrypted       = true

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.postgres.name

  backup_retention_period  = 0
  backup_window            = "03:00-04:00"
  maintenance_window       = "sun:04:00-sun:05:00"
  deletion_protection      = false       # prevents accidental destroy
  skip_final_snapshot      = false
  final_snapshot_identifier = "wallet-${var.environment}-final-snapshot-${formatdate("YYYY-MM-DD-hhmm", timestamp())}"

  multi_az               = false         # set true for HA in production
  publicly_accessible    = false

  tags = { Name = "wallet-${var.environment}-rds" }
}

# ── Outputs ─────────────────────────────────────────────────────────────────────

output "rds_endpoint" { value = aws_db_instance.main.endpoint }
output "rds_id"       { value = aws_db_instance.main.id }
output "rds_address"  { value = aws_db_instance.main.address }
