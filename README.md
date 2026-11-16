# Local Expense Tracker 📱💸

An intelligent, privacy-first Android personal finance application built with Kotlin and Jetpack Compose. It automatically reconciles expenses from transaction SMS notifications, tracks receivables/debts, categorizes transactions via an on-device heuristic engine or an experimental on-device Local LLM, and provides instant CSV data exports compatible with Microsoft Excel.

> [!NOTE]
> **AI-Generated Code Disclaimer:**
> The code, architecture, and documentation in this repository were generated with the assistance of AI. Please review, test, and adapt the code to your specific requirements before deploying to production.

---

## ✨ Features

- **🔒 100% Local & Privacy-First:** All financial records, SMS parsing, and AI models execute strictly offline on-device. No financial data is sent to external cloud servers.
- **🤖 Dual Parsing Engine (Heuristic + Experimental Local LLM):**
  - **Standard Mode:** Fast, zero-battery-drain regex and keyword classification (e.g. Milk ➔ Groceries, Bus/Taxi ➔ Transportation, Swiggy/Coffee ➔ Food & Dining).
  - **Experimental Local LLM Mode:** Dynamically downloads an ultra-compact on-device model weights file to the app's internal private storage (`/data/user/0/.../files/models/`) on first launch or when toggled, keeping the initial APK lightweight.
- **📩 Smart Bank & UPI SMS Reconciliation:**
  - Extracts amounts, merchants, and dates from transaction SMS.
  - Automatically identifies transaction type (**Expense** vs **Income / Salary / Refund**).
  - Identifies account type (**Bank Account** vs **Credit Card** vs **UPI**).
  - Configurable lookback period (2, 7, 14, 30 days) in Settings.
- **🤝 Receivables & Lending Management (Khaata):**
  - Track money lent to friends or pending reimbursements separately from your core expenses.
  - Visual summary of total receivables with one-tap "Settled ✓" reconciliation.
- **📊 CSV Data Export (Excel / Google Sheets Compatible):**
  - One-click export to standard `.csv` shared via Android system intent (opens natively in Microsoft Excel, Google Sheets, or can be saved to Downloads).
  - 0 KB APK overhead (no heavy Apache POI dependencies).
- **💱 Multi-Currency Display:** Switch easily between **INR (₹)**, **USD ($)**, **EUR (€)**, **GBP (£)**, and **AED (د.إ)** in Settings.
- **🔔 Daily 9:00 PM Briefing & Bill Reminders:** Scheduled alarm receiver and notifications for pending bill payments and daily budget review.
- **⚙️ Dedicated Settings Screen:** Centralized dashboard for LLM toggle, currency selection, SMS lookback days, lending manager, and data exports.

---

## 🏗️ Architecture & Unit Testing

- **Architecture:** Clean MVVM (Model-View-ViewModel) with Android Architecture Components.
- **Database:** Room Persistence Library (SQLite) with auto-migration.
- **UI:** 100% Jetpack Compose with Material Design 3.
- **Unit Testing:**
  - `LocalExpenseParser` is structured as a pure Kotlin class decoupled from the Android framework, allowing fast JUnit tests (`LocalExpenseParserTest.kt`) to verify parsing and categorization logic without requiring an emulator.

---

## 📋 TODO & Future Roadmap

- [ ] **Google Drive Auto-Backup:** Enable automated encrypted database backup and sync to user's private Google Drive AppData folder.
- [ ] **Receipt & Bill OCR Scanner:** On-device camera/gallery bill scanning using Google ML Kit Text Recognition combined with the local parser for instant receipt extraction.
- [ ] **Interactive Visual Analytics:** Animated spending charts, monthly trend lines, and category distribution pie charts.
- [ ] **Budget Limits & Alerts:** Custom category-wise monthly budgets with alert notifications when approaching limits.
- [ ] **Google Play Store Production Release:**
  - Generate production `.jks` release keystore and configure GitHub Actions release pipeline.
  - Implement Google Play SMS Policy declaration or transition to SMS User Consent / Retriever API for simplified store approval.
  - Privacy policy hosted page and Play Console listing assets.

---

## 🚀 Building & Running

### Automated CI/CD
This repository includes a GitHub Actions workflow (`.github/workflows/build-apk.yml`) that automatically compiles and packages a **Debug APK** artifact on every push to `main`. Download the APK directly from the **Actions** tab on GitHub!

### Local Build
```bash
# Clone the repository
git clone https://github.com/shijinmathew012/Local-Expense-Tracker.git
cd Local-Expense-Tracker

# Build debug APK with Gradle
./gradlew assembleDebug
```
The compiled APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 📄 License

This project is open-source under the [MIT License](LICENSE).
