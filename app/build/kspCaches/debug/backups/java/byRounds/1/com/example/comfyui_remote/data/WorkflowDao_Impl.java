package com.example.comfyui_remote.data;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class WorkflowDao_Impl implements WorkflowDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<WorkflowEntity> __insertionAdapterOfWorkflowEntity;

  private final EntityDeletionOrUpdateAdapter<WorkflowEntity> __deletionAdapterOfWorkflowEntity;

  private final EntityDeletionOrUpdateAdapter<WorkflowEntity> __updateAdapterOfWorkflowEntity;

  public WorkflowDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfWorkflowEntity = new EntityInsertionAdapter<WorkflowEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `workflows` (`id`,`name`,`jsonContent`,`createdAt`,`lastImageName`,`baseModelName`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final WorkflowEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getJsonContent());
        statement.bindLong(4, entity.getCreatedAt());
        if (entity.getLastImageName() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getLastImageName());
        }
        if (entity.getBaseModelName() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getBaseModelName());
        }
      }
    };
    this.__deletionAdapterOfWorkflowEntity = new EntityDeletionOrUpdateAdapter<WorkflowEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `workflows` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final WorkflowEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfWorkflowEntity = new EntityDeletionOrUpdateAdapter<WorkflowEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `workflows` SET `id` = ?,`name` = ?,`jsonContent` = ?,`createdAt` = ?,`lastImageName` = ?,`baseModelName` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final WorkflowEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getJsonContent());
        statement.bindLong(4, entity.getCreatedAt());
        if (entity.getLastImageName() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getLastImageName());
        }
        if (entity.getBaseModelName() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getBaseModelName());
        }
        statement.bindLong(7, entity.getId());
      }
    };
  }

  @Override
  public Object insert(final WorkflowEntity workflow,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfWorkflowEntity.insertAndReturnId(workflow);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final WorkflowEntity workflow,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfWorkflowEntity.handle(workflow);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final WorkflowEntity workflow,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfWorkflowEntity.handle(workflow);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<WorkflowEntity>> getAll() {
    final String _sql = "SELECT * FROM workflows ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"workflows"}, new Callable<List<WorkflowEntity>>() {
      @Override
      @NonNull
      public List<WorkflowEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfJsonContent = CursorUtil.getColumnIndexOrThrow(_cursor, "jsonContent");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfLastImageName = CursorUtil.getColumnIndexOrThrow(_cursor, "lastImageName");
          final int _cursorIndexOfBaseModelName = CursorUtil.getColumnIndexOrThrow(_cursor, "baseModelName");
          final List<WorkflowEntity> _result = new ArrayList<WorkflowEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final WorkflowEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpJsonContent;
            _tmpJsonContent = _cursor.getString(_cursorIndexOfJsonContent);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpLastImageName;
            if (_cursor.isNull(_cursorIndexOfLastImageName)) {
              _tmpLastImageName = null;
            } else {
              _tmpLastImageName = _cursor.getString(_cursorIndexOfLastImageName);
            }
            final String _tmpBaseModelName;
            if (_cursor.isNull(_cursorIndexOfBaseModelName)) {
              _tmpBaseModelName = null;
            } else {
              _tmpBaseModelName = _cursor.getString(_cursorIndexOfBaseModelName);
            }
            _item = new WorkflowEntity(_tmpId,_tmpName,_tmpJsonContent,_tmpCreatedAt,_tmpLastImageName,_tmpBaseModelName);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
