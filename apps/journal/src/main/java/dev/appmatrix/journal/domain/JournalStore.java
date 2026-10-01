// SPDX-License-Identifier: GPL-3.0-or-later
package dev.appmatrix.journal.domain;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Versioned local snapshots. A single atomic pointer swap is the commit boundary. */
public final class JournalStore {
  public static final int VERSION = 1, MAX_ENTRIES = 1000, MAX_FILES = 2000;
  public static final long MAX_FILE = 12L * 1024 * 1024,
      MAX_ARCHIVE = 128L * 1024 * 1024,
      MAX_MANIFEST = 8L * 1024 * 1024;

  public interface MediaValidator {
    void validate(File file) throws IOException;
  }

  public static final class State {
    public final List<Entry> entries;
    public final String theme, journalId;
    public final int quality;
    public final File directory;

    State(List<Entry> entries, String theme, int quality, File directory, String journalId) {
      this.entries = Collections.unmodifiableList(entries);
      this.theme = theme;
      this.quality = quality;
      this.directory = directory;
      this.journalId = journalId;
    }

    public File media(String name) throws IOException {
      Entry.checkMediaName(name);
      return new File(new File(directory, "media"), name);
    }

    public int photoCount() {
      int n = 0;
      for (Entry entry : entries) if (!entry.source.isEmpty()) n++;
      return n;
    }
  }

  public static final class Preview {
    public final State state;
    private boolean consumed;
    private final boolean imported;

    Preview(State state, boolean imported) {
      this.state = state;
      this.imported = imported;
    }
  }

  private static final Set<String> OPENED_ROOTS = new HashSet<>();
  private final File root;
  private final MediaValidator validator;
  private String currentJournalId;

  public JournalStore(File root, MediaValidator validator) throws IOException {
    this.root = root;
    this.validator = validator;
    mkdir(root);
    // Only the first opener in this process removes crash leftovers. Another Activity
    // or store opened during an in-flight job must not remove its active staging area.
    synchronized (OPENED_ROOTS) {
      if (OPENED_ROOTS.add(root.getCanonicalPath())) {
        File[] abandoned = root.listFiles();
        if (abandoned != null)
          for (File file : abandoned)
            if (file.getName()
                .matches(
                    "stage-[a-f0-9-]{36}|incoming-[a-f0-9-]{36}\\.zip|active-[a-f0-9-]{36}\\.tmp"))
              removeTree(file);
      }
    }
  }

  public synchronized State load() throws IOException {
    File pointer = new File(root, "active");
    if (!pointer.exists()) {
      File[] existing = root.listFiles((d, n) -> n.matches("snapshot-[a-f0-9-]{36}"));
      if (existing != null && existing.length > 0)
        throw new IOException(
            "Journal index is missing; saved observations are retained for recovery");
      currentJournalId = "00000000-0000-0000-0000-000000000000";
      return new State(new ArrayList<>(), "system", 90, null, currentJournalId);
    }
    String name = new String(readLimited(pointer, 128), StandardCharsets.UTF_8).trim();
    if (!name.matches("snapshot-[a-f0-9-]{36}"))
      throw new IOException("Journal index is damaged; existing data has been kept");
    State result = readState(new File(root, name));
    currentJournalId = result.journalId;
    return result;
  }

  public synchronized State save(
      List<Entry> entries, String theme, int quality, Map<String, File> media) throws IOException {
    File stage = new File(root, "stage-" + UUID.randomUUID());
    mkdir(stage);
    try {
      if (entries.size() > MAX_ENTRIES)
        throw new IOException("The journal supports up to 1,000 observations");
      validateSettings(theme, quality);
      Set<String> required = references(entries);
      long total = 0;
      mkdir(new File(stage, "media"));
      if (currentJournalId == null) load();
      Properties manifest = encode(entries, theme, quality, currentJournalId);
      for (String name : required) {
        File source = media.get(name);
        if (source == null || !source.isFile())
          throw new IOException("An observation photo is missing");
        if (source.length() > MAX_FILE) throw new IOException("A photo exceeds the 12 MB limit");
        total += source.length();
        if (total > MAX_ARCHIVE - MAX_MANIFEST)
          throw new IOException(
              "The journal exceeds the 120 MB media limit; export and remove older entries");
        File target = new File(new File(stage, "media"), name);
        copy(source, target, MAX_FILE);
        validator.validate(target);
        manifest.setProperty("sha256." + name, sha256(target));
      }
      writeProperties(new File(stage, "journal.properties"), manifest);
      State validated = readState(stage);
      return activate(new Preview(validated, false));
    } catch (IOException | RuntimeException | Error e) {
      removeTree(stage);
      throw e;
    }
  }

  public synchronized void exportBackup(State state, OutputStream output) throws IOException {
    if (state.directory == null) {
      File stage = new File(root, "stage-" + UUID.randomUUID());
      mkdir(stage);
      mkdir(new File(stage, "media"));
      try {
        writeProperties(
            new File(stage, "journal.properties"),
            encode(state.entries, state.theme, state.quality, state.journalId));
        exportDirectory(readState(stage), output);
      } finally {
        removeTree(stage);
      }
    } else exportDirectory(state, output);
  }

  private void exportDirectory(State state, OutputStream output) throws IOException {
    try (ZipOutputStream zip = new ZipOutputStream(output)) {
      put(zip, "journal.properties", new File(state.directory, "journal.properties"));
      for (String name : references(state.entries)) put(zip, "media/" + name, state.media(name));
      zip.finish();
    }
  }

  /** Fully validates a backup in staging. This method cannot change the active journal. */
  public synchronized Preview previewRestore(InputStream input) throws IOException {
    File stage = new File(root, "stage-" + UUID.randomUUID());
    mkdir(stage);
    mkdir(new File(stage, "media"));
    File archive = new File(root, "incoming-" + UUID.randomUUID() + ".zip");
    try {
      // Materialize the bounded archive first. ZipFile requires a central directory and
      // rejects truncation which a streaming ZIP reader alone can silently accept.
      try (InputStream bounded = input) {
        copy(bounded, archive, MAX_ARCHIVE);
      }
      Set<String> seen = new HashSet<>();
      long total = 0;
      try (ZipFile zip = new ZipFile(archive)) {
        Enumeration<? extends ZipEntry> items = zip.entries();
        while (items.hasMoreElements()) {
          ZipEntry item = items.nextElement();
          String name = item.getName();
          if (item.isDirectory() || !safeArchivePath(name))
            throw new IOException("Backup contains an unsafe or unexpected path");
          if (!seen.add(name) || seen.size() > MAX_FILES + 1)
            throw new IOException("Backup contains duplicate or too many files");
          long limit = name.equals("journal.properties") ? MAX_MANIFEST : MAX_FILE;
          if (item.getSize() < 0 || item.getSize() > limit)
            throw new IOException("Backup file exceeds its size limit");
          File target = new File(stage, name);
          long written;
          try (InputStream contents = zip.getInputStream(item)) {
            written = copy(contents, target, limit);
          }
          if (written != item.getSize()) throw new IOException("Backup file is truncated");
          // ZipFile validates the directory; verify each expanded payload's CRC too.
          CRC32 crc = new CRC32();
          try (InputStream contents = new FileInputStream(target)) {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = contents.read(buffer)) != -1) crc.update(buffer, 0, n);
          }
          if (crc.getValue() != item.getCrc())
            throw new IOException("Backup file failed its archive integrity check");
          total += written;
          if (total > MAX_ARCHIVE) throw new IOException("Unpacked backup exceeds 128 MB");
        }
      }
      if (!seen.contains("journal.properties"))
        throw new IOException("This is not a Field Journal backup");
      State state = readState(stage);
      Set<String> expected = new HashSet<>();
      expected.add("journal.properties");
      for (String name : references(state.entries)) expected.add("media/" + name);
      if (!seen.equals(expected))
        throw new IOException("Backup contains unreferenced or missing media");
      return new Preview(state, true);
    } catch (IOException | RuntimeException | Error e) {
      removeTree(stage);
      throw e;
    } finally {
      archive.delete();
    }
  }

  public synchronized State activate(Preview preview) throws IOException {
    if (preview == null || preview.consumed)
      throw new IOException("This restore preview has expired");
    // Revalidate staging immediately before replacing anything, including every media hash/decode.
    State verified = readState(preview.state.directory);
    if (preview.imported) {
      Properties changed = new Properties();
      try (InputStream input =
          new FileInputStream(new File(verified.directory, "journal.properties"))) {
        changed.load(input);
      }
      changed.setProperty("journalId", UUID.randomUUID().toString());
      writeProperties(new File(verified.directory, "journal.properties"), changed);
      verified = readState(verified.directory);
    }
    String name = "snapshot-" + UUID.randomUUID();
    File destination = new File(root, name);
    move(verified.directory, destination);
    File temp = new File(root, "active-" + UUID.randomUUID() + ".tmp");
    try {
      try (FileOutputStream out = new FileOutputStream(temp)) {
        out.write(name.getBytes(StandardCharsets.UTF_8));
        out.getFD().sync();
      }
      // Keep the prior committed generation, so interrupted cleanup never destroys the current one.
      String previous = null;
      File active = new File(root, "active");
      if (active.isFile())
        previous = new String(readLimited(active, 128), StandardCharsets.UTF_8).trim();
      move(temp, active);
      preview.consumed = true;
      currentJournalId = verified.journalId;
      cleanup(name, previous);
      return new State(
          new ArrayList<>(verified.entries),
          verified.theme,
          verified.quality,
          destination,
          verified.journalId);
    } catch (IOException e) {
      removeTree(destination);
      temp.delete();
      throw e;
    }
  }

  public synchronized void cancel(Preview preview) {
    if (preview != null && !preview.consumed) {
      removeTree(preview.state.directory);
      preview.consumed = true;
    }
  }

  public synchronized State recoverPrevious() throws IOException {
    // Only explicitly requested by UI after a failed normal load. Never silently pick a snapshot.
    File[] dirs = root.listFiles((d, n) -> n.startsWith("snapshot-"));
    if (dirs == null) throw new IOException("No recoverable journal found");
    Arrays.sort(dirs, Comparator.comparingLong(File::lastModified).reversed());
    for (File dir : dirs) {
      try {
        State state = readState(dir);
        currentJournalId = state.journalId;
        Map<String, File> files = mediaMap(state);
        return save(state.entries, state.theme, state.quality, files);
      } catch (IOException ignored) {
      }
    }
    throw new IOException(
        "No complete previous journal could be recovered; import a backup instead");
  }

  public static Map<String, File> mediaMap(State state) throws IOException {
    Map<String, File> files = new HashMap<>();
    if (state.directory != null)
      for (String name : references(state.entries)) files.put(name, state.media(name));
    return files;
  }

  public static boolean safeArchivePath(String name) {
    return "journal.properties".equals(name)
        || name != null && name.matches("media/[a-f0-9-]{36}\\.jpg");
  }

  private State readState(File directory) throws IOException {
    File file = new File(directory, "journal.properties");
    Properties p = new UniqueProperties();
    try (InputStream input = new ByteArrayInputStream(readLimited(file, MAX_MANIFEST))) {
      p.load(input);
    } catch (IllegalArgumentException invalid) {
      throw new IOException("Duplicate or malformed backup metadata", invalid);
    }
    if (!"field-journal".equals(p.getProperty("format")))
      throw new IOException("This is not a Field Journal backup");
    if (!String.valueOf(VERSION).equals(p.getProperty("version")))
      throw new IOException("Unsupported backup version; journal unchanged");
    int count = integer(p, "count");
    if (count < 0 || count > MAX_ENTRIES) throw new IOException("Invalid observation count");
    String theme = required(p, "theme");
    int quality = integer(p, "quality");
    validateSettings(theme, quality);
    String journalId = required(p, "journalId");
    if (!journalId.matches("[a-f0-9-]{36}")) throw new IOException("Invalid journal identity");
    List<Entry> entries = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    Set<String> allowed =
        new HashSet<>(Arrays.asList("format", "version", "count", "theme", "quality", "journalId"));
    for (int i = 0; i < count; i++) {
      String prefix = "entry." + i + ".";
      int tags = integer(p, prefix + "tags");
      if (tags < 0 || tags > 20) throw new IOException("Invalid tag count");
      List<String> tagList = new ArrayList<>();
      for (int t = 0; t < tags; t++) {
        tagList.add(required(p, prefix + "tag." + t));
        allowed.add(prefix + "tag." + t);
      }
      long updated;
      try {
        updated = Long.parseLong(required(p, prefix + "updated"));
      } catch (NumberFormatException e) {
        throw new IOException("Invalid modification time");
      }
      Entry entry =
          new Entry(
              required(p, prefix + "id"),
              required(p, prefix + "title"),
              required(p, prefix + "body"),
              required(p, prefix + "date"),
              tagList,
              required(p, prefix + "source"),
              required(p, prefix + "rendered"),
              updated);
      if (!ids.add(entry.id)) throw new IOException("Duplicate observation ID");
      entries.add(entry);
      for (String key :
          Arrays.asList("id", "title", "body", "date", "source", "rendered", "updated", "tags"))
        allowed.add(prefix + key);
    }
    long total = 0;
    Set<String> refs = references(entries);
    for (String name : refs) {
      String hash = required(p, "sha256." + name);
      allowed.add("sha256." + name);
      if (!hash.matches("[a-f0-9]{64}")) throw new IOException("Invalid photo checksum");
      File media = new File(new File(directory, "media"), name);
      if (!media.isFile() || media.length() == 0 || media.length() > MAX_FILE)
        throw new IOException("Backup photo is missing or too large");
      total += media.length();
      if (total > MAX_ARCHIVE - MAX_MANIFEST) throw new IOException("Backup media exceeds 120 MB");
      if (!hash.equals(sha256(media)))
        throw new IOException("Backup photo failed its integrity check");
      validator.validate(media);
    }
    if (!p.stringPropertyNames().equals(allowed))
      throw new IOException("Unexpected or incomplete backup metadata");
    File mediaDir = new File(directory, "media");
    String[] children = mediaDir.list();
    if (children != null && !new HashSet<>(Arrays.asList(children)).equals(refs))
      throw new IOException("Backup includes unreferenced photos");
    return new State(entries, theme, quality, directory, journalId);
  }

  private static final class UniqueProperties extends Properties {
    @Override
    public synchronized Object put(Object key, Object value) {
      if (containsKey(key)) throw new IllegalArgumentException("Duplicate property");
      return super.put(key, value);
    }
  }

  private static Properties encode(List<Entry> entries, String theme, int quality, String journalId)
      throws IOException {
    Properties p = new Properties();
    p.setProperty("journalId", journalId);
    p.setProperty("format", "field-journal");
    p.setProperty("version", String.valueOf(VERSION));
    p.setProperty("count", String.valueOf(entries.size()));
    p.setProperty("theme", theme);
    p.setProperty("quality", String.valueOf(quality));
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < entries.size(); i++) {
      Entry e = entries.get(i);
      if (!ids.add(e.id)) throw new IOException("Duplicate observation ID");
      String prefix = "entry." + i + ".";
      p.setProperty(prefix + "id", e.id);
      p.setProperty(prefix + "title", e.title);
      p.setProperty(prefix + "body", e.body);
      p.setProperty(prefix + "date", e.date);
      p.setProperty(prefix + "source", e.source);
      p.setProperty(prefix + "rendered", e.rendered);
      p.setProperty(prefix + "updated", String.valueOf(e.updated));
      p.setProperty(prefix + "tags", String.valueOf(e.tags.size()));
      for (int t = 0; t < e.tags.size(); t++) p.setProperty(prefix + "tag." + t, e.tags.get(t));
    }
    return p;
  }

  private static Set<String> references(List<Entry> entries) throws IOException {
    Set<String> result = new TreeSet<>();
    for (Entry e : entries) {
      Entry.checkMediaName(e.source);
      Entry.checkMediaName(e.rendered);
      if (!e.source.isEmpty()) {
        result.add(e.source);
        result.add(e.rendered);
      }
    }
    return result;
  }

  private static void validateSettings(String theme, int quality) throws IOException {
    if (!Arrays.asList("system", "light", "dark").contains(theme)
        || quality != 80 && quality != 90 && quality != 100)
      throw new IOException("Invalid saved settings");
  }

  private static String required(Properties p, String key) throws IOException {
    String v = p.getProperty(key);
    if (v == null) throw new IOException("Missing backup field: " + key);
    return v;
  }

  private static int integer(Properties p, String key) throws IOException {
    try {
      return Integer.parseInt(required(p, key));
    } catch (NumberFormatException e) {
      throw new IOException("Invalid backup number: " + key);
    }
  }

  private static void writeProperties(File file, Properties properties) throws IOException {
    try (FileOutputStream output = new FileOutputStream(file)) {
      properties.store(output, "Field Journal v1 - PRIVATE UNENCRYPTED DATA");
      output.getFD().sync();
    }
    if (file.length() > MAX_MANIFEST) throw new IOException("Journal metadata is too large");
  }

  public static String sha256(File file) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream input = new FileInputStream(file)) {
        byte[] buffer = new byte[16384];
        int n;
        while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n);
      }
      StringBuilder s = new StringBuilder();
      for (byte value : digest.digest()) s.append(String.format(Locale.ROOT, "%02x", value & 255));
      return s.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("SHA-256 unavailable", e);
    }
  }

  private static byte[] readLimited(File file, long max) throws IOException {
    if (!file.isFile() || file.length() > max)
      throw new IOException("Journal metadata is missing or too large");
    try (InputStream input = new FileInputStream(file);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      long total = 0;
      int n;
      while ((n = input.read(buffer)) != -1) {
        total += n;
        if (total > max) throw new IOException("File exceeds its limit");
        out.write(buffer, 0, n);
      }
      return out.toByteArray();
    }
  }

  private static void copy(File source, File target, long limit) throws IOException {
    try (InputStream input = new FileInputStream(source)) {
      copy(input, target, limit);
    }
  }

  private static long copy(InputStream input, File target, long limit) throws IOException {
    long total = 0;
    try (FileOutputStream output = new FileOutputStream(target)) {
      byte[] buffer = new byte[16384];
      int n;
      while ((n = input.read(buffer)) != -1) {
        total += n;
        if (total > limit) throw new IOException("Backup file exceeds its size limit");
        output.write(buffer, 0, n);
      }
      output.getFD().sync();
    }
    return total;
  }

  private static void put(ZipOutputStream zip, String name, File file) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    try (InputStream input = new FileInputStream(file)) {
      byte[] buffer = new byte[16384];
      int n;
      while ((n = input.read(buffer)) != -1) zip.write(buffer, 0, n);
    }
    zip.closeEntry();
  }

  private static void mkdir(File directory) throws IOException {
    if (!directory.isDirectory() && !directory.mkdirs())
      throw new IOException("Cannot create private journal storage");
  }

  private static void move(File from, File to) throws IOException {
    Files.move(
        from.toPath(),
        to.toPath(),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING);
  }

  private void cleanup(String active, String previous) {
    File[] files = root.listFiles();
    if (files == null) return;
    for (File file : files) {
      String name = file.getName();
      if (name.startsWith("snapshot-") && !name.equals(active) && !name.equals(previous))
        removeTree(file);
    }
  }

  public static void removeTree(File file) {
    if (file == null) return;
    if (file.isDirectory()) {
      File[] children = file.listFiles();
      if (children != null) for (File child : children) removeTree(child);
    }
    file.delete();
  }
}
