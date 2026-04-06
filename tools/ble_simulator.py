"""
MeshTalk - Laptop Chat Simulator
==================================
Chat with your Android phone running MeshTalk from your Windows laptop.

Requirements:
    pip install bleak

Usage:
    python ble_simulator.py
"""

import asyncio
import sys
import uuid
import time
import threading

# Windows requires specific event loop policy
if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

from bleak import BleakClient, BleakScanner

# Must match BLEConstants.kt
SERVICE_UUID = "12345678-1234-1234-1234-123456789abc"
MESSAGE_CHAR_UUID = "12345678-1234-1234-1234-123456789001"
IDENTITY_CHAR_UUID = "12345678-1234-1234-1234-123456789002"

LAPTOP_DEVICE_ID = str(uuid.uuid4())[:8]
LAPTOP_USERNAME = "Laptop"


def on_message_notification(sender, data: bytearray):
    """Called when the phone sends a message via BLE notification."""
    try:
        payload = data.decode("utf-8")
        parts = payload.split("|", 2)
        if len(parts) >= 3:
            username = parts[1]
            text = parts[2]
            print(f"\n  📩 {username}: {text}")
        else:
            print(f"\n  📩 Received: {payload}")
        print("  You > ", end="", flush=True)
    except Exception as e:
        print(f"\n  ⚠️ Error reading message: {e}")


async def scan_for_meshtalk():
    """Scan for nearby MeshTalk devices."""
    print("\n  📡 Scanning for MeshTalk devices nearby...")
    print("  " + "=" * 46)

    found_devices = []

    def detection_callback(device, adv_data):
        service_uuids = [str(u).lower() for u in (adv_data.service_uuids or [])]
        if SERVICE_UUID.lower() in service_uuids:
            # Extract username from manufacturer data
            manufacturer_data = adv_data.manufacturer_data or {}
            username = "Unknown"
            if 0xFFFF in manufacturer_data:
                username = manufacturer_data[0xFFFF].decode("utf-8", errors="ignore")

            # Avoid duplicates
            if not any(d.address == device.address for d in found_devices):
                found_devices.append(device)
                print(f"  📱 Found: {username} | {device.address} | RSSI: {adv_data.rssi} dBm")

    scanner = BleakScanner(detection_callback=detection_callback)
    await scanner.start()
    await asyncio.sleep(8)
    await scanner.stop()

    return found_devices


async def chat_session(device):
    """Connect to a phone and start chatting."""
    print(f"\n  🔗 Connecting to {device.address}...")

    try:
        async with BleakClient(device.address, timeout=15.0) as client:
            if not client.is_connected:
                print("  ❌ Failed to connect!")
                return

            print("  ✅ Connected!")

            # Read peer's username
            try:
                identity_data = await client.read_gatt_char(IDENTITY_CHAR_UUID)
                peer_username = identity_data.decode("utf-8")
                print(f"  👤 Chatting with: {peer_username}")
            except Exception:
                peer_username = "Phone"
                print(f"  👤 Chatting with: {peer_username}")

            # Subscribe to notifications (receive messages from phone)
            try:
                await client.start_notify(MESSAGE_CHAR_UUID, on_message_notification)
                print("  🔔 Subscribed to message notifications")
            except Exception as e:
                print(f"  ⚠️ Could not subscribe to notifications: {e}")

            print()
            print("  " + "=" * 46)
            print(f"  💬 Chat with {peer_username} is LIVE!")
            print("  Type a message and press Enter to send.")
            print("  Type 'quit' to disconnect.")
            print("  " + "=" * 46)
            print()

            # Chat loop
            while True:
                try:
                    # Read input in a non-blocking way
                    user_input = await asyncio.get_event_loop().run_in_executor(
                        None, lambda: input("  You > ")
                    )

                    if user_input.lower() in ("quit", "exit", "q"):
                        print("\n  👋 Disconnecting...")
                        break

                    if not user_input.strip():
                        continue

                    # Format: deviceId|username|message (same as Android app)
                    payload = f"{LAPTOP_DEVICE_ID}|{LAPTOP_USERNAME}|{user_input}"
                    payload_bytes = payload.encode("utf-8")

                    # Send message by writing to the GATT characteristic
                    await client.write_gatt_char(MESSAGE_CHAR_UUID, payload_bytes, response=True)
                    timestamp = time.strftime("%H:%M")
                    print(f"  ✅ Sent [{timestamp}]")

                except EOFError:
                    break
                except Exception as e:
                    print(f"  ❌ Send failed: {e}")
                    if not client.is_connected:
                        print("  📴 Connection lost!")
                        break

            # Cleanup
            try:
                await client.stop_notify(MESSAGE_CHAR_UUID)
            except Exception:
                pass

    except Exception as e:
        print(f"  ❌ Connection error: {e}")


async def main():
    print()
    print("  ╔══════════════════════════════════════════════╗")
    print("  ║         🔷 MeshTalk Laptop Simulator         ║")
    print("  ║     Chat with your phone via Bluetooth!      ║")
    print("  ╚══════════════════════════════════════════════╝")
    print()
    print(f"  🆔 Laptop ID: {LAPTOP_DEVICE_ID}")
    print(f"  👤 Username: {LAPTOP_USERNAME}")

    # Step 1: Scan
    devices = await scan_for_meshtalk()

    if not devices:
        print("\n  ❌ No MeshTalk devices found!")
        print("  Make sure your phone app is running and scanning.")
        print("  Try again? Run: python ble_simulator.py")
        return

    # Step 2: Select device (auto-select if only one)
    if len(devices) == 1:
        target = devices[0]
        print(f"\n  📱 Auto-selecting: {target.address}")
    else:
        print("\n  Multiple devices found. Select one:")
        for i, d in enumerate(devices):
            print(f"    [{i+1}] {d.address}")
        try:
            choice = int(input("  Enter number: ")) - 1
            target = devices[choice]
        except (ValueError, IndexError):
            print("  Invalid choice.")
            return

    # Step 3: Connect and chat!
    await chat_session(target)

    print("\n  👋 Goodbye!\n")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n\n  👋 Interrupted. Goodbye!\n")
