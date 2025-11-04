import asyncio
import httpx
import json

BASE_URL = "http://localhost:8080"
USER_ID = "test_user_123"

async def listen_to_stream():
    stream_url = f"{BASE_URL}/chat/stream/{USER_ID}"
    print(f"Connecting to SSE stream: {stream_url}")

    async with httpx.AsyncClient(timeout=None) as client:
        async with client.stream("GET", stream_url) as response:
            print("Connected, listening for messages...")
            async for line in response.aiter_lines():
                if line.startswith("data:"):
                    data = line.removeprefix("data:").strip()
                    print("Token:", data)

async def send_message():
    async with httpx.AsyncClient() as client:
        payload = {"userId": USER_ID, "message": "Hello from Python test!"}
        print("Sending message:", payload)
        r = await client.post(f"{BASE_URL}/chat", json=payload)
        print("Message response:", r.json())

async def main():
    listener = asyncio.create_task(listen_to_stream())
    await asyncio.sleep(1)
    await send_message()
    await asyncio.sleep(10)
    listener.cancel()

if __name__ == "__main__":
    asyncio.run(main())
