# postfix-rest-connector

A simple TCP server that can be used as remote lookup for the Postfix mail server.


## Configuration

```json
{
  "user-agent": "Postfix REST Connector",
  "endpoints": []
}
```

* `user-agent`: The user-agent to use for outgoing web requests
* `endpoints`: The list of endpoints to setup

### Endpoint

Example:

```json
{
  "name": "domain-lookup",
  "mode": "tcp-lookup",
  "target": "https://somehost/tcp-lookup-route",
  "bind-address": "0.0.0.0",
  "bind-port": 9000,
  "auth-token": "test123",
  "request-timeout": 2000
}
```

* `name`: A name for logs
* `mode`: The kind of endpoint
* `bind-address`: The local IP address to bind to
* `bind-port`: The local TCP port to bind
* `auth-token`: An authentication token that the remote application can verify
* `request-timeout`: The request timeout in milliseconds


### Modes

#### TCP

Endpoint example:

```json
{
  "name": "domain-lookup",
  "mode": "tcp-lookup",
  "target": "https://somehost/tcp-lookup-route",
  "bind-address": "0.0.0.0",
  "bind-port": 9001,
  "auth-token": "test123",
  "request-timeout": 2000
}
```

[Postfix](http://www.postfix.org/tcp_table.5.html) example:

```
virtual_mailbox_domains = tcp:localhost:9001
```

#### Socketmap

Endpoint example:

```json
{
  "name": "domain-lookup",
  "mode": "socketmap-lookup",
  "target": "https://somehost/socketmap-lookup-route",
  "bind-address": "0.0.0.0",
  "bind-port": 9002,
  "auth-token": "test123",
  "request-timeout": 2000
}
```

[Postfix](http://www.postfix.org/socketmap_table.5.html) example:

```
virtual_mailbox_domains = socketmap:inet:localhost:9002:domain
```

#### Policy Check

Endpoint example:

```json
{
  "name": "domain-lookup",
  "mode": "policy",
  "target": "https://somehost/policy-check-route",
  "bind-address": "0.0.0.0",
  "bind-port": 9003,
  "auth-token": "test123",
  "request-timeout": 2000
}
```

[Postfix](http://www.postfix.org/SMTPD_POLICY_README.html) example:

```
smtpd_relay_restrictions =
    permit_mynetworks
    check_policy_service inet:localhost:9003
    reject
```
