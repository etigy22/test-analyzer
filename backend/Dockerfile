FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre AS runtime-base
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl fonts-dejavu-core && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 repoguard && useradd --uid 10001 --gid 10001 --no-create-home repoguard \
    && mkdir -p /app/var/work /app/var/quarantine /app/var/reports /signatures \
    && chown -R 10001:10001 /app /signatures
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=45 -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/repoguard.jar"]

FROM runtime-base AS runtime
COPY --from=build /workspace/target/repoguard-backend-0.1.0-SNAPSHOT.jar /app/repoguard.jar

FROM aquasec/trivy:0.67.2 AS trivy

FROM node:24.11.0-bookworm-slim AS eslint
WORKDIR /opt/eslint
RUN npm install --save-exact --ignore-scripts eslint@9.39.1 @typescript-eslint/parser@8.46.3 typescript@5.8.3

FROM composer:2.8 AS phpcs
WORKDIR /opt/phpcs
RUN composer require --no-interaction --no-plugins --no-scripts squizlabs/php_codesniffer:4.0.1

FROM ghcr.io/realm/swiftlint:0.62.2 AS swiftlint
RUN mkdir /opt/bundle && cp /usr/bin/swiftlint /usr/lib/libsourcekitdInProc.so /lib64/ld-linux-x86-64.so.2 /opt/bundle/ \
    && (ldd /usr/bin/swiftlint; ldd /usr/lib/libsourcekitdInProc.so) | awk '/=> \// {print $3}' | sort -u | xargs -I{} cp -L {} /opt/bundle/

FROM python:3.12.12-slim-bookworm AS python-scanners
RUN pip install --no-cache-dir bandit==1.8.6 ruff==0.14.3 semgrep==1.183.0 pydantic==2.11.10 pydantic-settings==2.10.1

FROM maven:3.9.11-eclipse-temurin-21 AS checkstyle
WORKDIR /opt/checkstyle
COPY scanners/checkstyle-pom.xml pom.xml
RUN mvn -B -q dependency:copy-dependencies -DoutputDirectory=/opt/checkstyle/lib

FROM runtime-base AS scanner
USER root
RUN apt-get update && apt-get install -y --no-install-recommends clamav clamav-freshclam cppcheck checkstyle golang-go rustfmt php-cli php-xml php-mbstring libexpat1 libffi8 && rm -rf /var/lib/apt/lists/*
COPY --from=python-scanners /usr/local /usr/local
RUN mkdir -p /opt/repoguard && ln -s /usr/local/bin /opt/repoguard/bin && ldconfig
COPY --from=trivy /usr/local/bin/trivy /usr/local/bin/trivy
COPY --from=eslint /usr/local/bin/node /usr/local/bin/node
COPY --from=eslint /opt/eslint /opt/eslint
COPY --from=phpcs /opt/phpcs /opt/phpcs
COPY --from=swiftlint /opt/bundle /opt/swiftlint
COPY --from=checkstyle /opt/checkstyle/lib /opt/checkstyle/lib
RUN mkdir -p /opt/detekt && curl -fsSL https://repo.maven.apache.org/maven2/io/gitlab/arturbosch/detekt/detekt-cli/1.23.8/detekt-cli-1.23.8-all.jar -o /opt/detekt/cli.jar \
    && curl -fsSL https://repo.maven.apache.org/maven2/io/gitlab/arturbosch/detekt/detekt-cli/1.23.8/detekt-cli-1.23.8-all.jar.sha256 -o /opt/detekt/expected.sha256 \
    && echo "$(cat /opt/detekt/expected.sha256)  /opt/detekt/cli.jar" | sha256sum -c -
COPY scanners/ /etc/repoguard/scanners/
COPY clamav/freshclam.conf /etc/repoguard/freshclam.conf
RUN touch /etc/repoguard/empty.txt && chmod 644 /etc/repoguard/freshclam.conf /etc/repoguard/empty.txt \
    && chmod 755 /etc/repoguard/scanners \
    && mkdir -p /scanner-cache && chown 10001:10001 /scanner-cache
USER 10001:10001
COPY --from=build /workspace/target/repoguard-backend-0.1.0-SNAPSHOT.jar /app/repoguard.jar
