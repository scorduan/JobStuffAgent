variable "aws_account_id" {
  description = "AWS account that owns the Bedrock invocation policy. Used to prevent applying to a different account."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be a 12-digit AWS account ID."
  }
}

variable "aws_region" {
  description = "AWS Region from which the application invokes Bedrock."
  type        = string

  validation {
    condition     = length(trimspace(var.aws_region)) > 0
    error_message = "aws_region must not be blank."
  }
}

variable "environment" {
  description = "Deployment environment label used in the policy name and tags."
  type        = string
  default     = "dev"

  validation {
    condition     = can(regex("^[a-z0-9-]+$", var.environment))
    error_message = "environment may contain lowercase letters, digits, and hyphens only."
  }
}

variable "bedrock_invocation_resource_arns" {
  description = <<-EOT
    Every Bedrock resource ARN the application may invoke. For a direct foundation
    model, include its foundation-model ARN. For an inference profile, include the
    profile ARN and every underlying foundation-model ARN required by that profile.
  EOT
  type = set(string)

  validation {
    condition     = length(var.bedrock_invocation_resource_arns) > 0
    error_message = "At least one Bedrock invocation resource ARN is required."
  }

  validation {
    condition = alltrue([
      for resource_arn in var.bedrock_invocation_resource_arns : startswith(resource_arn, "arn:aws:bedrock:")
    ])
    error_message = "Every invocation resource must be an AWS Bedrock ARN."
  }
}

variable "allow_streaming" {
  description = "Whether to grant bedrock:InvokeModelWithResponseStream in addition to non-streaming invocation."
  type        = bool
  default     = false
}
