data "aws_iam_policy_document" "bedrock_invoke" {
  statement {
    sid    = "InvokeConfiguredBedrockModels"
    effect = "Allow"

    actions = concat(
      ["bedrock:InvokeModel"],
      var.allow_streaming ? ["bedrock:InvokeModelWithResponseStream"] : [],
    )

    resources = tolist(var.bedrock_invocation_resource_arns)
  }
}

resource "aws_iam_policy" "bedrock_invoke" {
  name        = "job-stuff-agent-${var.environment}-bedrock-invoke"
  description = "Allows JobStuffAgent to invoke the explicitly configured Amazon Bedrock model resources."
  policy      = data.aws_iam_policy_document.bedrock_invoke.json
}
