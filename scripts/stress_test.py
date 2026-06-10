"""
Evil concurrency stress test for the tCketManage backend.

WHAT IT DOES
  1. Creates one big event (capacity ~3000 seats spread over several ticket
     types, including a tiny "FLASH" type the threads deliberately fight over).
  2. Fires N worker threads (default 10) that hammer the API with a random mix
     of order creation, cancellation, idempotent payment confirmation, oversell
     attempts, and reads — for a fixed duration.
  3. Classifies every response. 2xx and the *expected* 4xx (400/404/409) are
     normal; any 5xx, connection error, or timeout is recorded as a FINDING.
  4. After the storm, checks the core invariant the locking strategy exists to
     guarantee: for every ticket type, the number of issued tickets must never
     exceed its capacity, and must equal the number of seats we saw sold. A
     violation means the pessimistic locks / atomic capacity UPDATEs leaked
     under concurrency.

WHY POSTGRES
  The row locks (findByIdForUpdate) and atomic conditional capacity UPDATEs are
  effectively no-ops on SQLite. Run this against the Postgres stack
  (docker compose up --build) for the invariant to mean anything.

USAGE
  python scripts/stress_test.py
  python scripts/stress_test.py --base http://localhost:8080 --threads 10 --duration 30
"""
import argparse
import random
import string
import sys
import threading
import time
import uuid
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor

import requests

# --- Ticket-type plan: capacities sum to exactly 3000 ------------------------
# "FLASH" is intentionally tiny so concurrent buyers collide on its last seats —
# the sharpest probe for an oversell bug.
TICKET_PLAN = [
    {"key": "ga",      "name": "General Admission", "price": 25.00,  "capacity": 2000},
    {"key": "vip",     "name": "VIP",               "price": 80.00,  "capacity": 500},
    {"key": "balcony", "name": "Balcony",           "price": 45.00,  "capacity": 450},
    {"key": "flash",   "name": "Flash Sale",        "price": 10.00,  "capacity": 50},
]
TOTAL_CAPACITY = sum(t["capacity"] for t in TICKET_PLAN)  # 3000

ADMIN_TOKEN = "changeme-dev-token"


def rand_email():
    return f"load+{uuid.uuid4().hex[:10]}@example.test"


def rand_name():
    return "".join(random.choices(string.ascii_uppercase, k=6))


class Stats:
    """Thread-safe tally of outcomes, status codes, latencies, and findings."""

    def __init__(self):
        self._lock = threading.Lock()
        self.status_codes = Counter()
        self.op_counts = Counter()
        self.findings = []                 # (op, detail) for 5xx / exceptions
        self.latencies = []
        self.requests = 0
        # seats we believe were SOLD (order created -> PAID), per ticket-type id
        self.sold_by_type = Counter()
        self.created_order_ids = []        # for cancel/confirm targets

    def record(self, op, status, latency_ms, finding=None):
        with self._lock:
            self.requests += 1
            self.op_counts[op] += 1
            self.status_codes[status] += 1
            self.latencies.append(latency_ms)
            if finding:
                self.findings.append((op, finding))

    def record_sale(self, type_counts):
        with self._lock:
            self.sold_by_type.update(type_counts)

    def add_order(self, order_id):
        with self._lock:
            self.created_order_ids.append(order_id)

    def sample_order(self):
        with self._lock:
            return random.choice(self.created_order_ids) if self.created_order_ids else None


def setup_event(session, base):
    """Create the big event and return (event_id, [ticket_type dicts with id])."""
    payload = {
        "name": f"STRESS {uuid.uuid4().hex[:8]}",
        "time": "2026-12-31T23:00:00-05:00",
        "location": "Load Test Arena",
        "description": "Synthetic event for concurrency stress testing",
        "zones": [{"key": "main", "name": "Main", "description": "Main floor"}],
        "ticketTypes": [
            {
                "name": t["name"],
                "price": t["price"],
                "isActive": True,
                "capacity": t["capacity"],
                "entitlements": [{"zoneKey": "main", "maxEntries": 1}],
            }
            for t in TICKET_PLAN
        ],
    }
    r = session.post(f"{base}/api/v1/events/full", json=payload, timeout=30)
    if r.status_code != 201:
        sys.exit(f"Setup failed: POST /events/full -> {r.status_code}\n{r.text[:500]}")
    full = r.json()
    event_id = full["event"]["id"]
    types = full["ticketTypes"]
    # Map our plan onto the returned ids by name.
    by_name = {t["name"]: t["id"] for t in types}
    plan = []
    for t in TICKET_PLAN:
        plan.append({**t, "id": by_name[t["name"]]})
    return event_id, plan


# --- The random operations a worker can perform ------------------------------

def op_buy(session, base, stats, event_id, plan):
    """Create a small order (1-3 seats) of random types — settles to PAID."""
    n = random.randint(1, 3)
    chosen = random.choices(plan, weights=[5, 2, 2, 3], k=n)  # bias toward GA + FLASH
    items = [
        {
            "ticketTypeId": t["id"],
            "attendeeFirstName": rand_name(),
            "attendeeLastName": rand_name(),
            "attendeeEmail": rand_email(),
        }
        for t in chosen
    ]
    payload = {"buyerEmail": rand_email(), "eventId": event_id, "items": items}
    t0 = time.perf_counter()
    try:
        r = session.post(f"{base}/api/v1/orders", json=payload, timeout=30)
    except requests.RequestException as e:
        stats.record("buy", "EXC", (time.perf_counter() - t0) * 1000, finding=repr(e))
        return
    dt = (time.perf_counter() - t0) * 1000
    finding = r.text[:200] if r.status_code >= 500 else None
    stats.record("buy", r.status_code, dt, finding)
    if r.status_code == 201:
        body = r.json()
        stats.add_order(body["id"])
        if body.get("status") == "PAID":
            counts = Counter(t["id"] for t in chosen)
            stats.record_sale(counts)
    # 409 = sold out / not enough capacity: expected, not a finding.


def op_oversell(session, base, stats, event_id, plan):
    """Hammer the FLASH type with a big batch to force capacity conflicts."""
    flash = next(t for t in plan if t["key"] == "flash")
    n = random.randint(10, 60)
    items = [
        {
            "ticketTypeId": flash["id"],
            "attendeeFirstName": rand_name(),
            "attendeeLastName": rand_name(),
            "attendeeEmail": rand_email(),
        }
        for _ in range(n)
    ]
    payload = {"buyerEmail": rand_email(), "eventId": event_id, "items": items}
    t0 = time.perf_counter()
    try:
        r = session.post(f"{base}/api/v1/orders", json=payload, timeout=30)
    except requests.RequestException as e:
        stats.record("oversell", "EXC", (time.perf_counter() - t0) * 1000, finding=repr(e))
        return
    dt = (time.perf_counter() - t0) * 1000
    finding = r.text[:200] if r.status_code >= 500 else None
    stats.record("oversell", r.status_code, dt, finding)
    if r.status_code == 201:
        body = r.json()
        stats.add_order(body["id"])
        if body.get("status") == "PAID":
            stats.record_sale(Counter({flash["id"]: n}))


def op_cancel(session, base, stats, event_id, plan):
    """Cancel a random known order (mostly PAID -> 409) or a phantom id (404)."""
    oid = stats.sample_order() or str(uuid.uuid4())
    t0 = time.perf_counter()
    try:
        r = session.post(f"{base}/api/v1/orders/{oid}/cancel", timeout=30)
    except requests.RequestException as e:
        stats.record("cancel", "EXC", (time.perf_counter() - t0) * 1000, finding=repr(e))
        return
    dt = (time.perf_counter() - t0) * 1000
    finding = r.text[:200] if r.status_code >= 500 else None
    stats.record("cancel", r.status_code, dt, finding)


def op_confirm(session, base, stats, event_id, plan):
    """Re-confirm a known order via the mock complete hook — tests idempotency."""
    oid = stats.sample_order()
    if not oid:
        return
    t0 = time.perf_counter()
    try:
        r = session.post(
            f"{base}/api/v1/payments/mock/{oid}/complete",
            headers={"X-Admin-Token": ADMIN_TOKEN},
            timeout=30,
        )
    except requests.RequestException as e:
        stats.record("confirm", "EXC", (time.perf_counter() - t0) * 1000, finding=repr(e))
        return
    dt = (time.perf_counter() - t0) * 1000
    finding = r.text[:200] if r.status_code >= 500 else None
    stats.record("confirm", r.status_code, dt, finding)


def op_read(session, base, stats, event_id, plan):
    """Random GET against a mix of real and phantom resources."""
    choice = random.random()
    oid = stats.sample_order()
    if choice < 0.3:
        url, op = f"{base}/api/v1/events/{event_id}", "read_event"
    elif choice < 0.5:
        url, op = f"{base}/api/v1/orders?eventId={event_id}", "read_orders"
    elif choice < 0.7:
        url, op = f"{base}/api/v1/events/{event_id}/tickets?size=20", "read_tickets"
    elif choice < 0.85 and oid:
        url, op = f"{base}/api/v1/orders/{oid}", "read_order"
    else:
        url, op = f"{base}/api/v1/orders/{uuid.uuid4()}", "read_phantom"  # expect 404
    t0 = time.perf_counter()
    try:
        r = session.get(url, timeout=30)
    except requests.RequestException as e:
        stats.record(op, "EXC", (time.perf_counter() - t0) * 1000, finding=repr(e))
        return
    dt = (time.perf_counter() - t0) * 1000
    finding = r.text[:200] if r.status_code >= 500 else None
    stats.record(op, r.status_code, dt, finding)


# weighted operation table: (function, weight)
OPS = [
    (op_buy, 50),
    (op_oversell, 8),
    (op_cancel, 12),
    (op_confirm, 10),
    (op_read, 20),
]
_OP_FUNCS = [o[0] for o in OPS]
_OP_WEIGHTS = [o[1] for o in OPS]


def worker(base, stats, event_id, plan, stop_at):
    session = requests.Session()
    while time.time() < stop_at:
        op = random.choices(_OP_FUNCS, weights=_OP_WEIGHTS, k=1)[0]
        op(session, base, stats, event_id, plan)


def fetch_issued_counts(session, base, event_id):
    """Page through every issued ticket and count them by ticket-type id.

    Returns (counts, error). On a server-side failure we surface it rather than
    crashing the report — a 500 here is itself a finding worth printing.
    """
    counts = Counter()
    page, size = 0, 500
    while True:
        try:
            r = session.get(
                f"{base}/api/v1/events/{event_id}/tickets",
                params={"page": page, "size": size},
                timeout=60,
            )
        except requests.RequestException as e:
            return counts, f"exception paging tickets at page {page}: {e!r}"
        if r.status_code >= 500:
            return counts, f"HTTP {r.status_code} paging tickets at page {page}: {r.text[:200]}"
        body = r.json()
        for t in body.get("content", []):
            tt = t.get("ticketType") or {}
            if tt.get("id"):
                counts[tt["id"]] += 1
        if body.get("last", True) or not body.get("content"):
            break
        page += 1
    return counts, None


def main():
    ap = argparse.ArgumentParser(description="Concurrency stress test for tCketManage")
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--threads", type=int, default=10)
    ap.add_argument("--duration", type=float, default=150.0, help="seconds to hammer")
    ap.add_argument("--lifecycle", action="store_true",
                    help="server settles asynchronously (mock auto-confirm OFF + fast expiry "
                         "sweep): skip the issued==sold cross-check, since sales land via raced "
                         "confirms the client can't attribute. Oversell/5xx checks still apply.")
    args = ap.parse_args()
    base = args.base.rstrip("/")

    setup_session = requests.Session()
    print(f"== Setup: creating event with {TOTAL_CAPACITY} seats across "
          f"{len(TICKET_PLAN)} ticket types ==")
    event_id, plan = setup_event(setup_session, base)
    print(f"   eventId = {event_id}")
    for t in plan:
        print(f"   {t['name']:<20} capacity={t['capacity']:<5} id={t['id']}")

    stats = Stats()
    stop_at = time.time() + args.duration
    print(f"\n== Hammering with {args.threads} threads for {args.duration:.0f}s ==")
    start = time.time()
    with ThreadPoolExecutor(max_workers=args.threads) as pool:
        futures = [
            pool.submit(worker, base, stats, event_id, plan, stop_at)
            for _ in range(args.threads)
        ]
        for f in futures:
            f.result()
    elapsed = time.time() - start

    # --- Report -------------------------------------------------------------
    print(f"\n{'='*60}\nRESULTS  ({stats.requests} requests in {elapsed:.1f}s "
          f"= {stats.requests / elapsed:.0f} req/s)\n{'='*60}")

    print("\nStatus codes:")
    for code, n in sorted(stats.status_codes.items(), key=lambda x: str(x[0])):
        print(f"   {str(code):<6} {n}")

    print("\nOperations:")
    for op, n in sorted(stats.op_counts.items()):
        print(f"   {op:<14} {n}")

    if stats.latencies:
        lat = sorted(stats.latencies)
        p = lambda q: lat[min(len(lat) - 1, int(len(lat) * q))]
        print(f"\nLatency ms: p50={p(0.50):.0f}  p95={p(0.95):.0f}  "
              f"p99={p(0.99):.0f}  max={lat[-1]:.0f}")

    # --- Invariant check ----------------------------------------------------
    print(f"\n{'='*60}\nINVARIANT CHECK (issued tickets vs capacity)\n{'='*60}")
    issued, issued_err = fetch_issued_counts(setup_session, base, event_id)
    problems = []
    if issued_err:
        problems.append(("ticket-listing", [issued_err]))
        print(f"   !! could not fully read issued tickets: {issued_err}")
    for t in plan:
        cap = t["capacity"]
        iss = issued.get(t["id"], 0)
        sold = stats.sold_by_type.get(t["id"], 0)
        flags = []
        if iss > cap:
            flags.append(f"OVERSOLD by {iss - cap}")
        if not args.lifecycle and iss != sold:
            flags.append(f"issued({iss}) != observed-sold({sold})")
        status = "  <<< " + "; ".join(flags) if flags else "ok"
        if flags:
            problems.append((t["name"], flags))
        print(f"   {t['name']:<20} cap={cap:<5} issued={iss:<5} sold(seen)={sold:<5} {status}")

    total_issued = sum(issued.values())
    print(f"\n   TOTAL issued={total_issued} / capacity={TOTAL_CAPACITY}")
    if total_issued > TOTAL_CAPACITY:
        problems.append(("TOTAL", [f"OVERSOLD by {total_issued - TOTAL_CAPACITY}"]))

    # --- Findings -----------------------------------------------------------
    server_errors = sum(n for c, n in stats.status_codes.items()
                        if isinstance(c, int) and c >= 500)
    exceptions = stats.status_codes.get("EXC", 0)

    print(f"\n{'='*60}\nFINDINGS\n{'='*60}")
    print(f"   5xx responses:        {server_errors}")
    print(f"   client exceptions:    {exceptions}  (timeouts / connection resets)")
    print(f"   invariant violations: {len(problems)}")
    if stats.findings:
        print("\n   Sample server-side failures:")
        for op, detail in stats.findings[:10]:
            print(f"     [{op}] {detail}")
    if problems:
        print("\n   Invariant violations:")
        for name, flags in problems:
            print(f"     {name}: {'; '.join(flags)}")

    ok = server_errors == 0 and exceptions == 0 and not problems
    print(f"\n{'='*60}")
    print("VERDICT:", "PASS — no oversell, no server errors" if ok
          else "FAIL — see findings above")
    print(f"{'='*60}")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
