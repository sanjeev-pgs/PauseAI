import os
import json
import time
import hashlib
import requests

CACHE_FILE = "virustotal_cache.json"
CACHE_DAYS = 7

def load_cache():
    if not os.path.exists(CACHE_FILE):
        return {}
    try:
        with open(CACHE_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}

def save_cache(cache):
    with open(CACHE_FILE, "w", encoding="utf-8") as f:
        json.dump(cache, f, indent=2)

def check_url(url):
    cache = load_cache()
    key = hashlib.sha256(url.strip().lower().encode()).hexdigest()

    cached = cache.get(key)

    if cached:
        age = time.time() - cached.get("timestamp", 0)

        if age < CACHE_DAYS * 86400:
            return {
                "source": "cache",
                "url": url,
                "malicious": cached.get("malicious", 0),
                "suspicious": cached.get("suspicious", 0),
                "harmless": cached.get("harmless", 0),
                "undetected": cached.get("undetected", 0)
            }

    api_key = os.environ.get("VT_API_KEY")

    if not api_key:
        return {
            "source": "error",
            "error": "VT_API_KEY is not set"
        }

    headers = {
        "x-apikey": api_key
    }

    try:
        url_id = __import__("base64").urlsafe_b64encode(
            url.strip().encode()
        ).decode().strip("=")

        existing_response = requests.get(
            f"https://www.virustotal.com/api/v3/urls/{url_id}",
            headers=headers,
            timeout=15
        )

        if existing_response.status_code == 200:
            existing_data = existing_response.json()
            existing_stats = (
                existing_data
                .get("data", {})
                .get("attributes", {})
                .get("last_analysis_stats", {})
            )

            if existing_stats:
                cached_result = {
                    "timestamp": time.time(),
                    "malicious": existing_stats.get("malicious", 0),
                    "suspicious": existing_stats.get("suspicious", 0),
                    "harmless": existing_stats.get("harmless", 0),
                    "undetected": existing_stats.get("undetected", 0)
                }

                cache[key] = cached_result
                save_cache(cache)

                return {
                    "source": "virustotal",
                    "url": url,
                    **{
                        k: v for k, v in cached_result.items()
                        if k != "timestamp"
                    }
                }

        response = requests.post(
            "https://www.virustotal.com/api/v3/urls",
            headers=headers,
            data={"url": url},
            timeout=15
        )

        if response.status_code != 200:
            return {
                "source": "error",
                "status_code": response.status_code,
                "error": response.text[:500]
            }

        data = response.json()
        analysis_id = data["data"]["id"]

        result = None

        for _ in range(15):
            result_response = requests.get(
                f"https://www.virustotal.com/api/v3/analyses/{analysis_id}",
                headers=headers,
                timeout=15
            )

            if result_response.status_code != 200:
                return {
                    "source": "error",
                    "status_code": result_response.status_code,
                    "error": result_response.text[:500]
                }

            result = result_response.json()

            status = (
                result
                .get("data", {})
                .get("attributes", {})
                .get("status", "")
            )

            if status == "completed":
                break

            time.sleep(2)

        if result is None:
            return {
                "source": "error",
                "error": "VirusTotal analysis did not return a result"
            }

        stats = (
            result
            .get("data", {})
            .get("attributes", {})
            .get("stats", {})
        )

        cached_result = {
            "timestamp": time.time(),
            "malicious": stats.get("malicious", 0),
            "suspicious": stats.get("suspicious", 0),
            "harmless": stats.get("harmless", 0),
            "undetected": stats.get("undetected", 0)
        }

        cache[key] = cached_result
        save_cache(cache)

        return {
            "source": "virustotal",
            "url": url,
            **{
                k: v for k, v in cached_result.items()
                if k != "timestamp"
            }
        }

    except Exception as e:
        return {
            "source": "error",
            "error": str(e)
        }

