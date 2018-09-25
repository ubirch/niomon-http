FROM ubirch/java

COPY ${project.build.finalName}.${packaging} ${project.build.finalName}.${packaging}

EXPOSE 8080

CMD ["java","-Xmx256m","-Xms128m","-jar", "${project.build.finalName}.${packaging}"]