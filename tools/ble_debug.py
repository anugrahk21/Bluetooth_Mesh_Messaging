"""
BLE Diagnostic Scanner - finds ALL nearby BLE devices
to check if your phone is advertising at all.
"""
import asyncio
import sys

if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

from bleak import BleakScanner

SERVICE_UUID = "12345678-1234-1234-1234-123456789abc"

async def main():
    print("\n  🔍 Scanning ALL BLE devices for 10 seconds...")
    print("  " + "=" * 50)

    found = []

    def callback(device, adv_data):
        if device.address not in [d.address for d in found]:
            found.append(device)
            name = device.name or adv_data.local_name or "(unnamed)"
            uuids = [str(u) for u in (adv_data.service_uuids or [])]
            is_meshtalk = SERVICE_UUID.lower() in [u.lower() for u in uuids]
            tag = " ⭐ MESHTALK!" if is_meshtalk else ""

            print(f"  📡 {name:30s} | {device.address} | RSSI: {adv_data.rssi:4d} dBm{tag}")
            if uuids:
                for u in uuids:
                    print(f"      └── Service: {u}")
            if adv_data.service_data:
                for key, val in adv_data.service_data.items():
                    print(f"      └── Data[{key}]: {val}")

    scanner = BleakScanner(detection_callback=callback)
    await scanner.start()
    await asyncio.sleep(10)
    await scanner.stop()

    print(f"\n  📊 Total devices found: {len(found)}")
    meshtalk = [d for d in found if any(
        SERVICE_UUID.lower() in str(u).lower()
        for u in (d.metadata.get("uuids", []) if hasattr(d, 'metadata') else [])
    )]

    if not found:
        print("  ❌ No BLE devices found at all!")
        print("  → Is Bluetooth ON on your laptop?")
        print("  → Is your phone nearby?")
    elif not meshtalk:
        print("  ⚠️  Found BLE devices but none are MeshTalk.")
        print("  → The phone's BLE advertising may not be starting.")
        print("  → Check if BLUETOOTH_ADVERTISE permission was granted.")
        print("  → Check Android Studio Logcat for errors from 'BLEPeripheral'")
    print()

if __name__ == "__main__":
    asyncio.run(main())
