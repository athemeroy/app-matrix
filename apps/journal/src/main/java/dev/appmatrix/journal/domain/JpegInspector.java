// SPDX-License-Identifier: GPL-3.0-or-later
package dev.appmatrix.journal.domain;

import java.io.*;

/** Buffered traversal of every JPEG scan/segment; rejects metadata and trailing payloads. */
public final class JpegInspector {
  private JpegInspector() {}

  public static void validate(File file) throws IOException {
    long size = file.length();
    if (!file.isFile() || size < 4 || size > JournalStore.MAX_FILE)
      throw new IOException("Invalid photo size");
    try (CountingInputStream counted =
            new CountingInputStream(new BufferedInputStream(new FileInputStream(file), 65536));
        DataInputStream input = new DataInputStream(counted)) {
      if (input.readUnsignedShort() != 0xffd8) throw new IOException("Backup photo is not a JPEG");
      boolean inScan = false, sawScan = false;
      while (counted.position < size) {
        int value = input.readUnsignedByte();
        if (inScan && value != 0xff) continue;
        if (value != 0xff) throw new IOException("Invalid JPEG marker");
        int marker;
        do {
          marker = input.readUnsignedByte();
        } while (marker == 0xff);
        if (inScan && (marker == 0 || marker >= 0xd0 && marker <= 0xd7)) continue;
        inScan = false;
        if (marker == 0xd9) {
          if (!sawScan || counted.position != size)
            throw new IOException("JPEG has an incomplete scan or trailing data");
          return;
        }
        if (marker == 0xd8 || marker == 0 || marker >= 0xd0 && marker <= 0xd7)
          throw new IOException("Unexpected JPEG marker");
        if (marker == 0xe1 || marker == 0xed || marker == 0xfe)
          throw new IOException("Backup photo contains unsupported private metadata");
        if (marker == 1) continue;
        int length = input.readUnsignedShort();
        if (length < 2 || counted.position + length - 2 > size)
          throw new IOException("Truncated JPEG segment");
        int remaining = length - 2;
        while (remaining > 0) {
          int n = input.skipBytes(remaining);
          if (n <= 0) throw new IOException("Truncated JPEG segment");
          remaining -= n;
        }
        if (marker == 0xda) {
          inScan = true;
          sawScan = true;
        }
      }
      throw new IOException("Backup photo has no complete JPEG ending");
    } catch (EOFException e) {
      throw new IOException("Backup photo is truncated", e);
    }
  }

  private static final class CountingInputStream extends FilterInputStream {
    long position;

    CountingInputStream(InputStream input) {
      super(input);
    }

    @Override
    public int read() throws IOException {
      int value = in.read();
      if (value >= 0) position++;
      return value;
    }

    @Override
    public int read(byte[] data, int offset, int length) throws IOException {
      int count = in.read(data, offset, length);
      if (count > 0) position += count;
      return count;
    }

    @Override
    public long skip(long count) throws IOException {
      long skipped = in.skip(count);
      position += skipped;
      return skipped;
    }
  }
}
