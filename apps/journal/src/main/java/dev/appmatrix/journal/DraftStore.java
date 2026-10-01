// SPDX-License-Identifier: GPL-3.0-or-later
package dev.appmatrix.journal;

import dev.appmatrix.journal.domain.Entry;
import dev.appmatrix.journal.domain.JournalStore;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** One recoverable working draft; committed observations are never edited in-place. */
final class DraftStore {
  static final class Draft {
    String id = UUID.randomUUID().toString(),
        title = "",
        body = "",
        date = LocalDate.now().toString(),
        tags = "",
        source = "",
        rendered = "";
    String journalId = "00000000-0000-0000-0000-000000000000";
    boolean existing, dirty;

    Entry entry() throws IOException {
      return new Entry(
          id,
          title,
          body,
          date,
          Entry.parseTags(tags),
          source,
          rendered,
          System.currentTimeMillis());
    }
  }

  final File directory;

  DraftStore(File directory) {
    this.directory = directory;
  }

  File media(String name) throws IOException {
    Entry.checkMediaName(name);
    if (name.isEmpty()) throw new IOException("No photo");
    return new File(directory, name);
  }

  Draft begin(Entry entry, JournalStore.State state) throws IOException {
    JournalStore.removeTree(directory);
    mkdir();
    Draft draft = new Draft();
    draft.journalId = state.journalId;
    if (entry != null) {
      draft.id = entry.id;
      draft.title = entry.title;
      draft.body = entry.body;
      draft.date = entry.date;
      draft.tags = String.join(", ", entry.tags);
      draft.source = entry.source;
      draft.rendered = entry.rendered;
      draft.existing = true;
      if (!entry.source.isEmpty()) {
        Files.copy(
            state.media(entry.source).toPath(),
            media(entry.source).toPath(),
            StandardCopyOption.REPLACE_EXISTING);
        if (!entry.source.equals(entry.rendered))
          Files.copy(
              state.media(entry.rendered).toPath(),
              media(entry.rendered).toPath(),
              StandardCopyOption.REPLACE_EXISTING);
      }
    }
    save(draft);
    return draft;
  }

  Draft load() throws IOException {
    File file = new File(directory, "draft.properties");
    if (!file.exists()) return null;
    if (file.length() > 1024 * 1024) throw new IOException("Saved draft is too large");
    Properties p = new Properties();
    try (InputStream input = new FileInputStream(file)) {
      p.load(input);
    } catch (IllegalArgumentException error) {
      throw new IOException("The saved draft is malformed", error);
    }
    Draft d = new Draft();
    if (!"1".equals(p.getProperty("version")))
      throw new IOException("Unsupported saved draft version");
    d.journalId = p.getProperty("journalId", "");
    if (!d.journalId.matches("[a-f0-9-]{36}"))
      throw new IOException("Invalid draft journal identity");
    d.id = p.getProperty("id", "");
    if (!d.id.matches("[a-f0-9-]{36}")) throw new IOException("Invalid draft ID");
    d.title = p.getProperty("title", "");
    d.body = p.getProperty("body", "");
    d.date = p.getProperty("date", LocalDate.now().toString());
    d.tags = p.getProperty("tags", "");
    d.source = p.getProperty("source", "");
    d.rendered = p.getProperty("rendered", "");
    d.existing = Boolean.parseBoolean(p.getProperty("existing"));
    d.dirty = Boolean.parseBoolean(p.getProperty("dirty"));
    Entry.checkMediaName(d.source);
    Entry.checkMediaName(d.rendered);
    if (!d.source.isEmpty() && (!media(d.source).isFile() || !media(d.rendered).isFile()))
      throw new IOException("The saved draft photo is missing");
    return d;
  }

  void save(Draft d) throws IOException {
    mkdir();
    Properties p = new Properties();
    p.setProperty("version", "1");
    p.setProperty("journalId", d.journalId);
    p.setProperty("id", d.id);
    p.setProperty("title", d.title);
    p.setProperty("body", d.body);
    p.setProperty("date", d.date);
    p.setProperty("tags", d.tags);
    p.setProperty("source", d.source);
    p.setProperty("rendered", d.rendered);
    p.setProperty("existing", String.valueOf(d.existing));
    p.setProperty("dirty", String.valueOf(d.dirty));
    File temp = new File(directory, "draft.tmp");
    try (FileOutputStream output = new FileOutputStream(temp)) {
      p.store(output, "Private working draft");
      output.getFD().sync();
    }
    Files.move(
        temp.toPath(),
        new File(directory, "draft.properties").toPath(),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING);
  }

  void discard() {
    JournalStore.removeTree(directory);
  }

  private void mkdir() throws IOException {
    if (!directory.isDirectory() && !directory.mkdirs())
      throw new IOException("Cannot save a local draft");
  }
}
