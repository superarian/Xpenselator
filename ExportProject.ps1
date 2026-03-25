# Set the output file name
$outputFile = "Xpenselator_Full_Source.txt"
$sourcePath = "app/src/main"

# Clear the file or create it
Set-Content -Path $outputFile -Value "--- XPENSELATOR FULL EXPORT ---`n"

# Get all Kotlin and XML files
$files = Get-ChildItem -Path $sourcePath -Include *.kt, *.xml -Recurse

foreach ($file in $files) {
    # Skip build folders
    if ($file.FullName -like "*\build\*") { continue }

    # Create a nice header for each file
    $header = "`n################################################`n"
    $header += "FILE: " + $file.Name + "`n"
    $header += "PATH: " + $file.FullName + "`n"
    $header += "################################################`n"
    
    Add-Content -Path $outputFile -Value $header
    
    # Read the file content and append it
    Get-Content $file.FullName | Add-Content -Path $outputFile
}

Write-Host "Success! Check for Xpenselator_Full_Source.txt" -ForegroundColor Green
Write-Host "Press any key to close..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")