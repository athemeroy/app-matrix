// SPDX-License-Identifier: GPL-3.0-or-later
package dev.appmatrix.journal.domain;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Dependency-free JVM regression suite; run apps/journal/test-domain.sh. */
public final class JournalStoreTest {
  private static int checks;
  private static File temp;
  private static final String A = "11111111-1111-1111-1111-111111111111",
      B = "22222222-2222-2222-2222-222222222222";
  private static final String SOURCE = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg",
      RENDER = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.jpg";

  public static void main(String[] args) throws Exception {
    temp = Files.createTempDirectory("field-journal-tests-").toFile();
    try {
      validation();
      roundTripAndMutation();
      malformedBackups();
      failureAtomicity();
      limits();
      jpegPrivacy();
      restartCleanup();
      System.out.println("JournalStoreTest: " + checks + " checks passed");
    } finally {
      JournalStore.removeTree(temp);
    }
  }

  private static JournalStore store(String name) throws IOException {
    return new JournalStore(new File(temp, name), JpegInspector::validate);
  }

  private static Entry note(String id, String date, String title) throws IOException {
    return new Entry(
        id,
        title,
        "Plain text <script>alert('x')</script> 😀\nSecond line",
        date,
        Arrays.asList("Birds", " walk "),
        "",
        "",
        1);
  }

  private static Entry photo() throws IOException {
    return new Entry(
        A,
        "Kingfisher",
        "A blue flash\nBy the river",
        "2026-10-01",
        Arrays.asList("birds", "river"),
        SOURCE,
        RENDER,
        100);
  }

  private static Map<String, File> fixture() throws IOException {
    Map<String, File> media = new HashMap<>();
    String[] data = {
      "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAAGAAgDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDEoooqD48//9k=",
      "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAAGAAgDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDGooor6I+dP//Z"
    };
    int i = 0;
    for (String name : Arrays.asList(SOURCE, RENDER)) {
      File f = new File(temp, name);
      Files.write(f.toPath(), Base64.getDecoder().decode(data[i++]));
      media.put(name, f);
    }
    return media;
  }

  private static void validation() throws Exception {
    Entry e = note(A, "2026-10-01", "A thought");
    eq(e.tags, Arrays.asList("birds", "walk"), "tags normalize");
    yes(e.matches("<SCRIPT>", "birds"), "plain text malicious strings searchable");
    yes(e.matches("😀", null), "unicode preserved");
    no(e.matches("river", null), "search misses");
    List<Entry> entries = Arrays.asList(e, note(B, "2025-12-31", "Before"));
    eq(Entry.filter(entries, "", null, true).get(0).id, A, "newest order");
    eq(Entry.filter(entries, "", null, false).get(0).id, B, "oldest order");
    eq(Entry.filter(entries, "2025-12", "", true).size(), 1, "date search");
    fails(() -> note(A, "2026-02-30", "Bad"), "invalid date");
    fails(() -> note(A, "2026-10-01", "  "), "empty title");
    fails(() -> note("../unsafe", "2026-10-01", "Bad"), "invalid ID");
    fails(
        () -> new Entry(A, "Title", "", "2026-10-01", Arrays.asList("a,b"), "", "", 1),
        "tag delimiter");
    fails(
        () -> new Entry(A, "Title", "", "2026-10-01", Collections.emptyList(), SOURCE, "", 1),
        "half photo reference");
    for (String path :
        Arrays.asList(
            "../escape",
            "/absolute",
            "media/../../escape",
            "media\\bad.jpg",
            "media/",
            "journal.properties/extra",
            "media/x.jpg",
            "media/a/../" + SOURCE))
      no(JournalStore.safeArchivePath(path), "reject unsafe path " + path);
    yes(JournalStore.safeArchivePath("media/" + SOURCE), "safe media path");
    yes(JournalStore.safeArchivePath("journal.properties"), "safe manifest");
  }

  private static void roundTripAndMutation() throws Exception {
    JournalStore journal = store("roundtrip");
    eq(journal.load().entries.size(), 0, "first launch empty");
    Map<String, File> media = fixture();
    JournalStore.State saved =
        journal.save(Arrays.asList(photo(), note(B, "2026-09-30", "Thought")), "dark", 80, media);
    eq(journal.load().entries.size(), 2, "restart count");
    eq(journal.load().theme, "dark", "restart theme");
    eq(journal.load().quality, 80, "restart quality");
    byte[] exported = backup(journal, saved);
    Map<String, byte[]> parsed = unzip(exported);
    eq(parsed.size(), 3, "backup includes manifest and both source/render media");
    Properties manifest = properties(parsed.get("journal.properties"));
    eq(manifest.getProperty("format"), "field-journal", "independent manifest parse");
    eq(manifest.getProperty("version"), "1", "versioned backup");
    yes(
        Arrays.equals(
            parsed.get("media/" + SOURCE), Files.readAllBytes(media.get(SOURCE).toPath())),
        "source photo preserved byte-for-byte");
    JournalStore fresh = store("restored");
    JournalStore.Preview preview = fresh.previewRestore(new ByteArrayInputStream(exported));
    eq(preview.state.photoCount(), 1, "preview photo observations");
    eq(fresh.load().entries.size(), 0, "preview cannot change active");
    JournalStore.State restored = fresh.activate(preview);
    eq(restored.entries.get(1).body, saved.entries.get(1).body, "unicode/plaintext note restored");
    eq(restored.entries.get(0).tags, saved.entries.get(0).tags, "tags restored");
    eq(restored.entries.get(0).date, saved.entries.get(0).date, "date restored");
    eq(restored.theme, "dark", "restore settings");
    no(restored.journalId.equals(saved.journalId), "restore creates new journal identity");
    eq(
        JournalStore.sha256(restored.media(RENDER)),
        JournalStore.sha256(saved.media(RENDER)),
        "render integrity round trip");
    fails(() -> fresh.activate(preview), "cannot reuse confirmed preview");
    JournalStore.Preview cancel = fresh.previewRestore(new ByteArrayInputStream(exported));
    File stage = cancel.state.directory;
    fresh.cancel(cancel);
    no(stage.exists(), "cancel removes staging");
    eq(fresh.load().entries.size(), 2, "cancel preserves active");
    fails(() -> fresh.activate(cancel), "cancelled preview cannot activate");
    JournalStore.State deleted =
        fresh.save(
            Collections.singletonList(note(B, "2026-09-30", "Thought")),
            "system",
            90,
            JournalStore.mediaMap(restored));
    eq(deleted.entries.size(), 1, "delete selected observation");
    eq(deleted.photoCount(), 0, "delete removes active photo references");
    eq(deleted.journalId, restored.journalId, "ordinary save preserves journal identity");
    yes(restored.media(SOURCE).exists(), "previous complete snapshot retained");
    fresh.save(deleted.entries, "light", 100, JournalStore.mediaMap(deleted));
    File[] generations = new File(temp, "restored").listFiles((d, n) -> n.startsWith("snapshot-"));
    eq(generations.length, 2, "keep current and one recovery generation");
    JournalStore empty = store("empty");
    JournalStore.Preview emptyPreview =
        empty.previewRestore(new ByteArrayInputStream(backup(empty, empty.load())));
    eq(emptyPreview.state.entries.size(), 0, "empty backup supported");
    empty.cancel(emptyPreview);
  }

  private static void malformedBackups() throws Exception {
    JournalStore journal = store("malformed");
    JournalStore.State saved =
        journal.save(Collections.singletonList(photo()), "system", 90, fixture());
    byte[] valid = backup(journal, saved);
    String active = pointer("malformed");
    reject(journal, Arrays.copyOf(valid, valid.length - 22), active, "missing ZIP end directory");
    reject(journal, Arrays.copyOf(valid, valid.length / 2), active, "truncated ZIP");
    reject(journal, "not a zip".getBytes(StandardCharsets.UTF_8), active, "not zip");
    for (String path :
        Arrays.asList("../escape", "/absolute", "media\\evil.jpg", "media/", "extra.txt")) {
      Map<String, byte[]> bad = unzip(valid);
      bad.put(path, new byte[] {1});
      reject(journal, zip(bad), active, "bad archive path " + path);
    }
    rejectChangedProperty(journal, valid, active, "version", "999", "future version");
    rejectChangedProperty(journal, valid, active, "count", "1001", "too many entries");
    rejectChangedProperty(journal, valid, active, "quality", "42", "bad settings");
    rejectChangedProperty(journal, valid, active, "entry.0.date", "2026-02-30", "invalid date");
    rejectChangedProperty(journal, valid, active, "entry.0.tags", "21", "too many tags");
    rejectChangedProperty(
        journal, valid, active, "entry.0.source", "../escape", "unsafe media ref");
    rejectChangedProperty(journal, valid, active, "unexpected", "value", "unexpected metadata");
    Map<String, byte[]> duplicateProperty = unzip(valid);
    byte[] originalManifest = duplicateProperty.get("journal.properties");
    ByteArrayOutputStream duplicateText = new ByteArrayOutputStream();
    duplicateText.write(originalManifest);
    duplicateText.write("\nentry.0.title=ambiguous\n".getBytes(StandardCharsets.ISO_8859_1));
    duplicateProperty.put("journal.properties", duplicateText.toByteArray());
    reject(journal, zip(duplicateProperty), active, "duplicate manifest fields");
    Map<String, byte[]> missing = unzip(valid);
    missing.remove("media/" + SOURCE);
    reject(journal, zip(missing), active, "missing source media");
    Map<String, byte[]> corrupt = unzip(valid);
    corrupt.put("media/" + SOURCE, new byte[] {0, 1, 2});
    reject(journal, zip(corrupt), active, "hash mismatch");
    Properties p = properties(corrupt.get("journal.properties"));
    File badFile = new File(temp, "invalid.jpg");
    Files.write(badFile.toPath(), new byte[] {0, 1, 2});
    p.setProperty("sha256." + SOURCE, JournalStore.sha256(badFile));
    corrupt.put("journal.properties", propertiesBytes(p));
    reject(journal, zip(corrupt), active, "corrupt image with matching hash");
    Map<String, byte[]> extra = unzip(valid);
    extra.put("media/cccccccc-cccc-cccc-cccc-cccccccccccc.jpg", extra.get("media/" + SOURCE));
    reject(journal, zip(extra), active, "unreferenced media");
    // Create duplicate names by changing equal-length local and central filenames after ZIP
    // construction.
    Map<String, byte[]> duplicated = unzip(valid);
    String other = "cccccccc-cccc-cccc-cccc-cccccccccccc.jpg";
    duplicated.put("media/" + other, duplicated.get("media/" + SOURCE));
    byte[] dup = zip(duplicated);
    replaceAscii(dup, other, SOURCE);
    reject(journal, dup, active, "duplicate ZIP paths");
    // Bytes can be tampered after preview, so confirmation performs validation again.
    JournalStore.Preview preview = journal.previewRestore(new ByteArrayInputStream(valid));
    Files.write(preview.state.media(SOURCE).toPath(), new byte[] {1, 2, 3});
    fails(() -> journal.activate(preview), "revalidate staged photo on confirmation");
    eq(pointer("malformed"), active, "failed activation keeps active pointer");
    journal.cancel(preview);
    File[] incoming =
        new File(temp, "malformed")
            .listFiles((d, n) -> n.startsWith("incoming-") || n.startsWith("stage-"));
    eq(incoming.length, 0, "failed restores clean staging");
  }

  private static void failureAtomicity() throws Exception {
    JournalStore journal = store("atomic");
    JournalStore.State initial =
        journal.save(Collections.singletonList(photo()), "system", 90, fixture());
    String before = pointer("atomic");
    fails(
        () -> journal.save(initial.entries, "dark", 90, Collections.emptyMap()),
        "missing photo save fails");
    eq(pointer("atomic"), before, "missing photo keeps pointer");
    eq(journal.load().entries.get(0).title, "Kingfisher", "missing photo keeps data");
    fails(
        () -> journal.save(initial.entries, "unknown", 90, JournalStore.mediaMap(initial)),
        "invalid settings save fails");
    eq(pointer("atomic"), before, "settings failure atomic");
    fails(
        () ->
            journal.exportBackup(
                initial,
                new OutputStream() {
                  public void write(int b) throws IOException {
                    throw new IOException("Disk full");
                  }

                  public void write(byte[] b, int o, int n) throws IOException {
                    throw new IOException("Disk full");
                  }
                }),
        "export destination write failure");
    eq(pointer("atomic"), before, "export failure cannot mutate source");
    List<Entry> newer = new ArrayList<>(initial.entries);
    newer.add(note(B, "2026-10-02", "Next"));
    JournalStore.State second = journal.save(newer, "light", 100, JournalStore.mediaMap(initial));
    Files.write(
        new File(second.directory, "journal.properties").toPath(),
        "corrupt".getBytes(StandardCharsets.UTF_8));
    fails(journal::load, "detect corrupt active manifest");
    JournalStore.State recovered = journal.recoverPrevious();
    eq(recovered.entries.size(), 1, "explicit previous snapshot recovery");
    eq(journal.load().entries.size(), 1, "recovery persisted");
    new File(new File(temp, "atomic"), "active").delete();
    fails(journal::load, "missing pointer with saved snapshots requires recovery");
    yes(recovered.directory.exists(), "missing index does not erase snapshots");
    eq(journal.recoverPrevious().entries.size(), 1, "recover after index loss");
  }

  private static void limits() throws Exception {
    JournalStore journal = store("limits");
    JournalStore.State initial =
        journal.save(Collections.singletonList(photo()), "system", 90, fixture());
    byte[] valid = backup(journal, initial);
    String active = pointer("limits");
    Map<String, byte[]> oversized = unzip(valid);
    oversized.put("media/" + SOURCE, new byte[(int) JournalStore.MAX_FILE + 1]);
    reject(journal, zip(oversized), active, "compressed media bomb bounded before decode");
    Map<String, byte[]> manifest = unzip(valid);
    manifest.put("journal.properties", new byte[(int) JournalStore.MAX_MANIFEST + 1]);
    reject(journal, zip(manifest), active, "oversized manifest bounded");
    String longBody = String.join("", Collections.nCopies(50001, "x"));
    fails(
        () -> new Entry(A, "title", longBody, "2026-10-01", Collections.emptyList(), "", "", 1),
        "body limit");
    String longTag = String.join("", Collections.nCopies(33, "x"));
    fails(
        () -> new Entry(A, "title", "", "2026-10-01", Arrays.asList(longTag), "", "", 1),
        "tag length limit");
  }

  private static void jpegPrivacy() throws Exception {
    File good = fixture().get(SOURCE);
    JpegInspector.validate(good);
    checks++;
    byte[] original = Files.readAllBytes(good.toPath());
    File appended = new File(temp, "trailing.jpg");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(original);
    out.write(
        new byte[] {
          (byte) 0xff, (byte) 0xe1, 0, 8, 'E', 'x', 'i', 'f', 0, 0, (byte) 0xff, (byte) 0xd9
        });
    Files.write(appended.toPath(), out.toByteArray());
    fails(() -> JpegInspector.validate(appended), "reject trailing EXIF after complete JPEG");
    out.reset();
    out.write(original, 0, 2);
    out.write(new byte[] {(byte) 0xff, (byte) 0xe1, 0, 8, 'E', 'x', 'i', 'f', 0, 0});
    out.write(original, 2, original.length - 2);
    Files.write(appended.toPath(), out.toByteArray());
    fails(() -> JpegInspector.validate(appended), "reject in-header EXIF");
    Files.write(appended.toPath(), Arrays.copyOf(original, original.length - 1));
    fails(() -> JpegInspector.validate(appended), "reject incomplete JPEG end");
  }

  private static void restartCleanup() throws Exception {
    File root = new File(temp, "cleanup");
    root.mkdirs();
    File abandoned = new File(root, "stage-" + UUID.randomUUID());
    abandoned.mkdirs();
    Files.write(new File(abandoned, "partial-private-photo").toPath(), new byte[] {1});
    File incoming = new File(root, "incoming-" + UUID.randomUUID() + ".zip");
    Files.write(incoming.toPath(), new byte[] {1});
    File pointerTemp = new File(root, "active-" + UUID.randomUUID() + ".tmp");
    Files.write(pointerTemp.toPath(), new byte[] {1});
    JournalStore restarted = new JournalStore(root, JpegInspector::validate);
    no(abandoned.exists(), "startup cleans abandoned stage");
    no(incoming.exists(), "startup cleans abandoned archive");
    no(pointerTemp.exists(), "startup cleans abandoned pointer temp");
    eq(restarted.load().entries.size(), 0, "cleanup preserves empty state");
    JournalStore crashing =
        new JournalStore(
            new File(temp, "oom"),
            file -> {
              throw new OutOfMemoryError("synthetic");
            });
    try {
      crashing.save(Collections.singletonList(photo()), "system", 90, fixture());
      throw new AssertionError("Expected memory failure");
    } catch (OutOfMemoryError expected) {
      checks++;
    }
    File[] leftover = new File(temp, "oom").listFiles((d, n) -> n.startsWith("stage-"));
    eq(leftover.length, 0, "memory failure cleans stage without restart");
  }

  private static byte[] backup(JournalStore store, JournalStore.State state) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    store.exportBackup(state, out);
    return out.toByteArray();
  }

  private static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
    Map<String, byte[]> map = new LinkedHashMap<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        zip.transferTo(out);
        map.put(entry.getName(), out.toByteArray());
      }
    }
    return map;
  }

  private static byte[] zip(Map<String, byte[]> files) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      for (Map.Entry<String, byte[]> entry : files.entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue());
        zip.closeEntry();
      }
    }
    return out.toByteArray();
  }

  private static Properties properties(byte[] bytes) throws IOException {
    Properties p = new Properties();
    p.load(new ByteArrayInputStream(bytes));
    return p;
  }

  private static byte[] propertiesBytes(Properties p) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    p.store(out, "test");
    return out.toByteArray();
  }

  private static void rejectChangedProperty(
      JournalStore s, byte[] valid, String active, String key, String value, String label)
      throws Exception {
    Map<String, byte[]> files = unzip(valid);
    Properties p = properties(files.get("journal.properties"));
    p.setProperty(key, value);
    files.put("journal.properties", propertiesBytes(p));
    reject(s, zip(files), active, label);
  }

  private static void reject(JournalStore store, byte[] bytes, String active, String label)
      throws Exception {
    fails(() -> store.previewRestore(new ByteArrayInputStream(bytes)), label);
    eq(store.load().directory.getName(), active, label + " preserves pointer");
  }

  private static String pointer(String directory) throws IOException {
    return new String(
        Files.readAllBytes(new File(new File(temp, directory), "active").toPath()),
        StandardCharsets.UTF_8);
  }

  private static void replaceAscii(byte[] bytes, String from, String to) {
    byte[] a = from.getBytes(StandardCharsets.US_ASCII), b = to.getBytes(StandardCharsets.US_ASCII);
    for (int i = 0; i <= bytes.length - a.length; i++) {
      boolean match = true;
      for (int j = 0; j < a.length; j++)
        if (bytes[i + j] != a[j]) {
          match = false;
          break;
        }
      if (match) {
        System.arraycopy(b, 0, bytes, i, b.length);
        i += b.length - 1;
      }
    }
  }

  private interface Checked {
    void run() throws Exception;
  }

  private static void fails(Checked action, String name) throws Exception {
    try {
      action.run();
      throw new AssertionError("Expected rejection: " + name);
    } catch (IOException expected) {
      checks++;
    }
  }

  private static void eq(Object actual, Object expected, String name) {
    if (!Objects.equals(actual, expected))
      throw new AssertionError(name + ": expected " + expected + ", got " + actual);
    checks++;
  }

  private static void yes(boolean value, String name) {
    eq(value, true, name);
  }

  private static void no(boolean value, String name) {
    eq(value, false, name);
  }
}
