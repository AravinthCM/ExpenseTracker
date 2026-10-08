package com.example.expensetracker

import com.example.expensetracker.model.TransactionSource
import com.example.expensetracker.model.TransactionType
import com.example.expensetracker.service.DeduplicationResult
import com.example.expensetracker.service.NotificationParser
import com.example.expensetracker.service.TransactionDeduplicator
import com.example.expensetracker.service.TransactionNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationParserTest {

    private val gmailPackage = "com.google.android.gm"
    private val gpayPackage = "com.google.android.apps.nbu.paisa.user"
    private val messagingPackage = "com.google.android.apps.messaging"

    @Before
    fun setUp() {
        TransactionDeduplicator.clearHistory()
    }

    @Test
    fun testExactCanaraBankEmailParsing() {
        val subject = "UPI/IMPS/MB Transaction Alert"
        val sender = "canarabank@canarabank.com"
        val emailBody = """
            Dear Customer,

            Thanking you for banking with Canara Bank.

            An amount of INR 104.00 has been DEBITED on 23/09/26 from your account XXX528 to THIRUCHENDUR with UPI Ref No.:663206421839. Total Available Balance INR 364.08.

            To report fraud & stop further debit transaction, Call : 18001030.

            If the transaction was not initiated by you, send an email from your registered e-mail id with the subject line in the below format to reportfraud@canarabank.com to block.

            BLOCKUPI<Space><Mobile Number prefixed with Country Code>

            For blocking through SMS, send SMS from Registered Mobile Number as per below format:

            To block Mobile Banking: BLOCKMB to 9901771222.

            This is an auto generated mail
        """.trimIndent()

        val parsed = NotificationParser.parse(
            packageName = gmailPackage,
            title = subject,
            text = emailBody,
            postTime = System.currentTimeMillis()
        )

        println("=== CANARA BANK EXACT EMAIL PARSE TEST ===")
        println("Sender: $sender")
        println("Subject: $subject")
        println("Parsed Object: $parsed")
        if (parsed != null) {
            println("PARSED AMOUNT: ${parsed.amount}")
            println("PARSED TYPE: ${parsed.type}")
            println("PARSED MERCHANT/NAME: ${parsed.name}")
            println("PARSED SOURCE: ${parsed.source}")
        } else {
            println("PARSED OBJECT IS NULL")
        }
        println("==========================================")

        assertNotNull("NotificationParser should parse the Canara Bank email", parsed)
        assertEquals(104.00, parsed!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("THIRUCHENDUR", parsed.name)
        assertEquals(TransactionSource.BANK_EMAIL.name, parsed.source)
    }

    @Test
    fun testGmailNotificationExtractionSimulation() {
        val subject = "UPI/IMPS/MB Transaction Alert"
        val fullEmailBody = """
            Dear Customer,

            Thanking you for banking with Canara Bank.

            An amount of INR 104.00 has been DEBITED on 23/09/26 from your account XXX528 to THIRUCHENDUR with UPI Ref No.:663206421839. Total Available Balance INR 364.08.

            To report fraud & stop further debit transaction, Call : 18001030.

            If the transaction was not initiated by you, send an email from your registered e-mail id with the subject line in the below format to reportfraud@canarabank.com to block.

            BLOCKUPI<Space><Mobile Number prefixed with Country Code>

            For blocking through SMS, send SMS from Registered Mobile Number as per below format:

            To block Mobile Banking: BLOCKMB to 9901771222.

            This is an auto generated mail
        """.trimIndent()

        // 1. Inspect existing ExpenseNotificationListenerService text extraction logic:
        // isGmail = packageName == "com.google.android.gm" || packageName.contains("gmail", ignoreCase = true)
        // title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        // text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        // bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        // bodyText = when { isGmail && bigText.isNotBlank() -> bigText; isGmail && text.isNotBlank() -> text; else -> text }
        // Note: Notification.EXTRA_TEXT_LINES, EXTRA_SUB_TEXT, EXTRA_TITLE_BIG are ignored by the listener.

        // Scenario 1: Realistic Gmail Notification where EXTRA_BIG_TEXT is populated with full body
        val extraTitle1 = subject
        val extraText1 = "Dear Customer, Thanking you for banking with Canara Bank. An amount of INR 104.00 has been DEBITED..."
        val extraBigText1 = fullEmailBody
        val extraTextLines1: Array<CharSequence>? = null

        val extractedText1 = when {
            extraBigText1.isNotBlank() -> extraBigText1
            extraText1.isNotBlank() -> extraText1
            else -> extraText1
        }

        val parsedResult1 = NotificationParser.parse(
            packageName = gmailPackage,
            title = extraTitle1,
            text = extractedText1,
            postTime = System.currentTimeMillis()
        )

        // Scenario 2: Realistic Gmail Notification where EXTRA_BIG_TEXT is null/empty, EXTRA_TEXT has standard Gmail snippet
        val extraTitle2 = "canarabank@canarabank.com"
        val extraText2 = "Dear Customer, Thanking you for banking with Canara Bank. An amount of INR 104.00 has been DEBITED on 23/09/26 from your account XXX528 to THIRUCHENDUR with UPI Ref No.:663206421839..."
        val extraBigText2 = ""

        val extractedText2 = when {
            extraBigText2.isNotBlank() -> extraBigText2
            extraText2.isNotBlank() -> extraText2
            else -> extraText2
        }

        val parsedResult2 = NotificationParser.parse(
            packageName = gmailPackage,
            title = extraTitle2,
            text = extractedText2,
            postTime = System.currentTimeMillis()
        )

        // Scenario 3: Bundled/Summary Gmail Notification (InboxStyle using EXTRA_TEXT_LINES)
        val extraTitle3 = "Canara Bank"
        val extraText3 = "2 new messages"
        val extraBigText3 = ""
        val extraTextLines3 = listOf(
            "An amount of INR 104.00 has been DEBITED on 23/09/26...",
            "Statement for account XXX528..."
        )

        val extractedText3 = when {
            extraBigText3.isNotBlank() -> extraBigText3
            extraText3.isNotBlank() -> extraText3
            else -> extraText3
        }

        val parsedResult3 = NotificationParser.parse(
            packageName = gmailPackage,
            title = extraTitle3,
            text = extractedText3,
            postTime = System.currentTimeMillis()
        )

        // Scenario 4: Gmail preview truncated before transaction details
        val extraTitle4 = "canarabank@canarabank.com"
        val extraText4 = "Dear Customer, Thanking you for banking with Canara Bank..."
        val extraBigText4 = ""

        val extractedText4 = when {
            extraBigText4.isNotBlank() -> extraBigText4
            extraText4.isNotBlank() -> extraText4
            else -> extraText4
        }

        val parsedResult4 = NotificationParser.parse(
            packageName = gmailPackage,
            title = extraTitle4,
            text = extractedText4,
            postTime = System.currentTimeMillis()
        )

        println("=== GMAIL NOTIFICATION EXTRACTION TEST ===")
        println("SCENARIO 1 (FULL EXTRA_BIG_TEXT):")
        println("EXTRA_TITLE = $extraTitle1")
        println("EXTRA_TEXT = $extraText1")
        println("EXTRA_BIG_TEXT = [${extraBigText1.length} chars]")
        println("EXTRA_TEXT_LINES = $extraTextLines1")
        println("TEXT ACTUALLY PASSED TO PARSER = [${extractedText1.length} chars]")
        println("PARSER RESULT = $parsedResult1")

        println("\nSCENARIO 2 (SNIPPET EXTRA_TEXT ONLY):")
        println("EXTRA_TITLE = $extraTitle2")
        println("EXTRA_TEXT = $extraText2")
        println("EXTRA_BIG_TEXT = \"\"")
        println("TEXT ACTUALLY PASSED TO PARSER = \"$extractedText2\"")
        println("PARSER RESULT = $parsedResult2")

        println("\nSCENARIO 3 (BUNDLED INBOXSTYLE / EXTRA_TEXT_LINES):")
        println("EXTRA_TITLE = $extraTitle3")
        println("EXTRA_TEXT = $extraText3")
        println("EXTRA_TEXT_LINES = $extraTextLines3")
        println("TEXT ACTUALLY PASSED TO PARSER = \"$extractedText3\"")
        println("PARSER RESULT = $parsedResult3")

        println("\nSCENARIO 4 (SHORT TRUNCATED EXTRA_TEXT):")
        println("EXTRA_TITLE = $extraTitle4")
        println("EXTRA_TEXT = $extraText4")
        println("TEXT ACTUALLY PASSED TO PARSER = \"$extractedText4\"")
        println("PARSER RESULT = $parsedResult4")
        println("==========================================")

        assertNotNull(parsedResult1)
    }

    @Test
    fun testTestNotificationEntityDataCapture() {
        // 1. WhatsApp notification (Non-bank)
        val rawTitleWhatsApp = "Mom"
        val rawTextWhatsApp = "Don't forget to buy milk"
        val parsedWhatsApp = NotificationParser.parse("com.whatsapp", rawTitleWhatsApp, rawTextWhatsApp, System.currentTimeMillis())

        val testEntityWhatsApp = com.example.expensetracker.data.local.TestNotificationEntity(
            id = java.util.UUID.randomUUID().toString(),
            receivedAt = System.currentTimeMillis(),
            packageName = "com.whatsapp",
            notificationId = 1,
            title = rawTitleWhatsApp,
            text = rawTextWhatsApp,
            status = if (parsedWhatsApp != null) "SUCCESS" else "FAIL",
            detectedPayment = parsedWhatsApp != null,
            failureReason = "Package 'com.whatsapp' is not supported for payment extraction"
        )

        assertEquals("FAIL", testEntityWhatsApp.status)
        assertEquals(false, testEntityWhatsApp.detectedPayment)
        assertEquals("Mom", testEntityWhatsApp.title)
        assertEquals("Don't forget to buy milk", testEntityWhatsApp.text)

        // 2. Successful Canara notification
        val rawTitleCanara = "UPI/IMPS/MB Transaction Alert"
        val rawTextCanara = "An amount of INR 104.00 has been DEBITED on 23/09/26 from your account XXX528 to THIRUCHENDUR with UPI Ref No.:663206421839. Total Available Balance INR 364.08."
        val parsedCanara = NotificationParser.parse(gmailPackage, rawTitleCanara, rawTextCanara, System.currentTimeMillis())

        assertNotNull(parsedCanara)
        val testEntityCanara = com.example.expensetracker.data.local.TestNotificationEntity(
            id = java.util.UUID.randomUUID().toString(),
            receivedAt = System.currentTimeMillis(),
            packageName = gmailPackage,
            notificationId = 2,
            title = rawTitleCanara,
            text = rawTextCanara,
            status = if (parsedCanara != null) "SUCCESS" else "FAIL",
            detectedPayment = parsedCanara != null,
            amount = parsedCanara!!.amount,
            transactionType = parsedCanara.type.name,
            merchant = parsedCanara.name,
            source = parsedCanara.source
        )

        assertEquals("SUCCESS", testEntityCanara.status)
        assertEquals(true, testEntityCanara.detectedPayment)
        assertEquals(104.00, testEntityCanara.amount!!, 0.001)
        assertEquals("THIRUCHENDUR", testEntityCanara.merchant)

        // 3. Verify non-deduplication of identical notifications
        val entity1 = testEntityCanara.copy(id = "id-1")
        val entity2 = testEntityCanara.copy(id = "id-2")

        assertTrue("Identical notifications must produce distinct test data entries", entity1.id != entity2.id)
        assertEquals(entity1.title, entity2.title)
        assertEquals(entity1.text, entity2.text)
    }

    @Test
    fun testCanaraDebitEmailParsing() {
        val emailBody = """
            UPI/IMPS/MB Transaction Alert
            Dear Customer,
            Thanking you for banking with Canara Bank.

            An amount of INR 1.00 has been DEBITED on 20/09/26 from your account XXX528 to KRISHNA KUMA with UPI Ref No.:6263222146179. Total Available Balance INR 593.08. To report fraud & stop further debit transaction, Call : 18001030.
        """.trimIndent()

        val parsed = NotificationParser.parse(
            packageName = gmailPackage,
            title = "canarabank",
            text = emailBody,
            postTime = System.currentTimeMillis()
        )

        assertNotNull(parsed)
        assertEquals(1.00, parsed!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("KRISHNA KUMA", parsed.name)
        assertEquals(TransactionSource.BANK_EMAIL.name, parsed.source)
    }

    @Test
    fun testCanaraCreditEmailParsing() {
        val emailBody = """
            UPI/IMPS/MB Transaction Alert
            Dear Customer,
            Thanking you for banking with Canara Bank.

            An amount of INR 500.00 has been CREDITED on 20/09/26 to your account XXX528 from RAMESH with UPI Ref No.:6263222146180. Total Available Balance INR 1093.08.
        """.trimIndent()

        val parsed = NotificationParser.parse(
            packageName = gmailPackage,
            title = "canarabank",
            text = emailBody,
            postTime = System.currentTimeMillis()
        )

        assertNotNull(parsed)
        assertEquals(500.00, parsed!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("RAMESH", parsed.name)
        assertEquals(TransactionSource.BANK_EMAIL.name, parsed.source)
    }

    @Test
    fun testBalanceMustNotBeParsed() {
        val textWithOnlyBalance = "Total Available Balance INR 593.08"

        val parsed = NotificationParser.parse(
            packageName = gmailPackage,
            title = "canarabank",
            text = textWithOnlyBalance,
            postTime = System.currentTimeMillis()
        )

        assertNull("Balance text alone should not be parsed as a transaction", parsed)
    }

    @Test
    fun testGPayIncomeAndExpenseParsing() {
        // GPay Income
        val incomeParsed = NotificationParser.parse(
            packageName = gpayPackage,
            title = "LOGESHWARAN S paid you ₹1.00",
            text = "Tap to view.",
            postTime = System.currentTimeMillis()
        )
        assertNotNull(incomeParsed)
        assertEquals(1.00, incomeParsed!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, incomeParsed.type)
        assertEquals("LOGESHWARAN S", incomeParsed.name)
        assertEquals(TransactionSource.GPAY.name, incomeParsed.source)

        // GPay Expense
        val expenseParsed = NotificationParser.parse(
            packageName = gpayPackage,
            title = "Paid ₹149 to KFC",
            text = "Tap to view.",
            postTime = System.currentTimeMillis()
        )
        assertNotNull(expenseParsed)
        assertEquals(149.00, expenseParsed!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, expenseParsed.type)
        assertEquals("KFC", expenseParsed.name)
        assertEquals(TransactionSource.GPAY.name, expenseParsed.source)
    }

    @Test
    fun testIDBIBankSmsParsing() {
        val smsText = "IDBI Bank Acct XX617 debited for Rs 1.00 on 20-Sep-26; Bal Rs 16599.27 ARAVINTH C M credited. UPI:662948348715. To Block UPI send SMS ..."

        val parsed = NotificationParser.parse(
            packageName = messagingPackage,
            title = "JD-IDBIBK-S",
            text = smsText,
            postTime = System.currentTimeMillis()
        )

        assertNotNull(parsed)
        assertEquals(1.00, parsed!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("ARAVINTH C M", parsed.name)
        assertEquals(TransactionSource.BANK_SMS.name, parsed.source)
    }

    @Test
    fun testTransferParsing() {
        val transferText = "Transfer of Rs 1,000.00 to Acct XX999 on 20-Sep-26"

        val parsed = NotificationParser.parse(
            packageName = messagingPackage,
            title = "JD-IDBIBK-S",
            text = transferText,
            postTime = System.currentTimeMillis()
        )

        assertNotNull(parsed)
        assertEquals(1000.00, parsed!!.amount, 0.001)
        assertEquals(TransactionType.TRANSFER, parsed.type)
        assertEquals(TransactionSource.BANK_SMS.name, parsed.source)
    }

    @Test
    fun testGPaySmsAndEmailDeduplication() {
        val now = System.currentTimeMillis()

        // 1. GPay Transaction
        val gpayParsed = NotificationParser.parse(gpayPackage, "Paid ₹1.00 to KRISHNA KUMA", "Tap to view", now)
        assertNotNull(gpayParsed)
        val gpayNormalized = TransactionNormalizer.normalize(gpayParsed!!, "Paid ₹1.00 to KRISHNA KUMA")
        val res1 = TransactionDeduplicator.process(gpayNormalized)
        assertTrue(res1 is DeduplicationResult.NewTransaction)

        // 2. Bank SMS Transaction (10 seconds later)
        val smsText = "IDBI Bank Acct XX617 debited for Rs 1.00 on 20-Sep-26; Bal Rs 16599.27 KRISHNA KUMA credited."
        val smsParsed = NotificationParser.parse(messagingPackage, "JD-IDBIBK-S", smsText, now + 10_000)
        assertNotNull(smsParsed)
        val smsNormalized = TransactionNormalizer.normalize(smsParsed!!, smsText)
        val res2 = TransactionDeduplicator.process(smsNormalized)
        assertTrue(res2 is DeduplicationResult.DuplicateTransaction)

        // 3. Bank Email Transaction (20 seconds later)
        val emailText = "UPI/IMPS/MB Transaction Alert\nDear Customer,\nAn amount of INR 1.00 has been DEBITED on 20/09/26 from your account XXX528 to KRISHNA KUMA with UPI Ref No.:6263222146179."
        val emailParsed = NotificationParser.parse(gmailPackage, "canarabank", emailText, now + 20_000)
        assertNotNull(emailParsed)
        val emailNormalized = TransactionNormalizer.normalize(emailParsed!!, emailText)
        val res3 = TransactionDeduplicator.process(emailNormalized)
        assertTrue(res3 is DeduplicationResult.DuplicateTransaction)

        // Verify confirmed sources include all 3
        val dupResult = res3 as DeduplicationResult.DuplicateTransaction
        assertEquals(3, dupResult.confirmedSources.size)
        assertTrue(dupResult.confirmedSources.contains(TransactionSource.GPAY.name))
        assertTrue(dupResult.confirmedSources.contains(TransactionSource.BANK_SMS.name))
        assertTrue(dupResult.confirmedSources.contains(TransactionSource.BANK_EMAIL.name))
    }

    @Test
    fun testTransactionNotificationHelperFormatting() {
        // Automatic Expense
        assertEquals("Expense added", com.example.expensetracker.service.TransactionNotificationHelper.buildNotificationTitle(TransactionType.EXPENSE))
        val msgAutoExpense = com.example.expensetracker.service.TransactionNotificationHelper.buildNotificationMessage(
            type = TransactionType.EXPENSE,
            amount = 80.0,
            name = "VALAA CAFE",
            source = TransactionSource.BANK_SMS.name
        )
        assertEquals("₹80 at VALAA CAFE", msgAutoExpense)

        // Manual Expense
        val msgManualExpense = com.example.expensetracker.service.TransactionNotificationHelper.buildNotificationMessage(
            type = TransactionType.EXPENSE,
            amount = 250.0,
            name = "Lunch",
            source = TransactionSource.MANUAL.name
        )
        assertEquals("₹250 for Lunch", msgManualExpense)

        // Empty Merchant Expense
        val msgEmptyExpense = com.example.expensetracker.service.TransactionNotificationHelper.buildNotificationMessage(
            type = TransactionType.EXPENSE,
            amount = 80.0,
            name = "",
            source = TransactionSource.BANK_SMS.name
        )
        assertEquals("₹80 recorded", msgEmptyExpense)

        // Income
        assertEquals("Income added", com.example.expensetracker.service.TransactionNotificationHelper.buildNotificationTitle(TransactionType.INCOME))
        val msgIncome = com.example.expensetracker.service.TransactionNotificationHelper.buildNotificationMessage(
            type = TransactionType.INCOME,
            amount = 500.0,
            name = "RAMESH",
            source = TransactionSource.BANK_EMAIL.name
        )
        assertEquals("₹500 from RAMESH", msgIncome)

        // Transfer
        assertEquals("Transfer recorded", com.example.expensetracker.service.TransactionNotificationHelper.buildNotificationTitle(TransactionType.TRANSFER))
        val msgTransfer = com.example.expensetracker.service.TransactionNotificationHelper.buildNotificationMessage(
            type = TransactionType.TRANSFER,
            amount = 1000.0,
            name = "",
            source = TransactionSource.BANK_SMS.name
        )
        assertEquals("₹1,000", msgTransfer)
    }
}
