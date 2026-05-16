import requests
import cv2
import time
import os
import sys
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.backends import default_backend

# ── CONFIG ────────────────────────────────────────────────────────────────────
API_URL = "http://127.0.0.1:8000/api/security/push-frame/CAM-SECURE"
MASTER_KEY = "bsc-tactical-secret-key-2026-v1"
SALT = b'bsc_secure_salt_v1'

# ── KEY DERIVATION ────────────────────────────────────────────────────────────
kdf = PBKDF2HMAC(
    algorithm=hashes.SHA256(),
    length=32,
    salt=SALT,
    iterations=100000,
    backend=default_backend()
)
key = kdf.derive(MASTER_KEY.encode())
aesgcm = AESGCM(key)

def encrypt_and_send(image_path):
    if not os.path.exists(image_path):
        print(f"Error: Image {image_path} not found")
        return

    # 1. Read image
    img = cv2.imread(image_path)
    _, buffer = cv2.imencode('.jpg', img)
    raw_bytes = buffer.tobytes()

    # 2. Encrypt (AES-256-GCM)
    nonce = os.urandom(12)
    ciphertext_with_tag = aesgcm.encrypt(nonce, raw_bytes, None)
    payload = nonce + ciphertext_with_tag

    # 3. Send to Backend
    print(f"Sending encrypted frame ({len(payload)} bytes)...")
    try:
        response = requests.post(
            API_URL,
            data=payload,
            headers={'Content-Type': 'application/octet-stream'}
        )
        print(f"Response: {response.status_code} - {response.json()}")
    except Exception as e:
        print(f"Failed to send: {e}")

if __name__ == "__main__":
    # For testing, we'll use a sample image if provided, or just exit
    if len(sys.argv) > 1:
        encrypt_and_send(sys.argv[1])
    else:
        print("Usage: python mock_secure_client.py <path_to_image>")
