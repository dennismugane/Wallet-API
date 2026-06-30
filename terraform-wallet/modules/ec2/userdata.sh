#!/bin/bash
set -e

# Update system
yum update -y
yum install -y docker aws-cli

# Start Docker
systemctl start docker
systemctl enable docker

# Create wallet user and add to docker group
useradd -m wallet || true
usermod -aG docker wallet || true

# Log Docker in (for pulling from Docker Hub)
docker login -u "${DOCKER_HUB_USERNAME}" -p "${DOCKER_HUB_PASSWORD}"

# Fetch secrets from AWS Secrets Manager
DB_PASSWORD=$(aws secretsmanager get-secret-value \
  --secret-id "${DB_PASSWORD}" \
  --region "${AWS_REGION}" \
  --query SecretString \
  --output text)

JWT_SECRET=$(aws secretsmanager get-secret-value \
  --secret-id "${JWT_SECRET}" \
  --region "${AWS_REGION}" \
  --query SecretString \
  --output text)

# Write environment values to a safe env file
cat > /home/wallet/wallet.env <<EOF
DB_PASSWORD=${DB_PASSWORD}
JWT_SECRET=${JWT_SECRET}
SPRING_DATASOURCE_URL=jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}
SPRING_DATASOURCE_USERNAME=${DB_USERNAME}
EOF
chown wallet:docker /home/wallet/wallet.env
chmod 600 /home/wallet/wallet.env

# Pull and run the wallet application
docker run -d \
  --name wallet-app \
  --restart=always \
  -p 8080:8080 \
  --env-file /home/wallet/wallet.env \
  ${DOCKER_IMAGE}:${IMAGE_TAG}

# Log rotation setup
cat > /etc/docker/daemon.json <<EOF
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
EOF

systemctl restart docker

# CloudWatch agent setup (optional)
wget https://s3.amazonaws.com/amazoncloudwatch-agent/amazon_linux/amd64/latest/amazon-cloudwatch-agent.rpm
rpm -U ./amazon-cloudwatch-agent.rpm || true
