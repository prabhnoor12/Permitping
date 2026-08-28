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
- Manage separate email/SMS reminder subscriptions with explicit consent evidence, local suppression controls, unsubscribe history, and provider opt-out enforcement.
- Create secure, expiring subcontractor upload requests; accept or reject submitted files from a review inbox before they become compliance evidence.
- Assign and unassign contractors from projects with readiness status and issue details.
- Project readiness tracking with Ready, At Risk, and Blocked states, plus configurable per-project requirement templates enforced against each assigned subcontractor.
- Global search across documents, profiles, projects, and assignments, with locally saved search filters.
- Configure reminder thresholds, snooze reminders, manually send pending reminders, and review delivery history.
- Administrator-only user and role management, including account activation, role assignment, and custom permissions.
- Sign out and re-authenticate without restarting the application; navigation validates that the signed-in account is still active.
- Optional SendGrid email and Twilio SMS delivery with sent, failed, and skipped tracking.
- CSV exports for active documents, expiring documents, and assignment readiness.
- Verified local backup bundles containing the SQLite database and managed documents, backup restore with safety copies, and backup activity history.
- Local audit history for backup operations and detailed reminder-delivery records including attempts, provider IDs, recipients, and failure details.

The current JavaFX workspace exposes Dashboard, All documents, Profiles, Assignments, Reminders, Archived, System settings, and Activity history according to the signed-in user's permissions. Global Search, Compliance reports, and reminder Delivery History are opened from their related workspace pages.

## Reminder delivery

Contractor notification is opt-in per profile. Edit a profile and enable **Allow reminder delivery**, then choose Email, SMS, both, or no notification. SMS phone numbers must use international E.164 format, such as `+15551234567`.

Set these environment variables before running the application:

```powershell
$env:PERMITPING_SENDGRID_API_KEY = "your-sendgrid-api-key"
$env:PERMITPING_FROM_EMAIL = "compliance@your-company.example"
$env:PERMITPING_SENDGRID_UNSUBSCRIBE_GROUP_ID = "12345"
$env:PERMITPING_TWILIO_ACCOUNT_SID = "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
$env:PERMITPING_TWILIO_AUTH_TOKEN = "your-twilio-auth-token"
$env:PERMITPING_TWILIO_FROM_NUMBER = "+15557654321"
$env:PERMITPING_UPLOAD_ENABLED = "true"
$env:PERMITPING_UPLOAD_BIND_ADDRESS = "127.0.0.1"
$env:PERMITPING_UPLOAD_PORT = "8765"
$env:PERMITPING_UPLOAD_BASE_URL = "https://compliance.your-company.example"
```

Create a Twilio account, obtain an Account SID and Auth Token, and verify or purchase the sending number shown in `PERMITPING_TWILIO_FROM_NUMBER`. The sending number and recipient numbers must be valid for your Twilio account and region. The application sends only to profiles with notification delivery enabled, a recorded subscription for the selected channel, the selected contact channel configured, and a document linked to that profile ID. The profile delivery toggle and channel subscription are separate safeguards. New or existing profiles are not automatically subscribed; a manager must record the recipient's express consent and its source in **Manage subscriptions**. Unchecking a channel suppresses it, and all subscribe/unsubscribe changes are retained in the local database and audit history.

SendGrid unsubscribe groups must be configured before email can be sent. PermitPing adds SendGrid's `%asm_group_unsubscribe_url%` tag and the configured `asm.group_id` to each email. Twilio SMS includes `Reply STOP to unsubscribe` and `Reply HELP for help`; a Twilio opt-out response (error 21610) is recorded as a local SMS unsubscribe. Email and SMS delivery attempts are tracked independently, so a failed SMS can retry without resending a successful email. Successful delivery is marked as sent; failures remain visible and retryable. Do not commit API keys or put them in the database. Persist the variables as Windows user environment variables if PermitPing will run through Task Scheduler.

These controls are product safeguards, not legal advice. The operator remains responsible for obtaining valid consent, preserving evidence, honoring applicable country/state rules, and configuring provider sender registration. PermitPing has no hosted inbound webhook, so an email unsubscribe performed in SendGrid is enforced by SendGrid but is not automatically copied into the local subscription table; use **Manage subscriptions** to keep the local record aligned. Twilio's provider opt-out remains authoritative for SMS.

## Subcontractor self-upload

The upload portal is disabled unless `PERMITPING_UPLOAD_ENABLED` is `true`. In the Assignments page, select an approved subcontractor and choose **Request documents**. PermitPing creates a per-request random token; only its SHA-256 hash is stored in SQLite. Links expire after 1–90 days, can be revoked, accept only PDF/image/Word files up to 10 MB, and never expose the local database path. Uploaded files enter **Review uploads** as pending and do not affect clearance until a reviewer accepts them and supplies the expiration date.

The default bind address is loopback for safety. To let an external subcontractor reach the portal, deploy the application on a reachable host, set `PERMITPING_UPLOAD_BIND_ADDRESS` appropriately, set `PERMITPING_UPLOAD_BASE_URL` to the public HTTPS URL, and place TLS termination, firewall rules, authentication/network controls, and rate limiting in front of the embedded endpoint. The embedded endpoint is an intake component, not a production cloud hosting service.

## Run locally on Windows

Requirements: JDK 17+ and Maven 3.9+.

From PowerShell:

```powershell
.\scripts\setup.ps1
.\scripts\test.ps1
.\scripts\run.ps1
.\scripts\install-startup-task.ps1
```

`install-startup-task.ps1` registers a per-user Windows Task Scheduler task that launches PermitPing at logon. It uses the current project directory and the existing `run.ps1` launcher, and does not require administrator elevation. Remove it with:

```powershell
.\scripts\remove-startup-task.ps1
```

The startup task runs only when the configured Windows user signs in. Keep the project directory and Java/Maven installation available to that user. If provider variables were set only in a temporary PowerShell session, the scheduled task will not see them; use persistent Windows user environment variables instead.

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
domain/         Documents, profiles, assignments, readiness, reminders, upload requests, delivery records, users, and roles
application/    Services, validation, delivery boundaries, repository contracts
infrastructure/ SQLite repositories, backups, file storage, upload portal, SendGrid integration
```

`App` is the composition root. `MainView` assembles the workspace, navigation, reminder delivery service, subscription service, SendGrid adapter, and Twilio adapter. Application services own validation and business rules; repositories isolate SQLite; UI pages do not issue SQL directly. `ReminderDeliveryService` coordinates profile eligibility, subscription enforcement, message creation, independent email/SMS provider calls, channel-specific idempotency, and delivery history.

## Database migrations

SQLite schema upgrades run automatically through `PRAGMA user_version`. Existing databases receive new columns and tables without being recreated. Current migrations include archived records, profile IDs for document holders, notification preferences, reminder delivery history, authentication users, persisted roles, per-channel notification subscriptions with append-only subscription events, and upload requests/submissions. The initial schema also includes document versions, requirement templates, project-template assignments, reminder settings, and assignment storage.

Create a verified backup bundle before manually changing or moving the database or managed files. Restoring a bundle keeps safety copies of the previous database and document directory next to the active data.

## Testing

Run:

```powershell
mvn test
```

The suite covers document validation and expiry rules, profile validation and lifecycle behavior, assignments and project readiness, per-project clearance rules, subcontractor upload token and review flows, reminders and delivery eligibility, subscription restrictions and history, authentication and password rules, document versioning, file storage, exports, SendGrid and Twilio integration, JavaFX UI behavior, stale-record protection, and SQLite persistence. The current suite contains 80 passing tests.

## Operational notes

- Local in-app reminder alerts do not mark reminders as delivered.
- Email reminders are attempted on startup, every 15 minutes while the application is open, and manually from the Reminders page when delivery is configured.
- Delivery history records recipient, status, timestamp, provider ID, and failure details.
- A subcontractor assignment is only shown as Cleared when its assignment is approved and its project evidence satisfies the assigned requirement template. Missing required evidence, expired evidence, expiring evidence, and missing files are explained directly in the clearance details.
- A local in-app alert is only a local alert; it is never treated as contractor delivery.
- A successful backup restore stops reminder work and returns to sign-in so the restored database and permissions are loaded into a fresh workspace.
- Reminder checks run while PermitPing is open; the optional Windows startup task launches the app at user logon so those checks can begin automatically.
- Activity History currently records backup-related audit events; reminder delivery has its own detailed history page. Users with audit permission can open Activity history directly from the main navigation.
- PermitPing now requires a local sign-in before opening the workspace; the first launch creates the initial administrator account. Role permissions control workspace navigation and mutation controls. Built-in roles, custom roles, and permission checks are implemented in the domain/application and SQLite layers, with administrator user-management screens available from System settings. Users can change their own password, and administrators can reset another user's password.
- The application currently has no hosted cloud API, encrypted cloud storage, templated HTML email, provider webhook synchronization, or provider retry queue. The optional embedded upload endpoint must be fronted by an HTTPS-capable deployment boundary before external use.

## License

Private MVP. Add a commercial license before distributing to customers.
