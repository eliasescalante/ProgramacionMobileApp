package com.example.api1_eliasescalante_pm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Única instancia de la base local de la aplicación.
 *
 * Versión 2 (Actividad 4):
 *  - Se agrega `ownerUid` para aislar los datos por cuenta.
 *  - Se reemplaza el `remoteId` numérico por el id estable del documento de
 *    Firestore, que coincide con la clave primaria local.
 *  - Se agrega `syncedAt` para diagnóstico.
 *
 * La migración descarta los recordatorios anónimos de la versión 3: no tienen
 * dueño conocido y asignarlos automáticamente a la primera cuenta que inicie
 * sesión sería exactamente el problema de privacidad que esta versión corrige.
 */
@Database(
    entities = [ReminderEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao

    companion object {
        private const val DATABASE_NAME = "reminders.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reminders_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        ownerUid TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        category TEXT NOT NULL,
                        priority TEXT NOT NULL,
                        status TEXT NOT NULL,
                        dueDate INTEGER,
                        locationOrNotice TEXT,
                        syncState TEXT NOT NULL,
                        syncError TEXT,
                        isDeleted INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncedAt INTEGER
                    )
                    """.trimIndent()
                )
                // Los datos de la versión 3 no tienen uid: no se migran.
                db.execSQL("DROP TABLE IF EXISTS reminders")
                db.execSQL("ALTER TABLE reminders_new RENAME TO reminders")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_ownerUid ON reminders(ownerUid)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_reminders_ownerUid_status " +
                        "ON reminders(ownerUid, status)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_reminders_ownerUid_priority " +
                        "ON reminders(ownerUid, priority)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_reminders_ownerUid_syncState " +
                        "ON reminders(ownerUid, syncState)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_syncedAt ON reminders(syncedAt)")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
