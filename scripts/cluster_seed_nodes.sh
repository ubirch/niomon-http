#!/usr/bin/env bash

curl -s http://localhost:8558/bootstrap/seed-nodes | jq .
