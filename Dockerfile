FROM debian:bookworm-slim

RUN apt update && apt full-upgrade -y
RUN apt install libcurl4 -y

ARG FILE_PATH

COPY --chmod=755 "$FILE_PATH" /usr/local/bin/postfix-rest-connector

ENTRYPOINT [ "postfix-rest-connector" ]
