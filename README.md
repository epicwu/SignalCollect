# SignalCollect

An Android app that continuously logs cellular signal strength together with GPS position. It is meant for drive or walk tests that map coverage. Once per second it samples the serving cell (LAC, cell ID, signal strength), neighboring cells, the current Wi‑Fi connection and the GPS fix. It then:

- shows the latest readings on screen,
- uploads location-bucketed signal averages to a MongoDB server, and
- writes raw samples to text files on the SD card.

## How it works

| Component | Role |
|---|---|
| `SignalCollect` (Activity) | Entry point. Holds a partial wake lock so sampling continues with the screen off, starts the collector, and displays updates. Pressing **Back** sends the app to the home screen instead of exiting. |
| `BackgroundCollecting` | Runs a 1 s `Timer`. Each tick it reads GSM cell location, neighboring cells, signal strength (via `PhoneStateListener`), the GPS fix (1 s / 1 m updates) and Wi‑Fi SSID/RSSI. It then pushes them to the UI through a `Messenger`. |
| `MongoHelper` | Connects to MongoDB and upserts one aggregated record per (cell, location bucket). |
| `SignalRecord` | Plain data holder: `lac`, `cellid`, `ss`, `lat`, `lon`, `time`. |
| `AutoBoot` | `BOOT_COMPLETED` receiver that relaunches the app after the device restarts. |

Signal strength is converted from ASU to dBm with `dBm = -113 + 2 × ASU`.

### MongoDB output

- **Server:** `BackgroundCollecting.ServerIP`, port `27017` (hard-coded)
- **Database:** `test`
- **Collection:** the device model name (`Build.MODEL`, with spaces and dashes replaced by `_`)
- **Document shape:**

  ```json
  { "cid": 12345, "lac": 678, "lat": 473765, "lon": 85480, "ss": -79.5, "cnt": 12 }
  ```

  `lat` and `lon` are degrees × 10⁴, truncated to an int, which gives buckets of roughly 10 m. `ss` is the running average in dBm across `cnt` samples in that bucket.

A sample is skipped if there is no GPS fix (lat/lon ≈ 0). It is also skipped if the position jumped by more than 2 buckets since the previous sample, which filters out GPS glitches.

### File output

Raw samples are buffered in memory and flushed to `<external storage>/celldata/` every 10 minutes (on the :00, :10, :20 … mark) and when the activity is destroyed. Each file is named `yyyy-MM-dd_H_M.txt` and looks like this:

```
<device model>
<SIM operator name>(<MCC+MNC>)
<minute> <second> <lac> <cid> <ss_dBm> <lat> <lon>
...
```

## Building

This is a legacy **Eclipse ADT / Ant** project with no Gradle build.

- **Target SDK:** `android-19` (`project.properties`), `minSdkVersion 8`, `targetSdkVersion 18`
- **Bundled libraries** (in `libs/`): `mongo-java-driver-2.12.0-rc0.jar`, `android-support-v4.jar`

Two ways to build it:

1. **Eclipse ADT:** *File → Import → Existing Android Code into Workspace*, then pick this folder.
2. **Ant:**
   ```sh
   android update project -p . -t android-19
   ant debug
   adb install -r bin/SignalCollect-debug.apk
   ```

Android Studio can also import the project through *File → New → Import Project*, which converts it to Gradle.

## Configuration

Before running, point the app at your own MongoDB instance by editing `ServerIP` in `src/com/example/signalcollect/BackgroundCollecting.java`. The server has to accept unauthenticated connections on port 27017 from the device.

## Running

1. Install the app on a GSM/UMTS phone with GPS turned on.
2. Launch **SignalCollect**. The screen shows the device model, running time, GPS position, the serving and neighbor cells, and Wi‑Fi info.
3. GPS coordinates appear on screen after about 40 seconds, while the receiver gets a fix.
4. Leave it running. It keeps sampling in the background and restarts automatically after a reboot.

## Known limitations

- **GSM only.** The app casts `getCellLocation()` to `GsmCellLocation`, so it crashes on CDMA networks. It also depends on `getNeighboringCellInfo()`, which modern Android deprecated and which returns nothing there.
- **Southern and western hemispheres are dropped.** `MongoHelper.insert` treats `lat < 1e-6 || lon < 1e-6` as "no fix", so negative coordinates are never uploaded. The jump filter also only checks for increases in position.
- **Network on the main thread.** The MongoDB connection is opened in `onCreate`, and `StrictMode` is relaxed to allow it.
- **Malformed permissions.** Several permissions in `AndroidManifest.xml` lack the `android.permission.` prefix, including `READ_PHONE_STATE` and `ACCESS_COARSE_LOCATION`. Android ignores these, and newer versions also need runtime permission prompts that the app doesn't implement.
- **No auth or TLS.** The MongoDB server address is hard-coded and the connection is unencrypted.
- **Build outputs committed.** The `bin/` and `gen/` folders are build artifacts and could be added to `.gitignore`.
