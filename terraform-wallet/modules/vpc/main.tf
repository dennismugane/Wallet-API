variable "environment" {}
variable "vpc_cidr" {}

locals {
  azs             = ["${data.aws_region.current.name}a", "${data.aws_region.current.name}b"]
  public_cidrs    = ["10.0.1.0/24", "10.0.2.0/24"]
  private_cidrs   = ["10.0.11.0/24", "10.0.12.0/24"]
}

data "aws_region" "current" {}

# ── VPC ────────────────────────────────────────────────────────────────────────

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = { Name = "wallet-${var.environment}-vpc" }
}

# ── Internet Gateway ────────────────────────────────────────────────────────────

resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "wallet-${var.environment}-igw" }
}

# ── Public Subnets ──────────────────────────────────────────────────────────────

resource "aws_subnet" "public" {
  count                   = 2
  vpc_id                  = aws_vpc.main.id
  cidr_block              = local.public_cidrs[count.index]
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "wallet-${var.environment}-public-${count.index + 1}"
    "kubernetes.io/cluster/${var.environment}" = "shared"
    "kubernetes.io/role/elb" = "1"
  }
}

# ── Private Subnets (RDS/EKS) ───────────────────────────────────────────────────

resource "aws_subnet" "private" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = local.private_cidrs[count.index]
  availability_zone = local.azs[count.index]

  tags = {
    Name = "wallet-${var.environment}-private-${count.index + 1}"
    "kubernetes.io/cluster/${var.environment}" = "shared"
    "kubernetes.io/role/internal-elb" = "1"
  }
}

# ── Route Tables ────────────────────────────────────────────────────────────────

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.igw.id
  }

  tags = { Name = "wallet-${var.environment}-public-rt" }
}

resource "aws_route_table_association" "public" {
  count          = 2
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# ── NAT Gateway (lets RDS/EKS access internet for patches) ──────────────────────

resource "aws_eip" "nat" {
  domain = "vpc"
  tags   = { Name = "wallet-${var.environment}-nat-eip" }
  depends_on = [aws_internet_gateway.igw]
}

resource "aws_nat_gateway" "nat" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id
  tags          = { Name = "wallet-${var.environment}-nat" }
  depends_on    = [aws_internet_gateway.igw]
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.nat.id
  }

  tags = { Name = "wallet-${var.environment}-private-rt" }
}

resource "aws_route_table_association" "private" {
  count          = 2
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# ── Outputs ────────────────────────────────────────────────────────────────────

output "vpc_id" { value = aws_vpc.main.id }
output "public_subnet_ids" { value = aws_subnet.public[*].id }
output "private_subnet_ids" { value = aws_subnet.private[*].id }
