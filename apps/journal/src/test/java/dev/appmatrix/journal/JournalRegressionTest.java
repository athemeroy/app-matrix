// SPDX-License-Identifier: GPL-3.0-or-later
package dev.appmatrix.journal;

import dev.appmatrix.journal.domain.JournalStoreTest;
import org.junit.Test;

/** Gradle/CI entry point for the same dependency-free suites used by test-domain.sh. */
public final class JournalRegressionTest {
  @Test
  public void storeAndBackupSafety() throws Exception {
    JournalStoreTest.main(new String[0]);
  }

  @Test
  public void recoverableDrafts() throws Exception {
    DraftStoreTest.main(new String[0]);
  }
}
