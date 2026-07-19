# Privacy

DexWorkspaceTouch has no account system, analytics, advertising, cloud synchronization, or crash-reporting SDK.

Workspace definitions are stored locally in the app's Room database. Android Auto Backup and device-to-device backup are disabled for the beta. Workspace content leaves the app only when the user explicitly chooses Share or Save. Imported files are read only after the user selects or shares them with DexWorkspaceTouch.

The app queries launcher activities so the App Picker can show launchable applications. It does not store Drawable or Bitmap app icons in the domain or database; the icon cache is RAM-only. Temporary export files are stored in the app cache and are subject to cleanup.

DexWorkspaceTouch does not intentionally transmit workspace data, installed-app lists, or device identifiers to a remote service.
