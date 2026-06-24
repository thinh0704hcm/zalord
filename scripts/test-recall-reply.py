"""E2E: recall + reply."""
import asyncio, json, time, urllib.request, urllib.error
import websockets

API = "http://kong:8000/api/v1"
WS  = "ws://kong:8000/ws/chat"

def req(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token: headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(f"{API}{path}", data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(r) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())

def hdr(t): return {"Authorization": f"Bearer {t}"}

A = req("POST", "/auth/login", {"phoneNumber":"0900111001","password":"secret123"})[1]["data"]
B = req("POST", "/auth/login", {"phoneNumber":"0900111002","password":"secret123"})[1]["data"]
ATOK, BTOK = A["accessToken"], B["accessToken"]
BUID = req("GET", "/users/me", token=BTOK)[1]["userId"]
AUID = req("GET", "/users/me", token=ATOK)[1]["userId"]
CID  = req("POST", "/conversations", {"type":"DIRECT","memberUserId":BUID}, ATOK)[1]["data"]["id"]
print(f"conv={CID}\nalice={AUID}  bob={BUID}")

async def collect(ws, seconds):
    out = []
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        try:
            raw = await asyncio.wait_for(ws.recv(), timeout=max(0.01, end - time.monotonic()))
            out.append(json.loads(raw))
        except (asyncio.TimeoutError, websockets.ConnectionClosed):
            break
    return out

async def run():
    alice = await websockets.connect(WS, additional_headers=hdr(ATOK))
    bob   = await websockets.connect(WS, additional_headers=hdr(BTOK))
    await asyncio.sleep(0.5)

    # --- REPLY ---
    print("\n=== T1: reply — Alice sends 'hello' then Bob replies quoting it ===")
    code, sent = req("POST", "/messages", {"conversationId":CID,"content":"hello world"}, ATOK)
    parent_id = sent["data"]["id"]
    print(f"  parent={parent_id}")
    await asyncio.sleep(2.5)

    code, rep = req("POST", "/messages",
                    {"conversationId":CID, "content":"yes I see it",
                     "replyToMessageId": parent_id}, BTOK)
    assert code == 201, f"reply failed: {rep}"
    print(f"  reply response: {json.dumps(rep['data'], indent=2)}")
    assert rep["data"]["replyTo"]["messageId"] == parent_id
    assert rep["data"]["replyTo"]["senderId"] == AUID
    assert rep["data"]["replyTo"]["preview"] == "hello world"
    print("  PASS reply snapshot in response")

    # Alice should receive WS frame with replyTo embedded
    await asyncio.sleep(3)
    fr = await collect(alice, 0.5)
    msg_frames = [f for f in fr if f.get("type") == "message.created"]
    print(f"  alice WS frames: {len(msg_frames)}")
    reply_frame = next(f for f in msg_frames if f["data"].get("replyTo"))
    assert reply_frame["data"]["replyTo"]["preview"] == "hello world"
    print("  PASS reply snapshot in WS frame")

    # --- REPLY validation: cross-conversation rejected ---
    print("\n=== T2: reply to msg from another conv → 400 ===")
    code, _ = req("POST", "/messages",
                  {"conversationId":CID, "content":"x",
                   "replyToMessageId": "00000000-0000-0000-0000-000000000000"}, BTOK)
    print(f"  HTTP {code}")
    assert code == 400
    print("  PASS reply validation")

    # --- RECALL: send a msg, then recall it ---
    print("\n=== T3: recall — Alice sends 'oops' then recalls it ===")
    code, oops = req("POST", "/messages", {"conversationId":CID,"content":"oops typo"}, ATOK)
    oops_id = oops["data"]["id"]
    print(f"  oops={oops_id}")
    await asyncio.sleep(2.5)
    # drain bob's queue
    await collect(bob, 0.3)

    code, _ = req("DELETE", f"/messages/{oops_id}", token=ATOK)
    print(f"  recall HTTP {code}")
    assert code == 200

    await asyncio.sleep(3)
    fr = await collect(bob, 1.0)
    recall_frames = [f for f in fr if f.get("type") == "message.recalled"]
    print(f"  bob recall frames: {recall_frames}")
    assert recall_frames and recall_frames[0]["data"]["messageId"] == oops_id
    print("  PASS recall fan-out")

    # --- RECALL: non-sender → 403 ---
    print("\n=== T4: Bob tries to recall Alice's message → 403 ===")
    code, body = req("POST", "/messages", {"conversationId":CID,"content":"mine"}, ATOK)
    alice_msg = body["data"]["id"]
    code, err = req("DELETE", f"/messages/{alice_msg}", token=BTOK)
    print(f"  HTTP {code}, errorCode={err.get('errorCode')}")
    assert code == 403
    print("  PASS recall authz")

    # --- RECALL: history returns recalled marker ---
    print("\n=== T5: GET /messages returns recalledAt + blanked content ===")
    code, hist = req("GET", f"/messages?conversationId={CID}&page=1&size=10", token=BTOK)
    rec = next(m for m in hist["data"]["items"] if m["id"] == oops_id)
    print(f"  recalled msg: content={rec['content']!r} recalledAt={rec['recalledAt']}")
    assert rec["recalledAt"] is not None
    assert rec["content"] == ""
    print("  PASS history blanks recalled body")

    # --- RECALL: inbox preview gets rewritten ---
    print("\n=== T6: inbox preview after recalling latest msg ===")
    # Send a fresh one, recall it, verify Bob's inbox preview rolled back
    code, latest = req("POST", "/messages", {"conversationId":CID,"content":"freshest"}, ATOK)
    latest_id = latest["data"]["id"]
    await asyncio.sleep(2.5)
    inbox_before = req("GET", "/inbox", token=BTOK)[1]["data"]["items"]
    pv_before = next(i for i in inbox_before if i["conversationId"] == CID)["lastMessagePreview"]
    print(f"  preview before recall: {pv_before!r}")
    assert pv_before == "freshest"

    req("DELETE", f"/messages/{latest_id}", token=ATOK)
    await asyncio.sleep(3)
    inbox_after = req("GET", "/inbox", token=BTOK)[1]["data"]["items"]
    pv_after = next(i for i in inbox_after if i["conversationId"] == CID)["lastMessagePreview"]
    print(f"  preview after recall: {pv_after!r}")
    assert pv_after != "freshest"  # rolled back to previous non-recalled msg
    print("  PASS inbox preview rewritten")

    await alice.close()
    await bob.close()
    print("\nALL TESTS PASSED")

asyncio.run(run())
