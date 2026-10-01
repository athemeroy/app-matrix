// SPDX-License-Identifier: GPL-3.0-or-later
package dev.appmatrix.journal.domain;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Plain-text, immutable journal record. No URI or external path is persisted. */
public final class Entry {
  public final String id, title, body, date, source, rendered;
  public final List<String> tags;
  public final long updated;

  public Entry(
      String id,
      String title,
      String body,
      String date,
      List<String> tags,
      String source,
      String rendered,
      long updated)
      throws IOException {
    if (id == null || !id.matches("[a-f0-9-]{36}")) throw new IOException("Invalid observation ID");
    if (title == null || title.trim().isEmpty() || title.length() > 160)
      throw new IOException("Add a title of 1–160 characters");
    if (body == null || body.length() > 50000)
      throw new IOException("Keep notes below 50,000 characters");
    try {
      LocalDate.parse(date);
    } catch (DateTimeParseException | NullPointerException e) {
      throw new IOException("Choose a valid observation date");
    }
    if (date.length() != 10 || tags == null || tags.size() > 20)
      throw new IOException("Use at most 20 tags");
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String tag : tags) {
      if (tag == null
          || tag.trim().isEmpty()
          || tag.length() > 32
          || tag.indexOf(',') >= 0
          || tag.indexOf('\n') >= 0
          || tag.indexOf('\r') >= 0)
        throw new IOException("Each tag must be 1–32 characters without commas or line breaks");
      normalized.add(tag.trim().toLowerCase(Locale.ROOT));
    }
    checkMediaName(source);
    checkMediaName(rendered);
    if (source.isEmpty() != rendered.isEmpty()) throw new IOException("Incomplete photo reference");
    if (updated < 0) throw new IOException("Invalid modification time");
    this.id = id;
    this.title = title.trim();
    this.body = body;
    this.date = date;
    this.tags = Collections.unmodifiableList(new ArrayList<>(normalized));
    this.source = source;
    this.rendered = rendered;
    this.updated = updated;
  }

  public static void checkMediaName(String name) throws IOException {
    if (name == null || (!name.isEmpty() && !name.matches("[a-f0-9-]{36}\\.jpg")))
      throw new IOException("Unsafe photo filename");
  }

  public static List<String> parseTags(String text) throws IOException {
    if (text.trim().isEmpty()) return Collections.emptyList();
    List<String> tags = new ArrayList<>();
    for (String part : text.split(",", -1)) {
      if (!part.trim().isEmpty()) tags.add(part.trim());
    }
    if (tags.size() > 20) throw new IOException("Use at most 20 tags");
    return tags;
  }

  public boolean matches(String query, String tag) {
    String haystack =
        (title + "\n" + body + "\n" + date + "\n" + String.join(" ", tags))
            .toLowerCase(Locale.ROOT);
    return (query == null || haystack.contains(query.trim().toLowerCase(Locale.ROOT)))
        && (tag == null || tag.isEmpty() || tags.contains(tag));
  }

  public static List<Entry> filter(
      List<Entry> source, String query, String tag, boolean newestFirst) {
    List<Entry> result = new ArrayList<>();
    for (Entry e : source) if (e.matches(query, tag)) result.add(e);
    Comparator<Entry> comparator =
        Comparator.comparing((Entry e) -> e.date)
            .thenComparingLong(e -> e.updated)
            .thenComparing(e -> e.id);
    result.sort(newestFirst ? comparator.reversed() : comparator);
    return result;
  }
}
