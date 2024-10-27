FROM debian:bookworm-slim

ARG FILE_PATH

COPY --chmod=755 "$FILE_PATH" /usr/local/bin/postfix-rest-connector

ENTRYPOINT [ "postfix-rest-connector" ]
