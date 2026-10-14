FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon --version
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar' | head -n 1)" /workspace/app.jar

FROM eclipse-temurin:17-jre
RUN useradd --system --create-home --shell /usr/sbin/nologin martpick
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER martpick
ENV JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC"
EXPOSE 8080
# Render는 PORT를 주입한다. 없으면 8080을 쓴다.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
