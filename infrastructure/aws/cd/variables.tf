variable "aws_region" {
  description = "AWS region containing ECR and the target EC2 instance."
  type        = string
  default     = "sa-east-1"
}

variable "project_name" {
  description = "Name used to tag and name deployment resources."
  type        = string
  default     = "event-driven-ecommerce"
}

variable "environment" {
  description = "Deployment environment name."
  type        = string
  default     = "production"
}

variable "github_owner" {
  description = "GitHub organization or user that owns the repository."
  type        = string
  default     = "ToniniTech"
}

variable "github_repository" {
  description = "GitHub repository allowed to assume the deployment role."
  type        = string
  default     = "Event-Driven-E-commerce"
}

variable "github_branch" {
  description = "Only workflow runs from this branch may assume the deployment role."
  type        = string
  default     = "main"
}

variable "ec2_instance_id" {
  description = "Existing EC2 instance deployed by the workflow."
  type        = string

  validation {
    condition     = can(regex("^i-[0-9a-f]+$", var.ec2_instance_id))
    error_message = "ec2_instance_id must be a valid EC2 instance ID."
  }
}

variable "create_github_oidc_provider" {
  description = "Create GitHub's account-wide OIDC provider. Set false when it already exists."
  type        = bool
  default     = true
}

variable "github_oidc_provider_arn" {
  description = "Existing GitHub OIDC provider ARN when create_github_oidc_provider is false."
  type        = string
  default     = ""

  validation {
    condition = (
      var.create_github_oidc_provider ||
      can(regex("^arn:[^:]+:iam::[0-9]{12}:oidc-provider/token\\.actions\\.githubusercontent\\.com$", var.github_oidc_provider_arn))
    )
    error_message = "Provide the existing GitHub OIDC provider ARN when provider creation is disabled."
  }
}

variable "existing_ec2_role_name" {
  description = "Existing IAM role attached to EC2. Leave empty to create a new role and instance profile."
  type        = string
  default     = ""
}

variable "ecr_image_retention_count" {
  description = "Number of tagged release images retained per service."
  type        = number
  default     = 20

  validation {
    condition     = var.ecr_image_retention_count >= 2
    error_message = "Keep at least two images so automatic rollback remains possible."
  }
}
