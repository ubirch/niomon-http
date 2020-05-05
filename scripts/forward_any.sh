#!/usr/bin/env bash

if [ -z "$1" ] || [ -z "$2" ] || [ -z "$3" ]; then
  echo "help!"
  echo "Please specify the following parameters: environment namespace portRelation"
  echo "e.g: foward dev ubirch-dev 9010:9010"
  exit 1
fi

environment=$1
namespace=$2
portRelation=$3

pods=$(./cluster_pods.sh "$environment" "$namespace")
length=$(echo "$pods" | jq length)
index=$((0 + "$length" - 1))
randomIndex=$(shuf -i 0-"$index" -n 1)

isLeader=no

if [ "$randomIndex" -eq 0 ]
then
  isLeader=yes
fi

pod=$(echo "$pods"  | jq ".[$randomIndex].pod")
IP=$(echo "$pods"  | jq ".[$randomIndex].podIP")

echo "Forwarding -" "$pod" @ "$IP" - leader=$isLeader

k8sc.sh "$environment" -n "$namespace"  port-forward "$pod" "$portRelation"

