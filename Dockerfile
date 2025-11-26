FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY build/libs/arcana-cloud-java-1.0.0-SNAPSHOT.jar app.jar

# Default to monolithic mode - K8s ConfigMaps will override for layered
ENV DEPLOYMENT_MODE=monolithic
ENV JAVA_OPTS="-Xmx256m -Xms128m -XX:+UseG1GC"

EXPOSE 8080 9090

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
