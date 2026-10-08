#!/usr/bin/env python3
"""
GeoPulse Device Simulator
Simulates GPS asset trackers publishing telemetry to Mosquitto MQTT broker.
Configures MQTT Last-Will-and-Testament (LWT) for automatic offline status detection.
"""

import argparse
import json
import random
import time
import uuid
from datetime import datetime, timezone

try:
    import paho.mqtt.client as mqtt
except ImportError:
    print("paho-mqtt is required. Run: pip install paho-mqtt")
    exit(1)

DEVICES = [
    {
        "id": "GPS-001",
        "name": "Fleet Van 01",
        "lat": 37.7749,
        "lng": -122.4194,
        "speed": 50.0,
        "heading": 90.0,
        "battery": 85,
    },
    {
        "id": "GPS-002",
        "name": "Long-Haul Truck 12",
        "lat": 37.7833,
        "lng": -122.4167,
        "speed": 125.0,  # Will trigger speeding alert (>120 km/h)
        "heading": 180.0,
        "battery": 12,   # Will trigger low battery alert (<15%)
    },
    {
        "id": "GPS-003",
        "name": "Cargo Container 99",
        "lat": 37.8044,
        "lng": -122.2712,
        "speed": 35.0,
        "heading": 270.0,
        "battery": 95,
    },
]


def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print(f"[Simulator] Connected to MQTT broker successfully.")
    else:
        print(f"[Simulator] Failed to connect, return code {rc}")


def main():
    parser = argparse.ArgumentParser(description="GeoPulse Fleet Tracker Simulator")
    parser.add_argument("--broker", default="localhost", help="MQTT broker host")
    parser.add_argument("--port", type=int, default=1883, help="MQTT broker port")
    parser.add_argument("--interval", type=float, default=2.0, help="Publish interval in seconds")
    parser.add_argument("--count", type=int, default=10, help="Number of telemetry cycles (0 for infinite)")
    args = parser.parse_args()

    client = mqtt.Client(client_id=f"geopulse-sim-{uuid.uuid4().hex[:8]}")
    client.on_connect = on_connect

    # Configure LWT for device status
    lwt_payload = json.dumps({"deviceId": "GPS-SIM-ALL", "status": "OFFLINE"})
    client.will_set("devices/status", payload=lwt_payload, qos=1, retain=False)

    print(f"[Simulator] Connecting to MQTT broker at {args.broker}:{args.port}...")
    client.connect(args.broker, args.port, keepalive=60)
    client.loop_start()

    cycle = 0
    try:
        while args.count == 0 or cycle < args.count:
            cycle += 1
            print(f"\n--- Cycle {cycle} ---")
            for dev in DEVICES:
                # Random drift
                dev["lat"] += (random.random() - 0.48) * 0.002
                dev["lng"] += (random.random() - 0.48) * 0.002
                dev["battery"] = max(5, dev["battery"] - random.randint(0, 1))

                payload = {
                    "deviceId": dev["id"],
                    "lat": round(dev["lat"], 6),
                    "lng": round(dev["lng"], 6),
                    "speedKmh": round(dev["speed"] + (random.random() - 0.5) * 5.0, 1),
                    "headingDeg": round(dev["heading"], 1),
                    "batteryPct": dev["battery"],
                    "sos": False,
                    "recordedAt": datetime.now(timezone.utc).isoformat(),
                    "messageId": f"msg-{uuid.uuid4()}"
                }

                # Occasional SOS test for GPS-003 on cycle 3
                if dev["id"] == "GPS-003" and cycle == 3:
                    payload["sos"] = True
                    print(f"  [!] Triggering SOS for {dev['id']}")

                topic = f"devices/{dev['id']}/telemetry"
                client.publish(topic, json.dumps(payload), qos=1)
                print(f"  -> Published to {topic}: lat={payload['lat']}, lng={payload['lng']}, speed={payload['speedKmh']}km/h, bat={payload['batteryPct']}%")

            time.sleep(args.interval)

    except KeyboardInterrupt:
        print("\n[Simulator] Stopping simulator...")
    finally:
        client.loop_stop()
        client.disconnect()
        print("[Simulator] Disconnected.")


if __name__ == "__main__":
    main()
