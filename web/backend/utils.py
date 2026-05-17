"""
Utilities — High-performance helpers for Border Surveillance Command backend.
"""
import socket
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from typing import Optional

# Internal cache to avoid redundant network checks
_resolved_cache = {}

def is_url_reachable(url: str, timeout=2.5) -> bool:
    """
    Robust check if a stream URL host and port are reachable.
    Supports RTSP, HTTP, and raw IP URLs. Highly tolerant of high-latency Wi-Fi.
    """
    if not url:
        return False
        
    try:
        # Standardize prefix for parsing
        parse_url = url
        if not parse_url.startswith(("http://", "https://", "rtsp://")):
            parse_url = "http://" + parse_url
            
        parsed = urllib.parse.urlparse(parse_url)
        host = parsed.hostname
        port = parsed.port
        
        if not host:
            # Fallback if parsing failed
            host = parsed.path.split("/")[0]
            if ":" in host:
                host, port_str = host.split(":")
                port = int(port_str)
                
        if not port:
            if parsed.scheme == "rtsp":
                port = 554
            elif parsed.scheme == "https":
                port = 443
            else:
                # If HTTP/IP and no port specified, check typical ESP32 ports (80 and 81)
                for p in [80, 81, 8080]:
                    try:
                        with socket.create_connection((host, p), timeout=timeout):
                            return True
                    except Exception:
                        pass
                return False
                
        # Socket connection check
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except Exception:
        return False


def resolve_esp32_url(url: str) -> str:
    """
    Checks if a URL is a raw ESP32 IP, and probes typical candidates in parallel.
    Uses fast socket checks first, followed by lightweight HTTP requests.
    Caches successfully resolved URLs for sub-millisecond future lookups.
    Committed to 2.5s timeouts to guarantee success over latency-prone Wi-Fi.
    """
    if not url:
        return url
        
    # Standardize url format
    stripped = url.strip()
    if not stripped.startswith(("http://", "https://", "rtsp://")):
        stripped = "http://" + stripped
        
    # If it is already a specific stream path, return it directly
    if not stripped.startswith("http://") or any(x in stripped for x in ["/video", "/stream", ".mjpg", "?action="]):
        return stripped
        
    if stripped in _resolved_cache:
        return _resolved_cache[stripped]

    # Quick check if the base IP is reachable on port 80/81/8080/etc.
    parsed_base = urllib.parse.urlparse(stripped)
    base_host = parsed_base.hostname
    
    # Try a quick socket scan to see if any typical ESP32 ports are open (timeout 2.5s for weak signals)
    ports_to_check = [80, 81, 8080]
    any_port_open = False
    for p in ports_to_check:
        try:
            with socket.create_connection((base_host, p), timeout=2.5):
                any_port_open = True
                break
        except Exception:
            pass
            
    if not any_port_open:
        print(f"[!] ESP32 host {base_host} seems completely offline. Skipping detailed candidate probing.")
        _resolved_cache[stripped] = stripped
        return stripped

    base_url = stripped.rstrip("/")
    candidates = [
        f"{base_url}:81/stream",
        f"{base_url}/stream",
        f"{base_url}/video",
        f"{base_url}:80/stream",
        f"{base_url}:8080/video",
        f"{base_url}/?action=stream"
    ]

    print(f"[*] Starting high-performance ESP32 probe for {stripped}...")

    def probe_candidate(cand: str) -> Optional[str]:
        try:
            parsed = urllib.parse.urlparse(cand)
            host = parsed.hostname
            port = parsed.port or (443 if parsed.scheme == "https" else 80)
            
            # 1. Socket check (timeout 2.5s) to see if port is open
            with socket.create_connection((host, port), timeout=2.5):
                pass
                
            # 2. Port is open, let's do a fast HTTP check to verify path exists
            req = urllib.request.Request(cand, method="GET")
            req.add_header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            
            with urllib.request.urlopen(req, timeout=2.5) as response:
                status = response.status
                content_type = response.headers.get("Content-Type", "").lower()
                
                # Check for mjpeg or valid response
                if status == 200 or "multipart/x-mixed-replace" in content_type or "image/jpeg" in content_type:
                    return cand
        except Exception:
            pass
        return None

    # Probe candidates in parallel to avoid long sequential timeouts
    with ThreadPoolExecutor(max_workers=len(candidates)) as executor:
        results = list(executor.map(probe_candidate, candidates))
        
    for result in results:
        if result:
            print(f"[+] ESP32-CAM stream auto-resolved successfully to: {result}")
            _resolved_cache[stripped] = result
            return result

    # If no candidate worked, fall back to the original URL
    print(f"[!] No candidate worked for {stripped}. Falling back to original URL.")
    _resolved_cache[stripped] = stripped
    return stripped
