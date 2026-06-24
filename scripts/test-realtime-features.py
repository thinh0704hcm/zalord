"""E2E test: typing + presence + seen."""
import asyncio, json, time, urllib.request
import websockets

API = "http://kong:8000/api/v1"
WS  = "ws://kong:8000/ws/chat"

def post(path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token: headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else b""
    req = urllib.request.Request(f"{API}{path}", data=data, method="POST", headers=headers)
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())

def get(path, token=None):
    headers = {}
    if token: headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{API}{path}", headers=headers)
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())

def hdr(t): return {"Authorization": f"Bearer {t}"}

A = post("/auth/login", {"phoneNumber":"0900111001","password":"secret123"})["data"]
B = post("/auth/login", {"phoneNumber":"0900111002","password":"secret123"})["data"]
ATOK, BTOK = A["accessToken"], B["accessToken"]
BUID = get("/users/me", token=BTOK)["userId"]
AUID = get("/users/me", token=ATOK)["userId"]
CID  = post("/conversations", {"type":"DIRECT","memberUserId":BUID}, ATOK)["data"]["id"]
print(f"conv={CID}\nalice={AUID}  bob={BUID}")

async def collect(ws, seconds):
    frames = []
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        try:
            raw = await asyncio.wait_for(ws.recv(), timeout=max(0.01, end - time.monotonic()))
            frames.append(json.loads(raw))
        except (asyncio.TimeoutError, websockets.ConnectionClosed):
            break
    return frames

async def run():
    alice = await websockets.connect(WS, additional_headers=hdr(ATOK))
    bob   = await websockets.connect(WS, additional_headers=hdr(BTOK))
    await asyncio.sleep(0.5)

    print("\n=== T1: typing ===")
    await alice.send(json.dumps({"type":"typing","data":{"conversationId":CID,"isTyping":True}}))
    fr = await collect(bob, 1.0)
    typ = [f for f in fr if f.get("type") == "typing"]
    print("  bob got:", typ)
    assert typ and typ[0]["data"]["userId"] == AUID and typ[0]["data"]["isTyping"] is True
    print("  PASS typing")

    print("\n=== T2: presence.query ===")
    await alice.send(json.dumps({"type":"presence.query","data":{"userIds":[BUID, AUID]}}))
    fr = await collect(alice, 1.0)
    st = [f for f in fr if f.get("type") == "presence.state"]
    print("  reply:", st)
    assert st and st[0]["data"]["states"][BUID] == "online" and st[0]["data"]["states"][AUID] == "online"
    print("  PASS presence.query")

    print("\n=== T3: presence transition (bob closes -> alice sees offline) ===")
    await alice.send(json.dumps({"type":"presence.watch","data":{"userIds":[BUID]}}))
    await asyncio.sleep(0.3)
    await bob.close()
    fr = await collect(alice, 2.5)
    pres = [f for f in fr if f.get("type") == "presence"]
    print("  alice transitions:", pres)
    assert any(f["data"]["status"] == "offline" and f["data"]["userId"] == BUID for f in pres)
    print("  PASS presence transition")

    print("\n=== T4: seen (bob reads -> alice sees message.read) ===")
    msg = post("/messages", {"conversationId":CID,"content":"are you there"}, ATOK)["data"]
    print(f"  sent msg={msg['id']}")
    await asyncio.sleep(2.5)
    bob2 = await websockets.connect(WS, additional_headers=hdr(BTOK))
    await asyncio.sleep(0.5)
    post(f"/inbox/{CID}/read", {"messageId": msg["id"]}, BTOK)
    fr = await collect(alice, 5.0)
    read = [f for f in fr if f.get("type") == "message.read"]
    print("  alice read:", read)
    assert read and read[0]["data"]["readerId"] == BUID and read[0]["data"]["lastReadMessageId"] == msg["id"]
    print("  PASS seen")

    print("\n=== T5: inbox API returns lastReadMessageId for bob ===")
    inbox = get("/inbox", token=BTOK)["data"]["items"]
    mv = next(i for i in inbox if i["conversationId"] == CID)
    print(f"  bob view: lastReadMessageId={mv.get('lastReadMessageId')} unread={mv['unreadCount']}")
    assert mv["lastReadMessageId"] == msg["id"] and mv["unreadCount"] == 0
    print("  PASS inbox API")

    await alice.close()
    await bob2.close()
    print("\nALL TESTS PASSED")

asyncio.run(run())
