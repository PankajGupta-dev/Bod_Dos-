import requests
import urllib3

# Disable insecure request warnings for self-signed certificates
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

def main():
    try:
        url = "https://api.bordersentinel.com/api/alerts"
        print(f"Making GET request to {url}...")
        res = requests.get(url, verify=False)
        print("Status code:", res.status_code)
        print("Headers:", res.headers)
        print("Body:", res.text[:200])
    except Exception as e:
        print("Error:", e)

if __name__ == "__main__":
    main()
