// SPDX-License-Identifier: GPL-3.0-or-later
package dev.appmatrix.journal;

import dev.appmatrix.journal.domain.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class DraftStoreTest {
  private static int checks;

  public static void main(String[] args) throws Exception {
    File root = Files.createTempDirectory("journal-draft-test-").toFile();
    try {
      DraftStore drafts = new DraftStore(new File(root, "draft"));
      check(drafts.load() == null, "no initial draft");
      JournalStore journal = new JournalStore(new File(root, "journal"), f -> {});
      DraftStore.Draft draft = drafts.begin(null, journal.load());
      draft.title = "A saved draft";
      draft.body = "<script>plain text</script>\n😀";
      draft.tags = "birds, river";
      draft.dirty = true;
      drafts.save(draft);
      DraftStore.Draft resumed = new DraftStore(new File(root, "draft")).load();
      check(resumed.dirty, "dirty state survives restart");
      check(resumed.title.equals(draft.title), "draft title preserved");
      check(resumed.body.equals(draft.body), "unicode plain text preserved");
      check(
          resumed.entry().tags.equals(Arrays.asList("birds", "river")),
          "draft turns into validated entry");
      String name = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg";
      File source = new File(root, "source.jpg");
      Files.write(source.toPath(), new byte[] {1, 2, 3});
      Entry entry =
          new Entry(
              "11111111-1111-1111-1111-111111111111",
              "Saved",
              "Notes",
              "2026-10-01",
              Collections.emptyList(),
              name,
              name,
              1);
      JournalStore.State saved =
          journal.save(
              Collections.singletonList(entry), "dark", 90, Collections.singletonMap(name, source));
      DraftStore.Draft photo = drafts.begin(entry, saved);
      check(photo.existing && !photo.dirty, "saved entry opens clean");
      check(
          Arrays.equals(Files.readAllBytes(drafts.media(name).toPath()), new byte[] {1, 2, 3}),
          "private source copied to draft");
      Files.write(drafts.media(name).toPath(), new byte[] {4, 5, 6});
      check(
          Arrays.equals(Files.readAllBytes(saved.media(name).toPath()), new byte[] {1, 2, 3}),
          "draft edits do not touch saved source");
      drafts.discard();
      check(drafts.load() == null, "explicit discard clears draft");
      check(journal.load().entries.size() == 1, "discard preserves saved entry");
      drafts.begin(entry, saved);
      drafts.media(name).delete();
      try {
        drafts.load();
        throw new AssertionError("missing media accepted");
      } catch (IOException expected) {
        checks++;
      }
      check(
          new File(drafts.directory, "draft.properties").exists(),
          "broken draft retained for explicit user decision");
      Files.write(
          new File(drafts.directory, "draft.properties").toPath(),
          "body=\\uZZZZ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      try {
        drafts.load();
        throw new AssertionError("malformed properties accepted");
      } catch (IOException expected) {
        checks++;
      }
      System.out.println("DraftStoreTest: " + checks + " checks passed");
    } finally {
      JournalStore.removeTree(root);
    }
  }

  private static void check(boolean value, String label) {
    if (!value) throw new AssertionError(label);
    checks++;
  }
}
