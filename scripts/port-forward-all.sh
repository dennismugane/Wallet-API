#!/usr/bin/env bash
# scripts/port-forward-all.sh
set -e

echo "Forwarding Grafana on :3000, Prometheus on :9090, wallet-api on :8080..."

kubectl port-forward -n monitoring svc/kube-prometheus-stack-grafana 3000:80 &
kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090 &
kubectl port-forward -n wallet svc/muigo-wallet-svc 8080:80 &

echo "PIDs: $(jobs -p)"
echo "Ctrl+C to stop, or: kill $(jobs -p)"
wait