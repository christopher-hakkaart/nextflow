(outputs-page)=

# Output directives

Workflow outputs declare and publish the outputs of your workflow.

The following directives are available for outputs in the output block:

`index`
: Index file containing a record of each published value.

  The following directives are available in an index definition:

  `header`
  : When `true`, the keys of the first record are used as the column names. Can also be a list of column names. Only used for CSV files.

  `path`
  : Name of the index file relative to the base output directory (required). Can be a CSV, JSON, or YAML file.

  `sep`
  : Character used to separate values (default: `','`). Only used for CSV files.

`label`
: Label to be applied to every published file. Can be specified multiple times.

`path`
: Publish path relative to the output directory (default: `'.'`). Can be a path, a closure that defines a custom directory for each published value, or a closure that publishes individual files using the `>>` operator.

`contentType`
: *Currently only supported for S3*
: Media type, also known as [MIME type](https://developer.mozilla.org/en-US/docs/Web/HTTP/MIME_types), of published files. Can be a string (e.g. `'text/html'`), or `true` to infer the content type from the file extension. Default: `false`.

`enabled`
: When `true`, enable publishing. Default: `true`.

`ignoreErrors`
: When `true`, the workflow will not fail if a file can't be published for some reason. Default: `false`.

`mode`
: The file publishing method. Default: `'symlink'`.

: The following options are available:

  `'copy'`
  : Copy each file into the output directory.

  `'copyNoFollow'`
  : Copy each file into the output directory without following symlinks, i.e., only the link is copied.

  `'link'`
  : Create a hard link in the output directory for each file.

  `'move'`
  : Move each file into the output directory.
  : Should only be used for files which are not used by downstream processes in the workflow.

  `'rellink'`
  : Create a relative symbolic link in the output directory for each file.

  `'symlink'`
  : Create an absolute symbolic link in the output directory for each output file.

`overwrite`
: When `true`, any existing file in the specified folder will be overwritten. Default: `'standard'`.

: The following options are available:

  `false`
  : Never overwrite existing files.

  `true`
  : Always overwrite existing files.

  `'deep'`
  : Overwrite existing files when the file content is different.

  `'lenient'`
  : Overwrite existing files when the file size is different.

  `'standard'`
  : Overwrite existing files when the file size or last modified timestamp is different.

`storageClass`
: *Only supported for S3*
: Storage class for published files.

`tags`
: *Only supported for S3*
: Arbitrary tags for published files.
