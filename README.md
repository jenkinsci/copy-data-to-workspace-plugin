# Copy Data to Workspace Plugin

[![Build Status](https://ci.jenkins.io/job/Plugins/job/copy-data-to-workspace-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/copy-data-to-workspace-plugin/job/master/) [![Jenkins Security Scan](https://github.com/jenkinsci/badge-plugin/actions/workflows/jenkins-security-scan.yml/badge.svg)](https://github.com/jenkinsci/badge-plugin/actions/workflows/jenkins-security-scan.yml) [![Codecov Coverage](https://codecov.io/gh/jenkinsci/copy-data-to-workspace-plugin/branch/master/graph/badge.svg)](https://codecov.io/gh/jenkinsci/copy-data-to-workspace-plugin)

[![Installations](https://img.shields.io/jenkins/plugin/i/copy-data-to-workspace-plugin.svg?color=blue&label=installations)](https://stats.jenkins.io/pluginversions/copy-data-to-workspace-plugin.html) [![Contributors](https://img.shields.io/github/contributors/jenkinsci/copy-data-to-workspace-plugin.svg?color=blue)](https://github.com/jenkinsci/copy-data-to-workspace-plugin/graphs/contributors) [![Release](https://img.shields.io/github/release/jenkinsci/copy-data-to-workspace-plugin.svg?label=Release)](https://github.com/jenkinsci/copy-data-to-workspace-plugin/releases/latest)

## Introduction
This Jenkins plugin copies data from a specified folder within `$JENKINS_HOME/userContent` directory to the project workspace before each build.

## Features

- Copy files and directories recursively from `$JENKINS_HOME/userContent` to workspace
- Optional: Make files executable on Unix/Linux systems (chmod 0755)
- Optional: Automatic cleanup after build completion
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

- Jenkins 2.479.3 or newer
- Java 17 or newer