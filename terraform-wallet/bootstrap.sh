#!/bin/bash
# bootstrap.sh
# Run this ONCE before `terraform init` to create the remote state backend.

set -euo pipefail

REGION="${AWS_DEFAULT_REGION:-us-east-1}"
BUCKET="wallet-terraform-state-dev-2026"
TABLE="wallet-tfstate-lock"

echo "==> Creating S3 state bucket: $BUCKET"
if aws s3api head-bucket --bucket "$BUCKET" 2>/dev/null; then
  echo "    Bucket already exists, skipping."
else
  if [ "$REGION" = "us-east-1" ]; then
    aws s3api create-bucket --bucket "$BUCKET" --region "$REGION"
  else
    aws s3api create-bucket \
      --bucket "$BUCKET" \
      --region "$REGION" \
      --create-bucket-configuration LocationConstraint="$REGION"
  fi
fi

echo "==> Enabling versioning on $BUCKET"
aws s3api put-bucket-versioning \
  --bucket "$BUCKET" \
  --versioning-configuration Status=Enabled

echo "==> Enabling encryption on $BUCKET"
aws s3api put-bucket-encryption \
  --bucket "$BUCKET" \
  --server-side-encryption-configuration '{
    "Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]
  }'

echo "==> Blocking public access on $BUCKET"
aws s3api put-public-access-block \
  --bucket "$BUCKET" \
  --public-access-block-configuration \
    "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"

echo "==> Creating DynamoDB lock table: $TABLE"
if aws dynamodb describe-table --table-name "$TABLE" --region "$REGION" 2>/dev/null; then
  echo "    Table already exists, skipping."
else
  aws dynamodb create-table \
    --table-name "$TABLE" \
    --attribute-definitions AttributeName=LockID,AttributeType=S \
    --key-schema AttributeName=LockID,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region "$REGION"
fi

echo ""
echo "✅ Bootstrap complete."
echo ""
echo "Next steps:"
echo "  1. Run: terraform init"
echo "  2. Copy terraform.tfvars.example → terraform.tfvars and fill in your values"
echo "  3. Export secrets:"
echo "       export TF_VAR_db_password='...'"
echo "       export TF_VAR_jwt_secret='...'"
echo "  4. Run: terraform plan"
echo "  5. Run: terraform apply"
