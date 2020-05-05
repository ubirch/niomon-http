#!/usr/bin/env bash

if [ -z "$1" ] || [ -z "$2" ]; then
  echo "help!"
  exit 1
fi

environment=$1
namespace=$2

k8sc.sh "$environment" -n "$namespace" get pod --selector=app=niomon,component=http -o json | jq '[.items[] | {namespace: .metadata.namespace, pod: .metadata.name, podIP: .status.podIP, phase: .status.phase }] | sort_by(.podIP)'
