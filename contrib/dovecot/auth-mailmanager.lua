
local ltn12 = require("ltn12")
local http = require("socket.http")
local json = require("json")

local base_url = "http://localhost:9000/api/mailbox/"
local passdb_endpoint = base_url .. "authenticate"
local userdb_endpoint = base_url .. "lookup"
local auth_token = "test123"

function protected_decode(data)
    local success, res = pcall(json.decode, data)
    if success then
        return res
    else
        return {}
    end
end

function script_init()
    print("auth-mailmanager started!")
    return 0
end

function script_deinit()
end

function json_request(url, authToken, payload)
    local request_body = json.encode(payload)

    local response_sink = {}
    local request = {
        url = url,
        method = "POST",
        redirect = true,
        headers = {
            ["X-Auth-Token"] = authToken,
            ["Content-Type"] = "application/json",
            ["Content-Length"] = tostring(string.len(request_body))
        },
        source = ltn12.source.string(request_body),
        sink = ltn12.sink.table(response_sink)
    }
    local _, code, _, status = http.request(request)

    return code, status, table.concat(response_sink)
end

function auth_passdb_verify(req)
    local payload = {
        mailbox = req.user,
        password = req.password
    }

    local code, status, response_body = json_request(passdb_endpoint, auth_token, payload)

    if code == 200 then
        local result = protected_decode(response_body)
        if next(result) == nil then
            req.log_error("Response body: " .. response_body)
            return dovecot.auth.PASSDB_RESULT_INTERNAL_FAILURE, "request failed (invalid response)"
        end

        return dovecot.auth.PASSDB_RESULT_OK, result
    end

    if code == 404 then
        return dovecot.auth.PASSDB_RESULT_USER_UNKNOWN, "unknown user"
    end

    if code == 401 then
        return dovecot.auth.PASSDB_RESULT_PASSWORD_MISMATCH, "wrong password"
    end

    if code >= 400 and code < 500 then
        req.log_error("Response status: " .. status)
        return dovecot.auth.PASSDB_RESULT_INTERNAL_FAILURE, "request failed (client error)"
    end

    if code >= 500 then
        req.log_error("Response status: " .. status)
        return dovecot.auth.PASSDB_RESULT_INTERNAL_FAILURE, "request failed (server error)"
    end

    return dovecot.auth.PASSDB_RESULT_NEXT, "next please"
end

function auth_userdb_lookup(req)
    local payload = {
        mailbox = req.user,
    }

    local code, status, response_body = json_request(userdb_endpoint, auth_token, payload)

    if code == 200 then
        local result = protected_decode(response_body)
        if next(result) == nil then
            req.log_error("Response body: " .. response_body)
            return dovecot.auth.USERDB_RESULT_INTERNAL_FAILURE, "request failed (invalid response)"
        end

        return dovecot.auth.USERDB_RESULT_OK, result
    end

    if code == 404 then
        return dovecot.auth.USERDB_RESULT_USER_UNKNOWN, "unknown user"
    end

    if code >= 400 and code < 500 then
        req.log_error("Response status: " .. status)
        return dovecot.auth.USERDB_RESULT_INTERNAL_FAILURE, "request failed (client error)"
    end

    if code >= 500 then
        req.log_error("Response status: " .. status)
        return dovecot.auth.USERDB_RESULT_INTERNAL_FAILURE, "request failed (server error)"
    end
end

--[[

dovecot = {
    auth = {
        PASSDB_RESULT_NEXT = 0,
        PASSDB_RESULT_OK = 1,
        PASSDB_RESULT_USER_UNKNOWN = 2,
        PASSDB_RESULT_PASSWORD_MISMATCH = 3,
        PASSDB_RESULT_INTERNAL_FAILURE = 4,
        USERDB_RESULT_OK = 5,
        USERDB_RESULT_USER_UNKNOWN  = 6,
        USERDB_RESULT_INTERNAL_FAILURE = 7,
    }
}

function log(text)
    print(text)
end

dummy_req = {
    user = "test",
    password = "test",
    log_debug = log,
    log_error = log,
    log_info = log,
    log_warning = log,
}

script_init()

print(auth_passdb_verify(dummy_req))
print(auth_userdb_lookup(dummy_req))

]]
