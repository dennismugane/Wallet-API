variable "environment" {}
variable "jwt_secret" { sensitive = true }

resource "random_password" "db_password" {
  length           = 24
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

# ── DB Password Secret ────────────────────────────────────────────────────────

resource "aws_secretsmanager_secret" "db_password" {
  name                    = "wallet-${var.environment}/db-password"
  recovery_window_in_days = 7

  tags = { Name = "wallet-${var.environment}-db-password" }
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id       = aws_secretsmanager_secret.db_password.id
  secret_string   = random_password.db_password.result
}

# ── JWT Secret ──────────────────────────────────────────────────────────────────

resource "random_password" "jwt_secret" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "aws_secretsmanager_secret" "jwt_secret" {
  name                    = "wallet-${var.environment}/jwt-secret"
  recovery_window_in_days = 7

  tags = { Name = "wallet-${var.environment}-jwt-secret" }
}

resource "aws_secretsmanager_secret_version" "jwt_secret" {
  secret_id     = aws_secretsmanager_secret.jwt_secret.id
  secret_string = trim(coalesce(var.jwt_secret, "")) != "" ? var.jwt_secret : random_password.jwt_secret.result
}

# ── Outputs ─────────────────────────────────────────────────────────────────────

output "db_password_secret_arn" {
  value = aws_secretsmanager_secret.db_password.arn
}
output "db_password_secret_name" {
  value = aws_secretsmanager_secret.db_password.name
}
output "db_password" {
  value     = random_password.db_password.result
  sensitive = true
}
output "jwt_secret_arn" {
  value = aws_secretsmanager_secret.jwt_secret.arn
}
output "jwt_secret_name" {
  value = aws_secretsmanager_secret.jwt_secret.name
}
