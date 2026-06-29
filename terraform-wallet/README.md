# Wallet Application - Terraform Infrastructure

This Terraform configuration deploys a complete AWS infrastructure for the Wallet Spring Boot application with support for both EC2 and EKS (Kubernetes) deployments.

## Infrastructure Components

### Core Services
- **VPC**: Virtual Private Cloud with public and private subnets across 2 availability zones
  - **RDS**: PostgreSQL 15 database with automated backups and encryption
- **EC2**: Auto-scaling capability with ALB (Application Load Balancer)
- **EKS**: Kubernetes cluster with managed node groups for containerized deployments
- **Secrets Manager**: Secure storage for database passwords and JWT secrets
- **IAM**: Role-based access control for EC2 and EKS services

## Prerequisites

1. **AWS Account**: With appropriate IAM permissions
2. **AWS CLI**: Configured with your credentials
3. **Terraform**: >= 1.6.0 installed
4. **S3 Bucket**: Shared with ecommerce (`dennism-terraform-state-dev-2026`)
5. **DynamoDB Table**: Shared with ecommerce (`ecommerce-tfstate-lock`)
6. **EC2 Key Pair**: An existing key pair in the target region
7. **Docker Hub Account**: For pulling Docker images (if using private registry)

> **Note**: The wallet application shares the same S3 bucket and DynamoDB table with the ecommerce application to reduce costs. Each application stores its state in a different path within the bucket.

## Setup Instructions

### 1. Prepare Configuration

```bash
cd terraform-wallet

# Copy and customize terraform.tfvars
cp terraform.tfvars terraform.tfvars.local
# Edit terraform.tfvars and update:
# - key_pair_name (your EC2 key pair)
# - docker_hub_username (your Docker Hub username)
```

### 2. Set Environment Variables

```bash
# Required secrets (use secure values in production)
export TF_VAR_db_password="your-secure-db-password"
export TF_VAR_jwt_secret="your-jwt-secret-key"
export TF_VAR_docker_hub_password="your-docker-hub-token"

# Optional: customize other variables
export TF_VAR_aws_region="us-east-1"
export TF_VAR_environment="prod"
```

### 3. Initialize and Deploy

```bash
# Initialize Terraform (backend already exists from ecommerce setup)
terraform init

# Review the deployment plan
terraform plan -out=tfplan

# Apply the configuration
terraform apply tfplan
```

## EC2 Deployment

The EC2 module deploys:
- Single EC2 instance running the wallet application via Docker
- Application Load Balancer with health checks
- Auto-scaling ready infrastructure

### Accessing EC2 Application
```bash
# Get ALB DNS name
terraform output alb_dns_name

# Access the application
curl http://<alb-dns-name>/api/v1/wallets
```

## EKS Deployment

The EKS module deploys:
- Managed Kubernetes cluster (1.28)
- Auto-scaling node group (2-5 nodes)
- OIDC provider for IRSA (IAM Roles for Service Accounts)
- Core add-ons: CoreDNS, kube-proxy, VPC CNI

### Configure kubectl

```bash
# Update kubeconfig
aws eks update-kubeconfig \
  --name wallet-prod-cluster \
  --region us-east-1

# Verify cluster access
kubectl cluster-info
kubectl get nodes
```

### Deploy Application to EKS

```bash
# Create namespace
kubectl create namespace wallet

# Create secrets for database and JWT
kubectl create secret generic wallet-secrets \
  --from-literal=DB_PASSWORD='your-db-password' \
  --from-literal=JWT_SECRET='your-jwt-secret' \
  -n wallet

# Create deployment (you'll need a deployment manifest)
kubectl apply -f k8s/deployment.yml -n wallet
```

## Database Setup

The RDS module creates:
- MySQL 8.0 database instance
- Automatic daily backups (7-day retention)
- Encryption at rest
- Security groups for EC2 and EKS access

### Connect to Database

```bash
# Get RDS endpoint
RDS_HOST=$(terraform output -raw rds_address)

# Install PostgreSQL client (if needed)
# Ubuntu/Debian: sudo apt-get install postgresql-client
# macOS: brew install postgresql

# Connect to database
psql -h $RDS_HOST -U admin -d wallet
```

## Outputs

After successful deployment, key outputs include:

```bash
# Get all outputs
terraform output

# Get specific outputs
terraform output vpc_id
terraform output rds_endpoint
terraform output alb_dns_name
terraform output eks_cluster_endpoint
```

## Security Considerations

1. **Secrets**: Never commit `terraform.tfvars` with secrets. Use environment variables.
2. **SSH Access**: Update security groups to restrict SSH to your IP only.
3. **Database**: Set `multi_az = true` in production for high availability.
4. **Backup**: Ensure RDS backup retention is appropriate for your use case.
5. **Network**: Review CIDR blocks and security group rules for your needs.

## Destroy Infrastructure

```bash
# This will destroy all AWS resources (WARNING: cannot be undone)
terraform destroy
```

## Troubleshooting

### State Lock Issues
```bash
# Force unlock (use with caution)
terraform force-unlock <LOCK_ID>
```

### EC2 Instance SSH Access
```bash
# Get EC2 instance ID
INSTANCE_ID=$(terraform output -raw ec2_instance_id)

# SSH into instance
ssh -i /path/to/key.pem ec2-user@<public-ip>

# Check application logs
docker logs wallet-app
```

### EKS Cluster Access Issues
```bash
# Verify OIDC provider setup
aws iam list-open-id-connect-providers

# Check cluster security groups
aws ec2 describe-security-groups --filters Name=group-name,Values=wallet-*
```

## Cost Optimization

- Use `t3` instance types for non-production workloads
- Adjust EKS node count based on actual usage
- Consider spot instances for EKS nodes in non-critical environments
- Monitor RDS instance type and storage allocation

## Monitoring and Logging

- **CloudWatch**: EKS cluster logs available in CloudWatch Logs
- **EC2**: CloudWatch agent configured for instance metrics
- **RDS**: Enhanced monitoring available with additional IAM permissions

## Updates and Patches

- Terraform auto-applies patches during maintenance windows
- EKS cluster updates: Update cluster version first, then node groups
- RDS engine version updates: Plan maintenance during off-peak hours

## Support

For issues or questions:
1. Check CloudFormation events for deployment errors
2. Review IAM permissions on AWS console
3. Check Terraform log output with `TF_LOG=DEBUG`

## Related Files

- [Kubernetes Deployment Manifests](../../k8s/)
- [Application Configuration](../../k8s/deployment.yml)
- [Docker Configuration](../../Dockerfile)
