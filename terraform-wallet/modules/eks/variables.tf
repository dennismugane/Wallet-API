variable "environment" {}

variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
  default     = "muigo-eks"
}

variable "vpc_id" {
  description = "VPC id where the EKS cluster will be created"
  type        = string
}

variable "private_subnet_ids" {
  description = "List of private subnet IDs for EKS worker nodes"
  type        = list(string)
}

variable "eks_node_instance_type" {
  description = "Instance type for EKS worker nodes"
  type        = string
}

variable "eks_desired_node_count" {
  description = "Desired number of worker nodes in the EKS cluster"
  type        = number
}

variable "eks_min_node_count" {
  description = "Minimum number of worker nodes"
  type        = number
}

variable "eks_max_node_count" {
  description = "Maximum number of worker nodes for auto-scaling"
  type        = number
}

variable "cluster_version" {
  description = "Kubernetes version for the EKS control plane"
  type        = string
  default     = "1.30"
}
