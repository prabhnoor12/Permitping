# PermitPing

PermitPing is a local JavaFX workspace for construction companies managing licenses, insurance certificates, OSHA cards, permits, and subcontractor compliance documents.

## Current capabilities

- Workspace areas include Dashboard, Documents, Profiles, Assignments, Reminders, Archived records, Reports, Activity History, and System Settings.
- Add, edit, renew, archive, restore, and delete documents.
- Document status calculation: Current, Expiring soon, or Expired.
- Search, project filtering, quick expiry filters, missing-file detection, duplicate detection, and multi-selection.
- Three-step document form with inline validation, date shortcuts, file validation, and expired-date warnings.
- Split document details panel with file, version history, archive, renew, and edit actions.
- Manage profiles with edit, archive, restore, delete, contact validation, detail summaries, and notification preferences.
- Assign and unassign contractors from projects with readiness status and issue details.
- Project readiness tracking with Ready, At Risk, and Blocked states, plus configurable requirement templates for project document types.
- Global search across documents, profiles, projects, and assignments, with locally saved search filters.
- Configure reminder thresholds, snooze reminders, manually send pending reminders, and review delivery history.
- Administrator-only user and role management, including account activation, role assignment, and custom permissions.
- Sign out and re-authenticate without restarting the application; navigation validates that the signed-in account is still active.
- Optional SendGrid email delivery with sent, failed, and skipped tracking.
- CSV exports for active documents, expiring documents, and assignment readiness.
- Verified local backup bundles containing the SQLite database and managed documents, backup restore with safety copies, and backup activity history.
- Local audit history for backup operations and detailed reminder-delivery records including attempts, provider IDs, recipients, and failure details.

The current JavaFX workspace exposes Dashboard, All documents, Profiles, Assignments, Reminders, Archived, System settings, and Activity history according to the signed-in user's permissions. Global Search, Compliance reports, and reminder Delivery History are opened from their related workspace pages.

## Reminder delivery

Contractor notification is opt-in per profile. Edit a profile and enable **Allow reminder delivery**, then choose Email, SMS, both, or no notification. SMS is currently stored as a preference but is not yet connected to an SMS provider; email delivery is supported through SendGrid.

Set these environment variables before running the application:

```powershell
$env:PERMITPING_SENDGRID_API_KEY = "your-sendgrid-api-key"
$env:PERMITPING_FROM_EMAIL = "compliance@your-company.example"
```

The application sends only to profiles with notification delivery enabled, an email channel selected, a valid email address, and a document linked to that profile ID. Successful delivery is marked as sent; failures remain visible and retryable. Do not commit API keys or put them in the database.

## Run locally on Windows

Requirements: JDK 17+ and Maven 3.9+.

From PowerShell:

```powershell
.\scripts\setup.ps1
.\scripts\test.ps1
.\scripts\run.ps1
```

Equivalent Maven commands:

```powershell
mvn clean test
mvn javafx:run
```

The project configures the Maven compiler to fork `javac`. This is important on the current Windows/JDK setup because JavaFX dependency compilation can otherwise fail with file-access errors.

The database is created at `data/permitping.db`, managed files are stored under `data/documents/`, and portable backup bundles are stored under `data/backups/`. New backups use `.zip` bundles containing both the database and managed files; older `.db` database-only backups can still be restored. These runtime files are ignored by Git.

## Architecture

```text
ui/             JavaFX pages, dialogs, controls, and notifications
domain/         Documents, profiles, assignments, readiness, reminders, delivery records, users, and roles
application/    Services, validation, delivery boundaries, repository contracts
infrastructure/ SQLite repositories, backups, file storage, SendGrid integration
```

`App` is the composition root. `MainView` assembles the workspace, navigation, reminder delivery service, and SendGrid adapter. Application services own validation and business rules; repositories isolate SQLite; UI pages do not issue SQL directly. `ReminderDeliveryService` coordinates profile eligibility, message creation, provider calls, idempotency, and delivery history.

## Database migrations

SQLite schema upgrades run automatically through `PRAGMA user_version`. Existing databases receive new columns and tables without being recreated. Current migrations include archived records, profile IDs for document holders, notification preferences, reminder delivery history, authentication users, and persisted roles. The initial schema also includes document versions, requirement templates, project-template assignments, reminder settings, and assignment storage.

Create a verified backup bundle before manually changing or moving the database or managed files. Restoring a bundle keeps safety copies of the previous database and document directory next to the active data.

## Testing

Run:

```powershell
mvn test
```

The suite covers document validation and expiry rules, profile validation and lifecycle behavior, assignments and project readiness, reminders and delivery eligibility, authentication and password rules, document versioning, file storage, exports, SendGrid integration, JavaFX UI behavior, stale-record protection, and SQLite persistence. The current suite contains 68 passing tests.

## Operational notes

- Local in-app reminder alerts do not mark reminders as delivered.
- Email reminders are attempted on startup, every 15 minutes while the application is open, and manually from the Reminders page when delivery is configured.
- Delivery history records recipient, status, timestamp, provider ID, and failure details.
- A local in-app alert is only a local alert; it is never treated as contractor delivery.
- A successful backup restore stops reminder work and returns to sign-in so the restored database and permissions are loaded into a fresh workspace.
- No SMS provider or Windows startup scheduler is connected yet; background checks run while PermitPing is open.
- Activity History currently records backup-related audit events; reminder delivery has its own detailed history page. Users with audit permission can open Activity history directly from the main navigation.
- PermitPing now requires a local sign-in before opening the workspace; the first launch creates the initial administrator account. Role permissions control workspace navigation and mutation controls. Built-in roles, custom roles, and permission checks are implemented in the domain/application and SQLite layers, with administrator user-management screens available from System settings. Users can change their own password, and administrators can reset another user's password.
- The application currently has no hosted API, encrypted cloud storage, templated HTML email, unsubscribe workflow, or provider retry queue.

## License

Private MVP. Add a commercial license before distributing to customers.
