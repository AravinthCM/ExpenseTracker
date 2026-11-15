package com.example.expensetracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID
import java.util.concurrent.Executors

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        CategoryMappingEntity::class,
        RecurringExpenseEntity::class,
        TestNotificationEntity::class,
        PaymentTemplateEntity::class,
        SplitGroupEntity::class,
        SplitGroupMemberEntity::class,
        ExpenseSplitEntity::class,
        ExpenseSplitShareEntity::class,
        RepaymentEntity::class,
        RepaymentAllocationEntity::class,
        AccountEntity::class,
        BalanceObservationEntity::class,
        SourceAccountMappingEntity::class,
        GPayRequestEntity::class,
        GPayMemberMappingEntity::class
    ],
    version = 16,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun categoryMappingDao(): CategoryMappingDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao
    abstract fun testNotificationDao(): TestNotificationDao
    abstract fun paymentTemplateDao(): PaymentTemplateDao
    abstract fun splitGroupDao(): SplitGroupDao
    abstract fun expenseSplitDao(): ExpenseSplitDao
    abstract fun repaymentDao(): RepaymentDao
    abstract fun accountDao(): AccountDao
    abstract fun balanceObservationDao(): BalanceObservationDao
    abstract fun sourceAccountMappingDao(): SourceAccountMappingDao
    abstract fun gPayRequestDao(): GPayRequestDao
    abstract fun gPayMemberMappingDao(): GPayMemberMappingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private val categoryInitializationLock = Any()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `categories` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `category_mappings` (
                        `id` TEXT NOT NULL,
                        `pattern` TEXT NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `priority` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN originalText TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recurring_expenses` (
                        `id` TEXT NOT NULL,
                        `merchant` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `frequency` TEXT NOT NULL,
                        `intervalDays` INTEGER NOT NULL,
                        `lastTransactionTimestamp` INTEGER NOT NULL,
                        `confirmedAt` INTEGER NOT NULL,
                        `active` INTEGER NOT NULL DEFAULT 1,
                        `dismissed` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `test_notifications` (
                        `id` TEXT NOT NULL,
                        `receivedAt` INTEGER NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `notificationId` INTEGER NOT NULL,
                        `notificationTag` TEXT,
                        `notificationKey` TEXT,
                        `title` TEXT,
                        `text` TEXT,
                        `bigText` TEXT,
                        `textLines` TEXT,
                        `subText` TEXT,
                        `summaryText` TEXT,
                        `titleBig` TEXT,
                        `rawExtras` TEXT,
                        `status` TEXT NOT NULL,
                        `detectedPayment` INTEGER NOT NULL,
                        `amount` REAL,
                        `transactionType` TEXT,
                        `merchant` TEXT,
                        `source` TEXT,
                        `failureReason` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `payment_templates` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `transactionType` TEXT NOT NULL,
                        `sampleText` TEXT NOT NULL,
                        `templatePattern` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE test_notifications ADD COLUMN detectionMethod TEXT")
                db.execSQL("ALTER TABLE test_notifications ADD COLUMN matchedTemplateName TEXT")
                db.execSQL("ALTER TABLE test_notifications ADD COLUMN accountNumber TEXT")
                db.execSQL("ALTER TABLE test_notifications ADD COLUMN referenceNumber TEXT")
                db.execSQL("ALTER TABLE test_notifications ADD COLUMN confidence TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `split_groups` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `split_group_members` (
                        `id` TEXT NOT NULL,
                        `groupId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `isMe` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`groupId`) REFERENCES `split_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_split_group_members_groupId` ON `split_group_members` (`groupId`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expense_splits` (
                        `id` TEXT NOT NULL,
                        `transactionId` TEXT NOT NULL,
                        `groupId` TEXT NOT NULL,
                        `amountPaise` INTEGER NOT NULL,
                        `payerMemberId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`groupId`) REFERENCES `split_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`payerMemberId`) REFERENCES `split_group_members`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_expense_splits_transactionId_groupId` ON `expense_splits` (`transactionId`, `groupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_groupId` ON `expense_splits` (`groupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_payerMemberId` ON `expense_splits` (`payerMemberId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expense_split_shares` (
                        `id` TEXT NOT NULL,
                        `splitId` TEXT NOT NULL,
                        `memberId` TEXT NOT NULL,
                        `shareAmountPaise` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`splitId`) REFERENCES `expense_splits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`memberId`) REFERENCES `split_group_members`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_split_shares_splitId` ON `expense_split_shares` (`splitId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_split_shares_memberId` ON `expense_split_shares` (`memberId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_expense_split_shares_splitId_memberId` ON `expense_split_shares` (`splitId`, `memberId`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `repayments` (
                        `id` TEXT NOT NULL,
                        `groupId` TEXT NOT NULL,
                        `memberId` TEXT NOT NULL,
                        `amountPaise` INTEGER NOT NULL,
                        `unallocatedAmountPaise` INTEGER NOT NULL DEFAULT 0,
                        `paymentDate` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `linkedTransactionId` TEXT,
                        `note` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`groupId`) REFERENCES `split_groups`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`memberId`) REFERENCES `split_group_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`linkedTransactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_repayments_linkedTransactionId` ON `repayments` (`linkedTransactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_repayments_groupId` ON `repayments` (`groupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_repayments_memberId` ON `repayments` (`memberId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `repayment_allocations` (
                        `id` TEXT NOT NULL,
                        `repaymentId` TEXT NOT NULL,
                        `shareId` TEXT NOT NULL,
                        `allocatedAmountPaise` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`repaymentId`) REFERENCES `repayments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`shareId`) REFERENCES `expense_split_shares`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_repayment_allocations_repaymentId` ON `repayment_allocations` (`repaymentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_repayment_allocations_shareId` ON `repayment_allocations` (`shareId`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `accounts` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `openingBalancePaise` INTEGER NOT NULL,
                        `isPrimary` INTEGER NOT NULL DEFAULT 0,
                        `isArchived` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_name` ON `accounts` (`name`)")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `accountId` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `referenceNumber` TEXT DEFAULT NULL")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_referenceNumber` ON `transactions` (`referenceNumber`)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `accountNumber` TEXT DEFAULT NULL")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `source_account_mappings` (
                        `id` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `sourcePattern` TEXT NOT NULL,
                        `senderOrPackage` TEXT,
                        `matchingContext` TEXT,
                        `isConfirmed` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_account_mappings_accountId` ON `source_account_mappings` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_account_mappings_sourcePattern_senderOrPackage` ON `source_account_mappings` (`sourcePattern`, `senderOrPackage`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `balance_observations` (
                        `id` TEXT NOT NULL,
                        `accountId` TEXT,
                        `accountSuffix` TEXT,
                        `balancePaise` INTEGER NOT NULL,
                        `balanceType` TEXT NOT NULL,
                        `currency` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `senderOrPackage` TEXT,
                        `referenceNumber` TEXT,
                        `rawText` TEXT NOT NULL,
                        `isResolved` INTEGER NOT NULL,
                        `isReconciled` INTEGER NOT NULL DEFAULT 0,
                        `reconciledAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_balance_observations_accountId` ON `balance_observations` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_balance_observations_referenceNumber` ON `balance_observations` (`referenceNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_balance_observations_timestamp` ON `balance_observations` (`timestamp`)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `balanceIncludedInAccount` INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `split_group_members` ADD COLUMN `openingDebtPaise` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `balanceBaselinePaise` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `balanceBaselineTimestamp` INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `gpay_requests` (
                        `id` TEXT NOT NULL,
                        `requestKey` TEXT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `notificationKey` TEXT,
                        `requesterRawName` TEXT NOT NULL,
                        `requesterNormalizedName` TEXT NOT NULL,
                        `amountPaise` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        `groupNameRaw` TEXT,
                        `originalText` TEXT NOT NULL,
                        `postTime` INTEGER NOT NULL,
                        `capturedAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `matchedGroupId` TEXT,
                        `matchedMemberId` TEXT,
                        `matchStatus` TEXT NOT NULL,
                        `processingStatus` TEXT NOT NULL,
                        `linkedTransactionId` TEXT,
                        `linkedSplitId` TEXT,
                        `paymentConfirmationTransactionId` TEXT,
                        `repaymentId` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`matchedGroupId`) REFERENCES `split_groups`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`matchedMemberId`) REFERENCES `split_group_members`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`linkedTransactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_gpay_requests_requestKey` ON `gpay_requests` (`requestKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gpay_requests_matchedGroupId` ON `gpay_requests` (`matchedGroupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gpay_requests_matchedMemberId` ON `gpay_requests` (`matchedMemberId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gpay_requests_linkedTransactionId` ON `gpay_requests` (`linkedTransactionId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `gpay_member_mappings` (
                        `id` TEXT NOT NULL,
                        `groupId` TEXT NOT NULL,
                        `memberId` TEXT NOT NULL,
                        `normalizedRequesterName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`groupId`) REFERENCES `split_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`memberId`) REFERENCES `split_group_members`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_gpay_member_mappings_groupId_normalizedRequesterName` ON `gpay_member_mappings` (`groupId`, `normalizedRequesterName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gpay_member_mappings_memberId` ON `gpay_member_mappings` (`memberId`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        populateDefaultCategories(context.applicationContext)
                    }
                })
                .build()
                INSTANCE = instance
                populateDefaultCategories(context.applicationContext)
                instance
            }
        }

        private fun populateDefaultCategories(context: Context) {
            Executors.newSingleThreadExecutor().execute {
                synchronized(categoryInitializationLock) {
                    try {
                        val db = INSTANCE ?: return@synchronized
                        val existing = db.categoryDao().getAllCategoriesSync()

                        // Populate defaults only when no categories exist. Existing rows are preserved.
                        if (existing.isEmpty()) {
                            val defaultNames = listOf(
                                "Food",
                                "Fuel",
                                "Shopping",
                                "Bills",
                                "Entertainment",
                                "Travel",
                                "Other"
                            )
                            defaultNames.forEach { name ->
                                db.categoryDao().insertCategory(
                                    CategoryEntity(id = UUID.randomUUID().toString(), name = name)
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore background init error if DB not ready
                    }
                }
            }
        }
    }
}
