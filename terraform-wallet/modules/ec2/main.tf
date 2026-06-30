variable "environment" {}
variable "vpc_id" {}
variable "public_subnet_ids" {}
variable "instance_type" {}
variable "key_name" {}
variable "iam_instance_profile" {}
variable "rds_endpoint" {}
variable "aws_region" {}
variable "db_name" {}
variable "db_username" {}
variable "db_password_secret_name" {}
variable "jwt_secret_name" {}
variable "docker_hub_username" {}
variable "image_tag" {}

data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }
}

# ── Security Groups ────────────────────────────────────────────────────────────

resource "aws_security_group" "alb" {
  name        = "wallet-${var.environment}-alb-sg"
  description = "Allow HTTPS/HTTP from anywhere"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "wallet-${var.environment}-alb-sg" }
}

resource "aws_security_group" "backend" {
  name        = "wallet-${var.environment}-backend-sg"
  description = "Allow port 8080 from ALB only"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]   # Tighten this to your IP in production
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "wallet-${var.environment}-backend-sg" }
}

# ── Application Load Balancer ──────────────────────────────────────────────────

resource "aws_lb" "backend" {
  name               = "wallet-${var.environment}-alb"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.public_subnet_ids

  tags = { Name = "wallet-${var.environment}-alb" }
}

resource "aws_lb_target_group" "backend" {
  name        = "wallet-${var.environment}-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "instance"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 2
    timeout             = 3
    interval            = 30
    path                = "/api/v1/wallets/health"  # Adjust if needed
    matcher             = "200-299"
  }

  tags = { Name = "wallet-${var.environment}-tg" }
}

resource "aws_lb_listener" "backend" {
  load_balancer_arn = aws_lb.backend.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }
}

# ── EC2 Instance ───────────────────────────────────────────────────────────────

locals {
  db_host = split(":", var.rds_endpoint)[0]
}

resource "aws_instance" "wallet" {
  ami                    = data.aws_ami.amazon_linux.id
  instance_type          = var.instance_type
  subnet_id              = var.public_subnet_ids[0]
  vpc_security_group_ids = [aws_security_group.backend.id]
  key_name               = var.key_name
  iam_instance_profile   = var.iam_instance_profile

  user_data = base64encode(templatefile("${path.module}/userdata.sh", {
    DB_HOST                = local.db_host
    DB_NAME                = var.db_name
    DB_USERNAME            = var.db_username
    AWS_REGION             = var.aws_region
    DB_PASSWORD            = var.db_password_secret_name  # Will be fetched from Secrets Manager
    JWT_SECRET             = var.jwt_secret_name          # Will be fetched from Secrets Manager
    DOCKER_HUB_USERNAME    = var.docker_hub_username
    DOCKER_IMAGE           = "${var.docker_hub_username}/wallet"
    IMAGE_TAG              = var.image_tag
  }))

  monitoring           = true
  tags = { Name = "wallet-${var.environment}-ec2" }


  depends_on = []
}

resource "aws_lb_target_group_attachment" "backend" {
  target_group_arn = aws_lb_target_group.backend.arn
  target_id        = aws_instance.wallet.id
  port             = 8080
}

# ── Outputs ────────────────────────────────────────────────────────────────────

output "alb_dns_name" { value = aws_lb.backend.dns_name }
output "backend_sg_id" { value = aws_security_group.backend.id }
output "ec2_instance_id" { value = aws_instance.wallet.id }
output "alb_arn" { value = aws_lb.backend.arn }
