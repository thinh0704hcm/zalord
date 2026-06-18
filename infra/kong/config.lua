-- Decode the (already signature-verified) JWT and forward identity as
-- headers. cjson parses the payload properly — no regex on JSON.
local cjson = require("cjson.safe")

-- Strip any client-supplied identity headers so they can't be forged.
kong.service.request.clear_header("X-User-Id")
kong.service.request.clear_header("X-User-Roles")

local auth = kong.request.get_header("authorization")
if not auth then return end

local token = auth:match("^[Bb]earer%s+(.+)$")
if not token then return end

local payload_b64 = token:match("^[^.]+%.([^.]+)%.[^.]+$")
if not payload_b64 then return end

-- base64url -> base64 (+ padding) so ngx.decode_base64 can read it
payload_b64 = payload_b64:gsub("-", "+"):gsub("_", "/")
local pad = #payload_b64 % 4
if pad > 0 then payload_b64 = payload_b64 .. string.rep("=", 4 - pad) end

local json = ngx.decode_base64(payload_b64)
if not json then return end

local claims = cjson.decode(json)
if type(claims) ~= "table" then return end

if claims.sub then
  kong.service.request.set_header("X-User-Id", claims.sub)
end

-- roles claim is a JSON array: "roles":["ADMIN","USER"]
local roles = claims.roles
if type(roles) == "table" then
  kong.service.request.set_header("X-User-Roles", table.concat(roles, ","))
elseif type(roles) == "string" then
  kong.service.request.set_header("X-User-Roles", roles)
end