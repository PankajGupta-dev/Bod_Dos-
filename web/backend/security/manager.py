import os
import base64
import logging
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.backends import default_backend

logger = logging.getLogger("bsc.security")

class EncryptionManager:
    """
    Handles AES-256-GCM encryption/decryption for secure camera feeds and evidence.
    GCM mode is used to provide both confidentiality and data integrity (tamper-proof).
    """
    
    def __init__(self, master_key: str = None):
        # In a real scenario, the master key would be retrieved from a hardware HSM or secure vault.
        # Here we use the one from environment or a default for testing.
        self.master_key = (master_key or os.getenv("AES_MASTER_KEY", "bsc-tactical-secret-key-2026-v1")).encode()
        self._salt = b'bsc_secure_salt_v1' # Constant salt for simple PKS
        
        # Derive a 256-bit key using PBKDF2
        kdf = PBKDF2HMAC(
            algorithm=hashes.SHA256(),
            length=32,
            salt=self._salt,
            iterations=100000,
            backend=default_backend()
        )
        self.key = kdf.derive(self.master_key)
        self.aesgcm = AESGCM(self.key)

    def encrypt_frame(self, frame_bytes: bytes) -> bytes:
        """
        Encrypts a raw frame or data packet.
        Returns: [Nonce (12b)] + [Tag (16b)] + [Ciphertext] (standard AESGCM output)
        """
        nonce = os.urandom(12)
        # AESGCM.encrypt returns ciphertext + tag
        ciphertext_with_tag = self.aesgcm.encrypt(nonce, frame_bytes, None)
        return nonce + ciphertext_with_tag

    def decrypt_frame(self, encrypted_data: bytes) -> bytes:
        """
        Decrypts an AES-256-GCM packet.
        Verifies integrity (Tag check) automatically.
        Raises: cryptography.exceptions.InvalidTag if tampering is detected.
        """
        try:
            nonce = encrypted_data[:12]
            ciphertext_with_tag = encrypted_data[12:]
            return self.aesgcm.decrypt(nonce, ciphertext_with_tag, None)
        except Exception as e:
            logger.error(f"Decryption/Integrity Check Failed: {e}")
            raise ValueError("Data tampering or invalid key detected.")

# Singleton instance
encryption_manager = EncryptionManager()
