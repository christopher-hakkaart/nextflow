# Language server

This page describes common errors and strategies strategies to resolve language server error.

## Stop and restart

In the event of an error, you can stop or restart the language server from the Command Palette. The following commands are available:

- Nextflow: Stop language server
- Nextflow: Restart language server

## Debug logs

Logs can be viewed in VS Code to help debug the language server.

To view logs in VS Code:

1. Open the **Output** tab in your console.
2. Select **Nextflow Language Server** from the dropdown.

To show additional log messages in VS Code:

1. Open the **Extensions** view in the left-hand menu.
2. Select the **Nextflow** extension.
3. Select the **Manage** icon.
3. Enable **Nextflow > Debug** in the extension settings.

## Filesystem changes

The language server does not detect certain filesystem changes. For example, changing the current Git branch. Restart the language server from the command palette to sync it with your workspace. See [Stop and restart](#stop-and-restart) for more information.

## Groovy scripts

The language server provides limited support for Groovy scripts in the lib directory. Errors in Groovy scripts are not reported as diagnostics, and changing a Groovy script does not automatically re-compile the Nextflow scripts that reference it. Edit or close and re-open the Nextflow script to refresh the diagnostics.

## Report an issue

Report issues at [`nextflow-io/vscode-language-nextflow`](https://github.com/nextflow-io/vscode-language-nextflow) or [`nextflow-io/language-server`](https://github.com/nextflow-io/language-server).

When reporting issues, include a minimal code snippet that reproduces the issue and any error logs from the server.
