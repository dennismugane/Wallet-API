# Wallet Application - Infrastructure Architecture

## Overview

This document describes the complete AWS infrastructure architecture for deploying the Wallet Spring Boot application.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                           Internet                              │
└──────────────────────┬────────────────────────────────────────┘
                       │
        ┌──────────────┴──────────────┐
        │      AWS Region             │
        │   us-east-1 (Primary)       │
        │                              │
        │  ┌────────────────────────┐ │
        │  │   VPC (10.0.0.0/16)    │ │
        │  │                        │ │
        │  │  Public Subnets:       │ │
        │  │  ├─ 10.0.1.0/24 (AZ-a)│ │
        │  │  └─ 10.0.2.0/24 (AZ-b)│ │
        │  │                        │ │
        │  │  Private Subnets:      │ │
        │  │  ├─ 10.0.11.0/24 (AZ-a)
        │  │  └─ 10.0.12.0/24 (AZ-b)
        │  └────────────────────────┘ │
        │                              │
        └──────────────────────────────┘
```

## Detailed Architecture

### Network Layer

#### VPC Configuration
- **CIDR Block**: 10.0.0.0/16
- **Availability Zones**: 2 (us-east-1a, us-east-1b)
- **DNS**: Enabled for both hostnames and support

#### Subnets
| Type | AZ | CIDR | Purpose |
|------|----|----|---------|
| Public | a | 10.0.1.0/24 | ALB, NAT Gateway |
| Public | b | 10.0.2.0/24 | ALB redundancy |
| Private | a | 10.0.11.0/24 | RDS, EKS nodes |
| Private | b | 10.0.12.0/24 | RDS, EKS nodes |

#### Routing
- **Internet Gateway**: Routes public subnet traffic to internet
- **NAT Gateway**: Enables private subnet outbound internet access
- **Route Tables**: Separate public and private routing configurations

### Compute Layer - EC2 Deployment

```
┌─────────────────────────────────────────┐
│  Application Load Balancer (ALB)        │
│  - Port 80 (HTTP)                       │
│  - Port 443 (HTTPS - optional)          │
│  - Health Check: /api/v1/wallets/health│
└──────────────┬──────────────────────────┘
               │
        ┌──────▼──────┐
        │ Target Group│
        │  Port 8080  │
        └──────┬──────┘
               │
        ┌──────▼──────────────────┐
        │   EC2 Instance          │
        │ - Instance Type: t3.small
        │ - AMI: Amazon Linux 2023│
        │ - Docker Installed      │
        │ - Security Group        │
        │   (Port 8080 from ALB)  │
        │   (Port 22 SSH)         │
        └─────────────────────────┘
```

**EC2 Deployment Features:**
- Docker container running Spring Boot application
- Application Load Balancer for traffic distribution
- Security groups with restricted access
- CloudWatch monitoring enabled
- Auto-scaling ready architecture

### Compute Layer - EKS Deployment

```
┌──────────────────────────────────────────┐
│  EKS Cluster (wallet-prod-cluster)       │
│  - Version: 1.28                         │
│  - Kubernetes API Endpoint               │
│  - OIDC Provider (for IRSA)              │
│                                          │
│  ┌────────────────────────────────────┐ │
│  │  Node Group 1 (AZ-a)               │ │
│  │  - Instance Type: t3.medium        │ │
│  │  - Initial Count: 1                │ │
│  └────────────────────────────────────┘ │
│                                          │
│  ┌────────────────────────────────────┐ │
│  │  Node Group 2 (AZ-b)               │ │
│  │  - Instance Type: t3.medium        │ │
│  │  - Initial Count: 1                │ │
│  └────────────────────────────────────┘ │
│                                          │
│  Add-ons:                                │
│  - CoreDNS (v1.9.3)                     │
│  - kube-proxy (v1.28.0)                 │
│  - VPC CNI (v1.14.0)                    │
└──────────────────────────────────────────┘
```

**EKS Deployment Features:**
- Managed Kubernetes cluster (AWS handles control plane)
- Auto-scaling node groups (2-5 nodes)
- Multiple availability zones for HA
- OIDC provider for IAM Roles for Service Accounts (IRSA)
- CloudWatch integration for cluster logging

### Database Layer - RDS

```
┌──────────────────────────────────────────┐
│  RDS - PostgreSQL 15                     │
│  - Instance ID: wallet-prod-postgres     │
│  - Instance Class: db.t3.micro           │
│  - Engine Version: 15.3                  │
│  - Storage: 20 GB (auto-scales to 100GB) │
│  - Storage Type: gp3 (SSD)               │
│  - Encryption: Enabled                   │
│  - Backup Retention: 7 days              │
│  - Multi-AZ: Disabled (enable for prod)  │
│                                          │
│  ┌────────────────────────────────────┐ │
│  │ DB Subnet Group                    │ │
│  │ - Subnets in AZ-a and AZ-b         │ │
│  │ - Private network only             │ │
│  └────────────────────────────────────┘ │
│                                          │
│  Database: wallet                        │
│  Master User: admin                      │
│  Encoding: UTF-8                         │
└──────────────────────────────────────────┘
```

**RDS Features:**
- Automated daily backups with point-in-time recovery
- Automatic storage scaling
- Multi-AZ capable for high availability
       - Parameter groups for PostgreSQL 15 optimization
- Security groups for restricted access from EC2/EKS only

### Security Layer

#### IAM Roles
- **EC2 Role**: Access to Secrets Manager for DB password and JWT
- **EKS Cluster Role**: Manage Kubernetes cluster resources
- **EKS Node Role**: Node permissions for networking and logging
- **IRSA**: For pod-level service account permissions

#### Secrets Manager
| Secret | Value |
|--------|-------|
| wallet-prod/db-password | Database password |
| wallet-prod/jwt-secret | JWT signing key |

#### Security Groups

| Resource | Rules | Purpose |
|----------|-------|---------|
| ALB | HTTP (80), HTTPS (443) in; All out | Load balancer |
| EC2 | 8080 (from ALB), 22 (SSH) in; All out | Application |
| EKS Nodes | All in/out | Kubernetes nodes |
| RDS | 5432 (from EC2/EKS) in; All out | Database access |

### Monitoring & Logging

- **CloudWatch Logs**: EKS cluster logs (API, audit, controller manager, scheduler)
- **CloudWatch Metrics**: EC2 CPU, memory, disk I/O
- **Application Logs**: Docker container logs (EC2), Pod logs (EKS)

## Deployment Flow

### EC2 Deployment
1. User creates Terraform configuration
2. Terraform creates VPC, subnets, and networking
3. RDS instance is provisioned
4. EC2 instance is launched with Docker
5. Application container is pulled and started
6. ALB targets the EC2 instance
7. Application becomes accessible via ALB DNS

### EKS Deployment
1. VPC and networking created
2. EKS cluster created with OIDC provider
3. Node groups are provisioned
4. Core add-ons are installed
5. User deploys application via kubectl
6. Application pods run on EKS nodes
7. Services expose application (via ingress or LoadBalancer)

## High Availability Considerations

### For Production
1. **Database**: Enable Multi-AZ for RDS
2. **EKS**: Use 3+ nodes for critical workloads
3. **Load Balancing**: ALB distributes traffic across nodes
4. **Auto-scaling**: Enable to handle traffic spikes
5. **Backups**: RDS automated backups with cross-region replication

### Disaster Recovery
1. **Backup Strategy**: Daily RDS snapshots, 7-day retention
2. **Failover**: RDS Multi-AZ provides automatic failover
3. **Recovery**: Application can be redeployed from container images
4. **Data**: Encrypted storage for data at rest and in transit

## Scaling Strategy

### Vertical Scaling
- **RDS**: Increase instance class (db.t3.small, db.t3.medium, etc.)
- **EC2**: Resize instance type
- **EKS**: Update node instance type

### Horizontal Scaling
- **EKS**: Auto-scaling node groups adjust based on CPU/memory
- **EC2**: Ready for ASG implementation
- **Pods**: Kubernetes HPA scales pod replicas

## Cost Optimization

| Component | Cost Factor | Optimization |
|-----------|------------|---------------|
| EC2 | Instance type, hours running | Use t3 types, scheduled scaling |
| RDS | Instance class, storage | Use db.t3.micro for dev, t3.small for prod |
| EKS | Per cluster ($0.10/hour) + node compute | Consolidate workloads, use spot instances |
| NAT Gateway | Data processed | Minimize egress traffic |
| Secrets Manager | Per secret ($0.40/month) | Combine secrets if possible |

## Network Diagram - Data Flow

```
Internet
  │
  ▼
┌─────────────┐
│   ALB       │ (Public subnet)
└──────┬──────┘
       │ (Port 8080)
┌──────▼──────────────────┐
│ EC2/EKS Instance        │ (Private subnet)
│ ├─ Spring Boot App      │
│ └─ Docker Container     │
└──────┬──────────────────┘
       │ (Port 3306)
┌──────▼──────────────────┐
│ RDS MySQL Instance      │ (Private subnet)
│ └─ Database             │
└─────────────────────────┘
```

## Security Flow

```
AWS Secrets Manager
  │ (Encrypted)
  ├─ DB Password ─────▶ EC2 IAM Role ─────▶ Spring App
  ├─ JWT Secret   ─────▶ EKS Node Role────▶ K8s Pod
  └─ (Other secrets)
```

## Next Steps

1. Customize variables in `terraform.tfvars` for the environment
2. Run `terraform plan` to review resources
3. Run `terraform apply` to create infrastructure
4. Configure kubectl for EKS access
5. Deploy application using provided manifest
6. Monitor via CloudWatch and application logs

## Troubleshooting

### Common Issues

1. **RDS Connection Fails**: Check security group rules and IP connectivity
2. **EKS Pod Can't Access RDS**: Verify IAM permissions and network policy
3. **EC2 Docker Pull Fails**: Check Docker Hub credentials and network
4. **ALB Health Check Failed**: Verify application health endpoint
