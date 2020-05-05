#!/usr/bin/env bash

if [ -z "$1" ] || [ -z "$2" ]; then
  echo "help!"
  echo "Please specify the following parameters: environment namespace"
  echo "e.g: foward dev ubirch-dev"
  exit 1
fi

environment=$1
namespace=$2

./forward_any.sh "$environment" "$namespace" 8558:8558






