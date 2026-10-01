// SPDX-License-Identifier: GPL-3.0-or-later
package dev.appmatrix.journal;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.EditText;

/** Real Android TextWatcher checks. Invoke run(Context) on the main thread. */
public final class JournalUndoInstrumentationChecks {
    private JournalUndoInstrumentationChecks() { }

    public static int run(Context context) {
        int passed = 0;
        EditText editor = new EditText(context);
        editor.setText("ac");
        editor.setSelection(1);
        int[] dirty = {0}, callbacks = {0};
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { dirty[0]++; }
        });
        JournalUndoController undo = new JournalUndoController(editor, () -> callbacks[0]++);
        check(!undo.canUndo() && !undo.canRedo(), "Initial content entered history");
        editor.getText().insert(1, "b");
        check(undo.canUndo() && undo.undo(), "Typed insertion was not undoable");
        check(editor.getText().toString().equals("ac") && editor.getSelectionStart() == 1,
                "Undo did not restore text and cursor");
        check(undo.redo() && editor.getText().toString().equals("abc")
                && editor.getSelectionStart() == 2, "Redo did not restore text and cursor");
        check(dirty[0] == 3 && callbacks[0] == 3, "Replay detached dirty watcher or recorded itself");
        passed++;

        undo.suspendRecording(() -> editor.setText("blue sky"));
        editor.setSelection(0, 4);
        editor.getText().replace(0, 4, "red");
        check(undo.undo() && editor.getText().toString().equals("blue sky")
                && editor.getSelectionStart() == 0 && editor.getSelectionEnd() == 4,
                "Replacement lost the selected range");
        check(undo.redo() && editor.getText().toString().equals("red sky"), "Replacement redo failed");
        passed++;

        undo.suspendRecording(() -> editor.setText(""));
        editor.setSelection(0);
        editor.getText().append("a");
        editor.getText().append("b");
        editor.getText().append(" ");
        check(undo.undo() && editor.length() == 0 && !undo.canUndo(), "Typed word did not coalesce");
        check(undo.redo() && editor.getText().toString().equals("ab "), "Coalesced redo failed");
        passed++;

        undo.suspendRecording(() -> editor.setText("new entry"));
        check(!undo.canUndo() && !undo.canRedo() && !undo.undo(), "History crossed an entry boundary");
        passed++;

        try {
            undo.suspendRecording(() -> {
                editor.setText("loaded before failure");
                throw new IllegalStateException("simulated loader failure");
            });
            throw new AssertionError("Suspended action exception was lost");
        } catch (IllegalStateException expected) { }
        editor.setSelection(editor.length());
        editor.getText().append("!");
        check(undo.undo() && editor.getText().toString().equals("loaded before failure")
                && !undo.canUndo(), "Failed load left recording suspended or old history alive");
        passed++;

        undo.suspendRecording(() -> editor.setText("🌳"));
        editor.setSelection(0, 2);
        editor.getText().replace(0, 2, "🌲");
        check(undo.undo() && editor.getText().toString().equals("🌳")
                && editor.getSelectionEnd() == 2, "Emoji replacement/cursor failed");
        check(undo.redo() && editor.getText().toString().equals("🌲"), "Emoji redo failed");
        passed++;

        undo.suspendRecording(() -> editor.setText(""));
        editor.setSelection(0);
        editor.getText().append("x");
        editor.setFilters(new InputFilter[]{(source, start, end, dest, dstart, dend) ->
                dest.subSequence(dstart, dend)});
        check(!undo.undo() && editor.getText().toString().equals("x")
                && !undo.canUndo() && !undo.canRedo(), "Rejected replay left stale history");
        editor.setFilters(new InputFilter[0]);
        passed++;

        undo.dispose();
        undo.dispose();
        int oldCallbacks = callbacks[0], oldDirty = dirty[0];
        editor.getText().append("still editable");
        check(!undo.canUndo() && !undo.canRedo() && !undo.undo()
                && callbacks[0] == oldCallbacks && dirty[0] == oldDirty + 1,
                "Disposed controller retained its watcher or removed the dirty listener");
        JournalUndoController next = new JournalUndoController(editor, null);
        check(!next.canUndo() && !next.canRedo(), "New controller inherited prior history");
        next.dispose();
        passed++;
        return passed;
    }

    /** Optional attached-view lifecycle check, with no navigation or content replacement. */
    public static int runDetach(Activity activity) {
        ViewGroup root = activity.findViewById(android.R.id.content);
        EditText editor = new EditText(activity);
        root.addView(editor, new ViewGroup.LayoutParams(1, 1));
        JournalUndoController undo = new JournalUndoController(editor, null);
        editor.setText("temporary note");
        check(undo.canUndo(), "Attached editor did not record");
        root.removeView(editor);
        check(!undo.canUndo() && !undo.canRedo() && !undo.undo(), "Detach did not dispose history");
        return 1;
    }

    private static void check(boolean condition, String failure) {
        if (!condition) throw new AssertionError(failure);
    }
}
