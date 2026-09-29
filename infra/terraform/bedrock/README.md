# Bedrock invocation policy

This Terraform root creates one customer-managed IAM policy. It grants only
non-streaming `bedrock:InvokeModel` access to explicitly listed model resources;
it does not create IAM users, access keys, a runtime role, or a database.

State is stored in the `jobagent` HCP Terraform workspace
`job-stuff-agent-dev-bedrock`. Configure that workspace for **Local** execution:
Terraform Cloud stores and locks state, while `plan` and `apply` run on the
developer's machine using short-lived AWS credentials. Do not add AWS access
keys as workspace variables.

Keeping the policy separate is intentional. Local developers should authenticate
with short-lived AWS credentials (normally IAM Identity Center), while a future
deployed application should attach the same policy to its own runtime role. Do
not create or commit long-lived credentials for this project.

## What Terraform can and cannot automate

Terraform creates the IAM policy and protects against a mistaken target account
through `allowed_account_ids`. It does not accept provider agreements, model use
cases, Marketplace subscriptions, or organization-level service-control-policy
requirements. Confirm that the chosen model can run in the chosen Region in the
Bedrock console first.

Most foundation models are enabled by default, though first-time Anthropic use
can require use-case details. AWS documents the current model-access behavior at
<https://docs.aws.amazon.com/bedrock/latest/userguide/foundation-models-reference.html>.

## Required inputs

Copy the example and supply:

```sh
cd infra/terraform/bedrock
cp terraform.tfvars.example terraform.tfvars
```

- `aws_account_id`: the 12-digit AWS account that owns the policy.
- `aws_region`: the Region from which the local application will call Bedrock.
- `bedrock_invocation_resource_arns`: the exact foundation-model ARN, or the
  complete ARN set required by an inference profile.

The checked-in example selects `us-west-2` (Oregon) and Amazon Nova Lite
(`amazon.nova-lite-v1:0`) for the initial classifier. It is a direct,
in-region invocation rather than a cross-Region inference profile, keeping the
first policy limited to one model ARN. AWS lists Nova Lite as available in
Oregon; see its [model card](https://docs.aws.amazon.com/bedrock/latest/userguide/model-card-amazon-nova-lite.html).

For an inference profile, AWS requires the profile ARN *and* the underlying
foundation-model ARNs. Do not replace the list with a wildcard merely to make a
profile work. See [AWS's inference-profile prerequisites](https://docs.aws.amazon.com/bedrock/latest/userguide/inference-profiles-prereq.html).

## Apply

Authenticate your AWS CLI/SDK profile with short-lived credentials, select it
with `AWS_PROFILE` if necessary, and confirm the account before planning:

```sh
aws sts get-caller-identity
terraform init
terraform plan
terraform apply
```

Run `terraform login` before `terraform init` to authenticate the local CLI to
HCP Terraform.

Attach the output `bedrock_invoke_policy_arn` to one of the following outside
this Terraform root:

- the application's AWS runtime role, once deployment infrastructure exists; or
- the IAM Identity Center permission set used by local developers.

The initial Phase 5 classifier is non-streaming. Leave `allow_streaming = false`
until a streaming UI/API is intentionally introduced.
