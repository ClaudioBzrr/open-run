package com.openrun.app.util;

import com.openrun.app.data.PauseEntity;
import com.openrun.app.data.PointEntity;
import com.openrun.app.data.SessionEntity;
import com.openrun.app.data.SessionRepository;
import com.openrun.app.data.SplitEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypted Open Run backup (.runbackup).
 *
 * <p>The payload is a ZIP (manifest + JSON tables) encrypted as one AES-256-GCM
 * stream. The header carries only the KDF parameters and nonces; the passphrase
 * never leaves the device and is not stored.</p>
 */
public final class BackupManager {
    private static final byte[] MAGIC = "ORUNBAK1".getBytes(StandardCharsets.US_ASCII);
    private static final int FORMAT_VERSION = 1;
    private static final int KDF_ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int MAX_ENTRY_BYTES = 200 * 1024 * 1024;

    private BackupManager() {
    }

    public static void exportBackup(SessionRepository repository,
                                    OutputStream output,
                                    char[] passphrase) throws Exception {
        List<SessionEntity> allSessions = repository.loadAllSessions();
        List<PointEntity> allPoints = repository.loadAllPoints();
        List<PauseEntity> allPauses = repository.loadAllPauses();
        List<SplitEntity> allSplits = repository.loadAllSplits();

        List<SessionEntity> sessions = new ArrayList<>();
        Set<String> includedIds = new HashSet<>();
        for (SessionEntity session : allSessions) {
            if (session == null || session.deleted || !"FINISHED".equals(session.state)) {
                continue;
            }
            sessions.add(session);
            includedIds.add(session.id);
        }

        List<PointEntity> points = new ArrayList<>();
        for (PointEntity point : allPoints) {
            if (includedIds.contains(point.sessionId)) {
                points.add(point);
            }
        }
        List<PauseEntity> pauses = new ArrayList<>();
        for (PauseEntity pause : allPauses) {
            if (includedIds.contains(pause.sessionId)) {
                pauses.add(pause);
            }
        }
        List<SplitEntity> splits = new ArrayList<>();
        for (SplitEntity split : allSplits) {
            if (includedIds.contains(split.sessionId)) {
                splits.add(split);
            }
        }

        Header header = createHeader(passphrase);
        writeHeader(output, header);
        Cipher cipher = createCipher(Cipher.ENCRYPT_MODE, passphrase, header);
        try (CipherOutputStream encrypted = new CipherOutputStream(output, cipher);
             ZipOutputStream zip = new ZipOutputStream(encrypted)) {
            JSONObject manifest = new JSONObject();
            manifest.put("format", FORMAT_VERSION);
            manifest.put("exportedAt", System.currentTimeMillis());
            manifest.put("sessions", sessions.size());
            manifest.put("points", points.size());
            manifest.put("pauses", pauses.size());
            manifest.put("splits", splits.size());
            writeEntry(zip, "manifest.json", manifest.toString());

            JSONArray sessionArray = new JSONArray();
            for (SessionEntity session : sessions) {
                sessionArray.put(sessionToJson(session));
            }
            writeEntry(zip, "sessions.json", sessionArray.toString());

            JSONArray pointArray = new JSONArray();
            for (PointEntity point : points) {
                pointArray.put(pointToJson(point));
            }
            writeEntry(zip, "points.json", pointArray.toString());

            JSONArray pauseArray = new JSONArray();
            for (PauseEntity pause : pauses) {
                pauseArray.put(pauseToJson(pause));
            }
            writeEntry(zip, "pauses.json", pauseArray.toString());

            JSONArray splitArray = new JSONArray();
            for (SplitEntity split : splits) {
                splitArray.put(splitToJson(split));
            }
            writeEntry(zip, "splits.json", splitArray.toString());
        }
    }

    public static ImportResult importBackup(SessionRepository repository,
                                            InputStream input,
                                            char[] passphrase) throws Exception {
        Map<String, String> entries = new HashMap<>();
        Header header = readHeader(input);
        Cipher cipher = createCipher(Cipher.DECRYPT_MODE, passphrase, header);
        try (CipherInputStream decrypted = new CipherInputStream(input, cipher);
             ZipInputStream zip = new ZipInputStream(decrypted)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (!"manifest.json".equals(name)
                        && !"sessions.json".equals(name)
                        && !"points.json".equals(name)
                        && !"pauses.json".equals(name)
                        && !"splits.json".equals(name)) {
                    continue;
                }
                entries.put(name, readEntry(zip));
            }
        }

        if (!entries.containsKey("manifest.json")
                || !entries.containsKey("sessions.json")) {
            throw new IOException("Invalid backup payload");
        }
        JSONObject manifest = new JSONObject(entries.get("manifest.json"));
        if (manifest.optInt("format", 0) != FORMAT_VERSION) {
            throw new IOException("Unsupported backup version");
        }

        List<SessionEntity> sessions = parseSessions(entries.get("sessions.json"));
        List<PointEntity> points = parsePoints(entries.get("points.json"));
        List<PauseEntity> pauses = parsePauses(entries.get("pauses.json"));
        List<SplitEntity> splits = parseSplits(entries.get("splits.json"));

        Map<String, List<PointEntity>> pointsBySession = groupPoints(points);
        Map<String, List<PauseEntity>> pausesBySession = groupPauses(pauses);
        Map<String, List<SplitEntity>> splitsBySession = groupSplits(splits);

        int imported = 0;
        int skipped = 0;
        for (SessionEntity session : sessions) {
            boolean changed = repository.importSession(
                    session,
                    pointsBySession.get(session.id),
                    pausesBySession.get(session.id),
                    splitsBySession.get(session.id)
            );
            if (changed) {
                imported++;
            } else {
                skipped++;
            }
        }
        return new ImportResult(imported, skipped, points.size(), pauses.size(), splits.size());
    }

    private static Header createHeader(char[] passphrase) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(salt);
        random.nextBytes(nonce);
        return new Header(KDF_ITERATIONS, salt, nonce);
    }

    private static Cipher createCipher(int mode,
                                       char[] passphrase,
                                       Header header) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(
                "PBKDF2WithHmacSHA256"
        );
        PBEKeySpec spec = new PBEKeySpec(
                passphrase,
                header.salt,
                header.iterations,
                256
        );
        SecretKey key;
        try {
            key = new SecretKeySpec(
                    factory.generateSecret(spec).getEncoded(),
                    "AES"
            );
        } finally {
            spec.clearPassword();
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                mode,
                key,
                new GCMParameterSpec(128, header.nonce)
        );
        cipher.updateAAD(headerBytes(header));
        return cipher;
    }

    private static void writeHeader(OutputStream output, Header header) throws IOException {
        DataOutputStream data = new DataOutputStream(output);
        data.write(MAGIC);
        data.writeInt(FORMAT_VERSION);
        data.writeInt(header.iterations);
        data.write(header.salt);
        data.write(header.nonce);
        data.flush();
    }

    private static Header readHeader(InputStream input) throws IOException {
        DataInputStream data = new DataInputStream(input);
        byte[] magic = new byte[MAGIC.length];
        data.readFully(magic);
        for (int i = 0; i < MAGIC.length; i++) {
            if (magic[i] != MAGIC[i]) {
                throw new IOException("Not an Open Run backup");
            }
        }
        int version = data.readInt();
        if (version != FORMAT_VERSION) {
            throw new IOException("Unsupported backup version");
        }
        int iterations = data.readInt();
        if (iterations < 100_000 || iterations > 2_000_000) {
            throw new IOException("Invalid KDF parameters");
        }
        byte[] salt = new byte[SALT_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        data.readFully(salt);
        data.readFully(nonce);
        return new Header(iterations, salt, nonce);
    }

    private static byte[] headerBytes(Header header) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.write(MAGIC);
        data.writeInt(FORMAT_VERSION);
        data.writeInt(header.iterations);
        data.write(header.salt);
        data.write(header.nonce);
        data.flush();
        return bytes.toByteArray();
    }

    private static void writeEntry(ZipOutputStream zip, String name, String value)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String readEntry(ZipInputStream zip) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        int total = 0;
        while ((read = zip.read(buffer)) > 0) {
            total += read;
            if (total > MAX_ENTRY_BYTES) {
                throw new IOException("Backup entry too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static JSONObject sessionToJson(SessionEntity session) throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", session.id);
        json.put("type", session.type);
        json.put("state", session.state);
        json.put("startedAt", session.startedAt);
        json.put("startedElapsed", session.startedElapsed);
        json.put("recordingStartedElapsed", session.recordingStartedElapsed);
        json.put("recordingStartedAt", session.recordingStartedAt);
        json.put("bootCount", session.bootCount);
        putNullable(json, "endedAt", session.endedAt);
        json.put("movingMs", session.movingMs);
        json.put("pausedMs", session.pausedMs);
        json.put("distanceM", session.distanceM);
        json.put("elevationGainM", session.elevationGainM);
        json.put("elevationLossM", session.elevationLossM);
        json.put("pointCount", session.pointCount);
        putNullable(json, "lastLat", session.lastLat);
        putNullable(json, "lastLon", session.lastLon);
        putNullable(json, "lastAccuracy", session.lastAccuracy);
        putNullable(json, "lastPointAt", session.lastPointAt);
        json.put("createdAt", session.createdAt);
        json.put("updatedAt", session.updatedAt);
        json.put("deleted", session.deleted);
        return json;
    }

    private static JSONObject pointToJson(PointEntity point) throws Exception {
        JSONObject json = new JSONObject();
        json.put("sessionId", point.sessionId);
        json.put("seq", point.seq);
        json.put("lat", point.lat);
        json.put("lon", point.lon);
        putNullable(json, "altitude", point.altitude);
        putNullable(json, "accuracy", point.accuracy);
        putNullable(json, "speed", point.speed);
        putNullable(json, "bearing", point.bearing);
        json.put("timestamp", point.timestamp);
        json.put("elapsedMs", point.elapsedMs);
        json.put("movingElapsedMs", point.movingElapsedMs);
        return json;
    }

    private static JSONObject pauseToJson(PauseEntity pause) throws Exception {
        JSONObject json = new JSONObject();
        json.put("sessionId", pause.sessionId);
        json.put("startedElapsedMs", pause.startedElapsedMs);
        putNullable(json, "endedElapsedMs", pause.endedElapsedMs);
        json.put("startedAtEpochMs", pause.startedAtEpochMs);
        putNullable(json, "endedAtEpochMs", pause.endedAtEpochMs);
        json.put("autoPause", pause.autoPause);
        return json;
    }

    private static JSONObject splitToJson(SplitEntity split) throws Exception {
        JSONObject json = new JSONObject();
        json.put("sessionId", split.sessionId);
        json.put("splitIndex", split.splitIndex);
        json.put("distanceM", split.distanceM);
        json.put("durationMs", split.durationMs);
        json.put("paceSecPerKm", split.paceSecPerKm);
        return json;
    }

    private static List<SessionEntity> parseSessions(String value) throws Exception {
        List<SessionEntity> sessions = new ArrayList<>();
        if (value == null) {
            return sessions;
        }
        JSONArray array = new JSONArray(value);
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.getJSONObject(i);
            SessionEntity session = new SessionEntity();
            session.id = json.getString("id");
            session.type = json.optString("type", "run");
            session.state = json.optString("state", "FINISHED");
            session.startedAt = json.optLong("startedAt");
            session.startedElapsed = json.optLong("startedElapsed");
            session.recordingStartedElapsed = json.optLong("recordingStartedElapsed");
            session.recordingStartedAt = json.optLong("recordingStartedAt");
            session.bootCount = json.optInt("bootCount");
            session.endedAt = nullableLong(json, "endedAt");
            session.movingMs = json.optLong("movingMs");
            session.pausedMs = json.optLong("pausedMs");
            session.distanceM = json.optDouble("distanceM", 0);
            session.elevationGainM = json.optDouble("elevationGainM", 0);
            session.elevationLossM = json.optDouble("elevationLossM", 0);
            session.pointCount = json.optInt("pointCount");
            session.lastLat = nullableDouble(json, "lastLat");
            session.lastLon = nullableDouble(json, "lastLon");
            session.lastAccuracy = nullableDouble(json, "lastAccuracy");
            session.lastPointAt = nullableLong(json, "lastPointAt");
            session.createdAt = json.optLong("createdAt", System.currentTimeMillis());
            session.updatedAt = json.optLong("updatedAt", System.currentTimeMillis());
            session.deleted = json.optBoolean("deleted", false);
            sessions.add(session);
        }
        return sessions;
    }

    private static List<PointEntity> parsePoints(String value) throws Exception {
        List<PointEntity> points = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return points;
        }
        JSONArray array = new JSONArray(value);
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.getJSONObject(i);
            PointEntity point = new PointEntity();
            point.sessionId = json.getString("sessionId");
            point.seq = json.optInt("seq");
            point.lat = json.optDouble("lat");
            point.lon = json.optDouble("lon");
            point.altitude = nullableDouble(json, "altitude");
            point.accuracy = nullableDouble(json, "accuracy");
            point.speed = nullableDouble(json, "speed");
            point.bearing = nullableDouble(json, "bearing");
            point.timestamp = json.optLong("timestamp");
            point.elapsedMs = json.optLong("elapsedMs");
            point.movingElapsedMs = json.optLong("movingElapsedMs");
            points.add(point);
        }
        return points;
    }

    private static List<PauseEntity> parsePauses(String value) throws Exception {
        List<PauseEntity> pauses = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return pauses;
        }
        JSONArray array = new JSONArray(value);
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.getJSONObject(i);
            PauseEntity pause = new PauseEntity();
            pause.sessionId = json.getString("sessionId");
            pause.startedElapsedMs = json.optLong("startedElapsedMs");
            pause.endedElapsedMs = nullableLong(json, "endedElapsedMs");
            pause.startedAtEpochMs = json.optLong("startedAtEpochMs");
            pause.endedAtEpochMs = nullableLong(json, "endedAtEpochMs");
            pause.autoPause = json.optBoolean("autoPause", false);
            pauses.add(pause);
        }
        return pauses;
    }

    private static List<SplitEntity> parseSplits(String value) throws Exception {
        List<SplitEntity> splits = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return splits;
        }
        JSONArray array = new JSONArray(value);
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.getJSONObject(i);
            SplitEntity split = new SplitEntity();
            split.sessionId = json.getString("sessionId");
            split.splitIndex = json.optInt("splitIndex");
            split.distanceM = json.optDouble("distanceM", 1000);
            split.durationMs = json.optLong("durationMs");
            split.paceSecPerKm = json.optDouble("paceSecPerKm");
            splits.add(split);
        }
        return splits;
    }

    private static Map<String, List<PointEntity>> groupPoints(List<PointEntity> points) {
        Map<String, List<PointEntity>> grouped = new HashMap<>();
        for (PointEntity point : points) {
            grouped.computeIfAbsent(point.sessionId, key -> new ArrayList<>())
                    .add(point);
        }
        return grouped;
    }

    private static Map<String, List<PauseEntity>> groupPauses(List<PauseEntity> pauses) {
        Map<String, List<PauseEntity>> grouped = new HashMap<>();
        for (PauseEntity pause : pauses) {
            grouped.computeIfAbsent(pause.sessionId, key -> new ArrayList<>())
                    .add(pause);
        }
        return grouped;
    }

    private static Map<String, List<SplitEntity>> groupSplits(List<SplitEntity> splits) {
        Map<String, List<SplitEntity>> grouped = new HashMap<>();
        for (SplitEntity split : splits) {
            grouped.computeIfAbsent(split.sessionId, key -> new ArrayList<>())
                    .add(split);
        }
        return grouped;
    }

    private static void putNullable(JSONObject json, String key, Object value)
            throws Exception {
        json.put(key, value == null ? JSONObject.NULL : value);
    }

    private static Long nullableLong(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        return json.optLong(key);
    }

    private static Double nullableDouble(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        double value = json.optDouble(key);
        return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
    }

    private static final class Header {
        final int iterations;
        final byte[] salt;
        final byte[] nonce;

        Header(int iterations, byte[] salt, byte[] nonce) {
            this.iterations = iterations;
            this.salt = salt;
            this.nonce = nonce;
        }
    }

    public static final class ImportResult {
        public final int sessionsImported;
        public final int sessionsSkipped;
        public final int points;
        public final int pauses;
        public final int splits;

        ImportResult(int sessionsImported,
                     int sessionsSkipped,
                     int points,
                     int pauses,
                     int splits) {
            this.sessionsImported = sessionsImported;
            this.sessionsSkipped = sessionsSkipped;
            this.points = points;
            this.pauses = pauses;
            this.splits = splits;
        }
    }
}
