from flask import Flask, request, jsonify

from ai_detector import detect_scam
from virustotal_checker import check_url

import re
import threading

app = Flask(__name__)

virus_total_results = {}
virus_total_lock = threading.Lock()

def extract_url(text):
    regex = r'(https?://[^\s<>"]+|www\.[^\s<>"]+)'

    match = re.search(
        regex,
        text,
        re.IGNORECASE
    )

    if not match:
        return None

    return match.group(1).rstrip(
        '.,!?:;)]}'
    )

def run_virustotal(url):
    try:
        result = check_url(url)

        with virus_total_lock:
            virus_total_results[url] = result

        print(
            "VIRUSTOTAL_BACKGROUND_RESULT:",
            result
        )

    except Exception as e:
        print(
            "VIRUSTOTAL_BACKGROUND_ERROR:",
            str(e)
        )

@app.route("/", methods=["GET"])
def home():
    return jsonify({
        "service": "PauseAI AI Server",
        "status": "running",
        "endpoints": [
            "/detect",
            "/virustotal_status"
        ]
    })

@app.route("/detect", methods=["POST"])
def detect():
    try:
        data = request.get_json(
            silent=True
        )

        if not data:
            return jsonify({
                "error": "Invalid JSON"
            }), 400

        message = data.get(
            "message",
            ""
        )

        if not message or not message.strip():
            return jsonify({
                "error": "Message is empty"
            }), 400

        result = detect_scam(
            message
        )

        url = extract_url(
            message
        )

        result["url"] = url
        result["virustotal"] = None

        if url:
            threading.Thread(
                target=run_virustotal,
                args=(url,),
                daemon=True
            ).start()

        return jsonify(
            result
        )

    except Exception as e:
        return jsonify({
            "error": str(e)
        }), 500

@app.route(
    "/virustotal_status",
    methods=["GET"]
)
def virustotal_status():
    url = request.args.get(
        "url",
        ""
    )

    if not url:
        return jsonify({
            "ready": False,
            "error": "URL is missing"
        }), 400

    with virus_total_lock:
        result = virus_total_results.get(
            url
        )

    if result is None:
        return jsonify({
            "ready": False,
            "url": url
        })

    return jsonify({
        "ready": True,
        "url": url,
        "virustotal": result
    })

if __name__ == "__main__":
    app.run(
        host="0.0.0.0",
        port=5000
    )
