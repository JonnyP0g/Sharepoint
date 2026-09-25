FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY HostedRelayServer.java .
RUN javac --release 21 -d /build/classes HostedRelayServer.java

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/classes /app
ENV PORT=8080 BIND_ADDRESS=0.0.0.0
EXPOSE 8080
USER 10001:10001
ENTRYPOINT ["java", "-Xmx256m", "-cp", "/app", "HostedRelayServer"]
