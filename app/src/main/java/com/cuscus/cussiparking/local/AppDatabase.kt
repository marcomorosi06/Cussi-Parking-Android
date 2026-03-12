package com.cuscus.cussiparking.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [VehicleEntity::class, TriggerEntity::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao
    abstract fun triggerDao(): TriggerDao

    companion object {

        // Migrazione da v5 a v6: aggiunge la tabella triggers senza perdere dati
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS triggers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        localVehicleId INTEGER NOT NULL,
                        vehicleName TEXT NOT NULL,
                        type TEXT NOT NULL,
                        identifier TEXT NOT NULL,
                        label TEXT NOT NULL,
                        locationMode TEXT NOT NULL DEFAULT 'last_known',
                        enabled INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
            }
        }

        // Migrazione da v6 a v7: aggiunge tagSourceVehicleId per i trigger NFC cross-device
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE triggers ADD COLUMN tagSourceVehicleId INTEGER")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "family_parking_local_db"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration() // solo per versioni non coperte da migration
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}