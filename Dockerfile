FROM maven:3.6-jdk-11-slim AS builder

COPY . /app
WORKDIR /app

RUN mvn install


FROM bellsoft/liberica-openjre-debian:10 AS runner

RUN apt-get update && apt-get install -y curl nano htop

WORKDIR /app
COPY --from=builder /app/search/target/search-1.0-SNAPSHOT-jar-with-dependencies.jar ./search.jar
COPY --from=builder /app/bot/target/bot-1.0-SNAPSHOT-jar-with-dependencies.jar ./bot.jar
