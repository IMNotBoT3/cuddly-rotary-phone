import asyncio
import ctypes
import io
import ipaddress
import socket
import struct
import threading
import time
from typing import Optional

from PIL import Image, ImageDraw, ImageGrab
from pynput import keyboard

try:
    import pystray
except ImportError:
    pystray = None

try:
    from bleak import BleakScanner, BleakClient
except ImportError:
    BleakScanner = None
    BleakClient = None


BLE_SERVICE_UUID = "7c8a7e20-3b1f-4e55-9f36-4d83f54bf0c1"
BLE_CHAR_UUID = "7c8a7e21-3b1f-4e55-9f36-4d83f54bf0c1"

WIFI_PORT = 50505
SCREENSHOT_PORT = 50506
MAX_SCREENSHOT_BYTES = 30 * 1024 * 1024

queue = None
loop = None

forwarding_enabled = True
shift_down = False
state_lock = threading.Lock()

wifi_phone_ip: Optional[str] = None
tray_icon = None

class KeyboardConnection:
    """Resilient Same-Wi-Fi keyboard channel with automatic reconnect."""

    def __init__(self, host, port):
        self.host = host
        self.port = port
        self.sock = None
        self.lock = threading.RLock()
        self.closed = False

    def _configure_socket(self, sock):
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_KEEPALIVE, 1)
        if hasattr(socket, "SIO_KEEPALIVE_VALS"):
            try:
                sock.ioctl(socket.SIO_KEEPALIVE_VALS, (1, 10_000, 3_000))
            except OSError:
                pass
        sock.settimeout(None)

    def connect(self):
        with self.lock:
            if self.closed:
                raise RuntimeError("Connection manager has been closed.")
            self._close_socket_locked()
            sock = socket.create_connection((self.host, self.port), timeout=5)
            self._configure_socket(sock)
            self.sock = sock
            return sock

    def ensure_connected(self):
        with self.lock:
            if self.closed:
                raise RuntimeError("Connection manager has been closed.")
            if self.sock is None:
                return self.connect()
            return self.sock

    def send_line(self, command, retries=6):
        packet = (command + "\\n").encode("utf-8")
        last_error = None
        for attempt in range(retries):
            with self.lock:
                if self.closed:
                    raise RuntimeError("WiFiSync connection is closed.")
                try:
                    sock = self.ensure_connected()
                    sock.sendall(packet)
                    return
                except (OSError, ConnectionError) as exc:
                    last_error = exc
                    self._close_socket_locked()
            time.sleep(min(0.4 * (attempt + 1), 2.0))
        raise ConnectionError(
            f"Could not restore the Wi-Fi keyboard connection after {retries} attempts: {last_error}"
        )

    def ping(self):
        self.send_line("K:PING", retries=2)

    def _close_socket_locked(self):
        if self.sock is not None:
            try: self.sock.shutdown(socket.SHUT_RDWR)
            except OSError: pass
            try: self.sock.close()
            except OSError: pass
            self.sock = None

    def close(self):
        with self.lock:
            self.closed = True
            self._close_socket_locked()


keyboard_connection = None

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


def is_forwarding():
    with state_lock:
        return forwarding_enabled


def set_forwarding(enabled):
    global forwarding_enabled

    with state_lock:
        forwarding_enabled = enabled
        state = "ON" if enabled else "OFF"

    print(f"\n[Phone mirror: {state}]")

    if enabled:
        print("Mode: MIRROR TO PHONE (Windows still receives the physical keys).")
    else:
        print("Mode: LAPTOP ONLY.")

    if tray_icon is not None:
        try:
            tray_icon.title = f"WiFiSync - Phone mirror {state}"
            tray_icon.update_menu()
        except Exception:
            pass


def toggle_forwarding():
    set_forwarding(not is_forwarding())


def notify_user(message, title="WiFiSync"):
    print(f"\n[{title}] {message}")

    if tray_icon is not None:
        try:
            tray_icon.notify(message, title)
        except Exception:
            pass


def make_tray_image():
    size = 64
    image = Image.new("RGBA", (size, size), (37, 43, 54, 255))
    draw = ImageDraw.Draw(image)

    # Simple Wi-Fi / sync-inspired icon.
    draw.arc((9, 8, 55, 50), start=215, end=325, width=6, fill=(245, 245, 245, 255))
    draw.arc((18, 20, 46, 47), start=215, end=325, width=5, fill=(245, 245, 245, 255))
    draw.ellipse((29, 45, 35, 51), fill=(245, 245, 245, 255))

    return image


def start_tray():
    global tray_icon

    if pystray is None:
        return

    def menu_toggle(icon, item):
        toggle_forwarding()

    def menu_capture_window(icon, item):
        start_screenshot_transfer(full_screen=False)

    def menu_capture_screen(icon, item):
        start_screenshot_transfer(full_screen=True)

    def menu_exit(icon, item):
        enqueue("__QUIT__")

    menu = pystray.Menu(
        pystray.MenuItem("Toggle laptop-only / phone mirror (F8)", menu_toggle),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Capture active window (F9)", menu_capture_window),
        pystray.MenuItem("Capture full screen (Shift+F9)", menu_capture_screen),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Exit WiFiSync", menu_exit),
    )

    tray_icon = pystray.Icon(
        "WiFiSync",
        make_tray_image(),
        "WiFiSync",
        menu,
    )

    threading.Thread(target=tray_icon.run, daemon=True).start()


def stop_tray():
    global tray_icon

    if tray_icon is not None:
        try:
            tray_icon.stop()
        except Exception:
            pass

    tray_icon = None

class KeyboardConnection:
    """Resilient Same-Wi-Fi keyboard channel with automatic reconnect."""

    def __init__(self, host, port):
        self.host = host
        self.port = port
        self.sock = None
        self.lock = threading.RLock()
        self.closed = False

    def _configure_socket(self, sock):
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_KEEPALIVE, 1)
        if hasattr(socket, "SIO_KEEPALIVE_VALS"):
            try:
                sock.ioctl(socket.SIO_KEEPALIVE_VALS, (1, 10_000, 3_000))
            except OSError:
                pass
        sock.settimeout(None)

    def connect(self):
        with self.lock:
            if self.closed:
                raise RuntimeError("Connection manager has been closed.")
            self._close_socket_locked()
            sock = socket.create_connection((self.host, self.port), timeout=5)
            self._configure_socket(sock)
            self.sock = sock
            return sock

    def ensure_connected(self):
        with self.lock:
            if self.closed:
                raise RuntimeError("Connection manager has been closed.")
            if self.sock is None:
                return self.connect()
            return self.sock

    def send_line(self, command, retries=6):
        packet = (command + "\\n").encode("utf-8")
        last_error = None
        for attempt in range(retries):
            with self.lock:
                if self.closed:
                    raise RuntimeError("WiFiSync connection is closed.")
                try:
                    sock = self.ensure_connected()
                    sock.sendall(packet)
                    return
                except (OSError, ConnectionError) as exc:
                    last_error = exc
                    self._close_socket_locked()
            time.sleep(min(0.4 * (attempt + 1), 2.0))
        raise ConnectionError(
            f"Could not restore the Wi-Fi keyboard connection after {retries} attempts: {last_error}"
        )

    def ping(self):
        self.send_line("K:PING", retries=2)

    def _close_socket_locked(self):
        if self.sock is not None:
            try: self.sock.shutdown(socket.SHUT_RDWR)
            except OSError: pass
            try: self.sock.close()
            except OSError: pass
            self.sock = None

    def close(self):
        with self.lock:
            self.closed = True
            self._close_socket_locked()


keyboard_connection = None


def get_active_window_bbox():
    if not hasattr(ctypes, "windll"):
        raise RuntimeError("Active-window capture is supported on Windows only.")

    user32 = ctypes.windll.user32
    hwnd = user32.GetForegroundWindow()

    if not hwnd:
        raise RuntimeError("No foreground window was found.")

    class RECT(ctypes.Structure):
        _fields_ = [
            ("left", ctypes.c_long),
            ("top", ctypes.c_long),
            ("right", ctypes.c_long),
            ("bottom", ctypes.c_long),
        ]

    rect = RECT()

    if not user32.GetWindowRect(hwnd, ctypes.byref(rect)):
        raise RuntimeError("Windows could not read the foreground window bounds.")

    if rect.right <= rect.left or rect.bottom <= rect.top:
        raise RuntimeError("The foreground window has invalid bounds.")

    return (rect.left, rect.top, rect.right, rect.bottom)


def capture_png(full_screen=False):
    if full_screen:
        image = ImageGrab.grab(all_screens=True)
    else:
        bbox = get_active_window_bbox()
        image = ImageGrab.grab(bbox=bbox, all_screens=True)

    output = io.BytesIO()
    image.save(output, format="PNG", optimize=True)
    data = output.getvalue()

    if not data:
        raise RuntimeError("Screenshot capture produced an empty image.")

    if len(data) > MAX_SCREENSHOT_BYTES:
        raise RuntimeError(
            f"Screenshot is too large ({len(data) / (1024*1024):.1f} MB). "
            "The current transfer limit is 30 MB."
        )

    return data


def send_screenshot_bytes(data):
    if not wifi_phone_ip:
        raise RuntimeError(
            "Screenshot transfer requires a Same Wi-Fi connection. "
            "Reconnect using connection mode 1."
        )

    with socket.create_connection(
        (wifi_phone_ip, SCREENSHOT_PORT),
        timeout=8
    ) as sock:
        sock.settimeout(12)
        sock.sendall(struct.pack(">I", len(data)))
        sock.sendall(data)

        ack = sock.recv(16)

        if ack.strip() != b"OK":
            raise RuntimeError(
                "The phone did not confirm the screenshot transfer."
            )


def screenshot_worker(full_screen):
    label = "full screen" if full_screen else "active window"

    try:
        data = capture_png(full_screen=full_screen)
        send_screenshot_bytes(data)
        notify_user(
            f"{label.capitalize()} screenshot sent to the phone. "
            "Open the WiFiSync notification on Android to preview/share it.",
            "Screenshot sent",
        )
    except Exception as exc:
        notify_user(str(exc), "Screenshot failed")


def start_screenshot_transfer(full_screen=False):
    if not wifi_phone_ip:
        notify_user(
            "Screenshot transfer is available in Same Wi-Fi mode only.",
            "Screenshot unavailable",
        )
        return

    threading.Thread(
        target=screenshot_worker,
        args=(full_screen,),
        daemon=True,
    ).start()


def on_press(key):
    global shift_down

    if key in SHIFT_KEYS:
        with state_lock:
            shift_down = True
        return

    # F8 toggles keyboard forwarding.
    if key == keyboard.Key.f8:
        toggle_forwarding()
        return

    # F9 captures an active window.
    # Holding Shift while pressing F9 captures the full desktop instead.
    if key == keyboard.Key.f9:
        with state_lock:
            full_screen = shift_down

        start_screenshot_transfer(full_screen=full_screen)
        return

    if key == keyboard.Key.esc:
        enqueue("__QUIT__")
        return False

    if not is_forwarding():
        return

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
    print("F8          Toggle LAPTOP ONLY / MIRROR TO PHONE")
    print("Enter       Normal Android Enter")
    print("Shift+Enter Shift-modified Enter")
    print("F9          Capture active window and send to Android (Wi-Fi mode)")
    print("Shift+F9    Capture full desktop and send to Android (Wi-Fi mode)")
    print("ESC         Disconnect and exit")
    print()
    print("F8 does not suppress Windows input; mirror mode sends a copy to Android.")
    print("Screenshot capture is user-triggered and WiFiSync reports the result")
    print("through its normal Windows tray/status notification.")
    print()


async def wifi_mode():
    global wifi_phone_ip, keyboard_connection

    ip = input("\nEnter the phone IP shown in the Android app: ").strip()
    try:
        parsed = ipaddress.ip_address(ip)
        if parsed.version != 4:
            raise ValueError("WiFiSync currently expects an IPv4 address.")
    except ValueError as exc:
        print(f"\nInvalid phone IP address: {exc}")
        return

    wifi_phone_ip = ip
    keyboard_connection = KeyboardConnection(ip, WIFI_PORT)
    print(f"\nConnecting keyboard channel to {ip}:{WIFI_PORT} ...")
    try:
        await asyncio.to_thread(keyboard_connection.connect)
    except Exception as exc:
        wifi_phone_ip = None
        keyboard_connection = None
        print(f"\nWi-Fi connection failed: {exc}")
        print("\nCheck:")
        print("  - Laptop and phone are on the same Wi-Fi")
        print("  - WiFiSync is selected as the Android keyboard")
        print("  - The displayed phone IP is correct")
        print("  - The Wi-Fi does not isolate devices from one another")
        return

    print("\nCONNECTED OVER WI-FI")
    print(f"Screenshot receiver: {ip}:{SCREENSHOT_PORT}")
    print("Automatic reconnect: ENABLED")
    print_controls()
    set_forwarding(False)
    start_tray()
    listener = start_listener()

    heartbeat_stop = asyncio.Event()
    async def heartbeat():
        while not heartbeat_stop.is_set():
            try:
                await asyncio.wait_for(heartbeat_stop.wait(), timeout=12.0)
                break
            except asyncio.TimeoutError:
                pass
            try:
                await asyncio.to_thread(keyboard_connection.ping)
            except Exception as exc:
                print(f"\n[Wi-Fi heartbeat reconnect pending: {exc}]")

    heartbeat_task = asyncio.create_task(heartbeat())
    try:
        while True:
            command = await queue.get()
            if command == "__QUIT__":
                break
            try:
                await asyncio.to_thread(keyboard_connection.send_line, command)
            except Exception as exc:
                print(f"\nWi-Fi keyboard connection could not be restored: {exc}")
                print("WiFiSync is still running; it will retry on subsequent input.")
    finally:
        heartbeat_stop.set()
        try: await heartbeat_task
        except Exception: pass
        listener.stop()
        stop_tray()
        if keyboard_connection is not None:
            keyboard_connection.close()
        keyboard_connection = None
        wifi_phone_ip = None


async def find_ble_phone():
    print("\nScanning for phone over Bluetooth LE...")

    devices = await BleakScanner.discover(
        timeout=8.0,
        return_adv=True,
    )

    for _, (device, adv) in devices.items():
        service_uuids = [
            item.lower()
            for item in (adv.service_uuids or [])
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
        return

    print(f"Found: {phone.name or phone.address}")
    print("Connecting...")

    try:
        async with BleakClient(phone) as client:
            if not client.is_connected:
                print("Could not connect.")
                return

            print("\nCONNECTED OVER BLUETOOTH")
            print("Keyboard forwarding works in this mode.")
            print("Screenshot transfer requires Same Wi-Fi mode.")
            print_controls()

            set_forwarding(False)
            start_tray()
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
                            response=False,
                        )
                    except Exception as exc:
                        print(f"\nBluetooth connection lost: {exc}")
                        break
            finally:
                listener.stop()
                stop_tray()

    except Exception as exc:
        print(f"\nBluetooth connection failed: {exc}")


async def main():
    global loop, queue

    loop = asyncio.get_running_loop()
    queue = asyncio.Queue()

    print("===================================")
    print("             WiFiSync")
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
    finally:
        stop_tray()
