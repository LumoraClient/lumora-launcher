; Inno Setup 6 script for the Lumora Client launcher.
; Built by `gradlew :launcher-app:installerExe` (passes AppVersion, SourceDir, RepoRoot).
; Per-user install (no administrator rights required).

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef SourceDir
  #error SourceDir (jpackage app image) must be defined
#endif
#ifndef RepoRoot
  #define RepoRoot "..\.."
#endif

[Setup]
AppId={{6F1C2B9E-6A55-4C1E-9B0A-4C7B1E3A9D21}
AppName=Lumora Client
AppVersion={#AppVersion}
AppPublisher=Lumora Client contributors
DefaultDirName={localappdata}\Programs\LumoraClient
DefaultGroupName=Lumora Client
PrivilegesRequired=lowest
DisableProgramGroupPage=yes
OutputBaseFilename=LumoraClient-Setup-{#AppVersion}
SetupIconFile={#RepoRoot}\packaging\icons\lumora.ico
UninstallDisplayIcon={app}\LumoraLauncher.exe
Compression=lzma2
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
LicenseFile={#RepoRoot}\LICENSE
WizardStyle=modern

[Languages]
Name: "german"; MessagesFile: "compiler:Languages\German.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion
Source: "{#RepoRoot}\packaging\windows\README.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#RepoRoot}\docs\THIRD_PARTY_NOTICES.md"; DestDir: "{app}\licenses"; DestName: "THIRD_PARTY_NOTICES.txt"; Flags: ignoreversion
Source: "{#RepoRoot}\LICENSE"; DestDir: "{app}\licenses"; DestName: "LICENSE-GPL-3.0.txt"; Flags: ignoreversion
Source: "{#RepoRoot}\packaging\licenses\*"; DestDir: "{app}\licenses"; Flags: ignoreversion

[Icons]
Name: "{group}\Lumora Client"; Filename: "{app}\LumoraLauncher.exe"
Name: "{group}\Lumora Client deinstallieren"; Filename: "{uninstallexe}"
Name: "{autodesktop}\Lumora Client"; Filename: "{app}\LumoraLauncher.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\LumoraLauncher.exe"; Description: "{cm:LaunchProgram,Lumora Client}"; Flags: nowait postinstall skipifsilent
