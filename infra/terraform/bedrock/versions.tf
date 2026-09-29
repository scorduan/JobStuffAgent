terraform {
  required_version = ">= 1.6.0"

  cloud {
    organization = "jobagent"

    workspaces {
      name = "job-stuff-agent-dev-bedrock"
    }
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}
