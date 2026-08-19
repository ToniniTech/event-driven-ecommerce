output "github_deploy_role_arn" {
  description = "Set this value as the AWS_DEPLOY_ROLE_ARN GitHub Actions variable."
  value       = aws_iam_role.github_deploy.arn
}

output "ecr_registry" {
  description = "ECR registry used by the deployment workflow."
  value       = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
}

output "ecr_repository_urls" {
  description = "Application image repositories."
  value       = { for name, repository in aws_ecr_repository.service : name => repository.repository_url }
}

output "ec2_instance_profile_name" {
  description = "Associate this profile with EC2 when Terraform created a new role. Null when an existing role was supplied."
  value       = local.create_ec2_role ? aws_iam_instance_profile.ec2[0].name : null
}

output "github_actions_variables" {
  description = "Non-secret repository variables required by deploy-ec2.yml."
  value = {
    AWS_REGION          = var.aws_region
    AWS_DEPLOY_ROLE_ARN = aws_iam_role.github_deploy.arn
    EC2_INSTANCE_ID     = var.ec2_instance_id
    EC2_APP_DIR         = "/opt/event-driven-ecommerce"
  }
}
