package com.jwhh.notekeeper;

import android.app.LoaderManager;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import android.provider.ContactsContract;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;

import com.jwhh.notekeeper.NoteKeeperDatabaseContract.CourseInfoEntry;
import com.jwhh.notekeeper.NoteKeeperDatabaseContract.NoteInfoEntry;

import java.util.List;

import static com.jwhh.notekeeper.NoteKeeperProviderContract.*;

public class NoteActivity extends AppCompatActivity
    implements LoaderManager.LoaderCallbacks<Cursor>
{
    private static final int LOADER_NOTES = 0;
    private static final int LOADER_COURSES = 1;
    private final String TAG = getClass().getSimpleName();
    public static final String NOTE_POSITION = "com.jwhh.notekeeper.NOTE_POSITION";
    public static final String NOTE_ID = "com.jwhh.notekeeper.NOTE_ID";
    public static final int NOTE_ID_NOT_SET = -1;
    private NoteInfo mNote;
    private boolean mIsNewNote;
    private Spinner mSpinnerCourses;
    private EditText mTextNoteTitle;
    private EditText mTextNoteText;
    private int mNoteId;
    private boolean mIsCancelling;
    private NoteActivityViewModel mViewModel;
    private NoteKeeperOpenHelper mDbOpenHelper;
    private Cursor mNoteCursor;
    private int mCourseIdPos;
    private int mNoteTitlePos;
    private int mNoteTextPos;
    private SimpleCursorAdapter mAdapterCourses;
    private boolean mCoursesQueryFinished;
    private boolean mNotesQueryFinished;
    private Uri mNoteUri;

    @Override
    protected void onDestroy() {
        mDbOpenHelper.close();
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mDbOpenHelper = new NoteKeeperOpenHelper(this);

        ViewModelProvider viewModelProvider = new ViewModelProvider(getViewModelStore(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication()));
        mViewModel = viewModelProvider.get(NoteActivityViewModel.class);
        if(mViewModel.mIsNewlyCreated && savedInstanceState != null)
            mViewModel.restoreState(savedInstanceState);
        mViewModel.mIsNewlyCreated = false;

        mSpinnerCourses = findViewById(R.id.spinner_courses);

        final String[] spinnerColumns = new String[]{
            CourseInfoEntry.COLUMN_COURSE_TITLE,
        };
        mAdapterCourses = new SimpleCursorAdapter(this, android.R.layout.simple_spinner_item, null,
                spinnerColumns, new int[] {android.R.id.text1}, 0 );
        mAdapterCourses.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerCourses.setAdapter(mAdapterCourses);

        getLoaderManager().initLoader(LOADER_COURSES, null, this);

        readDisplayStateValues();
        saveOriginalNoteValues();

        mTextNoteTitle = findViewById(R.id.text_note_title);
        mTextNoteText = findViewById(R.id.text_note_text);

        if(!mIsNewNote)
            getLoaderManager().initLoader(LOADER_NOTES, null, this);

        Log.d(TAG, "onCreate");
    }

    private void loadCourseData() {
        SQLiteDatabase db = mDbOpenHelper.getReadableDatabase();
        String[] courseColumns = {
                CourseInfoEntry.COLUMN_COURSE_TITLE,
                CourseInfoEntry.COLUMN_COURSE_ID,
                CourseInfoEntry._ID
        };

        Cursor courseCursor = db.query(CourseInfoEntry.TABLE_NAME, courseColumns,
                null, null, null, null, CourseInfoEntry.COLUMN_COURSE_TITLE);

        mAdapterCourses.changeCursor(courseCursor);
    }

    private void loadNoteData() {
        SQLiteDatabase db = mDbOpenHelper.getReadableDatabase();

        String selection = NoteInfoEntry._ID + " = ?";
        String[] selectionArgs = { Integer.toString(mNoteId) };

        String[] noteColumns = { NoteInfoEntry.COLUMN_COURSE_ID,
                                 NoteInfoEntry.COLUMN_NOTE_TITLE,
                                 NoteInfoEntry.COLUMN_NOTE_TEXT};

        mNoteCursor = db.query(NoteInfoEntry.TABLE_NAME, noteColumns,
                selection, selectionArgs, null, null, null);

        mCourseIdPos = mNoteCursor.getColumnIndex(NoteInfoEntry.COLUMN_COURSE_ID);
        mNoteTitlePos = mNoteCursor.getColumnIndex(NoteInfoEntry.COLUMN_NOTE_TITLE);
        mNoteTextPos = mNoteCursor.getColumnIndex(NoteInfoEntry.COLUMN_NOTE_TEXT);
        mNoteCursor.moveToNext();
        displayNote();
    }

    private void restoreOriginalNoteValues() {
//        CourseInfo course = DataManager.getInstance().getCourse(mViewModel.mOriginalNoteCourseId);
//        mNote.setCourse(course);
//        mNote.setTitle(mViewModel.mOriginalNoteTitle);
//        mNote.setText(mViewModel.mOriginalNoteText);
    }

    private void saveOriginalNoteValues() {
        if(mIsNewNote)
            return;
//        mViewModel.mOriginalNoteCourseId = mNote.getCourse().getCourseId();
//        mViewModel.mOriginalNoteTitle = mNote.getTitle();
//        mViewModel.mOriginalNoteText = mNote.getText();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mIsCancelling) {
            Log.i(TAG, "Cancelling note at position: " + mNoteId);
            if(mIsNewNote) {
               deleteNoteFromDatabase();
            } else {
                restoreOriginalNoteValues();
            }
        } else {
            saveNote();
        }
        Log.d(TAG, "onPause");
    }

    private void deleteNoteFromDatabase() {
        final String selection = NoteInfoEntry._ID + " = ?";
        final String[] selectionArgs = {Integer.toString(mNoteId)};

        AsyncTask task = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                SQLiteDatabase db = mDbOpenHelper.getWritableDatabase();
                db.delete(NoteInfoEntry.TABLE_NAME, selection, selectionArgs);
                return null;
            }
        };

        task.execute();

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(outState != null)
            mViewModel.saveState(outState);
    }

    private void saveNote() {
        String courseId = getSelectedCourseId();
        String noteTitle = mTextNoteTitle.getText().toString();
        String noteText = mTextNoteText.getText().toString();

        saveNoteToDatabase(courseId, noteTitle, noteText);
    }

    private String getSelectedCourseId() {
        int selectedPos = mSpinnerCourses.getSelectedItemPosition();
        Cursor cursor = mAdapterCourses.getCursor();
        cursor.moveToPosition(selectedPos);
        int courseIdPos = cursor.getColumnIndex(NoteInfoEntry.COLUMN_COURSE_ID);
        String courseId = cursor.getString(courseIdPos);
        return courseId;
    }

    private void saveNoteToDatabase(String courseId, String noteTitle, String noteText) {
        final String selection = NoteInfoEntry._ID + " = ?";
        final String[] selectionArgs = {Integer.toString(mNoteId)};

        ContentValues contentValues = new ContentValues();
        contentValues.put(NoteInfoEntry.COLUMN_COURSE_ID, courseId);
        contentValues.put(NoteInfoEntry.COLUMN_NOTE_TITLE, noteTitle);
        contentValues.put(NoteInfoEntry.COLUMN_NOTE_TEXT, noteText);

        SQLiteDatabase db = mDbOpenHelper.getWritableDatabase();
        db.update(NoteInfoEntry.TABLE_NAME, contentValues, selection, selectionArgs);
    }

    private void displayNote() {
        String courseId = mNoteCursor.getString(mCourseIdPos);
        String noteTitle = mNoteCursor.getString(mNoteTitlePos);
        String noteText = mNoteCursor.getString(mNoteTextPos);

        int courseIndex = getIndexOfCourseId(courseId);
        mSpinnerCourses.setSelection(courseIndex);
        mTextNoteTitle.setText(noteTitle);
        mTextNoteText.setText(noteText);
    }

    private int getIndexOfCourseId(String courseId) {
        Cursor cursor = mAdapterCourses.getCursor();
        int courseIdPos = cursor.getColumnIndex(CourseInfoEntry.COLUMN_COURSE_ID);
        int courseRowIndex = 0;

        boolean more = cursor.moveToFirst();
        while(more) {
            String cursorCourseId = cursor.getString(courseIdPos);
            if(courseId.equals(cursorCourseId))
                break;

            courseRowIndex++;
            more = cursor.moveToNext();
        }

        return courseRowIndex;
    }

    private void readDisplayStateValues() {
        Intent intent = getIntent();
        mNoteId = intent.getIntExtra(NOTE_ID, NOTE_ID_NOT_SET);
        mIsNewNote = mNoteId == NOTE_ID_NOT_SET;
        if(mIsNewNote){
            createNewNote();
        }

        Log.i(TAG, "mNotePosition: " + mNoteId);
        //mNote = DataManager.getInstance().getNotes().get(mNoteId);
    }

    private void createNewNote() {
        ContentValues values = new ContentValues();
        values.put(Notes.COLUMN_COURSE_ID, "");
        values.put(Notes.COLUMN_NOTE_TITLE, "");
        values.put(Notes.COLUMN_NOTE_TEXT, "");

        mNoteUri = getContentResolver().insert(Notes.CONTENT_URI, values);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_note, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_next);
        int lastNoteIndex = DataManager.getInstance().getNotes().size()-1;
        item.setEnabled(mNoteId < lastNoteIndex);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_send_mail) {
            sendEmail();
            return true;
        } else if (id == R.id.action_cancel) {
            mIsCancelling = true;
            finish();
        } else if(id == R.id.action_next) {
            moveNext();
        }

        return super.onOptionsItemSelected(item);
    }

    private void moveNext() {
        saveNote();

        DataManager dm = DataManager.getInstance();
        if(mNoteId < dm.getNotes().size()-1) {
            ++mNoteId;
        }
        mNote = dm.getNotes().get(mNoteId);

        saveOriginalNoteValues();

        displayNote();

        invalidateOptionsMenu();
    }

    private void sendEmail() {
        CourseInfo course = (CourseInfo) mSpinnerCourses.getSelectedItem();
        String subject = mTextNoteTitle.getText().toString();
        String text = "Checkout what I learned in the Pluralsight course \"" +
                course.getTitle() + "\"\n" + mTextNoteText.getText();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("message/rfc2822");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(intent);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        CursorLoader loader = null;
        if(i == LOADER_NOTES) {
            loader = createLoaderNotes();
        } else if (i == LOADER_COURSES) {
            loader = createLoaderCourses();
        }
        return loader;
    }

    private CursorLoader createLoaderCourses() {
        mCoursesQueryFinished = false;
        Uri uri = Courses.CONTENT_URI;
        String[] courseColumns = {
                Courses.COLUMN_COURSE_TITLE,
                Courses.COLUMN_COURSE_ID,
                Courses._ID
        };
        return new CursorLoader(this, uri, courseColumns, null, null, Courses.COLUMN_COURSE_TITLE);
    }

    private CursorLoader createLoaderNotes() {
        mNotesQueryFinished = false;
        return new CursorLoader(this) {
            @Override
            public Cursor loadInBackground() {
                SQLiteDatabase db = mDbOpenHelper.getReadableDatabase();

                String selection = NoteInfoEntry._ID + " = ?";
                String[] selectionArgs = { Integer.toString(mNoteId) };

                String[] noteColumns = { NoteInfoEntry.COLUMN_COURSE_ID,
                        NoteInfoEntry.COLUMN_NOTE_TITLE,
                        NoteInfoEntry.COLUMN_NOTE_TEXT};

                return db.query(NoteInfoEntry.TABLE_NAME, noteColumns,
                        selection, selectionArgs, null, null, null);

            }
        };
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (loader.getId() == LOADER_NOTES) {
            loadFinishedNotes(cursor);
        } else if (loader.getId() == LOADER_COURSES) {
            loadFinishedCourses(cursor);
        }
    }

    private void loadFinishedCourses(Cursor cursor) {
        mAdapterCourses.changeCursor(cursor);
        mCoursesQueryFinished = true;
        displayNoteWhenQueryHasFinshed();
    }

    private void loadFinishedNotes(Cursor cursor) {
        mNoteCursor = cursor;
        mCourseIdPos = mNoteCursor.getColumnIndex(NoteInfoEntry.COLUMN_COURSE_ID);
        mNoteTitlePos = mNoteCursor.getColumnIndex(NoteInfoEntry.COLUMN_NOTE_TITLE);
        mNoteTextPos = mNoteCursor.getColumnIndex(NoteInfoEntry.COLUMN_NOTE_TEXT);
        mNoteCursor.moveToNext();
        mNotesQueryFinished = true;
        displayNoteWhenQueryHasFinshed();
    }

    private void displayNoteWhenQueryHasFinshed() {
        if(mNotesQueryFinished && mCoursesQueryFinished) {
            displayNote();
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if(loader.getId() == LOADER_NOTES) {
            if(mNoteCursor != null) {
                mNoteCursor.close();
            }
        } else if(loader.getId() == LOADER_COURSES) {
            mAdapterCourses.changeCursor(null);
        }
    }
}










