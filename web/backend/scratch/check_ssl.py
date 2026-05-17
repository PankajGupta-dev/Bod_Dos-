import ssl
import socket

def main():
    try:
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        
        # Connect WITH SNI
        try:
            print("Connecting WITH SNI...")
            s = ctx.wrap_socket(socket.socket(), server_hostname="api.bordersentinel.com")
            s.connect(("api.bordersentinel.com", 443))
            print("Cipher info:", s.cipher())
            print("Peer Certificate:", s.getpeercert())
            s.close()
        except Exception as e:
            print(f"Failed with SNI: {e}")
            
        # Connect WITHOUT SNI
        try:
            print("\nConnecting WITHOUT SNI...")
            s2 = ctx.wrap_socket(socket.socket(), server_hostname=None)
            s2.connect(("api.bordersentinel.com", 443))
            print("Cipher info (no SNI):", s2.cipher())
            s2.close()
        except Exception as e:
            print(f"Failed without SNI: {e}")
            
    except Exception as e:
        print(f"General error: {e}")

if __name__ == "__main__":
    main()
