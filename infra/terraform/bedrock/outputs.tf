output "bedrock_invoke_policy_arn" {
  description = "Attach this policy to the application's runtime role or to the local-developer IAM Identity Center permission set."
  value       = aws_iam_policy.bedrock_invoke.arn
}

output "aws_account_id" {
  description = "The account Terraform is configured to target."
  value       = var.aws_account_id
}

output "bedrock_invocation_resource_arns" {
  description = "The model and/or inference-profile resources authorized by this policy."
  value       = var.bedrock_invocation_resource_arns
}
