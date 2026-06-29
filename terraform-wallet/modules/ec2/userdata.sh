#!/bin/bash
set -e

# Update system
yum update -y
yum install -y docker

# Start Docker
systemctl start docker
systemctl enable docker

# Create wallet user and add to docker group
useradd -m wallet || true
usermod -aG docker wallet || true

# Log Docker in (for pulling from Docker Hub)
docker login -u ${DOCKER_HUB_USERNAME} -p ${DOCKER_HUB_PASSWORD}

# Pull and run the wallet application
docker run -d \
  --name wallet-app \
  --restart=always \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}" \
  -e SPRING_DATASOURCE_USERNAME="${DB_USERNAME}" \
  -e SPRING_DATASOURCE_PASSWORD="${DB_PASSWORD}" \
  -e JWT_SECRET="${JWT_SECRET}" \
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
