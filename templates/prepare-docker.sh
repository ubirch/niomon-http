#! /bin/sh
BASEDIR=$(dirname "$0")
echo "v${version}" > version.txt
tar -C $BASEDIR -cvf \
  ${artifactId}.tar \
  Dockerfile \
  ${project.build.finalName}.${packaging} \
  version.txt
