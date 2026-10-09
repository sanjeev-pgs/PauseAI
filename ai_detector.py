from transformers import pipeline
import re

classifier = pipeline(
    "text-classification",
    model="anmolshrivastav/distilbert-scam-detector-india"
)

URGENT_PATTERNS = [
    r"\bimmediately\b",
    r"\burgent\b",
    r"\bact now\b",
    r"\baction required\b",
    r"\blast chance\b",
    r"\bexpires?\s+(today|now|soon)\b",
    r"\bwithin\s+\d+\s+(minutes?|hours?)\b",
    r"\btoday\b",
    r"\bdeadline\b"
]

THREAT_PATTERNS = [
    r"\baccount\b.{0,40}\b(block|blocked|suspend|suspended|close|closed)\b",
    r"\bnumber\b.{0,40}\b(block|blocked|deactivate|deactivated)\b",
    r"\blegal action\b",
    r"\bpolice\b",
    r"\barrest\b",
    r"\bfine\b",
    r"\bpenalty\b"
]

FINANCIAL_PATTERNS = [
    r"₹\s?\d+",
    r"\brs\.?\s?\d+",
    r"\binr\s?\d+",
    r"\bupi\b",
    r"\bpayment\b",
    r"\bpay\b",
    r"\bpaid\b",
    r"\brefund\b",
    r"\bcredited\b",
    r"\bcredit\b",
    r"\bdebited\b",
    r"\bdebit\b",
    r"\bbank\b",
    r"\baccount\b",
    r"\btransaction\b"
]

SENSITIVE_PATTERNS = [
    r"\botp\b",
    r"\bpin\b",
    r"\bpassword\b",
    r"\bpasscode\b",
    r"\bcvv\b",
    r"\bcard\s+number\b",
    r"\baccount\s+number\b",
    r"\baadhaar\b",
    r"\bpan\b",
    r"\bkyc\b",
    r"\bverification\s+code\b"
]

IMPERSONATION_PATTERNS = [
    r"\bcustomer\s+care\b",
    r"\bcustomer\s+support\b",
    r"\bbank\s+manager\b",
    r"\bbank\s+official\b",
    r"\bsupport\s+team\b",
    r"\bsecurity\s+team\b",
    r"\bgovernment\b",
    r"\bincome\s+tax\b",
    r"\bpolice\b"
]

REWARD_PATTERNS = [
    r"\bwon\b",
    r"\bwinner\b",
    r"\bprize\b",
    r"\breward\b",
    r"\blottery\b",
    r"\bcashback\b",
    r"\bfree\s+(gift|money|cash)\b",
    r"\bclaim\b"
]

ACTION_PATTERNS = [
    r"\bclick\b",
    r"\btap\b",
    r"\bopen\b",
    r"\bverify\b",
    r"\bconfirm\b",
    r"\bsend\b",
    r"\bshare\b",
    r"\btransfer\b",
    r"\bcall\b"
]

def count_matches(message, patterns):
    count = 0
    for pattern in patterns:
        if re.search(pattern, message, re.IGNORECASE):
            count += 1
    return count

def analyze_signals(message):
    return {
        "urgency": count_matches(message, URGENT_PATTERNS),
        "threat": count_matches(message, THREAT_PATTERNS),
        "financial": count_matches(message, FINANCIAL_PATTERNS),
        "sensitive_info": count_matches(message, SENSITIVE_PATTERNS),
        "impersonation": count_matches(message, IMPERSONATION_PATTERNS),
        "reward": count_matches(message, REWARD_PATTERNS),
        "action_request": count_matches(message, ACTION_PATTERNS),
        "has_url": bool(
            re.search(
                r"https?://\S+|www\.\S+",
                message,
                re.IGNORECASE
            )
        )
    }

def calculate_risk(model_result, signals):
    label = model_result["label"].lower()
    model_probability = model_result["score"] * 100

    risk_score = 0.0

    if label == "spam":
        risk_score += model_probability * 0.60
    else:
        risk_score += (100 - model_probability) * 0.20

    if signals["urgency"]:
        risk_score += 8

    if signals["threat"]:
        risk_score += 12

    if signals["financial"]:
        risk_score += 10

    if signals["sensitive_info"]:
        risk_score += 15

    if signals["impersonation"]:
        risk_score += 10

    if signals["reward"]:
        risk_score += 12

    if signals["action_request"]:
        risk_score += 6

    if signals["has_url"]:
        risk_score += 10

    risk_score = min(risk_score, 100.0)

    if risk_score >= 75:
        risk_level = "high"
    elif risk_score >= 45:
        risk_level = "suspicious"
    else:
        risk_level = "safe"

    return round(risk_score, 2), risk_level

def detect_scam(message):
    if not message or not message.strip():
        return {
            "is_scam": False,
            "probability": 0.0,
            "risk_score": 0.0,
            "risk_level": "safe",
            "label": "empty",
            "signals": {}
        }

    result = classifier(message)[0]
    signals = analyze_signals(message)

    risk_score, risk_level = calculate_risk(
        result,
        signals
    )

    return {
        "is_scam": risk_level == "high",
        "probability": round(result["score"] * 100, 2),
        "risk_score": risk_score,
        "risk_level": risk_level,
        "label": result["label"].lower(),
        "signals": signals
    }

if __name__ == "__main__":
    message = input("Enter message: ")
    result = detect_scam(message)
    print(result)
from transformers import pipeline
import re

classifier = pipeline(
    "text-classification",
    model="anmolshrivastav/distilbert-scam-detector-india"
)

URGENT_PATTERNS = [
    r"\bimmediately\b",
    r"\burgent\b",
    r"\bact now\b",
    r"\baction required\b",
    r"\blast chance\b",
    r"\bexpires?\s+(today|now|soon)\b",
    r"\bwithin\s+\d+\s+(minutes?|hours?)\b",
    r"\btoday\b",
    r"\bdeadline\b"
]

THREAT_PATTERNS = [
    r"\baccount\b.{0,40}\b(block|blocked|suspend|suspended|close|closed)\b",
    r"\bnumber\b.{0,40}\b(block|blocked|deactivate|deactivated)\b",
    r"\blegal action\b",
    r"\bpolice\b",
    r"\barrest\b",
    r"\bfine\b",
    r"\bpenalty\b"
]

FINANCIAL_PATTERNS = [
    r"₹\s?\d+",
    r"\brs\.?\s?\d+",
    r"\binr\s?\d+",
    r"\bupi\b",
    r"\bpayment\b",
    r"\bpay\b",
    r"\bpaid\b",
    r"\brefund\b",
    r"\bcredited\b",
    r"\bcredit\b",
    r"\bdebited\b",
    r"\bdebit\b",
    r"\bbank\b",
    r"\baccount\b",
    r"\btransaction\b"
]

SENSITIVE_PATTERNS = [
    r"\botp\b",
    r"\bpin\b",
    r"\bpassword\b",
    r"\bpasscode\b",
    r"\bcvv\b",
    r"\bcard\s+number\b",
    r"\baccount\s+number\b",
    r"\baadhaar\b",
    r"\bpan\b",
    r"\bkyc\b",
    r"\bverification\s+code\b"
]

IMPERSONATION_PATTERNS = [
    r"\bcustomer\s+care\b",
    r"\bcustomer\s+support\b",
    r"\bbank\s+manager\b",
    r"\bbank\s+official\b",
    r"\bsupport\s+team\b",
    r"\bsecurity\s+team\b",
    r"\bgovernment\b",
    r"\bincome\s+tax\b",
    r"\bpolice\b"
]

REWARD_PATTERNS = [
    r"\bwon\b",
    r"\bwinner\b",
    r"\bprize\b",
    r"\breward\b",
    r"\blottery\b",
    r"\bcashback\b",
    r"\bfree\s+(gift|money|cash)\b",
    r"\bclaim\b"
]

ACTION_PATTERNS = [
    r"\bclick\b",
    r"\btap\b",
    r"\bopen\b",
    r"\bverify\b",
    r"\bconfirm\b",
    r"\bsend\b",
    r"\bshare\b",
    r"\btransfer\b",
    r"\bcall\b"
]

def count_matches(message, patterns):
    count = 0

    for pattern in patterns:
        if re.search(pattern, message, re.IGNORECASE):
            count += 1

    return count

def analyze_signals(message):
    return {
        "urgency": count_matches(message, URGENT_PATTERNS),
        "threat": count_matches(message, THREAT_PATTERNS),
        "financial": count_matches(message, FINANCIAL_PATTERNS),
        "sensitive_info": count_matches(message, SENSITIVE_PATTERNS),
        "impersonation": count_matches(message, IMPERSONATION_PATTERNS),
        "reward": count_matches(message, REWARD_PATTERNS),
        "action_request": count_matches(message, ACTION_PATTERNS),
        "has_url": bool(
            re.search(
                r"https?://\S+|www\.\S+",
                message,
                re.IGNORECASE
            )
        )
    }

def calculate_risk(model_result, signals):
    label = model_result["label"].lower()
    model_probability = model_result["score"] * 100

    risk_score = 0.0

    if label == "spam":
        risk_score += model_probability * 0.60
    else:
        risk_score += (100 - model_probability) * 0.20

    if signals["urgency"]:
        risk_score += 8

    if signals["threat"]:
        risk_score += 12

    if signals["financial"]:
        risk_score += 10

    if signals["sensitive_info"]:
        risk_score += 15

    if signals["impersonation"]:
        risk_score += 10

    if signals["reward"]:
        risk_score += 12

    if signals["action_request"]:
        risk_score += 6

    if signals["has_url"]:
        risk_score += 10

    risk_score = min(risk_score, 100.0)

    if risk_score >= 75:
        risk_level = "high"
    elif risk_score >= 45:
        risk_level = "suspicious"
    else:
        risk_level = "safe"

    return round(risk_score, 2), risk_level

def detect_category(signals):
    if signals["sensitive_info"] and signals["financial"]:
        return "Payment / Credential Scam"

    if signals["reward"]:
        return "Prize / Reward Scam"

    if signals["impersonation"] and signals["threat"]:
        return "Impersonation Scam"

    if signals["threat"]:
        return "Account Threat"

    if signals["financial"]:
        return "Financial Scam"

    if signals["has_url"]:
        return "Suspicious Link"

    if signals["impersonation"]:
        return "Impersonation"

    if signals["urgency"] or signals["action_request"]:
        return "Urgent Action Request"

    return "General Message"

def build_reasons(signals):
    reasons = []

    if signals["urgency"]:
        reasons.append("Uses urgency or time pressure")

    if signals["threat"]:
        reasons.append("Contains account or legal threats")

    if signals["financial"]:
        reasons.append("Mentions money, payment or banking")

    if signals["sensitive_info"]:
        reasons.append("Requests or mentions sensitive information")

    if signals["impersonation"]:
        reasons.append("May imitate an official or support team")

    if signals["reward"]:
        reasons.append("Contains prize, reward or winning language")

    if signals["action_request"]:
        reasons.append("Requests the user to take an immediate action")

    if signals["has_url"]:
        reasons.append("Contains a URL")

    return reasons

def build_recommendation(risk_level, signals):
    if risk_level == "high":
        return "Do not click links, send money, share OTPs or provide sensitive information. Verify independently."

    if risk_level == "suspicious":
        return "Verify the sender and request independently before taking action."

    if signals["financial"]:
        return "If money or payment is involved, verify the transaction independently."

    if signals["has_url"]:
        return "Check the destination carefully before opening the link."

    return "No strong scam indicators were detected. Continue to stay cautious."

def detect_scam(message):
    if not message or not message.strip():
        return {
            "is_scam": False,
            "probability": 0.0,
            "risk_score": 0.0,
            "risk_level": "safe",
            "label": "empty",
            "category": "General Message",
            "signals": {},
            "reasons": [],
            "recommendation": "No message to analyze."
        }

    result = classifier(message)[0]

    signals = analyze_signals(message)

    risk_score, risk_level = calculate_risk(
        result,
        signals
    )

    category = detect_category(signals)

    reasons = build_reasons(
        signals
    )

    recommendation = build_recommendation(
        risk_level,
        signals
    )

    return {
        "is_scam": risk_level == "high",
        "probability": round(
            result["score"] * 100,
            2
        ),
        "risk_score": risk_score,
        "risk_level": risk_level,
        "label": result["label"].lower(),
        "category": category,
        "signals": signals,
        "reasons": reasons,
        "recommendation": recommendation
    }

if __name__ == "__main__":
    message = input("Enter message: ")

    result = detect_scam(message)

    print(result)