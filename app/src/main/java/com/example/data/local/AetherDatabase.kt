package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FolderEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        CachedModelEntity::class,
        CustomStickerEntity::class,
        PersonaCardEntity::class,
        McpServerEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AetherDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun cachedModelDao(): CachedModelDao
    abstract fun customStickerDao(): CustomStickerDao
    abstract fun personaCardDao(): PersonaCardDao
    abstract fun mcpServerDao(): McpServerDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_stickers` (
                        `id` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `localFilePath` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `packName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `personaId` TEXT")
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `enabledMcpServerIdsJson` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `persona_cards` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `avatarPath` TEXT NOT NULL,
                        `cardFormat` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `mcp_servers` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `endpointUrl` TEXT NOT NULL,
                        `authHeaderName` TEXT NOT NULL,
                        `authHeaderValue` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `toolsJson` TEXT NOT NULL,
                        `lastSyncedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        @Volatile
        private var INSTANCE: AetherDatabase? = null

        fun getInstance(context: Context): AetherDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AetherDatabase::class.java,
                    "aetherrouter_vault.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
