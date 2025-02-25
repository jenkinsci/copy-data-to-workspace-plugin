# Copy Data to Workspace Plugin

This Jenkins plugin copies data from a specified folder within `$JENKINS_HOME/userContent` directory to the project workspace before each build.

## Features

- Copy files and directories recursively from `$JENKINS_HOME/userContent` to workspace
- Make files executable on Unix/Linux systems (chmod 0755)
- Optional automatic cleanup after build completion
- Path validation for security
- Cross-platform support (Windows/Linux)

## Usage

1. Place your files in a subdirectory of `$JENKINS_HOME/userContent`
2. Configure the plugin in your job:
   - Enter the relative path to your data folder
   - Optionally enable "Make files executable"
   - Optionally enable "Delete files after build"

### Example

If your Jenkins home is `C:\Jenkins` and you want to copy files from `C:\Jenkins\userContent\data\project1`, enter `data\project1` in the plugin path configuration.

### Notes

- Files are copied before the build starts
- Executable permissions (0755) are set only on Unix/Linux systems
- When deletion is enabled, files are removed after build completion
- Ensure build artifacts are not in the copied files list if you need to preserve them

## Requirements

- Java 11 or newer