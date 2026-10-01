// SPDX-License-Identifier: GPL-3.0-or-later
package dev.appmatrix.journal;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import dev.appmatrix.core.*;
import dev.appmatrix.journal.domain.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Real framework bitmap/filesystem tests in an isolated private test directory. */
public final class JournalInstrumentation extends Instrumentation {
  private int passed;

  @Override
  public void onCreate(Bundle arguments) {
    super.onCreate(arguments);
    start();
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    File testRoot = null;
    try {
      testRoot =
          new File(
              getTargetContext().getCacheDir(), "journal-instrumentation-" + UUID.randomUUID());
      if (!testRoot.mkdirs()) throw new IOException("Cannot create test directory");
      check(
          MainActivity.isDocumentUri(Uri.parse("content://example.documents/document/1")),
          "SAF content destination accepted");
      check(
          !MainActivity.isDocumentUri(
              Uri.parse("file:///data/user/0/dev.appmatrix.journal/files/journal/active")),
          "private file URI rejected at UI boundary");
      check(
          !MainActivity.isDocumentUri(Uri.parse("content:///missing-authority")),
          "content URI without provider rejected");
      File source = new File(testRoot, "source.jpg"), rendered = new File(testRoot, "rendered.jpg");
      Bitmap bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
      for (int y = 0; y < 240; y++)
        for (int x = 0; x < 320; x++)
          bitmap.setPixel(x, y, Color.rgb(x % 256, y % 256, (x + y) % 256));
      try (FileOutputStream out = new FileOutputStream(source)) {
        BitmapTransforms.exportJpeg(bitmap, out, 90);
      }
      MainActivity.validatePhoto(source);
      check(true, "normalized Android JPEG validated");
      ToneCurves.CurvesToolValue curves = new ToneCurves.CurvesToolValue();
      curves.luminanceCurve.midtonesLevel = 70;
      curves.luminanceCurve.interpolateCurve();
      Bitmap adjusted = BitmapTransforms.applyCurves(bitmap, curves);
      check(!bitmap.sameAs(adjusted), "shared extracted curves change real pixels");
      Bitmap rotated = BitmapTransforms.rotate(adjusted, 1);
      check(rotated.getWidth() == 240 && rotated.getHeight() == 320, "rotation dimensions");
      try (FileOutputStream out = new FileOutputStream(rendered)) {
        BitmapTransforms.exportJpeg(rotated, out, 90);
      }
      MainActivity.validatePhoto(rendered);
      check(true, "edited photo validated");
      bitmap.recycle();
      adjusted.recycle();
      rotated.recycle();
      String sourceName = UUID.randomUUID() + ".jpg", renderName = UUID.randomUUID() + ".jpg";
      Entry photo =
          new Entry(
              UUID.randomUUID().toString(),
              "River kingfisher",
              "Blue wings\nPlain text <script> stays text 😀",
              "2026-10-01",
              Arrays.asList("birds", "river"),
              sourceName,
              renderName,
              System.currentTimeMillis());
      Entry note =
          new Entry(
              UUID.randomUUID().toString(),
              "A text-only thought",
              "Offline works",
              "2026-09-30",
              Arrays.asList("ideas"),
              "",
              "",
              1);
      Map<String, File> media = new HashMap<>();
      media.put(sourceName, source);
      media.put(renderName, rendered);
      File storeRoot = new File(testRoot, "journal");
      JournalStore store = new JournalStore(storeRoot, MainActivity::validatePhoto);
      JournalStore.State saved = store.save(Arrays.asList(photo, note), "dark", 80, media);
      check(
          saved.entries.size() == 2 && saved.photoCount() == 1,
          "text and photo observations saved");
      JournalStore.State restarted =
          new JournalStore(storeRoot, MainActivity::validatePhoto).load();
      check(
          restarted.entries.get(0).body.equals(photo.body),
          "notes unicode and literal markup survive reopen");
      check(restarted.entries.get(0).tags.equals(photo.tags), "tags survive reopen");
      check(restarted.theme.equals("dark") && restarted.quality == 80, "settings survive reopen");
      check(
          Entry.filter(restarted.entries, "kingfisher", "birds", true).size() == 1,
          "search and tag filter");
      check(
          Entry.filter(restarted.entries, "", "", false).get(0).id.equals(note.id),
          "chronological ordering");
      ByteArrayOutputStream backup = new ByteArrayOutputStream();
      store.exportBackup(saved, backup);
      check(backup.size() > 0, "backup bytes produced");
      JournalStore destination =
          new JournalStore(new File(testRoot, "restored"), MainActivity::validatePhoto);
      JournalStore.Preview preview =
          destination.previewRestore(new ByteArrayInputStream(backup.toByteArray()));
      check(
          destination.load().entries.isEmpty() && preview.state.entries.size() == 2,
          "preview validates without mutation");
      JournalStore.State restored = destination.activate(preview);
      check(restored.entries.size() == 2, "backup restored into independent namespace");
      check(!restored.journalId.equals(saved.journalId), "restore invalidates old draft identity");
      check(
          JournalStore.sha256(restored.media(sourceName)).equals(JournalStore.sha256(source)),
          "source preserved byte-for-byte");
      check(
          JournalStore.sha256(restored.media(renderName)).equals(JournalStore.sha256(rendered)),
          "edited revision preserved");
      Bitmap decoded = MediaFiles.loadBitmap(restored.media(renderName).getPath());
      check(
          decoded.getWidth() == 240 && decoded.getHeight() == 320,
          "restored image independently decodes");
      File export = new File(testRoot, "photo-export.jpg");
      MediaFiles.copyTo(getTargetContext(), Uri.fromFile(export), decoded, 80);
      decoded.recycle();
      MainActivity.validatePhoto(export);
      check(true, "actual image export decodes and is normalized");
      JournalStore.Preview cancelled =
          destination.previewRestore(new ByteArrayInputStream(backup.toByteArray()));
      File staging = cancelled.state.directory;
      destination.cancel(cancelled);
      check(
          !staging.exists() && destination.load().entries.size() == 2,
          "cancel removes preview and preserves journal");
      boolean truncated = false;
      byte[] raw = backup.toByteArray();
      try {
        destination.previewRestore(new ByteArrayInputStream(Arrays.copyOf(raw, raw.length - 22)));
      } catch (IOException expected) {
        truncated = true;
      }
      check(
          truncated && destination.load().entries.size() == 2,
          "truncated backup rejected atomically");
      File malformed = new File(testRoot, "not-an-image.jpg");
      Files.write(malformed.toPath(), "not an image".getBytes(StandardCharsets.UTF_8));
      boolean rejected = false;
      try {
        MainActivity.validatePhoto(malformed);
      } catch (IOException expected) {
        rejected = true;
      }
      check(rejected, "corrupt media rejected");
      File trailing = new File(testRoot, "trailing.jpg");
      try (FileOutputStream out = new FileOutputStream(trailing)) {
        out.write(Files.readAllBytes(source.toPath()));
        out.write(
            new byte[] {
              (byte) 0xff, (byte) 0xe1, 0, 8, 'E', 'x', 'i', 'f', 0, 0, (byte) 0xff, (byte) 0xd9
            });
      }
      boolean metadataRejected = false;
      try {
        MainActivity.validatePhoto(trailing);
      } catch (IOException expected) {
        metadataRejected = true;
      }
      check(metadataRejected, "trailing EXIF payload rejected on Android");
      DraftStore drafts = new DraftStore(new File(testRoot, "draft"));
      DraftStore.Draft draft = drafts.begin(photo, saved);
      draft.body = "Uncommitted edit";
      draft.dirty = true;
      drafts.save(draft);
      DraftStore.Draft recovered = drafts.load();
      check(
          recovered.dirty && recovered.body.equals("Uncommitted edit"),
          "dirty draft survives reopen");
      check(
          !recovered.journalId.equals(restored.journalId),
          "old dirty draft cannot target restored journal");
      drafts.discard();
      check(store.load().entries.size() == 2, "discard does not modify committed notes");
      final Throwable[] undoFailure = {null};
      final int[] undoPassed = {0};
      runOnMainSync(
          () -> {
            try {
              undoPassed[0] = JournalUndoInstrumentationChecks.run(getTargetContext());
            } catch (Throwable error) {
              undoFailure[0] = error;
            }
          });
      if (undoFailure[0] != null)
        throw new AssertionError("Undo framework checks failed", undoFailure[0]);
      passed += undoPassed[0];
      result.putInt("passed", passed);
      result.putString("stream", "Field Journal: " + passed + " Android framework checks passed\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable error) {
      result.putInt("passed", passed);
      result.putString(
          "stream",
          "FAIL after " + passed + " checks: " + android.util.Log.getStackTraceString(error));
      finish(Activity.RESULT_CANCELED, result);
    } finally {
      JournalStore.removeTree(testRoot);
    }
  }

  private void check(boolean condition, String label) {
    if (!condition) throw new AssertionError(label);
    passed++;
  }
}
