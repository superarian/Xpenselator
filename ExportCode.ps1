# --- CONFIGURATION ---
$projectName = "Xpenselator"
$outputFile = "$HOME\Desktop\Xpenselator_FULL_CODE.txt"

# Get the path to the project (Assuming it's in the standard Android Studio folder)
$projectPath = "$HOME\StudioProjects\$projectName"

# --- LOGIC ---
$content = "PROJECT EXPORT: $projectName`nDate: $(Get-Date)`n"
$content += "=========================================`n`n"

# Function to read and append files
function Append-File($path, $label) {
    if (Test-Path $path) {
        $fileContent = Get-Content $path -Raw
        return "`n`n--- START OF FILE: $label ---`n$fileContent`n--- END OF FILE: $label ---`n"
    } else {
        return "`n`n!!! FILE NOT FOUND: $label !!!`n"
    }
}

# 1. FIXED: Find ALL Kotlin files (Database, Entity, DAO, MainActivity)
# Using "*.kt" ensures we grab the missing database files automatically.
$ktFiles = Get-ChildItem -Path "$projectPath\app\src\main\java" -Filter "*.kt" -Recurse
foreach ($file in $ktFiles) {
    # Dynamically use the file's actual name for the label
    $content += Append-File $file.FullName $file.Name
}

# 2. Get AndroidManifest.xml
$manifestPath = "$projectPath\app\src\main\AndroidManifest.xml"
$content += Append-File $manifestPath "AndroidManifest.xml"

# 3. Get All Layout XMLs (activity_main.xml, etc.)
$layoutFiles = Get-ChildItem -Path "$projectPath\app\src\main\res\layout" -Filter "*.xml"
foreach ($file in $layoutFiles) {
    $content += Append-File $file.FullName $file.Name
}

# 4. Get Build.Gradle (Module Level - to check plugins/namespace)
$gradlePath = "$projectPath\app\build.gradle.kts"
if (-not (Test-Path $gradlePath)) { $gradlePath = "$projectPath\app\build.gradle" } # Try non-kts too
$content += Append-File $gradlePath "build.gradle (App Level)"

# --- SAVE OUTPUT ---
Set-Content -Path $outputFile -Value $content
Write-Host "✅ Success! Code exported to: $outputFile"
Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")