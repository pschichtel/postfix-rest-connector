
-- Configuration
local base_url = "http://localhost:9000/api/mailbox/"
local passdb_endpoint = base_url .. "authenticate"
local userdb_endpoint = base_url .. "lookup"
local request_headers = {
    ["X-Auth-Token"] = "test123"
}
local static_values = {
}
-- End of Configuration

local ltn12 = require("ltn12")
local http = require("socket.http")
local json = require("json")

function script_init()
    print("auth-mailmanager started!")
    return 0
end

function script_deinit()
end

function auth_password_verify(req, password)
    local payload = {
        mailbox = req.username,
        password = password
    }

    local code, status, response_body = json_request(passdb_endpoint, payload)

    if code == 200 then
        local result = protected_decode(response_body)
        if next(result) == nil then
            req.log_error("Response body: " .. response_body)
            return dovecot.auth.PASSDB_RESULT_INTERNAL_FAILURE, "request failed (invalid response)"
        end

        return dovecot.auth.PASSDB_RESULT_OK, tableMerge(result, static_values)
    end

    if code == 404 then
        return dovecot.auth.PASSDB_RESULT_USER_UNKNOWN, {}
    end

    if code == 401 then
        return dovecot.auth.PASSDB_RESULT_PASSWORD_MISMATCH, {}
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
        mailbox = req.username,
    }

    local code, status, response_body = json_request(userdb_endpoint, payload)

    if code == 200 then
        local result = protected_decode(response_body)
        if next(result) == nil then
            req.log_error("Response body: " .. response_body)
            return dovecot.auth.USERDB_RESULT_INTERNAL_FAILURE, "request failed (invalid response)"
        end

        return dovecot.auth.USERDB_RESULT_OK, tableMerge(result, static_values)
    end

    if code == 404 then
        return dovecot.auth.USERDB_RESULT_USER_UNKNOWN, {}
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

function json_request(url, payload)
    local request_body = json.encode(payload)

    local response_sink = {}
    local request = {
        url = url,
        method = "POST",
        redirect = true,
        headers = tableMerge(request_headers, {
            ["Content-Type"] = "application/json",
            ["Content-Length"] = tostring(string.len(request_body))
        }),
        source = ltn12.source.string(request_body),
        sink = ltn12.sink.table(response_sink)
    }
    local _, code, _, status = http.request(request)

    return code, status, table.concat(response_sink)
end

function tableMerge(first_table, second_table)
    local out = {}
    for k, v in pairs(first_table) do out[k] = v end
    for k,v in pairs(second_table) do out[k] = v end
    return out
end

function protected_decode(data)
    local success, res = pcall(json.decode, data)
    if success then
        return res
    else
        return {}
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
    log_debug = log,
    log_error = log,
    log_info = log,
    log_warning = log,
}
password = "test"

script_init()

local pretty = require("pl.pretty")

local _, result = auth_password_verify(dummy_req, password)
pretty.dump(result)

local _, result = auth_userdb_lookup(dummy_req)
pretty.dump(result)

]]
