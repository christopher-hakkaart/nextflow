(config-page)=

# Configuration

## Configuration file

When a pipeline script is launched, Nextflow looks for configuration files in multiple locations. Since each configuration file may contain conflicting settings, they are applied in the following order (from lowest to highest priority):

1. The config file `$HOME/.nextflow/config` (or `$NXF_HOME/config` when {ref}`NXF_HOME <nxf-env-vars>` is set).
2. The config file `nextflow.config` in the project directory
3. The config file `nextflow.config` in the launch directory
4. Config files specified using the `-c <config-files>` option

:::{tip}
You can alternatively use the `-C <config-file>` option to specify a fixed set of configuration files and ignore all other files.
:::

(config-syntax)=

## Syntax

The Nextflow configuration syntax is based on the Nextflow script syntax. It is designed for setting configuration options in a declarative manner while also allowing for dynamic expressions where appropriate.

A Nextflow config file may consist of any number of *assignments*, *blocks*, and *includes*. Config files may also contain comments in the same manner as scripts.

See {ref}`syntax-page` for more information about the Nextflow script syntax.

### Assignments

A config assignment consists of a config option and an expression separated by an equals sign:

```groovy
workDir = 'work'
docker.enabled = true
process.maxErrors = 10
```

A config option consists of an *option name* prefixed by any number of *scopes* separated by dots. Config scopes are used to group related config options. See {ref}`config-options` for the full set of config options.

The expression is typically a literal value such as a number, boolean, or string. However, any expression can be used:

```groovy
params.helper_file = "${projectDir}/assets/helper.txt"
```

### Blocks

A config scope can also be specified as a block, which may contain multiple configuration options. For example:

```groovy
// dot syntax
docker.enabled = true
docker.runOptions = '-u $(id -u):$(id -g)'

// block syntax
docker {
    enabled = true
    runOptions = '-u $(id -u):$(id -g)'
}
```

As a result, deeply nested config options can be assigned in various ways. For example, the following three assignments are equivalent:

```groovy
executor.retry.maxAttempt = 5

executor {
    retry.maxAttempt = 5
}

executor {
    retry {
        maxAttempt = 5
    }
}
```

### Includes

A configuration file can include any number of other configuration files using the `includeConfig` keyword:

```groovy
process.executor = 'sge'
process.queue = 'long'
process.memory = '10G'

includeConfig 'path/extra.config'
```

Relative paths are resolved against the location of the including file.

:::{note}
Config includes can also be specified within config blocks. However, config files should only be included at the top level or in a [profile](#config-profiles) so that the included config file is valid on its own and in the context in which it is included.
:::

## Constants

The following constants are globally available in a Nextflow configuration file:

`baseDir: Path`
: :::{deprecated} 20.04.0
  :::
: Alias for `projectDir`.

`launchDir: Path`
: The directory where the workflow was launched.

`projectDir: Path`
: The directory where the main script is located.

`secrets: Map<String,String>`
: Map of pipeline secrets. See {ref}`secrets-page` for more information.

## Functions

The following functions are globally available in a Nextflow configuration file:

`env( name: String ) -> String`
: :::{versionadded} 24.11.0-edge
  :::
: Get the value of the environment variable with the specified name in the Nextflow launch environment.

(config-params)=

## Parameters

Pipeline parameters can be defined in the config file using the `params` scope:

```groovy
params.alpha = 123
params.beta = 'string value .. '

params {
    gamma = true
    delta = "params.alpha is ${params.alpha}"
}
```

See {ref}`cli-params` for information about how to specify pipeline parameters.

(config-process)=

## Process configuration

The `process` scope allows you to specify {ref}`process directives <process-reference>` separately from the pipeline code.

For example:

```groovy
process {
    executor = 'sge'
    queue = 'long'
    clusterOptions = '-pe smp 10 -l virtual_free=64G,h_rt=30:00:00'
}
```

By using this configuration, all processes in your pipeline will be executed through the SGE cluster, with the specified settings.

(config-process-selectors)=

### Process selectors

The `withLabel` selectors allow the configuration of all processes annotated with a {ref}`process-label` directive as shown below:

```groovy
process {
    withLabel: big_mem {
        cpus = 16
        memory = 64.GB
        queue = 'long'
    }
}
```

The above configuration example assigns 16 cpus, 64 Gb of memory and the `long` queue to all processes annotated with the `big_mem` label.

In the same manner, the `withName` selector allows the configuration of a specific process in your pipeline by its name. For example:

```groovy
process {
    withName: hello {
        cpus = 4
        memory = 8.GB
        queue = 'short'
    }
}
```

The `withName` selector applies both to processes defined with the same name and processes included under the same alias. For example, `withName: hello` will apply to any process originally defined as `hello`, as well as any process included under the alias `hello`.

Furthermore, selectors for the alias of an included process take priority over selectors for the original name of the process. For example, given a process defined as `hello` and included as `sayHello`, the selectors `withName: hello` and `withName: sayHello` will both be applied to the process, with the second selector taking priority over the first.

:::{tip}
Label and process names do not need to be enclosed with quotes, provided the name does not include special characters (`-`, `!`, etc) and is not a keyword or a built-in type identifier. When in doubt, you can enclose the label name or process name with single or double quotes.
:::

(config-selector-expressions)=

### Selector expressions

Both label and process name selectors allow the use of a regular expression in order to apply the same configuration to all processes matching the specified pattern condition. For example:

```groovy
process {
    withLabel: 'hello|bye' {
        cpus = 2
        memory = 4.GB
    }
}
```

The above configuration snippet requests 2 cpus and 4 GB of memory for processes labeled as `hello` or `bye`.

A process selector can be negated prefixing it with the special character `!`. For example:

```groovy
process {
    withLabel: 'hello' { cpus = 2 }
    withLabel: '!hello' { cpus = 4 }
    withName: '!align.*' { queue = 'long' }
}
```

The above configuration snippet sets 2 cpus for every process labeled as `hello` and 4 cpus to every process *not* label as `hello`. It also specifies the `long` queue for every process whose name does *not* start with `align`.

(config-selector-priority)=

### Selector priority

Process configuration settings are applied to a process in the following order (from lowest to highest priority):

1. Process configuration settings (without a selector)
2. Process directives in the process definition
3. `withLabel` selectors matching any of the process labels
4. `withName` selectors matching the process name
5. `withName` selectors matching the process included alias
6. `withName` selectors matching the process fully qualified name

For example:

```groovy
process {
    cpus = 4
    withLabel: hello { cpus = 8 }
    withName: bye { cpus = 16 }
    withName: 'mysub:bye' { cpus = 32 }
}
```

With the above configuration:
- All processes will use 4 cpus (unless otherwise specified in their process definition).
- Processes annotated with the `hello` label will use 8 cpus.
- Any process named `bye` (or imported as `bye`) will use 16 cpus.
- Any process named `bye` (or imported as `bye`) invoked by a workflow named `mysub` will use 32 cpus.

(config-profiles)=

## Config profiles

Configuration files can define one or more *profiles*. A profile is a set of configuration settings that can be selected during pipeline execution using the `-profile` command line option.

Configuration profiles are defined in the `profiles` scope. For example:

```groovy
profiles {
    standard {
        process.executor = 'local'
    }

    cluster {
        process.executor = 'sge'
        process.queue = 'long'
        process.memory = '10GB'
    }

    cloud {
        process.executor = 'cirrus'
        process.container = 'cbcrg/imagex'
        docker.enabled = true
    }
}
```

The above configuration defines three profiles: `standard`, `cluster`, and `cloud`. Each profile provides a different configuration for a given execution environment. The `standard` profile is used by default when no profile is specified.

Configuration profiles can be specified at runtime as a comma-separated list:

```bash
nextflow run <your script> -profile standard,cloud
```

Config profiles are applied in the order in which they were defined in the config file, regardless of the order they are specified on the command line.

:::{versionadded} 25.02.0-edge
When using the {ref}`strict config syntax <updating-config-syntax>`, profiles are applied in the order in which they are specified on the command line.
:::

:::{danger}
When defining a profile in the config file, avoid using both the dot and block syntax for the same scope. For example:

```groovy
profiles {
    cluster {
        process.memory = '2 GB'
        process {
            cpus = 2
        }
    }
}
```

Due to a limitation of the legacy config parser, the first setting will be overwritten by the second:

```console
$ nextflow config -profile cluster
process {
   cpus = 2
}
```

This limitation can be avoided by using the {ref}`strict config syntax <updating-config-syntax>`.
:::

## Workflow handlers

Workflow event handlers can be defined in the config file, which is useful for handling pipeline events without having to modify the pipeline code:

```groovy
workflow.onComplete = {
    // any workflow property can be used here
    println "Pipeline complete"
    println "Command line: $workflow.commandLine"
}

workflow.onError = {
    println "Error: something when wrong"
}
```

See {ref}`workflow-handlers` for more information.
