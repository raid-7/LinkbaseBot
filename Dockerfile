FROM maven:3.6-jdk-11-slim AS builder

COPY . /app
WORKDIR /app

RUN mvn install


FROM bellsoft/liberica-openjre-debian:10 AS runner

RUN apt-get update && apt-get install -y curl nano htop

WORKDIR /app
COPY --from=builder /app/search/search.jar ./
COPY --from=builder /app/bot/bot.jar ./
