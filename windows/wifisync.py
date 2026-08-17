import asyncio
import socket
import threading
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

# Forwarding state. The local Windows keystroke is never suppressed.
forwarding_enabled = True
shift_down = False
state_lock = threading.Lock()

SPECIAL = {
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

SHIFT_KEYS = {
    keyboard.Key.shift,
    keyboard.Key.shift_l,
    keyboard.Key.shift_r,
}

def enqueue(command):
    if loop is not None:
        asyncio.run_coroutine_threadsafe(queue.put(command), loop)

def set_forwarding(enabled):
    global forwarding_enabled
    with state_lock:
        forwarding_enabled = enabled
        state = "ON" if forwarding_enabled else "OFF"

    print(f"\n[Phone forwarding: {state}]")
    if forwarding_enabled:
        print("Laptop keys are being mirrored to the phone.")
    else:
        print("Laptop-only mode. Press F8 to resume phone forwarding.")

def toggle_forwarding():
    with state_lock:
        new_state = not forwarding_enabled
    set_forwarding(new_state)

def is_forwarding():
    with state_lock:
        return forwarding_enabled

def on_press(key):
    global shift_down

    # Track Shift so Enter and Shift+Enter can behave differently.
    if key in SHIFT_KEYS:
        with state_lock:
            shift_down = True
        return

    # F8 toggles phone forwarding without closing the connection.
    if key == keyboard.Key.f8:
        toggle_forwarding()
        return

    # ESC fully exits the bridge.
    if key == keyboard.Key.esc:
        enqueue("__QUIT__")
        return False

    # When disabled, do not mirror anything to Android.
    if not is_forwarding():
        return

    # Preserve Enter semantics.
    if key == keyboard.Key.enter:
        with state_lock:
            shifted = shift_down

        if shifted:
            enqueue("K:SHIFT_ENTER")
        else:
            enqueue("K:ENTER")
        return

    if key in SPECIAL:
        enqueue(SPECIAL[key])
        return

    # pynput resolves printable keys, including Shift-modified characters,
    # into key.char on common Windows keyboard layouts.
    try:
        if key.char:
            enqueue("T:" + key.char)
    except Exception:
        pass

def on_release(key):
    global shift_down
    if key in SHIFT_KEYS:
        with state_lock:
            shift_down = False

def start_listener():
    listener = keyboard.Listener(
        on_press=on_press,
        on_release=on_release,
    )
    listener.start()
    return listener

def print_controls():
    print()
    print("Controls")
    print("--------")
    print("F8          Toggle phone forwarding ON/OFF")
    print("Enter       Normal Enter")
    print("Shift+Enter Shift-modified Enter / new line in supported editors")
    print("ESC         Disconnect and exit")
    print()
    print("Important: local Windows keystrokes are NOT suppressed.")
    print("The bridge only mirrors them to Android while forwarding is ON.")
    print()

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
        print("  - WiFiSync is selected as the Android keyboard")
        print("  - The displayed phone IP is correct")
        print("  - Your Wi-Fi does not isolate devices from each other")
        return

    print("\nCONNECTED OVER WI-FI")
    print_controls()
    set_forwarding(True)

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
        print("  - WiFiSync is selected as the Android keyboard")
        return

    print(f"Found: {phone.name or phone.address}")
    print("Connecting...")

    try:
        async with BleakClient(phone) as client:
            if not client.is_connected:
                print("Could not connect.")
                return

            print("\nCONNECTED OVER BLUETOOTH")
            print_controls()
            set_forwarding(True)

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
    print("       WiFiSync")
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
