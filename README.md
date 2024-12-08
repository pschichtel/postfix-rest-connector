# postfix-rest-connector

A simple TCP server that can be used as remote lookup for the Postfix mail server.

## Running

Either install one of the available binaries onto you system or run the container image `ghcr.io/pschichtel/postfix-rest-connector`.

The program accepts exactly one commandline argument: The path, relative or absolute, to a configuration file
as described by the following section. The container images don't contain such configuration, but the file `sample.json`
provides a somewhat complete example to craft one.

### Native Binaries

The native binaries use different ktor client engines depending on the target platform:

* Linux X86_64 uses the curl engine and as such the binary dynamically links libcurl.
* Linux Arm 64 uses the CIO engine, no further dependencies. **Note**: This binary does **not** support TLS connections.
* Windows uses the WinHttp engine

## Configuration

```json
{
  "user-agent": "Postfix REST Connector",
  "endpoints": []
}
```

* `user-agent`: The user-agent to use for outgoing web requests
* `endpoints`: The list of endpoints to set up

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
* `target`: The URL to be called
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
virtual_mailbox_domains = tcp:127.0.0.1:9001
```

##### Minimal Request

```
GET {target-path}?key={lookup-key} HTTP/1.0
Host: {target-host}
User-Agent: {user-agent}
X-Auth-Token: {auth-token}

```

##### Minimal Expected Successful Response

```
HTTP/1.0 200 OK
Content-Length: {length}

["json", "string", "array"]
```

##### Error Response Statuses

* `404`: For new results
* Anything >= 400 and < 500: Signal misconfiguration (permanent error)
* Anything >= 500 and < 600: Signal technical error (temporary error)

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
virtual_mailbox_domains = socketmap:inet:127.0.0.1:9002:domain
```

##### Minimal Request

```
GET {target-path}?name={map name}&key={lookup-key} HTTP/1.0
Host: {target-host}
User-Agent: {user-agent}
X-Auth-Token: {auth-token}

```

##### Minimal Expected Successful Response

```
HTTP/1.0 200 OK
Content-Length: {length}

["json", "string", "array"]
```

##### Error Response Statuses

* `404`: For new results
* Anything >= 400 and < 500: Signal misconfiguration (permanent error)
* Anything >= 500 and < 600: Signal technical error (temporary error)

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
    check_policy_service inet:127.0.0.1:9003
    reject
```

##### Minimal Request

```
GET {target-path} HTTP/1.0
Host: {target-host}
User-Agent: {user-agent}
X-Auth-Token: {auth-token}

name=value&name2=value2
```

Actual values are documented at [the Postfix policy documentation](http://www.postfix.org/SMTPD_POLICY_README.html).

##### Minimal Expected Successful Response

```
HTTP/1.0 200 OK
Content-Length: {length}

{policy action}
```

##### Error Response Statuses

* Anything >= 400 and < 500: Signal misconfiguration (permanent error)
* Anything >= 500 and < 600: Signal technical error (temporary error)
