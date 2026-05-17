import requests

def main():
    try:
        # Login to get token
        login_url = "http://localhost:8000/api/auth/login"
        payload = {
            "officerId": "IND-ARMY-601",
            "password": "1234",
            "unitCode": "HQ-COMMAND"
        }
        res = requests.post(login_url, json=payload)
        res.raise_for_status()
        token = res.json()["access_token"]
        
        # Query sentinel status
        status_url = "http://localhost:8000/api/sentinel/status"
        headers = {
            "Authorization": f"Bearer {token}"
        }
        status_res = requests.get(status_url, headers=headers)
        status_res.raise_for_status()
        
        import json
        print(json.dumps(status_res.json(), indent=2))
    except Exception as e:
        print(f"Error querying backend status: {e}")

if __name__ == "__main__":
    main()
