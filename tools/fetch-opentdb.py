#!/usr/bin/env python3
"""Pulls every verified question from the Open Trivia Database (https://opentdb.com,
CC BY-SA 4.0) into app/src/main/assets/questions/en.json.

Standard library only. It asks for a session token so no question comes twice,
respects the API's rate limit of one request per five seconds, decodes the
RFC 3986 encoding, drops duplicates and anything unsuitable for a family living
room, and writes a stable, sorted JSON array:

    {"id", "categoryId", "category", "difficulty", "type",
     "question", "correct", "incorrect": []}

`id` is a hash of the English question and answer, so the same question keeps
its id in every language file and "seen" bookkeeping survives a re-fetch.

Usage:
    python3 tools/fetch-opentdb.py                 # fetch, filter, write
    python3 tools/fetch-opentdb.py --cache DIR     # keep raw pages, resume after a stop
    python3 tools/fetch-opentdb.py --from-cache    # rebuild en.json from the cache only
"""
import argparse
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://opentdb.com"
RATE_LIMIT_SECONDS = 5.5  # the API allows one request per 5 s per IP
USER_AGENT = "qupa-question-fetcher/0.1 (+https://github.com/zaitsew/qupa)"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "questions", "en.json")

# Words that never belong on a shared TV screen. Matched on whole words, case-insensitive.
BLOCKED_WORDS = [
    "porn", "pornography", "pornographic", "rape", "raped", "rapist", "fuck", "fucking", "shit",
    "bitch", "cunt", "nigger", "nigga", "faggot", "retard", "retarded", "whore", "slut", "dildo",
    "orgasm", "masturbate", "masturbation", "blowjob", "cum", "cock", "pussy", "penis", "vagina",
    "suicide",
]
BLOCKED_RE = re.compile(r"\b(" + "|".join(re.escape(w) for w in BLOCKED_WORDS) + r")\b", re.IGNORECASE)

# Four questions whose text the repository's leak gate refuses (a first name it treats as
# private). They are dropped by id rather than allow-listed.
LEAK_GATE_IDS = {"d448aebcb61e", "59ddbf73ec34", "98f0bb058987", "e24202913db9"}
# Eight more that pass the word filter but read badly on a family TV: sexual jokes, drug and
# self-harm references, and a "most killed" ranking of dictators. Found by reading the regex hits.
FAMILY_ROOM_IDS = {"de8a750ec8d0", "db5c06556ed1", "c55e946d5c85", "aa0b3e8d6b19", "88df160deb51", "390623b3753d", "317f0ba8c9ba", "c7221c3e1724"}
EXCLUDED_IDS = LEAK_GATE_IDS | FAMILY_ROOM_IDS

# A question that points at a picture, a list on screen or "the following" cannot be read aloud.
UNANSWERABLE_RE = re.compile(
    r"(pictured|shown (below|above|here)|in the (image|picture|photo)|this (image|picture|photo|logo|flag)|"
    r"which of the following (images|pictures))",
    re.IGNORECASE,
)
MAX_QUESTION = 280
MAX_ANSWER = 90

# Categories dropped for a family living room. Empty today: all 24 are fine.
DROPPED_CATEGORIES = set()

last_request = 0.0


def get_json(url):
    """GET with the rate limit; returns parsed JSON."""
    global last_request
    wait = RATE_LIMIT_SECONDS - (time.time() - last_request)
    if wait > 0:
        time.sleep(wait)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(6):
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                last_request = time.time()
                return json.loads(resp.read().decode("utf-8"))
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
            last_request = time.time()
            print(f"  request failed ({exc}); retry {attempt + 1}/6", file=sys.stderr)
            time.sleep(8)
    raise SystemExit(f"giving up on {url}")


def new_token():
    data = get_json(f"{API}/api_token.php?command=request")
    if data.get("response_code") != 0:
        raise SystemExit(f"token request failed: {data}")
    return data["token"]


def decode(s):
    return urllib.parse.unquote(s)


def question_id(question, correct):
    return hashlib.sha1(f"{question}\n{correct}".encode("utf-8")).hexdigest()[:12]


def fetch_category(cat_id, expected, token, cache_dir):
    """Returns (raw question dicts, token). Stops on 'no more questions'."""
    cache_file = os.path.join(cache_dir, f"cat-{cat_id}.json") if cache_dir else None
    if cache_file and os.path.exists(cache_file):
        with open(cache_file, encoding="utf-8") as f:
            return json.load(f), token
    collected = []
    amount = 50
    while len(collected) < expected:
        amount = min(amount, expected - len(collected))
        url = f"{API}/api.php?amount={amount}&category={cat_id}&token={token}&encode=url3986"
        data = get_json(url)
        code = data.get("response_code")
        if code == 0:
            collected.extend(data["results"])
            print(f"  category {cat_id}: {len(collected)}/{expected}", file=sys.stderr)
        elif code == 1:  # not enough questions for this amount
            if amount == 1:
                break
            amount = max(1, amount // 2)
        elif code == 4:  # the token has seen everything in this category
            break
        elif code == 5:  # rate limit
            time.sleep(8)
        elif code == 3:  # token expired
            token = new_token()
        else:
            raise SystemExit(f"unexpected response {data}")
    if cache_file:
        os.makedirs(cache_dir, exist_ok=True)
        with open(cache_file, "w", encoding="utf-8") as f:
            json.dump(collected, f)
    return collected, token


def convert(raw):
    """One decoded question dict, or (None, reason)."""
    question = decode(raw["question"]).strip()
    correct = decode(raw["correct_answer"]).strip()
    incorrect = [decode(a).strip() for a in raw["incorrect_answers"]]
    qtype = raw["type"]
    texts = [question, correct] + incorrect
    if any(BLOCKED_RE.search(t) for t in texts):
        return None, "blocked word"
    if UNANSWERABLE_RE.search(question):
        return None, "refers to a picture"
    if len(question) > MAX_QUESTION or any(len(t) > MAX_ANSWER for t in [correct] + incorrect):
        return None, "too long for the screen"
    if not question or not correct or any(not a for a in incorrect):
        return None, "empty text"
    if len({correct.lower(), *[a.lower() for a in incorrect]}) != 1 + len(incorrect):
        return None, "duplicate answers"
    if qtype == "boolean":
        if correct not in ("True", "False") or incorrect != [("False" if correct == "True" else "True")]:
            return None, "malformed boolean"
    elif len(incorrect) != 3:
        return None, "not four answers"
    if question_id(question, correct) in EXCLUDED_IDS:
        return None, "excluded by id"
    return {
        "id": question_id(question, correct),
        "difficulty": raw["difficulty"],
        "type": qtype,
        "question": question,
        "correct": correct,
        "incorrect": incorrect,
    }, None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", help="directory for raw category pages; reused when present")
    ap.add_argument("--from-cache", action="store_true", help="never touch the network")
    ap.add_argument("--out", default=OUT)
    args = ap.parse_args()

    categories = {}
    verified = {}
    if args.from_cache:
        cat_names = json.load(open(os.path.join(args.cache, "categories.json"), encoding="utf-8"))
        categories = {int(k): v for k, v in cat_names.items()}
        verified = {c: 10_000 for c in categories}
    else:
        for c in get_json(f"{API}/api_category.php")["trivia_categories"]:
            categories[c["id"]] = c["name"]
        counts = get_json(f"{API}/api_count_global.php")["categories"]
        verified = {c: counts[str(c)]["total_num_of_verified_questions"] for c in categories}
        if args.cache:
            os.makedirs(args.cache, exist_ok=True)
            with open(os.path.join(args.cache, "categories.json"), "w", encoding="utf-8") as f:
                json.dump({str(k): v for k, v in categories.items()}, f)
    token = None if args.from_cache else new_token()

    seen = set()
    result = []
    dropped = {}
    for cat_id in sorted(categories):
        if cat_id in DROPPED_CATEGORIES:
            continue
        print(f"{cat_id} {categories[cat_id]} ({verified[cat_id]} verified)", file=sys.stderr)
        raws, token = fetch_category(cat_id, verified[cat_id], token, args.cache)
        for raw in raws:
            q, reason = convert(raw)
            if q is None:
                dropped[reason] = dropped.get(reason, 0) + 1
                print(f"  dropped ({reason}): {decode(raw['question'])[:80]}", file=sys.stderr)
                continue
            key = re.sub(r"\W+", " ", q["question"].lower()).strip() + "|" + q["correct"].lower()
            if key in seen:
                dropped["duplicate"] = dropped.get("duplicate", 0) + 1
                continue
            seen.add(key)
            result.append({"id": q["id"], "categoryId": cat_id, "category": categories[cat_id], **{k: v for k, v in q.items() if k != "id"}})

    result.sort(key=lambda q: (q["categoryId"], q["id"]))
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    print(f"wrote {len(result)} questions to {args.out}; dropped {dropped}", file=sys.stderr)


if __name__ == "__main__":
    main()
