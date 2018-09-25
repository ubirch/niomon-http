#! /bin/sh
BASEDIR=$(dirname "$0")
tar -C $BASEDIR -cvf ${artifactId}.tar Dockerfile ${project.build.finalName}.${packaging}
