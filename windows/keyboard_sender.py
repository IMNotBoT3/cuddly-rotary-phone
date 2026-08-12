import asyncio
import socket
import sys
from pynput import keyboard

try:
    from bleak import BleakScanner, BleakClient
except ImportError:
    BleakScanner = None
    BleakClient = None

BLE_SERVICE_UUID = "7c8a7e20-3b1f-4e55-9f36-4d83f54bf0c1"
BLE_CHAR_UUID = "7c8a7e21-3b1f-4e55-9f36-4d83f54bf0c1"

WIFI_PORT = 50505

queue = None
loop = None

SPECIAL = {
    keyboard.Key.enter: "K:ENTER",
    keyboard.Key.backspace: "K:BACKSPACE",
    keyboard.Key.tab: "K:TAB",
    keyboard.Key.left: "K:LEFT",
    keyboard.Key.right: "K:RIGHT",
    keyboard.Key.up: "K:UP",
    keyboard.Key.down: "K:DOWN",
    keyboard.Key.home: "K:HOME",
    keyboard.Key.end: "K:END",
    keyboard.Key.space: "T: ",
}

def enqueue(command):
    if loop is not None:
        asyncio.run_coroutine_threadsafe(queue.put(command), loop)

def on_press(key):
    if key == keyboard.Key.esc:
        enqueue("__QUIT__")
        return False

    if key in SPECIAL:
        enqueue(SPECIAL[key])
        return

    try:
        if key.char:
            enqueue("T:" + key.char)
    except Exception:
        pass

def start_listener():
    listener = keyboard.Listener(on_press=on_press)
    listener.start()
    return listener

async def wifi_mode():
    ip = input("\nEnter the phone IP shown in the Android app: ").strip()

    print(f"\nConnecting to {ip}:{WIFI_PORT} ...")

    try:
        sock = await asyncio.to_thread(
            socket.create_connection,
            (ip, WIFI_PORT),
            5
        )
    except Exception as e:
        print(f"\nWi-Fi connection failed: {e}")
        print("\nCheck:")
        print("  - Laptop and phone are on the same Wi-Fi")
        print("  - The Android keyboard is selected")
        print("  - The displayed phone IP is correct")
        print("  - Windows/phone firewall is not blocking local traffic")
        return

    print("\nCONNECTED OVER WI-FI")
    print("Tap a text field on the phone.")
    print("Type on the laptop.")
    print("Press ESC to disconnect.\n")

    listener = start_listener()

    try:
        while True:
            command = await queue.get()

            if command == "__QUIT__":
                break

            packet = (command + "\n").encode("utf-8")

            try:
                await asyncio.to_thread(sock.sendall, packet)
            except Exception as e:
                print(f"\nWi-Fi connection lost: {e}")
                break
    finally:
        listener.stop()
        try:
            sock.close()
        except Exception:
            pass

async def find_ble_phone():
    print("\nScanning for phone over Bluetooth LE...")

    devices = await BleakScanner.discover(
        timeout=8.0,
        return_adv=True
    )

    for _, (device, adv) in devices.items():
        service_uuids = [
            u.lower()
            for u in (adv.service_uuids or [])
        ]

        if BLE_SERVICE_UUID.lower() in service_uuids:
            return device

    return None

async def bluetooth_mode():
    if BleakScanner is None or BleakClient is None:
        print("\nBleak is not installed.")
        print("Run: pip install -r requirements.txt")
        return

    phone = await find_ble_phone()

    if phone is None:
        print("\nPhone not found over Bluetooth.")
        print("\nCheck:")
        print("  - Bluetooth is enabled on both devices")
        print("  - Android app has Bluetooth permissions")
        print("  - Laptop Keyboard Bridge is the selected Android keyboard")
        return

    print(f"Found: {phone.name or phone.address}")
    print("Connecting...")

    try:
        async with BleakClient(phone) as client:
            if not client.is_connected:
                print("Could not connect.")
                return

            print("\nCONNECTED OVER BLUETOOTH")
            print("Tap a text field on the phone.")
            print("Type on the laptop.")
            print("Press ESC to disconnect.\n")

            listener = start_listener()

            try:
                while True:
                    command = await queue.get()

                    if command == "__QUIT__":
                        break

                    try:
                        await client.write_gatt_char(
                            BLE_CHAR_UUID,
                            command.encode("utf-8"),
                            response=False
                        )
                    except Exception as e:
                        print(f"\nBluetooth connection lost: {e}")
                        break
            finally:
                listener.stop()

    except Exception as e:
        print(f"\nBluetooth connection failed: {e}")

async def main():
    global loop, queue

    loop = asyncio.get_running_loop()
    queue = asyncio.Queue()

    print("===================================")
    print("  Laptop Keyboard Bridge")
    print("===================================")
    print()
    print("1. Same Wi-Fi")
    print("2. Bluetooth")
    print()

    choice = input("Choose connection mode [1/2]: ").strip()

    if choice == "1":
        await wifi_mode()
    elif choice == "2":
        await bluetooth_mode()
    else:
        print("Invalid choice.")

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
