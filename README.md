# Local Expense Tracker 📱💸

An intelligent, privacy-first Android expense tracking application built with Kotlin and Jetpack Compose. It automatically reconciles expenses from transaction SMS notifications and categorizes them using on-device parsing.

> [!NOTE]
> **AI Generated Code Disclaimer:**
> The code and architecture in this repository were generated with the assistance of AI. Please review, test, and adapt the code to your specific requirements before deploying to production.

---

## ✨ Features

- **🔒 Local & Privacy-First:** All expense records, bill data, and SMS parsing happen locally on-device. No personal financial data is shared with external third-party servers.
- **📩 Smart SMS Parsing & Reconciliation:** Extracts transaction details (amount, vendor, category, timestamp) from banking and transaction SMS alerts automatically.
- **🤖 On-Device AI / Heuristic Expense Parser:** Categorizes transactions and parses complex messaging templates locally.
- **🔔 Bill & Expense Reminders:** Built-in scheduler and alarm receivers to remind you of recurring bills and daily budget check-ins.
- **📊 Modern UI with Jetpack Compose:** Clean, reactive, Material 3 design with intuitive navigation and expense summaries.
- **💾 Room Persistence Library:** Robust offline SQLite database with Room DAOs for high performance and integrity.

---

## 🛠️ Tech Stack & Architecture

- **Language:** Kotlin
- **UI Toolkit:** Jetpack Compose (Material 3)
- **Architecture:** MVVM (Model-View-ViewModel) with Android Architecture Components
- **Database:** Room Database (SQLite)
- **Dependency / Processing:** Kotlin Symbol Processing (KSP)
- **Asynchronous & Flow:** Kotlin Coroutines & StateFlow
- **Background Tasks:** Android AlarmManager & BroadcastReceivers

---

## 📁 Project Structure

```text
app/src/main/java/com/expensetracker/
├── ai/
│   └── LocalExpenseParser.kt       # On-device SMS/expense parsing logic
├── data/
│   ├── AppDatabase.kt              # Room database configuration
│   ├── BillDao.kt / BillEntity.kt  # Bill entity & DAO definitions
│   └── ExpenseDao.kt / ExpenseEntity.kt # Expense entity & DAO definitions
├── reminder/
│   ├── DailyReminderReceiver.kt    # Broadcast receiver for scheduled reminders
│   └── ReminderScheduler.kt        # AlarmManager scheduling helper
├── sms/
│   └── SmsReconciler.kt            # Transaction SMS reconciliation
└── ui/
    ├── ExpenseTrackerApp.kt        # Jetpack Compose UI screens & navigation
    ├── ExpenseViewModel.kt         # UI state management and business logic
    └── MainActivity.kt             # Application entry point
```

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio:** Hedgehog (2023.1.1) or newer
- **JDK:** Version 17
- **Target SDK:** Android 34 (Android 14)
- **Min SDK:** Android 26 (Android 8.0 Oreo)

### Build & Run

1. Clone the repository:
   ```bash
   git clone https://github.com/shijinmathew012/Local-Expense-Tracker.git
   cd Local-Expense-Tracker
   ```
2. Open the project in **Android Studio**.
3. Let Gradle sync project dependencies.
4. Select an emulator or connected Android device with Developer Mode & USB Debugging enabled.
5. Click **Run (`Shift + F10`)**.

---

## 📄 License

This project is open-source under the [MIT License](LICENSE).
