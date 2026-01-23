package com.example.comfyui_remote.data;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile WorkflowDao _workflowDao;

  private volatile GeneratedMediaDao _generatedMediaDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `workflows` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `jsonContent` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `lastImageName` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `generated_media` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workflowName` TEXT NOT NULL, `fileName` TEXT NOT NULL, `subfolder` TEXT, `serverHost` TEXT NOT NULL, `serverPort` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `mediaType` TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'f76391b2da9a903c179c50cf2402416b')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `workflows`");
        db.execSQL("DROP TABLE IF EXISTS `generated_media`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsWorkflows = new HashMap<String, TableInfo.Column>(5);
        _columnsWorkflows.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkflows.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkflows.put("jsonContent", new TableInfo.Column("jsonContent", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkflows.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkflows.put("lastImageName", new TableInfo.Column("lastImageName", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysWorkflows = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesWorkflows = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoWorkflows = new TableInfo("workflows", _columnsWorkflows, _foreignKeysWorkflows, _indicesWorkflows);
        final TableInfo _existingWorkflows = TableInfo.read(db, "workflows");
        if (!_infoWorkflows.equals(_existingWorkflows)) {
          return new RoomOpenHelper.ValidationResult(false, "workflows(com.example.comfyui_remote.data.WorkflowEntity).\n"
                  + " Expected:\n" + _infoWorkflows + "\n"
                  + " Found:\n" + _existingWorkflows);
        }
        final HashMap<String, TableInfo.Column> _columnsGeneratedMedia = new HashMap<String, TableInfo.Column>(8);
        _columnsGeneratedMedia.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGeneratedMedia.put("workflowName", new TableInfo.Column("workflowName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGeneratedMedia.put("fileName", new TableInfo.Column("fileName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGeneratedMedia.put("subfolder", new TableInfo.Column("subfolder", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGeneratedMedia.put("serverHost", new TableInfo.Column("serverHost", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGeneratedMedia.put("serverPort", new TableInfo.Column("serverPort", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGeneratedMedia.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGeneratedMedia.put("mediaType", new TableInfo.Column("mediaType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysGeneratedMedia = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesGeneratedMedia = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoGeneratedMedia = new TableInfo("generated_media", _columnsGeneratedMedia, _foreignKeysGeneratedMedia, _indicesGeneratedMedia);
        final TableInfo _existingGeneratedMedia = TableInfo.read(db, "generated_media");
        if (!_infoGeneratedMedia.equals(_existingGeneratedMedia)) {
          return new RoomOpenHelper.ValidationResult(false, "generated_media(com.example.comfyui_remote.data.GeneratedMediaEntity).\n"
                  + " Expected:\n" + _infoGeneratedMedia + "\n"
                  + " Found:\n" + _existingGeneratedMedia);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "f76391b2da9a903c179c50cf2402416b", "a2f798e862101ea6a3e8516d8d62a358");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "workflows","generated_media");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `workflows`");
      _db.execSQL("DELETE FROM `generated_media`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(WorkflowDao.class, WorkflowDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(GeneratedMediaDao.class, GeneratedMediaDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public WorkflowDao workflowDao() {
    if (_workflowDao != null) {
      return _workflowDao;
    } else {
      synchronized(this) {
        if(_workflowDao == null) {
          _workflowDao = new WorkflowDao_Impl(this);
        }
        return _workflowDao;
      }
    }
  }

  @Override
  public GeneratedMediaDao generatedMediaDao() {
    if (_generatedMediaDao != null) {
      return _generatedMediaDao;
    } else {
      synchronized(this) {
        if(_generatedMediaDao == null) {
          _generatedMediaDao = new GeneratedMediaDao_Impl(this);
        }
        return _generatedMediaDao;
      }
    }
  }
}
